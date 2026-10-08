package com.zhku.knowledge.controlled;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 实际原Application、认证/CSRF、JDBC和两PID；不以MockMvc或同进程重建替代。 */
class ConversationHttpRestartTest {
    private static Data data;
    private static UUID retake;
    private static final String C=PREFIX+"/conversations";
    @BeforeAll static void seed() throws Exception {
        data=ControlledCampusTestFixtures.seed(); retake=UUID.randomUUID();
        ControlledConversationFacadeTest.seedRetake(data.source(),retake);
    }
    @AfterAll static void cleanup() throws Exception {
        ControlledConversationFacadeTest.cleanupConversations(data,retake); ControlledCampusTestFixtures.cleanup(data);
    }
    @Test @Timeout(30) void actualJsonHttpIsolatesUsersAndMaintainerAndKeepsOnlySixStateFields() throws Exception {
        var model=new Model();
        try(var app=ConversationHttpRestartProbe.start(data,model); var a=app.user(); var b=app.user(); var m=app.user(); var anonymous=app.user()) {
            assertThat(anonymous.send("GET",C+"/"+UUID.randomUUID(),null,false).statusCode()).isEqualTo(401);
            assertThat(anonymous.send("POST",C,"{}",false).statusCode()).isEqualTo(403);
            login(a,data.a()); login(b,data.b()); login(m,data.maintainer());
            var created=expect(a.send("POST",C,"{}",true),201);
            assertThat(created.size()).isEqualTo(6); assertThat(created.has("owner_user_id")).isFalse();
            UUID id=UUID.fromString(created.path("sessionId").asText());
            assertThat(m.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(403);
            assertThat(expect(m.send("POST",C,"{}",true),403).path("code").asText()).isEqualTo("ROLE_DENIED");
            for(String suffix:List.of("","/messages","/cancel")) {
                var response=b.send(suffix.isEmpty()?"GET":"POST",C+"/"+id+suffix,
                        suffix.isEmpty()?null:suffix.equals("/messages")?message(0,"专业选修课"):"{\"expectedRevision\":0}",!suffix.isEmpty());
                assertThat(expect(response,404).path("code").asText()).isEqualTo("SESSION_NOT_FOUND");
            }
            assertThat(a.send("POST",C+"/"+id+"/messages","{\"message\":\"专业选修课\",\"expectedRevision\":0,\"userId\":\"forged\"}",true).statusCode()).isEqualTo(400);
            assertThat(a.send("POST",C+"/"+id+"/cancel","{}",true).statusCode()).isEqualTo(400);
            assertThat(a.send("GET",C,null,false).statusCode()).isEqualTo(403);
            assertThat(a.send("GET",C+"/"+id+"/stream",null,false).statusCode()).isEqualTo(403);
            assertThat(a.send("GET",C+"/bad-uuid",null,false).statusCode()).isEqualTo(400);
            var pending=expect(a.send("POST",C+"/"+id+"/messages",message(0,"选修课挂科了可以重修吗？"),true),200);
            assertThat(pending.path("type").asText()).isEqualTo("NEED_CLARIFICATION");
            var state=expect(a.send("GET",C+"/"+id,null,false),200);
            assertThat(state.path("pendingIntent").asText()).isEqualTo("ELECTIVE_RETAKE_AFTER_FAILURE");
            assertThat(state.path("revision").asLong()).isEqualTo(1);
            var answer=expect(a.send("POST",C+"/"+id+"/messages",message(1,"专业选修课"),true),200);
            assertThat(answer.path("type").asText()).isEqualTo("ANSWER");
            assertThat(answer.path("answer").path("sourceId").asText()).isEqualTo(retake.toString());
            assertThat(answer.path("guideCard").isNull()).isTrue();
            var cleared=expect(a.send("GET",C+"/"+id,null,false),200);
            assertThat(cleared.path("pendingIntent").isNull()).isTrue(); assertThat(cleared.path("revision").asLong()).isEqualTo(2);
            assertThat(expect(a.send("GET",C+"/"+id,null,false),200)).isEqualTo(cleared);
            assertThat(model.calls.get()).isZero();
        }
    }
    @Test @Timeout(30) void realHttpCasCancelExpiryAndTtlCannotReviveOrOverwrite() throws Exception {
        var model=new Model();
        try(var app=ConversationHttpRestartProbe.start(data,model); var a=app.user()) {
            login(a,data.a()); Instant before=Instant.now(); var created=expect(a.send("POST",C,"{}",true),201); Instant after=Instant.now();
            Instant expiry=Instant.parse(created.path("expiresAt").asText());
            assertThat(expiry).isBetween(before.plusSeconds(600),after.plusSeconds(600));
            UUID id=UUID.fromString(created.path("sessionId").asText());
            assertThat(expect(a.send("POST",C+"/"+id+"/messages",message(1,"选修课挂科重修"),true),409).path("code").asText()).isEqualTo("SESSION_STATE_CHANGED");
            var ready=new CountDownLatch(2); var start=new CountDownLatch(1); var callers=Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> send=()->{ ready.countDown(); if(!start.await(5,TimeUnit.SECONDS)) throw new AssertionError("屏障超时");
                    return a.send("POST",C+"/"+id+"/messages",message(0,"选修课挂科重修"),true).statusCode(); };
                var first=callers.submit(send); var second=callers.submit(send); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
                assertThat(List.of(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            } finally { start.countDown(); callers.shutdownNow(); assertThat(callers.awaitTermination(2,TimeUnit.SECONDS)).isTrue(); }
            var pending=expect(a.send("GET",C+"/"+id,null,false),200); assertThat(pending.path("revision").asLong()).isEqualTo(1);
            var cancelled=expect(a.send("POST",C+"/"+id+"/cancel","{\"expectedRevision\":1}",true),200);
            assertThat(cancelled.path("expiresAt")).isEqualTo(pending.path("expiresAt")); assertThat(cancelled.path("revision").asLong()).isEqualTo(2);
            assertThat(expect(a.send("POST",C+"/"+id+"/cancel","{\"expectedRevision\":0}",true),200)).isEqualTo(cancelled);
            assertThat(expect(a.send("POST",C+"/"+id+"/messages",message(2,"专业选修课"),true),409).path("code").asText()).isEqualTo("SESSION_CANCELLED");
            sql("UPDATE conversation_session SET expires_at=? WHERE session_id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
            var expired=expect(a.send("GET",C+"/"+id,null,false),410);
            assertThat(expired.path("code").asText()).isEqualTo("SESSION_EXPIRED"); assertThat(expired.has("pendingIntent")).isFalse();
            sql("DELETE FROM conversation_session WHERE session_id=?",id);
            assertThat(a.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(404); assertThat(model.calls.get()).isZero();
        }
    }
    @Test @Timeout(30) void actualSourceAndSessionSqlFailureNeverDowngradeToMemoryOrOldAnswer() throws Exception {
        var model=new Model(); boolean sessionRenamed=false,sourceRenamed=false;
        try(var app=ConversationHttpRestartProbe.start(data,model); var a=app.user()) {
            login(a,data.a()); UUID id=UUID.fromString(expect(a.send("POST",C,"{}",true),201).path("sessionId").asText());
            expect(a.send("POST",C+"/"+id+"/messages",message(0,"选修课挂科重修"),true),200);
            sql("ALTER TABLE conversation_session RENAME TO conversation_session_fault_fixture"); sessionRenamed=true;
            var unavailable=expect(a.send("GET",C+"/"+id,null,false),503);
            assertThat(unavailable.path("code").asText()).isEqualTo("SESSION_STORE_UNAVAILABLE"); assertThat(unavailable.has("state")).isFalse();
            sql("ALTER TABLE conversation_session_fault_fixture RENAME TO conversation_session"); sessionRenamed=false;
            sql("ALTER TABLE knowledge_chunk RENAME TO knowledge_chunk_fault_fixture"); sourceRenamed=true;
            assertThat(expect(a.send("POST",C+"/"+id+"/messages",message(1,"专业选修课"),true),503).has("answer")).isFalse();
            sql("ALTER TABLE knowledge_chunk_fault_fixture RENAME TO knowledge_chunk"); sourceRenamed=false;
            var preserved=expect(a.send("GET",C+"/"+id,null,false),200); assertThat(preserved.path("revision").asLong()).isEqualTo(1);
            sql("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false WHERE id=?",retake);
            var withdrawn=expect(a.send("POST",C+"/"+id+"/messages",message(1,"专业选修课"),true),200);
            assertThat(withdrawn.path("type").asText()).isEqualTo("EVIDENCE_UNAVAILABLE");
            assertThat(withdrawn.path("answer").isNull()).isTrue(); assertThat(withdrawn.path("citations").isEmpty()).isTrue();
            assertThat(model.calls.get()).isZero();
        } finally {
            // 仅临时库的可逆故障注入；无论断言失败与否都恢复表名和本夹具资料许可。
            if(sessionRenamed) sql("ALTER TABLE conversation_session_fault_fixture RENAME TO conversation_session");
            if(sourceRenamed) sql("ALTER TABLE knowledge_chunk_fault_fixture RENAME TO knowledge_chunk");
            sql("UPDATE knowledge_source SET visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=true WHERE id=?",retake);
        }
    }
    @Test @Timeout(30) void revokedAndReenabledIdentityRequiresNewLoginWithStableUserId() throws Exception {
        var model=new Model();
        try(var app=ConversationHttpRestartProbe.start(data,model); var a=app.user(); var old=app.user()) {
            login(a,data.a()); UUID id=UUID.fromString(expect(a.send("POST",C,"{}",true),201).path("sessionId").asText()); a.copyCookiesTo(old);
            sql("UPDATE preview_identity SET enabled=false,auth_revision=auth_revision+1 WHERE user_id=?",data.a().id());
            assertThat(expect(a.send("GET",C+"/"+id,null,false),401).path("code").asText()).isEqualTo("AUTH_REVOKED");
            sql("UPDATE preview_identity SET enabled=true,auth_revision=auth_revision+1 WHERE user_id=?",data.a().id());
            assertThat(old.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(401);
            assertThat(expect(a.login(data.a()),200).path("userId").asText()).isEqualTo(data.a().id().toString());
            expect(a.send("GET",C+"/"+id,null,false),200); assertThat(model.calls.get()).isZero();
        } finally { sql("UPDATE preview_identity SET enabled=true,auth_revision=auth_revision+1 WHERE user_id=?",data.a().id()); }
    }
    @Test @Timeout(value=90,unit=TimeUnit.SECONDS) void differentPidsRestorePendingAfterOldCookieFailsAndOwnerRelogs() throws Exception {
        var children=new ArrayList<Child>(); Path evidence=Path.of("target","conversation-process-probes",UUID.randomUUID().toString()); Files.createDirectories(evidence);
        try {
            var old=launch(); children.add(old); int oldPort=old.ready();
            try(var a=new User(oldPort)) {
                login(a,data.a()); UUID id=UUID.fromString(expect(a.send("POST",C,"{}",true),201).path("sessionId").asText());
                expect(a.send("POST",C+"/"+id+"/messages",message(0,"选修课挂科了可以重修吗？"),true),200);
                var pending=expect(a.send("GET",C+"/"+id,null,false),200); assertThat(pending.path("revision").asLong()).isEqualTo(1);
                old.stop(); assertThat(old.process.isAlive()).isFalse();
                var next=launch(); children.add(next); int nextPort=next.ready(); assertThat(next.process.pid()).isNotEqualTo(old.process.pid());
                try(var restored=new User(nextPort); var other=new User(nextPort); var maintenance=new User(nextPort)) {
                    a.copyCookiesTo(restored); assertThat(restored.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(401);
                    assertThat(expect(restored.login(data.a()),200).path("userId").asText()).isEqualTo(data.a().id().toString());
                    login(other,data.b()); login(maintenance,data.maintainer());
                    assertThat(expect(restored.send("GET",C+"/"+id,null,false),200)).isEqualTo(pending);
                    assertThat(other.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(404);
                    assertThat(other.send("POST",C+"/"+id+"/messages",message(1,"专业选修课"),true).statusCode()).isEqualTo(404);
                    assertThat(maintenance.send("GET",C+"/"+id,null,false).statusCode()).isEqualTo(403);
                    var reply=expect(restored.send("POST",C+"/"+id+"/messages",message(1,"专业选修课"),true),200);
                    assertThat(reply.path("type").asText()).isEqualTo("ANSWER"); assertThat(reply.path("answer").path("sourceId").asText()).isEqualTo(retake.toString());
                    var cleared=expect(restored.send("GET",C+"/"+id,null,false),200);
                    assertThat(cleared.path("revision").asLong()).isEqualTo(2); assertThat(cleared.path("pendingIntent").isNull()).isTrue();
                }
                next.stop();
                JSON.writeValue(evidence.resolve("phase.json").toFile(),Map.ofEntries(Map.entry("phase","VERIFIED"),
                        Map.entry("oldPid",old.process.pid()),Map.entry("newPid",next.process.pid()),Map.entry("oldPort",oldPort),Map.entry("newPort",nextPort),
                        Map.entry("oldExited",!old.process.isAlive()),Map.entry("newExited",!next.process.isAlive()),Map.entry("stableUserId",true),
                        Map.entry("oldCookieRejected",true),Map.entry("pendingRevision",1),Map.entry("completedRevision",2),
                        Map.entry("expiryUnchangedAcrossRestart",true),Map.entry("otherUserRejected",true),Map.entry("maintainerRejected",true),
                        Map.entry("modelCalls",0),Map.entry("sessionThreadsRemaining",0),Map.entry("connectionsRemaining",0)));
            }
        } finally {
            boolean forced=false; for(var child:children) forced|=child.cleanup();
            assertThat(forced).as("finally强杀不能冒充优雅关闭验收").isFalse();
        }
    }
    static String message(long revision,String text) throws Exception { return JSON.writeValueAsString(Map.of("message",text,"expectedRevision",revision)); }
    static void login(User user,Account account) throws Exception { expect(user.login(account),200); }
    static JsonNode expect(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).as("实际HTTP状态").isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store"); return JSON.readTree(response.body());
    }
    static void sql(String sql,Object... arguments) throws Exception {
        try(var connection=data.source().getConnection(); var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(1); for(int i=0;i<arguments.length;i++) statement.setObject(i+1,arguments[i]); statement.executeUpdate();
        }
    }
    static Child launch() throws Exception {
        if(System.getenv("ZHKU_DB_PASSWORD")==null) throw new IllegalArgumentException("缺少子进程环境凭据");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),ConversationHttpRestartProbe.class.getName()).redirectErrorStream(true);
        builder.environment().put("ZHKU_DB_URL",((org.springframework.jdbc.datasource.DriverManagerDataSource)data.source()).getUrl());
        builder.environment().put("ZHKU_DB_USER","zhkuagent"); return new Child(builder.start());
    }
    static final class Child {
        final Process process; final ArrayBlockingQueue<String> phases=new ArrayBlockingQueue<>(16); final Thread reader; int port;
        Child(Process process) {
            this.process=process;
            reader=Thread.ofPlatform().name("conversation-process-output").start(()->{
                try(var lines=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                    for(String line;(line=lines.readLine())!=null;)
                        if(line.matches("CONVERSATION_(READY [0-9]+ [0-9]+|CLOSED [0-9]+ [0-9]+ [0-9]+ [0-9]+|FAILED [A-Za-z0-9]+)"))
                            if(!phases.offer(line)) return;
                } catch(IOException closed) { }
            });
        }
        int ready() throws Exception {
            var line=await("CONVERSATION_READY",Duration.ofSeconds(20)).split(" ");
            assertThat(Long.parseLong(line[1])).isEqualTo(process.pid()); return port=Integer.parseInt(line[2]);
        }
        String await(String prefix,Duration budget) throws Exception {
            long deadline=System.nanoTime()+budget.toNanos();
            do {
                String line=phases.poll(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS); if(line==null) break;
                if(line.startsWith("CONVERSATION_FAILED")) throw new AssertionError(line); if(line.startsWith(prefix)) return line;
            } while(System.nanoTime()<deadline);
            throw new AssertionError("子进程阶段预算耗尽: "+prefix);
        }
        void stop() throws Exception {
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
            assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();
            if(process.exitValue()!=0) await("CONVERSATION_FAILED",Duration.ofMillis(200)); assertThat(process.exitValue()).isZero();
            var line=await("CONVERSATION_CLOSED",Duration.ofMillis(200)).split(" ");
            for(int i=1;i<line.length;i++) assertThat(Integer.parseInt(line[i])).isZero();
            reader.join(2000); assertThat(reader.isAlive()).isFalse(); verifyPortClosed();
        }
        boolean cleanup() throws Exception {
            boolean forced=process.isAlive(); if(forced) { process.destroyForcibly(); assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue(); }
            reader.join(2000); assertThat(reader.isAlive()).isFalse(); assertThat(process.isAlive()).isFalse(); if(port!=0) verifyPortClosed(); return forced;
        }
        void verifyPortClosed() throws Exception {
            try(var socket=new java.net.Socket()) { socket.connect(new java.net.InetSocketAddress("127.0.0.1",port),200); throw new AssertionError("PID退出后监听仍存在"); }
            catch(IOException expectedClosed) { }
        }
    }
}
