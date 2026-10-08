package com.zhku.knowledge.controlled;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 真实网页补验专用：故障只在测试传输层注入，生产鉴权、事务和派发均实际执行。 */
@Timeout(900)
class SubmissionBrowserSupplementTest {
    private static final Path EVIDENCE=Path.of("..","docs","evidence","milestone-3c","2026-10-07","3c-3","browser-supplement").toAbsolutePath().normalize();

    @Test void browserUsesRealControlledBackendThroughNginx() throws Exception {
        var data=seed(); var model=new Model();
        try {
            try(var app=start(data,model); var bridge=new Bridge(app,data,model); var nginx=new Nginx(bridge.port)) {
                Files.writeString(EVIDENCE.resolve("ready.json"),JSON.writeValueAsString(Map.of(
                        "webPort",nginx.port,"controlPort",bridge.port,"backendPort",app.port,"providerCalls",0)),StandardCharsets.UTF_8);
                assertThat(bridge.done.await(720,TimeUnit.SECONDS)).as("预先固定网页操作窗口720秒").isTrue();
                var stats=bridge.stats();
                assertThat(stats.get("submissions")).isEqualTo(2L);
                assertThat(stats.get("runs")).isEqualTo(2L);
                assertThat(stats.get("dispatches")).isEqualTo(2L);
                assertThat(stats.get("modelRequests")).isEqualTo(4);
                assertThat(bridge.lost).hasValue(1); assertThat(bridge.conflicts).hasValue(1);
                assertThat(bridge.replays).hasValue(1); assertThat(bridge.denials).hasValueGreaterThanOrEqualTo(1);
                assertThat(model.active).hasValue(0);
                Files.writeString(EVIDENCE.resolve("backend-results.json"),JSON.writeValueAsString(stats),StandardCharsets.UTF_8);
            }
        } finally { cleanup(data); }
        Files.writeString(EVIDENCE.resolve("resources.json"),"{\"backendClosed\":true,\"nginxClosed\":true,\"bridgeClosed\":true,\"connections\":0,\"providerCalls\":0}",StandardCharsets.UTF_8);
    }

    private static final class Bridge implements AutoCloseable {
        final HttpServer server; final int port; final Running app; final Data data; final Model model;
        final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),
                r->new Thread(r,"browser-supplement-bridge"),new ThreadPoolExecutor.AbortPolicy());
        final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        final AtomicReference<String> fault=new AtomicReference<>("");
        final AtomicInteger posts=new AtomicInteger(),lost=new AtomicInteger(),conflicts=new AtomicInteger(),replays=new AtomicInteger(),denials=new AtomicInteger();
        final CountDownLatch done=new CountDownLatch(1);
        final Set<String> credentialsDelivered=ConcurrentHashMap.newKeySet();
        final List<Map<String,Object>> events=new CopyOnWriteArrayList<>();
        Bridge(Running app,Data data,Model model)throws Exception {
            this.app=app;this.data=data;this.model=model;
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),16);port=server.getAddress().getPort();
            server.setExecutor(workers);server.createContext("/",this::handle);server.start();
        }
        void handle(HttpExchange x) {
            try {
                String path=x.getRequestURI().getPath();
                if(path.startsWith("/__test/")) { control(x,path);return; }
                byte[] body=x.getRequestBody().readNBytes(16385);
                if(body.length>16384){reply(x,413,"application/json","{}");return;}
                boolean post=x.getRequestMethod().equals("POST")&&path.equals(PREFIX+"/runs");
                String mode=post?fault.getAndSet(""):"";
                if(post)posts.incrementAndGet();
                if(mode.equals("conflict")) {
                    var json=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(body);
                    json.put("question",json.path("question").asText()+" 合成不同内容");body=JSON.writeValueAsBytes(json);
                }
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.port+x.getRequestURI())).timeout(Duration.ofSeconds(8));
                x.getRequestHeaders().forEach((name,values)-> {
                    if(!Set.of("host","connection","content-length","expect","upgrade").contains(name.toLowerCase(Locale.ROOT)))
                        values.forEach(value->request.header(name,value));
                });
                var response=client.send(request.method(x.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
                // 故障只丢已受理响应：实际Run继续运行，不伪造结果，也不重发POST。
                if(mode.equals("lose")) {assertThat(response.statusCode()).isEqualTo(202);lost.incrementAndGet();x.close();return;}
                if(post||response.statusCode()==404) {
                    String run="";
                    if(post && response.statusCode()<300)run=JSON.readTree(response.body()).path("runId").asText();
                    if(events.size()<64)events.add(Map.of("method",x.getRequestMethod(),"path",path,"status",response.statusCode(),"runId",run));
                }
                if(post&&response.statusCode()==409)conflicts.incrementAndGet();
                if(post&&response.statusCode()==200)replays.incrementAndGet();
                if(response.statusCode()==404&&path.contains("/submissions/"))denials.incrementAndGet();
                response.headers().map().forEach((name,values)->{
                    if(!Set.of("transfer-encoding","connection","content-length").contains(name.toLowerCase(Locale.ROOT)))x.getResponseHeaders().put(name,values);
                });
                x.sendResponseHeaders(response.statusCode(),response.body().length);x.getResponseBody().write(response.body());x.close();
            }catch(Exception|AssertionError failure){try{reply(x,502,"application/json","{\"code\":\"TEST_TRANSPORT_FAILURE\"}");}catch(Exception ignored){x.close();}}
        }
        void control(HttpExchange x,String path)throws Exception {
            if(path.startsWith("/__test/credential/")) {
                String name=path.substring(path.lastIndexOf('/')+1);
                var account=switch(name){case "a"->data.a();case "b"->data.b();default->null;};
                if(account==null||!credentialsDelivered.add(name)){reply(x,404,"text/plain","unavailable");return;}
                // 随机凭证仅在测试进程内存和一次性本机页面之间传递；不写文件、不记日志。
                reply(x,200,"text/html;charset=UTF-8","<main><span id='account'>"+account.login()+"</span><span id='secret'>"+account.password()+"</span></main>");return;
            }
            if(path.equals("/__test/stats")){reply(x,200,"application/json",JSON.writeValueAsString(stats()));return;}
            if(!x.getRequestMethod().equals("POST")){reply(x,405,"text/plain","POST required");return;}
            switch(path) {
                case "/__test/arm-conflict"->fault.set("conflict");
                case "/__test/arm-lose"->fault.set("lose");
                case "/__test/finish"->done.countDown();
                default->{reply(x,404,"text/plain","unavailable");return;}
            }
            reply(x,200,"application/json","{\"ok\":true}");
        }
        Map<String,Object> stats()throws Exception {
            long submissions=0,runs=0;
            try(var c=data.source().getConnection()) {
                for(String table:List.of("campus_agent_run_submission","campus_agent_run_archive")) {
                    try(var s=c.prepareStatement("SELECT count(*) FROM "+table+" WHERE owner_user_id=?")) {
                        s.setQueryTimeout(1);s.setObject(1,data.a().id());try(var rows=s.executeQuery()){rows.next();if(table.endsWith("submission"))submissions=rows.getLong(1);else runs=rows.getLong(1);}
                    }
                }
            }
            return Map.of("submissions",submissions,"runs",runs,"dispatches",((ThreadPoolExecutor)app.context.getBean("controlledCampusWorker")).getTaskCount(),
                    "modelRequests",model.calls.get(),"modelActive",model.active.get(),"posts",posts.get(),"lost",lost.get(),"conflicts",conflicts.get(),"replays",replays.get(),"events",events);
        }
        static void reply(HttpExchange x,int code,String type,String value)throws Exception {
            x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("Cache-Control","no-store");
            byte[] bytes=value.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code,bytes.length);x.getResponseBody().write(bytes);x.close();
        }
        @Override public void close()throws Exception {server.stop(0);client.close();workers.shutdown();assertThat(workers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    private static final class Nginx implements AutoCloseable {
        final int port=57482;final String name="zhku-browser-"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        final Path root=Path.of("..","agent","frontend").toAbsolutePath().normalize();final int backend;
        Nginx(int backend)throws Exception {this.backend=backend;
            assertThat(Files.exists(root.resolve("dist/index.html"))).isTrue();
            try {command("up","--detach","--no-build","--wait","--wait-timeout","15");command("exec","--no-TTY","web","nginx","-t");}
            catch(Exception|AssertionError failure){close();throw failure;}
        }
        void command(String... args)throws Exception {
            var command=new ArrayList<>(List.of("docker.exe","compose","--project-name",name,"--file",root.resolve("compose.preview.yaml").toString()));command.addAll(List.of(args));
            var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("ZHKU_CONTROLLED_BACKEND_PORT",Integer.toString(backend));builder.environment().put("ZHKU_WEB_PORT",Integer.toString(port));
            var process=builder.start();boolean exited=process.waitFor(20,TimeUnit.SECONDS);
            if(!exited){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}
            assertThat(exited).as("代理操作预算20秒").isTrue();assertThat(process.exitValue()).isZero();
        }
        @Override public void close()throws Exception {command("down","--timeout","10");
            try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),200);fail("代理监听残留");}catch(java.io.IOException closed){}
            var p=new ProcessBuilder("docker.exe","ps","-a","--filter","label=com.docker.compose.project="+name,"--format","{{.ID}}")
                    .redirectErrorStream(true).start();assertThat(p.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();
            assertThat(new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8)).isBlank();
        }
    }
}
