package com.zhku.knowledge.controlled;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 固定镜像的真实Nginx + 真库/安全链；业务模型仍是test-only替身。 */
@Timeout(90)
class SubmissionNginxHttpTest {
    private Data currentData;
    @AfterEach void cleanData()throws Exception{cleanup(currentData);currentData=null;}
    @Test void actualProxyForwardsKeyStatusNoStoreAndRejectsBodyAndOldRoutes()throws Exception {
        var data=seed();currentData=data;var model=new Model();
        try(var app=start(data,model);var nginx=new Nginx(app.port);var a=new User(nginx.port)) {
            assertThat(a.send("GET",PREFIX+"/auth/me",null,false).statusCode()).isEqualTo(401);
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            var key=UUID.randomUUID();var first=a.submitWithKey(QUESTION,COURSES,key);assertThat(first.statusCode()).isEqualTo(202);
            assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
            var id=UUID.fromString(JSON.readTree(first.body()).path("runId").asText());a.awaitTerminal(id);
            var replay=a.submitWithKey(QUESTION,COURSES,key);assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(replay.body()).path("runId").asText()).isEqualTo(id.toString());
            assertThat(a.submitWithKey(QUESTION+"不同内容",COURSES,key).statusCode()).isEqualTo(409);
            assertThat(a.sendWithKey("POST",PREFIX+"/runs","{\"question\":\""+"x".repeat(20000)+"\",\"courses\":[]}",true,UUID.randomUUID()).statusCode()).isEqualTo(413);
            for(String path:List.of("/api","/api/ai/manus/chat","/api/zhku/local-preview/conversations","/api/zhku/local-admin","/api/zhku/controlled-preview/v1"))
                assertThat(a.send("GET",path,null,false).statusCode()).isEqualTo(404);
            assertThat(a.send("GET","/campus/run",null,false).statusCode()).isEqualTo(200);
            assertThat(a.send("GET",PREFIX+"/submissions/"+key,null,false).headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(model.calls).hasValue(2);assertThat(((ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isEqualTo(1);
            System.out.println("3C-NGINX keyForwarded=true statuses=200,202,409,413 oldRoutes=404 noStore=true dispatches=1 modelRequests=2");
        }
    }
    @Test void actualProxyPreservesQuota429AndConfirmedUnknown503WithoutRetryingPost()throws Exception {
        var data=seed();currentData=data;var model=new Model();model.block=true;
        try(var app=start(data,model);var nginx=new Nginx(app.port);var a=new User(nginx.port)) {
            try {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                a.submitWithKey(QUESTION,COURSES,UUID.randomUUID());a.submitWithKey(QUESTION,COURSES,UUID.randomUUID());
                var denied=a.submitWithKey(QUESTION,COURSES,UUID.randomUUID());assertThat(denied.statusCode()).isEqualTo(429);
                assertThat(denied.headers().firstValue("Cache-Control")).contains("no-store");
                assertThat(((ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isEqualTo(2);
            }finally{model.release.countDown();}
        }
        var armed=new AtomicBoolean(false);var nextModel=new Model();
        try(var app=start(data,nextModel,context->context.registerBean("nginxCommitFault",org.springframework.beans.factory.config.BeanPostProcessor.class,
            ()->new org.springframework.beans.factory.config.BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){
                return bean instanceof javax.sql.DataSource source?com.zhku.knowledge.campusagent.submission.CampusRunSubmissionStoreIntegrationTest.fault(source,armed,true):bean;
            }}));var nginx=new Nginx(app.port);var a=new User(nginx.port)) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);armed.set(true);var key=UUID.randomUUID();
            var unknown=a.submitWithKey(QUESTION,COURSES,key);assertThat(unknown.statusCode()).isEqualTo(503);
            assertThat(JSON.readTree(unknown.body()).path("acceptance").asText()).isEqualTo("UNKNOWN");
            assertThat(unknown.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(a.send("GET",PREFIX+"/submissions/"+key,null,false).statusCode()).isEqualTo(200);
            assertThat(nextModel.calls).hasValue(0);assertThat(((ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount()).isZero();
            System.out.println("3C-NGINX statuses=429,503 noStore=true unknownDispatches=0 unknownModels=0");
        }
    }
    private static final class Nginx implements AutoCloseable {
        final int port=57481;final String name="zhku-3c-proxy-"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        final Path root=Path.of("..","agent","frontend").toAbsolutePath().normalize();final int backend;
        Nginx(int backend)throws Exception {this.backend=backend;
            assertThat(Files.exists(root.resolve("dist/index.html"))).as("须先构建本轮Vue").isTrue();
            assertThat(Files.readString(root.resolve("nginx.conf"))).contains("proxy_next_upstream off;");
            try {command("up","--detach","--no-build","--wait","--wait-timeout","15");command("exec","--no-TTY","web","nginx","-t");}
            catch(Exception|AssertionError failure){close();throw failure;}
        }
        private void command(String... arguments)throws Exception {
            var command=new ArrayList<>(List.of("docker.exe","compose","--project-name",name,"--file",root.resolve("compose.preview.yaml").toString()));command.addAll(List.of(arguments));
            var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("ZHKU_CONTROLLED_BACKEND_PORT",Integer.toString(backend));builder.environment().put("ZHKU_WEB_PORT",Integer.toString(port));
            var process=builder.start();boolean exited=process.waitFor(20,TimeUnit.SECONDS);
            if(!exited){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}
            assertThat(exited).as("固定代理操作预算20秒").isTrue();assertThat(process.exitValue()).isZero();
        }
        @Override public void close()throws Exception{command("down","--timeout","10");
            try(var socket=new java.net.Socket()){socket.connect(new java.net.InetSocketAddress("127.0.0.1",port),200);fail("代理退出后端口仍可连接");}catch(java.io.IOException closed){}
            var inspection=new ProcessBuilder("docker.exe","ps","-a","--filter","label=com.docker.compose.project="+name,"--format","{{.ID}}")
                    .redirectErrorStream(true).start();assertThat(inspection.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(inspection.exitValue()).isZero();
            assertThat(new String(inspection.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isBlank();
        }
    }
}
