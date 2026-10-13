package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import com.zhku.agent.agent.runtime.*;

/** 随机回环HTTP、真实安全链/TaskStore；身份仓储与执行器显式为测试替身，零数据库/供应商。 */
@Timeout(90)
class RuleAgentTaskHttpTest {
    static final String P="/api/zhku/controlled-preview/v2";
    static final ObjectMapper JSON=new ObjectMapper();
    static final TrustedActor A=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    static final TrustedActor B=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    static final TrustedActor ADMIN=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.MAINTAINER,0);
    static final AtomicInteger runs=new AtomicInteger();
    static final List<Map<String,Object>> exchanges=Collections.synchronizedList(new ArrayList<>());
    static final BlockingQueue<Runnable> queue=new LinkedBlockingQueue<>();
    static final AtomicBoolean originalCurrent=new AtomicBoolean(true);
    static final UUID TOKEN=UUID.randomUUID(),SOURCE=UUID.randomUUID(),CHUNK=UUID.randomUUID();
    static final String QUOTE="合成条款：仅用于接口验收。";
    static final String HASH="a".repeat(64);
    @SpringBootConfiguration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class,RuleCheckAuthController.class,RuleAgentHttpConfiguration.class})
    static class App {
        @Bean Clock clock(){return Clock.systemUTC();}
        @Bean PreviewIdentityRepository identities(){
            var repo=mock(PreviewIdentityRepository.class);
            for(var entry:Map.of("student-a",A,"student-b",B,"maintainer",ADMIN).entrySet())
                when(repo.authenticate(eq(entry.getKey()),any(char[].class))).thenReturn(Optional.of(new PreviewPrincipal(entry.getValue())));
            return repo;
        }
        @Bean RuleAgentTaskStore.Factory factory(){return (owner,task)->new RuleAgentTaskStore.Work(){
            public boolean revalidate(ResultEnvelope old){return true;}
            public RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input){
                runs.incrementAndGet();context.start();
                boolean waiting=input.question().equals("澄清")&&input.userConditions().isEmpty();
                boolean failed=input.question().equals("故障"),gap=input.question().equals("缺证");
                var issue=waiting?new Issue("condition",IssueKind.CLARIFICATION,ReasonCode.MISSING_CONDITION,"请确认课程类别","补充必要条件"):
                    failed?new Issue("failure",IssueKind.PROCESSING_FAILURE,ReasonCode.MODEL_UNAVAILABLE,"模型处理失败","稍后重新查证"):
                    new Issue("gap",IssueKind.DATA_GAP,ReasonCode.CURRENT_NOTICE_MISSING,"未取得当期通知","核对当期通知");
                boolean claim=!waiting&&!failed&&!gap;
                boolean partial=input.question().equals("部分");
                var items=List.of(new Subquestion("q1",claim?(partial?Coverage.PARTIAL:Coverage.COMPLETE):Coverage.NONE,
                    claim?List.of(new Claim("answer","fixture-fact","合成确认内容",Map.of(),ConditionSource.USER_DECLARED)):List.of(),
                    claim&&!partial?List.of():List.of(issue),waiting?List.of(new Clarification("courseType","课程类别？",List.of("PUBLIC","PROFESSIONAL"))):List.of()));
                var evidence=claim?List.of(new Evidence("e1",SOURCE,CHUNK,"2025.9.2",HASH,0,QUOTE.length(),QUOTE,TOKEN)):List.<Evidence>of();
                var relations=claim?List.of(new EvidenceRelation("e1","q1",EvidenceRole.CLAIM,"answer")):List.<EvidenceRelation>of();
                var result=new ResultEnvelope(input.version(),context.runId(),"synthetic-http-v1","2025.9.2",items.getFirst().coverage(),items,evidence,relations);
                var state=waiting||gap||partial?AgentRunState.INCOMPLETE:failed?AgentRunState.FAILED:AgentRunState.SUCCEEDED;
                context.finish(state);context.cleanupOnce(()->{});context.signalTermination();context.publishTermination();
                return new RuleAgentRunner.Outcome(new TaskSnapshot(3,task,UUID.randomUUID(),context.runId(),input.version(),waiting?TaskStatus.WAITING_USER:failed?TaskStatus.FAILED:TaskStatus.RESOLVED,state,true,result,waiting?UUID.randomUUID():null,new Execution(Mode.ONLINE,0,0,false,claim?AnswerSource.VERIFIED_RULE_ONLY:AnswerSource.NONE,ReplayCoverage.NOT_APPLICABLE)),List.of());
            }
        };}
        @Bean(name="ruleAgentHttpExecutor") java.util.concurrent.Executor executor(){return queue::add;}
        @Bean RuleAgentTaskController.OriginalReader originals(){return (o,t,k)->originalCurrent.get()&&TOKEN.equals(k)?Optional.of(new RuleCheckVerifiedExcerpt("e1",SOURCE,CHUNK,"2025.9.2",HASH,0,QUOTE.length(),QUOTE)):Optional.empty();}
        @Bean RuleCheckOriginalEvidenceService locator(){var locator=mock(RuleCheckOriginalEvidenceService.class);when(locator.locate(any())).thenReturn(new RuleCheckOriginalEvidenceService.Result(RuleCheckOriginalEvidenceService.Code.FOUND,new RuleCheckOriginalEvidenceService.View("e1",SOURCE,CHUNK,"2025.9.2","合成手册","合成条款",null,"UNKNOWN",null,null,QUOTE,0,0,QUOTE.length(),List.of("页码尚未记录"))));return locator;}
    }
    @Test void actualHttpLifecycleIdentityVersionsOriginalsAndExit()throws Exception{
        runs.set(0);queue.clear();exchanges.clear();originalCurrent.set(true);
        var app=new SpringApplication(App.class);app.setAdditionalProfiles("zhku-rule-check-preview");
        var settings=new HashMap<String,Object>();
        settings.put("server.port","0");settings.put("server.address","127.0.0.1");settings.put("server.servlet.context-path","/api");
        settings.put("server.servlet.session.timeout","15m");settings.put("server.servlet.session.persistent","false");settings.put("server.servlet.session.tracking-modes","cookie");
        settings.put("server.servlet.session.cookie.name","ZHKU_RULE_CHECK_SESSION");settings.put("server.servlet.session.cookie.path",P);
        settings.put("server.servlet.session.cookie.http-only","true");settings.put("server.servlet.session.cookie.secure","false");settings.put("server.servlet.session.cookie.same-site","strict");
        settings.put("zhku.rule-check-preview.enabled","true");settings.put("zhku.rule-agent-http.enabled","true");settings.put("logging.level.root","ERROR");settings.put("spring.main.allow-bean-definition-overriding","true");
        settings.put("spring.config.location","optional:classpath:/http-fixture-no-config.yml");
        app.setDefaultProperties(settings);
        int port;
        try(var context=(ServletWebServerApplicationContext)app.run()){
            port=context.getWebServer().getPort();var a=new User(port);var b=new User(port);var admin=new User(port);var anonymous=new User(port);
            assertThat(anonymous.get("/tasks/capabilities").statusCode()).isEqualTo(401);
            a.login("student-a");b.login("student-b");admin.login("maintainer");
            assertThat(admin.get("/tasks/capabilities").statusCode()).isEqualTo(403);
            assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(200);
            var form=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+P+"/tasks")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("question="+"x".repeat(70000))).build();
            assertThat(a.client.send(form,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(415);
            String key=UUID.randomUUID().toString();var body=Map.of("question","澄清","clientRequestId",key);
            assertThat(a.post("/tasks",body,false).statusCode()).isEqualTo(403);
            assertThat(a.post("/tasks",Map.of("question","问题","clientRequestId",key,"actorId",B.userId()),true).statusCode()).isEqualTo(400);
            assertThat(a.send("POST","/tasks","{\"question\":\"一\",\"question\":\"二\",\"clientRequestId\":\""+key+"\"}",true).statusCode()).isEqualTo(400);
            assertThat(a.send("POST","/tasks","x".repeat(65537),true).statusCode()).isEqualTo(413);
            var accepted=a.post("/tasks",body,true);assertThat(accepted.statusCode()).isEqualTo(202);assertThat(accepted.headers().firstValue("Cache-Control")).hasValue("no-store");
            var ticket=JSON.readTree(accepted.body());String task=ticket.path("taskId").asText();
            assertThat(JSON.readTree(a.post("/tasks",body,true).body()).path("duplicate").asBoolean()).isTrue();
            assertThat(a.post("/tasks",Map.of("question","不同问题","clientRequestId",key),true).statusCode()).isEqualTo(409);
            assertThat(a.get("/tasks/requests/CREATE/"+key).statusCode()).isEqualTo(200);
            assertThat(b.get("/tasks/"+task).statusCode()).isEqualTo(404);
            assertThat(b.get("/tasks/requests/CREATE/"+key).statusCode()).isEqualTo(404);
            assertThat(JSON.readTree(a.get("/tasks/"+task).body()).path("terminated").asBoolean()).isFalse();
            assertThat(runs.get()).isZero();queue.remove().run();assertThat(runs.get()).isEqualTo(1);
            var waiting=JSON.readTree(a.get("/tasks/"+task).body());assertThat(waiting.path("taskStatus").asText()).isEqualTo("WAITING_USER");
            var message=new HashMap<String,Object>();message.put("message","PUBLIC");message.put("continuationToken",waiting.path("continuationToken").asText());message.put("expectedVersion",1);message.put("clientRequestId",UUID.randomUUID());
            assertThat(a.post("/tasks/"+task+"/messages",message,true).statusCode()).isEqualTo(409);
            message.put("expectedVersion",1.5);assertThat(a.post("/tasks/"+task+"/messages",message,true).statusCode()).isEqualTo(400);
            message.put("expectedVersion",waiting.path("version").asLong());
            assertThat(a.post("/tasks/"+task+"/messages",message,true).statusCode()).isEqualTo(202);
            assertThat(a.post("/tasks/"+task+"/messages",message,true).statusCode()).isEqualTo(202);
            message.put("clientRequestId",UUID.randomUUID());assertThat(a.post("/tasks/"+task+"/messages",message,true).statusCode()).isEqualTo(409);
            queue.remove().run();assertThat(runs.get()).isEqualTo(2);
            assertThat(JSON.readTree(a.get("/tasks/"+task).body()).path("taskStatus").asText()).isEqualTo("RESOLVED");
            assertThat(b.get("/tasks/"+task+"/originals/"+TOKEN).statusCode()).isEqualTo(404);
            assertThat(a.get("/tasks/"+task+"/originals/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
            assertThat(a.get("/tasks/"+task+"/originals/"+TOKEN).statusCode()).isEqualTo(200);
            originalCurrent.set(false);assertThat(a.get("/tasks/"+task+"/originals/"+TOKEN).statusCode()).isEqualTo(409);
            for(String question:List.of("部分","缺证","故障","取消")){
                var t=JSON.readTree(a.post("/tasks",Map.of("question",question,"clientRequestId",UUID.randomUUID()),true).body());String id=t.path("taskId").asText();
                if(question.equals("取消")){
                    var cancel=Map.of("runId",t.path("runId").asText(),"expectedVersion",t.path("version").asLong(),"clientRequestId",UUID.randomUUID());
                    assertThat(a.post("/tasks/"+id+"/cancel",cancel,true).statusCode()).isEqualTo(202);
                    assertThat(JSON.readTree(a.get("/tasks/"+id).body()).path("terminated").asBoolean()).isFalse();
                }
                queue.remove().run();var s=JSON.readTree(a.get("/tasks/"+id).body());
                assertThat(s.path("taskStatus").asText()).isEqualTo(question.equals("故障")?"FAILED":question.equals("取消")?"STOPPED":"RESOLVED");
                assertThat(s.path("result").path("coverage").asText()).isEqualTo(question.equals("部分")?"PARTIAL":"NONE");
            }
            a.logout();a.login("student-a");assertThat(a.get("/tasks/"+task).statusCode()).isEqualTo(404);
            assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();assertThat(queue).isEmpty();
        }
        try(var socket=new java.net.Socket()){assertThatThrownBy(()->socket.connect(new InetSocketAddress("127.0.0.1",port),500)).isInstanceOf(java.io.IOException.class);}
        String directory=System.getProperty("zhku.rulecheck.evidenceDir","");if(!directory.isBlank())JSON.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(directory,"http-exchanges.json").toFile(),Map.of("schemaVersion",3,"executionMode","TEST_SIMULATION_NO_PROVIDER","sourceSha",System.getProperty("zhku.sourceSha","5ee26d897d21b9639eae6852d1fbfd14f0e3afd4"),"providerCalls",0,"databaseCalls",0,"resourcesExited",true,"exchanges",exchanges));
    }
    static class User{
        final HttpClient client;final int port;JsonNode csrf;
        User(int port){this.port=port;var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(2)).build();}
        HttpResponse<String> get(String path)throws Exception{return send("GET",path,null,false);}
        HttpResponse<String> post(String path,Object body,boolean token)throws Exception{return send("POST",path,JSON.writeValueAsString(body),token);}
        HttpResponse<String> send(String method,String path,String body,boolean token)throws Exception{
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+P+path)).timeout(Duration.ofSeconds(3)).header("Accept","application/json");
            if(body!=null)req.header("Content-Type","application/json; charset=UTF-8");if(token)req.header(csrf.path("headerName").asText(),csrf.path("token").asText());
            var response=client.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(path.startsWith("/tasks"))exchanges.add(Map.of("method",method,"path",path,"input",body==null?"":body.length()>4096?"OVERSIZE_SYNTHETIC_INPUT":body,"status",response.statusCode(),"actual",response.body()));
            return response;
        }
        void login(String login)throws Exception{
            csrf=JSON.readTree(get("/auth/csrf").body());var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+P+"/auth/login")).header("Content-Type","application/x-www-form-urlencoded").header(csrf.path("headerName").asText(),csrf.path("token").asText()).POST(HttpRequest.BodyPublishers.ofString("username="+login+"&password=synthetic-only-password")).build();
            assertThat(client.send(req,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);csrf=JSON.readTree(get("/auth/csrf").body());
        }
        void logout()throws Exception{assertThat(send("POST","/auth/logout",null,true).statusCode()).isEqualTo(204);}
    }
}
