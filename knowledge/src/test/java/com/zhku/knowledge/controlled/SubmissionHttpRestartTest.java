package com.zhku.knowledge.controlled;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@Timeout(90)
class SubmissionHttpRestartTest {
    static Data data;
    @BeforeAll static void seedData()throws Exception{data=seed();}
    @AfterAll static void clear()throws Exception{cleanup(data);}
    @Test void confirmedCommitBeforeDispatchCrashesAndDifferentPidReportsInterruptedWithoutReplay()throws Exception {
        var children=new ArrayList<Child>();var key=UUID.randomUUID();UUID id;
        try(var pool=Executors.newSingleThreadExecutor()) {
            var old=launch("BEFORE_DISPATCH");children.add(old);var oldPort=old.ready();
            try(var a=new User(oldPort)) {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                var post=pool.submit(()->a.submitWithKey(QUESTION,COURSES,key));old.await("SUBMISSION_COMMITTED",Duration.ofSeconds(5));
                try(var c=data.source().getConnection();var p=c.prepareStatement("SELECT run_id FROM campus_agent_run_submission WHERE owner_user_id=? AND submission_key=?")) {
                    p.setObject(1,data.a().id());p.setObject(2,key);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();id=r.getObject(1,UUID.class);assertThat(r.next()).isFalse();}
                }
                // 指定故障窗口的强制崩溃，绝不作为正常关闭/取消验收。
                old.process.destroyForcibly();assertThat(old.process.waitFor(5,TimeUnit.SECONDS)).isTrue();
                try{post.get(6,TimeUnit.SECONDS);fail("崩溃后的原POST不能正常返回");}catch(ExecutionException expected){assertThat(expected.getCause()).isInstanceOf(IOException.class);}
                var next=launch("NORMAL");children.add(next);var nextPort=next.ready();assertThat(next.process.pid()).isNotEqualTo(old.process.pid());
                try(var restored=new User(nextPort);var b=new User(nextPort)) {
                    a.copyCookiesTo(restored);assertThat(restored.send("GET",PREFIX+"/auth/me",null,false).statusCode()).isEqualTo(401);
                    assertThat(restored.login(data.a()).statusCode()).isEqualTo(200);assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
                    var reply=restored.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(reply.statusCode()).isEqualTo(200);
                    var body=JSON.readTree(reply.body());assertThat(body.path("runId").asText()).isEqualTo(id.toString());
                    assertThat(body.path("location").asText()).isEqualTo("ARCHIVED");assertThat(body.path("archived").path("archiveStatus").asText()).isEqualTo("INTERRUPTED");
                    assertThat(body.path("archived").path("execution").isNull()).isTrue();assertThat(body.path("archived").path("result").isNull()).isTrue();
                    assertThat(b.send("GET",PREFIX+"/submissions/"+key,null,false).statusCode()).isEqualTo(404);
                    assertThat(restored.submitWithKey(QUESTION,COURSES,key).statusCode()).isEqualTo(200);
                }
                next.stop("STOP");assertThat(next.calls).isZero();save("COMMIT_BEFORE_DISPATCH",old,next,key,0,true);
            }
        }finally{for(var child:children)child.cleanup();}
    }
    @Test void finalizedKeySurvivesRestartButCurrentSourceWithdrawalHidesBodyAndQAndEofExit()throws Exception {
        var children=new ArrayList<Child>();var key=UUID.randomUUID();
        try {
            var old=launch("NORMAL");children.add(old);var oldPort=old.ready();UUID id;
            try(var a=new User(oldPort)) {assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                var first=a.submitWithKey(QUESTION,COURSES,key);assertThat(first.statusCode()).isEqualTo(202);
                id=UUID.fromString(JSON.readTree(first.body()).path("runId").asText());a.awaitTerminal(id);}
            old.stop("q");assertThat(old.calls).isEqualTo(2);
            var next=launch("NORMAL");children.add(next);int port=next.ready();assertThat(next.process.pid()).isNotEqualTo(old.process.pid());
            try(var a=new User(port)) {assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                var response=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(response.statusCode()).isEqualTo(200);
                var body=JSON.readTree(response.body());assertThat(body.path("runId").asText()).isEqualTo(id.toString());
                assertThat(body.path("archived").path("archiveStatus").asText()).isEqualTo("FINALIZED");assertThat(body.path("archived").path("result").path("answer").asText()).contains("3.5");
                visibility("INTERNAL_ONLY");
                try {var hidden=a.send("GET",PREFIX+"/submissions/"+key,null,false);assertThat(hidden.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(hidden.body()).path("archived").path("result").isNull()).isTrue();assertThat(hidden.body()).doesNotContain("totalCredits","合成学分规则");
                }finally{visibility("STUDENT_VISIBLE");}
            }
            next.stop(null);assertThat(next.calls).isZero();save("FINALIZED_WITHDRAWAL_Q_EOF",old,next,key,2,false);
        }finally{for(var child:children)child.cleanup();}
    }
    private static void visibility(String scope)throws Exception{try(var c=data.source().getConnection();var p=c.prepareStatement("UPDATE knowledge_source SET visibility_scope=? WHERE id=?")){p.setString(1,scope);p.setObject(2,data.sourceId());assertThat(p.executeUpdate()).isEqualTo(1);}}
    private static Child launch(String mode)throws Exception {
        var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dfile.encoding=UTF-8","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),SubmissionHttpRestartProbe.class.getName(),mode).redirectErrorStream(true);
        if(System.getenv("ZHKU_DB_PASSWORD")==null)throw new IllegalArgumentException("缺少临时库环境");
        command.environment().put("ZHKU_DB_URL",((org.springframework.jdbc.datasource.DriverManagerDataSource)data.source()).getUrl());command.environment().put("ZHKU_DB_USER","zhkuagent");
        return new Child(command.start());
    }
    private static void save(String phase,Child old,Child next,UUID key,int originalCalls,boolean crash)throws Exception {
        var directory=Path.of("target","submission-process-probes",UUID.randomUUID().toString());Files.createDirectories(directory);
        JSON.writeValue(directory.resolve("phase.json").toFile(),Map.ofEntries(Map.entry("phase",phase),Map.entry("oldPid",old.process.pid()),Map.entry("newPid",next.process.pid()),
                Map.entry("oldExited",!old.process.isAlive()),Map.entry("newExited",!next.process.isAlive()),Map.entry("originalModelRequests",originalCalls),Map.entry("restartedModels",next.calls),
                Map.entry("submissionKey",key),Map.entry("intentionalCrash",crash),Map.entry("provider",false)));
    }
    private static final class Child {
        final Process process;final ArrayBlockingQueue<String> phases=new ArrayBlockingQueue<>(16);final Thread reader;int calls=-1;boolean intentionalCrash;
        Child(Process process){this.process=process;reader=Thread.ofPlatform().name("submission-probe-output").start(()->{
            try(var lines=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){for(String line;(line=lines.readLine())!=null;)
                if(line.matches("SUBMISSION_(READY [0-9]+ [0-9]+|COMMITTED 0 0|CLOSED [0-9]+ [0-9]+|FAILED [A-Za-z0-9]+)"))if(!phases.offer(line))return;
            }catch(IOException closed){} });}
        int ready()throws Exception{var ready=await("SUBMISSION_READY",Duration.ofSeconds(20)).split(" ");assertThat(Long.parseLong(ready[1])).isEqualTo(process.pid());return Integer.parseInt(ready[2]);}
        String await(String prefix,Duration duration)throws Exception{long deadline=System.nanoTime()+duration.toNanos();do{var line=phases.poll(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(line==null)break;if(line.startsWith("SUBMISSION_FAILED"))throw new AssertionError(line);if(line.startsWith(prefix))return line;}while(System.nanoTime()<deadline);throw new AssertionError("固定子进程预算耗尽："+prefix);}
        void stop(String command)throws Exception{if(command==null)process.getOutputStream().close();else{process.getOutputStream().write((command+"\n").getBytes(StandardCharsets.UTF_8));process.getOutputStream().flush();}
            assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();var closed=await("SUBMISSION_CLOSED",Duration.ofMillis(200)).split(" ");
            calls=Integer.parseInt(closed[1]);assertThat(Integer.parseInt(closed[2])).isZero();reader.join(2000);assertThat(reader.isAlive()).isFalse();}
        void cleanup()throws Exception{boolean forced=process.isAlive();if(forced){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}
            reader.join(2000);assertThat(process.isAlive()).isFalse();assertThat(reader.isAlive()).isFalse();assertThat(forced).as("finally强杀不能冒充正常退出").isFalse();}
    }
}
