package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** T08-2A：运行轨迹只允许白名单投影，且只由赢得发布的当前ticket落账。 */
@Timeout(15)
class RuleAgentRunTraceTest {
    final Owner owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
    final Queue<Runnable> queued=new ConcurrentLinkedQueue<>();
    final Deque<RuleAgentRunner.Outcome> outcomes=new ArrayDeque<>();
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-13T08:00:00Z"));
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};

    RuleAgentTaskStore store(){
        return new RuleAgentTaskStore(queued::add,this::work,clock,new RuleAgentTaskStore.Limits(8,4,32));
    }

    RuleAgentTaskStore.Work work(Owner actor,UUID task){
        return new RuleAgentTaskStore.Work(){
            public RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input){
                var outcome=outcomes.removeFirst();
                if(!context.start())throw new IllegalStateException("运行未启动");
                if(!context.finish(outcome.snapshot().runState()))throw new IllegalStateException("运行未收敛");
                context.cleanupOnce(()->{});
                context.signalTermination();context.publishTermination();
                return outcome;
            }
            public boolean revalidate(ResultEnvelope previous){return true;}
        };
    }

    void drain(){
        Runnable r;
        while((r=queued.poll())!=null){
            r.run();
            // AgentRunTask是FutureTask，直接run()会吞掉异常；测试必须取出真实失败。
            if(r instanceof FutureTask<?> task){
                try{task.get();}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                catch(ExecutionException e){throw new IllegalStateException(e.getCause());}
            }
        }
    }

    @Test void traceTurnIdUsesAcceptedTicketRatherThanRunnerSnapshotTurnId() {
        try (var s = store()) {
            var accepted = s.create(owner, new CreateTask("课程类别是什么？", UUID.randomUUID()));
            UUID runnerGeneratedTurnId = UUID.randomUUID();
            assertThat(runnerGeneratedTurnId).isNotEqualTo(accepted.turnId());

            // Mirrors RuleAgentRunner.run(), whose TaskSnapshot currently generates its own turnId.
            outcomes.add(resolvedOutcome(accepted.taskId(), runnerGeneratedTurnId, accepted.runId(),
                    accepted.version() + 1, List.of(event(1, "FINISH_CHECK", null))));
            drain();

            var published = s.snapshot(owner, accepted.taskId());
            var traces = s.traceHistory(owner, accepted.taskId());
            assertThat(published.turnId()).isEqualTo(accepted.turnId());
            assertThat(traces).singleElement()
                    .extracting(RuleAgentRunTrace::turnId)
                    .isEqualTo(accepted.turnId());
            assertThat(traces.getFirst().turnId()).isNotEqualTo(runnerGeneratedTurnId);
        }
    }
    @Test void currentRunTraceUsesAllowlistedProjectionAndKeepsEarlierClarificationTurn() throws Exception {
        try(var s=store()){
            var first=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(waitingOutcome(first.taskId(),first.turnId(),first.runId(),first.version()+1,
                    List.of(event(1,"SEARCH_REVIEWED_RULES",candidate("SECRET-CANDIDATE-EXCERPT")),
                            event(2,"READ_VERIFIED_RULE",null),
                            event(3,"SECRET-ACTION-should-not-leak",null))));
            drain();
            assertThat(s.snapshot(owner,first.taskId()).taskStatus()).isEqualTo(TaskStatus.WAITING_USER);

            var before=s.snapshot(owner,first.taskId());
            var second=s.message(owner,first.taskId(),new Message("A",before.continuationToken(),before.version(),UUID.randomUUID()));
            outcomes.add(resolvedOutcome(second.taskId(),second.turnId(),second.runId(),second.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();

            var traces=s.traceHistory(owner,first.taskId());
            assertThat(traces).hasSize(2);
            assertThat(traces).extracting(RuleAgentRunTrace::runId)
                    .containsExactly(first.runId(),second.runId());
            assertThat(traces).extracting(RuleAgentRunTrace::turnId)
                    .containsExactly(first.turnId(),second.turnId());
            assertThat(traces.get(0).taskStatus()).isEqualTo("WAITING_USER");
            assertThat(traces.get(1).taskStatus()).isEqualTo("RESOLVED");
            assertThat(traces.get(0).version()).isEqualTo(first.version()+1);
            assertThat(traces.get(1).version()).isEqualTo(second.version()+1);
            assertThat(traces.get(0).exceptionalFailure()).isFalse();
            assertThat(traces.get(1).truncated()).isFalse();

            var actions=traces.get(1).executionActions();
            assertThat(actions).singleElement().satisfies(a->{
                assertThat(a.type()).isEqualTo("FINISH_CHECK");
                assertThat(a.actionId()).isEqualTo(traces.get(1).actions().getFirst().actionId());
            });
            var observations=traces.get(1).executionObservations();
            assertThat(observations).singleElement().satisfies(o->{
                assertThat(o.status()).isEqualTo(RuleAgentRunTrace.NOT_OBSERVED);
                assertThat(o.dataLevel()).isNull();
            });

            // 原始候选原文、题面、Event.action原始串都不能进入轨迹序列化结果。
            String json=new ObjectMapper().writeValueAsString(traces);
            assertThat(json).doesNotContain("SECRET-CANDIDATE-EXCERPT");
            assertThat(json).doesNotContain("课程类别是什么？");
            assertThat(json).doesNotContain("SECRET-ACTION-should-not-leak");
        }
    }

    @Test void perTurnResultProjectionSurvivesClarification() throws Exception {
        try (var s=store()) {
            var first=s.create(owner,new CreateTask("TEST-SAFE-QUESTION",UUID.randomUUID()));
            outcomes.add(waitingOutcome(first.taskId(),first.turnId(),first.runId(),first.version()+1,
                    List.of(event(1,"ASK_NECESSARY_CONDITION",null))));
            drain();
            var before=s.snapshot(owner,first.taskId());
            var next=s.message(owner,first.taskId(),
                    new Message("A",before.continuationToken(),before.version(),UUID.randomUUID()));
            outcomes.add(resolvedOutcome(next.taskId(),next.turnId(),next.runId(),next.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();
            var traces=s.traceHistory(owner,first.taskId());
            assertThat(traces).hasSize(2);
            assertThat(traces.get(0).result().coverage()).isEqualTo("NONE");
            assertThat(traces.get(0).result().issues().getFirst().kind()).isEqualTo("CLARIFICATION");
            assertThat(traces.get(1).result().coverage()).isEqualTo("COMPLETE");
            assertThat(traces.get(1).result().evidence().getFirst().id()).isEqualTo("e1");
            assertThat(traces.get(1).result().evidenceRelations().getFirst().targetId()).isEqualTo("c1");
            assertThat(traces.get(1).result().items().getFirst().claims().getFirst().id()).isEqualTo("c1");
        }
    }

    @Test void resultProjectionExcludesSensitivePayloads() throws Exception {
        try (var s=store()) {
            var a=s.create(owner,new CreateTask("PRIVATE-QUESTION-NONCE",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();
            var result=s.traceHistory(owner,a.taskId()).getFirst().result();
            assertThat(result.items().getFirst().claims().getFirst().factId()).startsWith("sha256:");
            assertThat(result.evidence().getFirst().revision()).isEqualTo("2025.9.2");
            String json=new ObjectMapper().writeValueAsString(s.traceHistory(owner,a.taskId()));
            assertThat(json).doesNotContain("PRIVATE-QUESTION-NONCE","synthetic-evidence",
                    "synthetic claim","synthetic-catalogue","originalToken",
                    "continuationToken","checksum");
            assertThat(json).doesNotContain("\"factId\":\"fact\"");
        }
    }
    @Test void eventSequenceIsIndependentOfAttempt() {
        try (var s=store()) {
            var a=s.create(owner,new CreateTask("TEST-SEQUENCE",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"SEARCH_REVIEWED_RULES",null),
                            event(1,"READ_VERIFIED_RULE",null),
                            event(2,"PUBLICATION_REVIEW",null))));
            drain();
            var trace=s.traceHistory(owner,a.taskId()).getFirst();
            assertThat(trace.actions()).extracting(RuleAgentRunTrace.Action::step)
                    .containsExactly(1,2,3);
            assertThat(trace.actions()).extracting(RuleAgentRunTrace.Action::modelAttempt)
                    .containsExactly(1,1,2);
            assertThat(trace.executionActions()).extracting(RuleAgentRunTrace.ActionView::step)
                    .containsExactly(1,2,3);
            assertThat(trace.executionActions()).extracting(RuleAgentRunTrace.ActionView::modelAttempt)
                    .containsExactly(1,1,2);
            assertThat(trace.actions()).extracting(RuleAgentRunTrace.Action::actionId)
                    .doesNotHaveDuplicates();
        }
    }
    @Test void noObservationIsExplicitNotSynthesizedSuccessAndRawActionIsRejected() throws Exception {
        try(var s=store()){
            var a=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"SECRET-ACTION-should-not-leak",null),
                            event(2,"SEARCH_REVIEWED_RULES",candidate("SECRET-CANDIDATE-EXCERPT")))));
            drain();

            var trace=s.traceHistory(owner,a.taskId()).getFirst();
            assertThat(trace.actions().get(0).type()).isEqualTo("INVALID");
            assertThat(trace.executionObservations().get(0).status()).isEqualTo(RuleAgentRunTrace.NOT_OBSERVED);
            assertThat(trace.executionObservations().get(0).reasonCode()).isNull();
            assertThat(trace.executionObservations().get(1).status()).isEqualTo("SUCCEEDED");
            assertThat(trace.executionObservations().get(1).dataLevel()).isEqualTo("CANDIDATE");
            String json=new ObjectMapper().writeValueAsString(trace);
            assertThat(json).doesNotContain("SECRET-CANDIDATE-EXCERPT");
            assertThat(json).doesNotContain("SECRET-ACTION-should-not-leak");
        }
    }

    @Test void cancelledRunIsMarkedExceptionalWithEmptyTraceAndCannotBeResumed(){
        try(var s=store()){
            var a=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            s.cancel(owner,a.taskId(),new Cancel(a.runId(),a.version(),UUID.randomUUID()));
            drain();

            var traces=s.traceHistory(owner,a.taskId());
            assertThat(traces).hasSize(1);
            var trace=traces.getFirst();
            assertThat(trace.runId()).isEqualTo(a.runId());
            assertThat(trace.taskStatus()).isEqualTo("STOPPED");
            assertThat(trace.exceptionalFailure()).isTrue();
            assertThat(trace.actions()).isEmpty();
            assertThat(trace.executionActions()).isEmpty();
            assertThat(trace.executionObservations()).isEmpty();

            var stopped=s.snapshot(owner,a.taskId());
            assertThat(stopped.taskStatus()).isEqualTo(TaskStatus.STOPPED);
            assertThatThrownBy(()->s.message(owner,a.taskId(),
                    new Message("A",UUID.randomUUID(),stopped.version(),UUID.randomUUID())))
                    .isInstanceOf(RuleAgentTaskStore.Failure.class);
            assertThat(s.traceHistory(owner,a.taskId())).hasSize(1);
        }
    }

    @Test void staleOutcomeForPreviousRunCannotAppendToNewerAcceptedResult(){
        try(var s=store()){
            var first=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(waitingOutcome(first.taskId(),first.turnId(),first.runId(),first.version()+1,
                    List.of(event(1,"SEARCH_REVIEWED_RULES",candidate("first-run")))));
            drain();
            var before=s.snapshot(owner,first.taskId());
            var second=s.message(owner,first.taskId(),new Message("A",before.continuationToken(),before.version(),UUID.randomUUID()));
            // 用上一轮runId伪造迟到产物：当前ticket必须拒绝，且不得追加轨迹。
            outcomes.add(resolvedOutcome(first.taskId(),first.turnId(),first.runId(),second.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));

            assertThatThrownBy(this::drain).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(RuleAgentTaskStore.Failure.class);
            var traces=s.traceHistory(owner,first.taskId());
            assertThat(traces).hasSize(1);
            assertThat(traces.getFirst().runId()).isEqualTo(first.runId());
            assertThat(traces).noneMatch(t->t.runId().equals(second.runId()));
        }
    }

    @Test void eventProjectionAndPerTaskTraceCountAreBounded(){
        try(var s=store()){
            var a=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            var events=new ArrayList<RuleAgentRunner.Event>();
            for(int i=1;i<=RuleAgentRunTrace.MAX_ACTIONS+6;i++)events.add(event(i,"SEARCH_REVIEWED_RULES",null));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,events));
            drain();

            var trace=s.traceHistory(owner,a.taskId()).getFirst();
            assertThat(trace.actions()).hasSize(RuleAgentRunTrace.MAX_ACTIONS);
            assertThat(trace.truncated()).isTrue();
        }

        // 4条上限直接验证淘汰最旧；正常API最多3轮，上限用于防御未来扩展。
        var task=UUID.randomUUID();var retained=List.<RuleAgentRunTrace>of();
        for(int i=1;i<=RuleAgentTaskStore.MAX_TRACES_PER_TASK+1;i++){
            retained=RuleAgentTaskStore.retainTrace(retained,new RuleAgentRunTrace(task,UUID.randomUUID(),
                    UUID.randomUUID(),i,"RESOLVED","SUCCEEDED",false,false,List.of(),
                    new RuleAgentRunTrace.ResultView("NONE",List.of(),List.of(),List.of(),List.of())));
        }
        assertThat(retained).hasSize(RuleAgentTaskStore.MAX_TRACES_PER_TASK);
        assertThat(retained).extracting(RuleAgentRunTrace::version).containsExactly(2L,3L,4L,5L);
    }

    @Test void tracesAreDeletedWithTaskExpiryAndStoreClose(){
        var expiryStore=store();
        try{
            var a=expiryStore.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();
            assertThat(expiryStore.traceHistory(owner,a.taskId())).hasSize(1);
            now.set(now.get().plusMillis(DEFAULT_BUDGET.taskTtlMillis()));
            assertThatThrownBy(()->expiryStore.traceHistory(owner,a.taskId()))
                    .isInstanceOfSatisfying(RuleAgentTaskStore.Failure.class,
                            f->assertThat(f.code).isEqualTo(RuleAgentTaskStore.Code.EXPIRED));
        }finally{expiryStore.close();}

        var closeStore=store();
        try{
            var a=closeStore.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();
            assertThat(closeStore.traceHistory(owner,a.taskId())).hasSize(1);
            closeStore.close();
            assertThatThrownBy(()->closeStore.traceHistory(owner,a.taskId()))
                    .isInstanceOfSatisfying(RuleAgentTaskStore.Failure.class,
                            f->assertThat(f.code).isEqualTo(RuleAgentTaskStore.Code.CLOSED));
        }finally{closeStore.close();}
    }

    @Test void traceHistoryIsDeeplyImmutableAndOwnerIsolated(){
        try(var s=store()){
            var a=s.create(owner,new CreateTask("课程类别是什么？",UUID.randomUUID()));
            outcomes.add(resolvedOutcome(a.taskId(),a.turnId(),a.runId(),a.version()+1,
                    List.of(event(1,"FINISH_CHECK",null))));
            drain();

            var traces=s.traceHistory(owner,a.taskId());
            assertThatThrownBy(()->traces.add(traces.getFirst())).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(()->traces.getFirst().actions().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(s.traceHistory(owner,a.taskId())).hasSize(1);

            var foreignSession=new Owner(owner.actorId(),UUID.randomUUID());
            assertThatThrownBy(()->s.traceHistory(foreignSession,a.taskId()))
                    .isInstanceOf(RuleAgentTaskStore.Failure.class);
            var foreignActor=new Owner(UUID.randomUUID(),owner.sessionNonce());
            assertThatThrownBy(()->s.traceHistory(foreignActor,a.taskId()))
                    .isInstanceOf(RuleAgentTaskStore.Failure.class);
            assertThat(s.traceHistory(owner,a.taskId())).hasSize(1);
        }
    }

    static RuleAgentRunner.Event event(int attempt,String action,ToolObservation observation){
        return new RuleAgentRunner.Event(attempt,action,observation,null);
    }

    static ToolObservation candidate(String excerpt){
        return new ToolObservation(UUID.randomUUID(),UUID.randomUUID(),1,ObservationStatus.SUCCEEDED,null,
                DataLevel.CANDIDATE,"2025.9.2",
                List.of(new Candidate(UUID.randomUUID(),excerpt,"2025.9.2")),List.of(),List.of());
    }

    static RuleAgentRunner.Outcome resolvedOutcome(UUID task,UUID turn,UUID run,long version,
            List<RuleAgentRunner.Event> events){
        String quote="synthetic-evidence";
        var claim=new Claim("c1","fact","synthetic claim",Map.of(),ConditionSource.USER_DECLARED);
        var sub=new Subquestion("q1",Coverage.COMPLETE,List.of(claim),List.of(),List.of());
        var evidence=new Evidence("e1",UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","a".repeat(64),
                0,quote.length(),quote,UUID.randomUUID());
        var relation=new EvidenceRelation("e1","q1",EvidenceRole.CLAIM,"c1");
        var result=new ResultEnvelope(version,run,"synthetic-catalogue","2025.9.2",Coverage.COMPLETE,
                List.of(sub),List.of(evidence),List.of(relation));
        return new RuleAgentRunner.Outcome(new TaskSnapshot(SCHEMA_VERSION,task,turn,run,version,
                TaskStatus.RESOLVED,AgentRunState.SUCCEEDED,true,result,null,
                new Execution(Mode.ONLINE,1,1,false,AnswerSource.VERIFIED_RULE_ONLY,
                        ReplayCoverage.NOT_APPLICABLE)),events);
    }

    static RuleAgentRunner.Outcome waitingOutcome(UUID task,UUID turn,UUID run,long version,
            List<RuleAgentRunner.Event> events){
        var issue=new Issue("i1",IssueKind.CLARIFICATION,ReasonCode.MISSING_CONDITION,
                "需要补充条件","请补充课程类别");
        var clarification=new Clarification("kind","课程类别？",List.of("A","B"));
        var sub=new Subquestion("q1",Coverage.NONE,List.of(),List.of(issue),List.of(clarification));
        var result=new ResultEnvelope(version,run,"synthetic-catalogue","2025.9.2",Coverage.NONE,
                List.of(sub),List.of(),List.of());
        return new RuleAgentRunner.Outcome(new TaskSnapshot(SCHEMA_VERSION,task,turn,run,version,
                TaskStatus.WAITING_USER,AgentRunState.INCOMPLETE,true,result,UUID.randomUUID(),
                new Execution(Mode.ONLINE,1,1,false,AnswerSource.VERIFIED_RULE_ONLY,
                        ReplayCoverage.NOT_APPLICABLE)),events);
    }
}
