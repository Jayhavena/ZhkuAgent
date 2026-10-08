package com.zhku.knowledge.controlled;

import org.junit.jupiter.api.*;
import java.util.UUID;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 实际受控Application及TCP监听；模型替身与权限/持久化证据分别表达。 */
class ControlledCampusSubmissionHttpTest {
    static Data data;
    @BeforeAll static void seed()throws Exception{data=ControlledCampusTestFixtures.seed();}
    @AfterAll static void clean()throws Exception{cleanup(data);}
    @Test void missingKeyRejectsBeforeAnyAcceptanceOrModelCall()throws Exception {
        var model=new Model();try(var app=start(data,model);var a=app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            var response=a.send("POST",PREFIX+"/runs","{\"question\":\"合成问题\",\"courses\":[]}",true);
            assertThat(response.statusCode()).isEqualTo(400);assertThat(model.calls).hasValue(0);
        }
    }
    @Test void simultaneousUsersMayUseSameKeyButCannotReadEachOthersRun()throws Exception {
        var model=new Model();try(var app=start(data,model);var a=app.user();var b=app.user();var clients=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
            var key=UUID.randomUUID();var gate=new java.util.concurrent.CountDownLatch(1);
            var first=clients.submit(()->{assertThat(gate.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return a.submitWithKey(QUESTION,COURSES,key);});
            var second=clients.submit(()->{assertThat(gate.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return b.submitWithKey(QUESTION,COURSES,key);});gate.countDown();
            var ar=first.get(10,java.util.concurrent.TimeUnit.SECONDS);var br=second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(ar.statusCode()).isEqualTo(202);assertThat(br.statusCode()).isEqualTo(202);
            var aid=UUID.fromString(JSON.readTree(ar.body()).path("runId").asText());var bid=UUID.fromString(JSON.readTree(br.body()).path("runId").asText());assertThat(aid).isNotEqualTo(bid);
            a.awaitTerminal(aid);b.awaitTerminal(bid);assertThat(a.send("GET",PREFIX+"/runs/"+bid,null,false).statusCode()).isEqualTo(404);
            assertThat(b.send("GET",PREFIX+"/runs/"+aid,null,false).statusCode()).isEqualTo(404);
            assertThat(JSON.readTree(a.send("GET",PREFIX+"/submissions/"+key,null,false).body()).path("runId").asText()).isEqualTo(aid.toString());
            assertThat(JSON.readTree(b.send("GET",PREFIX+"/submissions/"+key,null,false).body()).path("runId").asText()).isEqualTo(bid.toString());
            assertThat(model.calls).hasValue(4);assertThat(((java.util.concurrent.ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isEqualTo(2);
            System.out.println("3C-HTTP-A2 users=2 submissions=2 runs=2 dispatches=2 modelRequests=4 crossRead=404");
        }
    }
    @Test void fullUserQuotaStillConfirmsAndReplaysOriginalKeyWithoutExtraReservation()throws Exception {
        var model=new Model();model.block=true;
        try(var app=start(data,model);var a=app.user()) {
            try {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);var key=UUID.randomUUID();
                var first=a.submitWithKey(QUESTION,COURSES,key);assertThat(first.statusCode()).isEqualTo(202);
                var id=UUID.fromString(JSON.readTree(first.body()).path("runId").asText());
                var second=a.submitWithKey(QUESTION,COURSES,UUID.randomUUID());assertThat(second.statusCode()).isEqualTo(202);
                assertThat(a.submitWithKey(QUESTION,COURSES,UUID.randomUUID()).statusCode()).isEqualTo(429);
                var replay=a.submitWithKey(QUESTION,COURSES,key);assertThat(replay.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(replay.body()).path("runId").asText()).isEqualTo(id.toString());
                assertThat(a.send("GET",PREFIX+"/submissions/"+key,null,false).statusCode()).isEqualTo(200);
                assertThat(((java.util.concurrent.ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isEqualTo(2);
                model.release.countDown();a.awaitTerminal(id);a.awaitTerminal(UUID.fromString(JSON.readTree(second.body()).path("runId").asText()));
                assertThat(model.calls).hasValue(4);
                System.out.println("3C-HTTP-A3 submissions=2 runs=2 dispatches=2 modelRequests=4 replayAtFull=true");
            }finally{model.release.countDown();}
        }
    }
    @Test void submissionReplayConflictOwnedQueryAndWithdrawalRetainOriginalRun()throws Exception {
        var model=new Model();try(var app=start(data,model);var a=app.user();var b=app.user();var m=app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
            assertThat(m.login(data.maintainer()).statusCode()).isEqualTo(200);
            var key=UUID.randomUUID();var first=a.submitWithKey(QUESTION,COURSES,key);
            assertThat(first.statusCode()).isEqualTo(202);var firstBody=JSON.readTree(first.body());
            assertThat(firstBody.path("submissionKey").asText()).isEqualTo(key.toString());
            var id=UUID.fromString(firstBody.path("runId").asText());a.awaitTerminal(id);assertThat(model.calls).hasValue(2);
            var replay=a.submitWithKey(QUESTION,COURSES,key);assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(replay.body()).path("runId").asText()).isEqualTo(id.toString());
            assertThat(JSON.readTree(replay.body()).path("replayed").asBoolean()).isTrue();assertThat(model.calls).hasValue(2);
            assertThat(a.submitWithKey(QUESTION+"另一种内容",COURSES,key).statusCode()).isEqualTo(409);
            var own=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(own.statusCode()).isEqualTo(200);
            assertThat(own.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(b.send("GET",PREFIX+"/submissions/"+key,null,false).statusCode()).isEqualTo(404);
            assertThat(m.send("GET",PREFIX+"/submissions/"+key,null,false).statusCode()).isEqualTo(403);
            try(var c=data.source().getConnection();var p=c.prepareStatement("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?")) {
                p.setObject(1,data.sourceId());p.executeUpdate();
                try {
                    var hidden=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(hidden.statusCode()).isEqualTo(200);
                    // 外层acceptedAt可包含数字3.5；隐藏结果必须没有学分正文/计算载荷。
                    assertThat(hidden.body()).doesNotContain("3.5 学分","合成学分规则","totalCredits");
                    assertThat(JSON.readTree(hidden.body()).path("live").path("run").path("answer").isNull()).isTrue();
                    assertThat(JSON.readTree(hidden.body()).path("live").path("resultVisibility").asText()).isEqualTo("HIDDEN");
                    assertThat(model.calls).hasValue(2);
                }finally {try(var restore=c.prepareStatement("UPDATE knowledge_source SET visibility_scope='STUDENT_VISIBLE' WHERE id=?")){restore.setObject(1,data.sourceId());restore.executeUpdate();}}
            }
        }
    }
    @Test void actualCommitAckLossReturnsUnknownAndGetOrReplayNeverDispatch()throws Exception {
        var armed=new java.util.concurrent.atomic.AtomicBoolean(false);var model=new Model();
        try(var app=start(data,model,context->context.registerBean("submissionTestCommitFault",org.springframework.beans.factory.config.BeanPostProcessor.class,
                ()->new org.springframework.beans.factory.config.BeanPostProcessor(){
                    @Override public Object postProcessAfterInitialization(Object bean,String name) {
                        return bean instanceof javax.sql.DataSource ds
                                ?com.zhku.knowledge.campusagent.submission.CampusRunSubmissionStoreIntegrationTest.fault(ds,armed,true):bean;
                    }
                }));var a=app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);var key=UUID.randomUUID();armed.set(true);
            var unknown=a.submitWithKey(QUESTION,COURSES,key);assertThat(unknown.statusCode()).isEqualTo(503);
            var error=JSON.readTree(unknown.body());assertThat(error.path("code").asText()).isEqualTo("SUBMISSION_UNCONFIRMED");
            assertThat(error.path("acceptance").asText()).isEqualTo("UNKNOWN");assertThat(error.path("submissionKey").asText()).isEqualTo(key.toString());
            assertThat(error.path("runId").isNull()).isTrue();assertThat(error.path("accepted").isNull()).isTrue();
            var confirmed=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(confirmed.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(confirmed.body()).path("location").asText()).isEqualTo("UNCONFIRMED");
            assertThat(a.submitWithKey(QUESTION,COURSES,key).statusCode()).isEqualTo(200);assertThat(model.calls).hasValue(0);
            var worker=(java.util.concurrent.ThreadPoolExecutor)app.context.getBean("controlledCampusWorker");
            assertThat(worker.getTaskCount()).isZero();
            System.out.println("3C-HTTP-A4 committed=true dispatches=0 modelRequests=0 queried=true replayed=true");
        }
    }
    @Test void twentyRealHttpCallsHaveOneSubmissionRunAndDispatchWithTwoModelRequests()throws Exception {
        var model=new Model();try(var app=start(data,model);var a=app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);var key=UUID.randomUUID();
            try(var clients=java.util.concurrent.Executors.newFixedThreadPool(20)) {
                var ready=new java.util.concurrent.CountDownLatch(20);var go=new java.util.concurrent.CountDownLatch(1);
                var responses=new java.util.ArrayList<java.util.concurrent.Future<java.net.http.HttpResponse<String>>>();
                for(int i=0;i<20;i++)responses.add(clients.submit(()->{ready.countDown();assertThat(go.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return a.submitWithKey(QUESTION,COURSES,key);}));
                assertThat(ready.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
                var completed=new java.util.ArrayList<java.net.http.HttpResponse<String>>();
                for(var future:responses)completed.add(future.get(10,java.util.concurrent.TimeUnit.SECONDS));
                var codes=new java.util.TreeMap<String,Integer>();
                for(var response:completed){String code=response.statusCode()+":"+JSON.readTree(response.body()).path("code").asText();codes.merge(code,1,Integer::sum);}
                try(var c=data.source().getConnection();var p=c.prepareStatement("SELECT count(*),count(DISTINCT a.run_id) FROM campus_agent_run_submission s JOIN campus_agent_run_archive a ON a.run_id=s.run_id WHERE s.owner_user_id=? AND s.submission_key=?")) {
                    p.setObject(1,data.a().id());p.setObject(2,key);try(var rows=p.executeQuery()) {rows.next();
                        System.out.printf("3C-HTTP-A1-DIAG submissions=%d runs=%d dispatchedTasks=%d modelRequests=%d codes=%s%n",rows.getInt(1),rows.getInt(2),
                                ((java.util.concurrent.ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount(),model.calls.get(),codes);
                    }
                }
                var ids=new java.util.HashSet<String>();int created=0;
                for(var response:completed) {
                    var body=JSON.readTree(response.body());
                    assertThat(response.statusCode()).as("正常同键并发必须全部确认原Run").isIn(200,202);

                    if(response.statusCode()==202)created++;
                    ids.add(body.path("runId").asText());
                }
                assertThat(created).isEqualTo(1);assertThat(ids).hasSize(1);var id=UUID.fromString(ids.iterator().next());a.awaitTerminal(id);
                assertThat(model.calls).hasValue(2);
                var worker=(java.util.concurrent.ThreadPoolExecutor)app.context.getBean("controlledCampusWorker");assertThat(worker.getTaskCount()).isEqualTo(1);
                try(var c=data.source().getConnection();var p=c.prepareStatement("SELECT count(*),count(DISTINCT run_id) FROM campus_agent_run_submission WHERE owner_user_id=? AND submission_key=?")) {
                    p.setObject(1,data.a().id());p.setObject(2,key);try(var r=p.executeQuery()){r.next();assertThat(r.getInt(1)).isEqualTo(1);assertThat(r.getInt(2)).isEqualTo(1);}
                }
                System.out.println("3C-HTTP-A1 submissions=1 runs=1 dispatches=1 modelRequests=2");
            }
        }
    }
}
