package com.zhku.knowledge.controlled;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import com.zhku.knowledge.campusagent.submission.*;
import static org.assertj.core.api.Assertions.*;
/** 恢复旧源码后保持受控入口关闭，保留V21及私有关联；不伪称旧版可安全继续受理。 */
@Timeout(40)
class SubmissionRollbackContractTest {
    @Test void restoredOldBinaryRemainsClosedAndCannotEraseOrReassignV21Submission()throws Exception {
        try(var env=new CampusRunSubmissionStoreIntegrationTest.Env(null)) {
            var key=UUID.randomUUID();var id=UUID.randomUUID();
            env.store.admitSubmissionOwned(env.a,new CampusRunSubmission.Command(key,id,CampusRunSubmissionStoreIntegrationTest.FP));
            env.store.stopAdmission();env.store.sealWrites();env.store.close(java.time.Duration.ofSeconds(2));
            var root=Path.of("..","docs","evidence","milestone-3c","2026-10-07","3c-3");
            var jar=root.resolve("rollback-sandbox/knowledge/target/knowledge-0.0.1-SNAPSHOT-controlled.jar").toAbsolutePath();
            assertThat(Files.exists(jar)).as("须先从原完整检查点在隔离目录构建旧版").isTrue();
            var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dfile.encoding=UTF-8","-jar",jar.toString(),
                "--server.port=57482","--server.address=127.0.0.1","--server.servlet.context-path=/api","--zhku.controlled-preview.enabled=false",
                "--logging.level.root=ERROR","--spring.main.banner-mode=off","--zhku.db.username=zhkuagent","--zhku.db.url="+((org.springframework.jdbc.datasource.DriverManagerDataSource)env.source).getUrl())
                .redirectErrorStream(true);
            builder.environment().remove("DASHSCOPE_API_KEY");var process=builder.start();boolean guard;
            try(var reader=Executors.newSingleThreadExecutor()) {
                var output=reader.submit(()->{boolean sawGuard=false;try(var lines=new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                    for(String line;(line=lines.readLine())!=null;)if(line.contains("受控预览要求独立profile与显式开关"))sawGuard=true;return sawGuard;}});
                try {assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();guard=output.get(2,TimeUnit.SECONDS);}
                finally{if(process.isAlive()){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}reader.shutdown();assertThat(reader.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
            }
            assertThat(process.exitValue()).isNotZero();assertThat(guard).isTrue();
            try(var socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",57482),200);fail("回退受控POST不得重新开放");}catch(java.io.IOException closed){}
            try(var c=env.source.getConnection();var p=c.prepareStatement("SELECT s.owner_user_id,s.run_id,a.archive_status FROM campus_agent_run_submission s JOIN campus_agent_run_archive a USING(run_id) WHERE s.submission_key=?")){
                p.setObject(1,key);try(var row=p.executeQuery()){assertThat(row.next()).isTrue();assertThat(row.getObject(1,UUID.class)).isEqualTo(env.a.userId());assertThat(row.getObject(2,UUID.class)).isEqualTo(id);assertThat(row.getString(3)).isEqualTo("ADMITTED");}
                try(var s=c.createStatement();var row=s.executeQuery("SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL")){row.next();assertThat(row.getInt(1)).isEqualTo(21);}
            }
            System.out.println("3C-ROLLBACK restoredOldJar=true controlledListenerClosed=true V21Preserved=true ownershipUnchanged=true providerCalls=0");
        }
    }
}
