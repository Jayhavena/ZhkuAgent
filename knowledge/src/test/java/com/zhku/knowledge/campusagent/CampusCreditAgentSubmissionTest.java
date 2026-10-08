package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.campusagent.submission.*;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmissionStoreIntegrationTest.Env;
import com.zhku.knowledge.tools.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CampusCreditAgentSubmissionTest {
    @Test void unknownAdmissionKeepsReservationUntilShutdownSealsWrites()throws Exception {
        var armed=new java.util.concurrent.atomic.AtomicBoolean(false);
        try(var env=new Env(ds->CampusRunSubmissionStoreIntegrationTest.fault(ds,armed,true))) {
            var budget=new CampusAdmissionBudget(2,18);var dispatches=new AtomicInteger();var model=mock(ChatModel.class);
            var service=new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->null,3);
            var timer=Executors.newSingleThreadScheduledExecutor();var manager=new CampusCreditAgentManager(service,r->dispatches.incrementAndGet(),timer,Duration.ofSeconds(30),env.store,budget,Duration.ofSeconds(10));
            try {
                var req=new CampusCreditAgentService.Request("合成问题",List.of());var fp=CampusRequestFingerprint.of(req);var key=UUID.randomUUID();armed.set(true);
                assertThatThrownBy(()->manager.submitOwned(env.a,key,req,fp)).isInstanceOf(CampusRunSubmission.Failure.class)
                        .extracting(e->((CampusRunSubmission.Failure)e).code()).isEqualTo(CampusRunSubmission.Code.SUBMISSION_UNCONFIRMED);
                assertThat(budget.inFlight(env.a.userId())).isEqualTo(1);assertThat(env.store.isAccepting()).isFalse();
                assertThat(manager.submitOwned(env.a,key,req,fp).claim().decision()).isEqualTo(CampusRunSubmission.Decision.REPLAYED);
                assertThat(budget.inFlight()).isEqualTo(1);assertThat(dispatches).hasValue(0);verifyNoInteractions(model);
                manager.close(Duration.ZERO);assertThat(budget.inFlight()).isZero();
            }finally{manager.close(Duration.ZERO);timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void twentyConcurrentRequestsCreateOneRunAndOneTaskWithNoReplayDispatch(boolean normal)throws Exception {
        try(var env=new Env(null)) {
            var calls=new AtomicInteger();var dispatches=new AtomicInteger();var model=mock(ChatModel.class);
            String courses="{\"courses\":[{\"courseId\":\"A\",\"category\":\"公共选修课\",\"credits\":1.5}]}";
            when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(i->{
                calls.incrementAndGet();if(!normal)throw new IllegalStateException("合成模型失败");
                boolean calculated=((org.springframework.ai.chat.prompt.Prompt)i.getArgument(0)).getContents().contains("政策查询结果：");
                return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("",Map.of(),
                        List.of(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall("test-"+UUID.randomUUID(),"function",
                                calculated?"summarize_course_credits":"lookup_public_elective_credit_limit",calculated?courses:"{}"))))));
            });
            var policy=new com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationReply(
                    com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType.ANSWER,"合成上限4学分",
                    new com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer("合成上限4学分","合成范围",UUID.randomUUID(),
                            "合成政策","v1","第1条","合成学生"),com.zhku.knowledge.routing.AnswerRoute.HIGH_RISK_RULE,false,List.of(),List.of());
            var service=new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->policy,3);
            var worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16));
            var timer=Executors.newSingleThreadScheduledExecutor();var clients=Executors.newFixedThreadPool(20);
            Executor counted=r->{dispatches.incrementAndGet();worker.execute(r);};
            var manager=new CampusCreditAgentManager(service,counted,timer,Duration.ofSeconds(30),env.store);
            try {
                var request=new CampusCreditAgentService.Request("合成问题",normal?List.of(new CampusCreditAgentService.CourseInput("A","公共选修课",new java.math.BigDecimal("1.5"))):List.of());var key=UUID.randomUUID();
                var fp=CampusRequestFingerprint.of(request);var ready=new CountDownLatch(20);var go=new CountDownLatch(1);
                var results=new ArrayList<Future<CampusCreditAgentManager.Submitted>>();
                for(int i=0;i<20;i++)results.add(clients.submit(()->{ready.countDown();assertThat(go.await(2,TimeUnit.SECONDS)).isTrue();return manager.submitOwned(env.a,key,request,fp);}));
                assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();go.countDown();
                var values=new ArrayList<CampusCreditAgentManager.Submitted>();for(var f:results)values.add(f.get(5,TimeUnit.SECONDS));
                var id=values.getFirst().claim().record().runId();
                assertThat(values).extracting(v->v.claim().record().runId()).containsOnly(id);
                assertThat(values.stream().filter(v->v.claim().decision()==CampusRunSubmission.Decision.CREATED)).hasSize(1);
                assertThat(dispatches).hasValue(1);assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);
                assertThat(env.count("campus_agent_run_archive")).isEqualTo(1);
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(!manager.findOwned(env.a,id).terminated()&&System.nanoTime()<until)Thread.sleep(1);
                assertThat(manager.findOwned(env.a,id).terminated()).isTrue();assertThat(calls).hasValue(normal?2:1);
                assertThat(manager.findOwned(env.a,id).state()).isEqualTo(normal?com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED:com.zhku.agent.agent.runtime.AgentRunState.FAILED);
                manager.submitOwned(env.a,key,request,fp);assertThat(dispatches).hasValue(1);
                System.out.printf("3C-A1 submissions=%d runs=%d dispatches=%d modelRequests=%d%n",env.count("campus_agent_run_submission"),env.count("campus_agent_run_archive"),dispatches.get(),calls.get());
            } finally {clients.shutdownNow();worker.shutdownNow();timer.shutdownNow();
                assertThat(clients.awaitTermination(2,TimeUnit.SECONDS)).isTrue();assertThat(worker.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
                assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();manager.close();}
        }
    }
    @Test void actualCommitUnknownNeverDispatchesEvenWhenRecordCanBeReadAndReposted()throws Exception {
        for(boolean after:new boolean[]{false,true}) {
            var armed=new java.util.concurrent.atomic.AtomicBoolean(false);
            try(var env=new Env(ds->CampusRunSubmissionStoreIntegrationTest.fault(ds,armed,after))) {
                var dispatches=new AtomicInteger();var calls=new AtomicInteger();var model=mock(ChatModel.class);
                var service=new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->null,3);
                var timer=Executors.newSingleThreadScheduledExecutor();
                var manager=new CampusCreditAgentManager(service,r->dispatches.incrementAndGet(),timer,Duration.ofSeconds(30),env.store);
                try {
                    var request=new CampusCreditAgentService.Request("合成问题",List.of());var fp=CampusRequestFingerprint.of(request);var key=UUID.randomUUID();
                    armed.set(true);
                    assertThatThrownBy(()->manager.submitOwned(env.a,key,request,fp)).isInstanceOf(CampusRunSubmission.Failure.class)
                            .extracting(e->((CampusRunSubmission.Failure)e).code()).isEqualTo(CampusRunSubmission.Code.SUBMISSION_UNCONFIRMED);
                    var row=env.store.findSubmissionOwned(env.a,key);assertThat(row.isPresent()).isEqualTo(after);
                    if(after)assertThat(manager.submitOwned(env.a,key,request,fp).claim().decision()).isEqualTo(CampusRunSubmission.Decision.REPLAYED);
                    assertThat(dispatches).hasValue(0);verifyNoInteractions(model);
                    System.out.printf("3C-A4 afterCommit=%s submissions=%d runs=%d dispatches=%d modelRequests=0%n",after,env.count("campus_agent_run_submission"),env.count("campus_agent_run_archive"),dispatches.get());
                }finally{timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();manager.close(Duration.ZERO);}
            }
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"CONTEXT","TIMER","EXECUTOR"})
    void confirmedAdmissionCannotBeRedispatchedAfterAssemblyOrExecutorRejection(String point)throws Exception {
        try(var env=new Env(null)) {
            var dispatches=new AtomicInteger();var model=mock(ChatModel.class);
            var service=point.equals("CONTEXT")?mock(CampusCreditAgentService.class):
                    new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->null,3);
            if(point.equals("CONTEXT"))when(service.createContext(any(UUID.class),any(CampusCreditAgentService.Request.class)))
                    .thenThrow(new IllegalStateException("SYNTHETIC_CONTEXT_FAILURE"));
            var timer=Executors.newSingleThreadScheduledExecutor();if(point.equals("TIMER"))timer.shutdown();
            Executor worker=r->{dispatches.incrementAndGet();throw new RejectedExecutionException("SYNTHETIC_EXECUTOR_FAILURE");};
            var manager=new CampusCreditAgentManager(service,worker,timer,Duration.ofSeconds(30),env.store);
            try {
                var request=new CampusCreditAgentService.Request("合成问题",List.of());var fp=CampusRequestFingerprint.of(request);var key=UUID.randomUUID();
                var first=manager.submitOwned(env.a,key,request,fp);var replay=manager.submitOwned(env.a,key,request,fp);
                assertThat(first.claim().decision()).isEqualTo(CampusRunSubmission.Decision.CREATED);
                assertThat(replay.claim().decision()).isEqualTo(CampusRunSubmission.Decision.REPLAYED);
                assertThat(replay.claim().record().runId()).isEqualTo(first.claim().record().runId());
                var archive=env.store.findOwned(env.a,first.claim().record().runId()).orElseThrow().archive();
                assertThat(archive.archiveStatus()).isEqualTo(com.zhku.knowledge.campusagent.archive.CampusRunArchive.ArchiveStatus.FINALIZED);
                assertThat(archive.execution().state()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.FAILED);
                assertThat(archive.execution().failureCategory()).isEqualTo("EXECUTOR_REJECTED");
                assertThat(dispatches).hasValue(point.equals("EXECUTOR")?1:0);verifyNoInteractions(model);
                assertThat(env.count("campus_agent_run_submission")).isEqualTo(1);assertThat(env.count("campus_agent_run_archive")).isEqualTo(1);
            }finally{timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();manager.close(Duration.ZERO);}
        }
    }
    @Test void resultCacheEvictionKeepsPersistentKeyAndArchivedProjectionWithoutNewExecution()throws Exception {
        try(var env=new Env(null)) {
            var calls=new AtomicInteger();org.springframework.ai.chat.model.ChatModel model=p->{calls.incrementAndGet();throw new IllegalStateException("SYNTHETIC_FAILURE");};
            var service=new CampusCreditAgentService(model,new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->null,3);
            var timer=Executors.newSingleThreadScheduledExecutor();
            var manager=new CampusCreditAgentManager(service,Runnable::run,timer,Duration.ofSeconds(30),1,Duration.ofMinutes(10),java.time.Clock.systemUTC(),env.store);
            var identities=mock(com.zhku.knowledge.security.PreviewIdentityRepository.class);
            var access=new com.zhku.knowledge.controlled.CampusRunAccessService(manager,env.store,identities);
            try {
                var request=new CampusCreditAgentService.Request("合成问题",List.of());var key=UUID.randomUUID();
                var first=access.submit(env.a,key,request);access.submit(env.a,UUID.randomUUID(),request);
                assertThatThrownBy(()->manager.findOwned(env.a,first.runId())).isInstanceOf(IllegalArgumentException.class);
                var restored=access.findSubmission(env.a,key);
                assertThat(restored.location()).isEqualTo("ARCHIVED");assertThat(restored.live()).isNull();
                assertThat(restored.archived().runId()).isEqualTo(first.runId());assertThat(restored.expiresAt()).isNotNull();
                assertThat(access.submit(env.a,key,request).replayed()).isTrue();assertThat(calls).hasValue(2);
            }finally{timer.shutdownNow();assertThat(timer.awaitTermination(2,TimeUnit.SECONDS)).isTrue();manager.close(Duration.ZERO);}
        }
    }
}
