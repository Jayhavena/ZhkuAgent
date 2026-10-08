package com.zhku.knowledge.tools.modelcall;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import com.zhku.agent.agent.runtime.*;
import com.zhku.knowledge.campusagent.*;
import com.zhku.knowledge.rag.*;
import com.zhku.knowledge.tools.*;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.*;
import static org.assertj.core.api.Assertions.*;

/** 产品Factory/Service与实际本机协议；供应商0，不能解释为真实模型行为验收。 */
@Timeout(15)
class CourseCreditModelBudgetProtocolTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String COURSES="{\"courses\":[{\"courseId\":\"A\",\"category\":\"公共选修课\",\"credits\":1.5},{\"courseId\":\"B\",\"category\":\"公共选修课\",\"credits\":2}]}";
    HttpServer server;ExecutorService handlers;CourseCreditModelTransport transport;
    final AtomicInteger received=new AtomicInteger();final List<JsonNode> requests=new CopyOnWriteArrayList<>();
    volatile String fault;volatile int status=200;Logger logger;Level previous;
    @BeforeEach void start()throws Exception {
        logger=((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger("org.springframework.ai");previous=logger.getLevel();logger.setLevel(Level.OFF);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);handlers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16));server.setExecutor(handlers);
        server.createContext("/v1/chat/completions",x->{try{
            JsonNode request=JSON.readTree(x.getRequestBody().readAllBytes());requests.add(request);int n=received.incrementAndGet();
            String body=fault==null?toolReply(n==1?"lookup_public_elective_credit_limit":"summarize_course_credits",n==1?"{}":COURSES):fault;
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);
            x.getResponseBody().write(bytes);
        }catch(java.io.IOException ignored){}finally{x.close();}});server.start();
        var p=new RagProviderProfile("local-contract","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","ZHKU_LOCAL_CONTRACT_KEY","stub-chat","stub-embed",1,Duration.ofSeconds(2),"stub-index");
        transport=CourseCreditChatModelFactory.open(p,new ApiKeyResolver(Map.of(p.apiKeyEnvVar(),"sk-local-contract-only")));
    }
    @AfterEach void close()throws Exception {
        try{if(transport!=null){transport.close();assertThat(transport.isTerminated()).isTrue();}}
        finally{server.stop(0);handlers.shutdownNow();assertThat(handlers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();logger.setLevel(previous);}
    }
    @Test void actualTwoDecisionTraceUsesFixedTokenOptionsAndCountsAttempts()throws Exception {
        var budget=new CampusExecutionBudget(Duration.ofSeconds(30));var context=AgentRunContext.create("合成问题");
        var result=service("合成上限4学分").run(context,request(),budget);context.publishTermination();
        assertThat(result.state()).isEqualTo(AgentRunState.SUCCEEDED);assertThat(result.answer()).contains("3.5");
        assertThat(received).hasValue(2);assertThat(budget.attemptedCalls()).isEqualTo(2);
        assertThat(requests).hasSize(2);for(var json:requests)options(json);
    }
    @Test void fourthAttemptIsRejectedBeforeAnyFourthExternalRequest() {
        var budget=new CampusExecutionBudget(Duration.ofSeconds(30));budget.reserveModelCall("合成占用1");budget.reserveModelCall("合成占用2");
        var context=AgentRunContext.create("合成问题");var result=service("合成上限4学分").run(context,request(),budget);context.publishTermination();
        assertThat(result.state()).isEqualTo(AgentRunState.FAILED);assertThat(result.failureCategory()).isEqualTo("MODEL_CALL_BUDGET_EXCEEDED");
        assertThat(received).hasValue(1);assertThat(budget.attemptedCalls()).isEqualTo(3);assertThat(result.answer()).doesNotContain("3.5");
    }
    @Test void oversizedSecondPromptDoesNotMakeSecondRequest() {
        var budget=new CampusExecutionBudget(Duration.ofSeconds(30));var context=AgentRunContext.create("合成问题");
        var result=service("合成政策"+"x".repeat(12000)).run(context,request(),budget);context.publishTermination();
        assertThat(result.failureCategory()).isEqualTo("MODEL_INPUT_LIMIT");assertThat(result.state()).isEqualTo(AgentRunState.FAILED);
        assertThat(received).hasValue(1);assertThat(budget.attemptedCalls()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"429","500","BAD_JSON","EMPTY","LENGTH"})
    void failedOrTruncatedResponsesNeverRetryOrInventAnswer(String type) {
        if(type.equals("429")||type.equals("500")){status=Integer.parseInt(type);fault="{\"error\":{\"message\":\"synthetic rejection\"}}";}
        else if(type.equals("BAD_JSON"))fault="{";
        else if(type.equals("EMPTY"))fault="{\"choices\":[]}";
        else fault="{\"id\":\"local\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"stub-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"合成截断\"},\"finish_reason\":\"length\"}]}";
        var budget=new CampusExecutionBudget(Duration.ofSeconds(30));var context=AgentRunContext.create("合成问题");
        var result=service("合成上限4学分").run(context,request(),budget);context.publishTermination();
        assertThat(result.state()).isEqualTo(AgentRunState.FAILED);assertThat(result.answer()).doesNotContain("3.5");
        assertThat(received).hasValue(1);assertThat(budget.attemptedCalls()).isEqualTo(1);options(requests.getFirst());
    }
    static void options(JsonNode j) {
        assertThat(j.path("max_tokens").asInt()).isEqualTo(2048);assertThat(j.hasNonNull("max_completion_tokens")).isFalse();
        assertThat(j.path("parallel_tool_calls").asBoolean(true)).isFalse();
        assertThat(j.path("tools").size()).isEqualTo(2);
    }
    CampusCreditAgentService service(String conclusion) {
        var policy=new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,conclusion,
                new LocalPreviewConversationFacade.ConversationAnswer(conclusion,"合成范围",UUID.randomUUID(),"合成政策","v1","合成出处","合成部门"),
                AnswerRoute.HIGH_RISK_RULE,false,List.of(),List.of());
        return new CampusCreditAgentService(transport.model(),new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),q->policy,3);
    }
    static CampusCreditAgentService.Request request(){return new CampusCreditAgentService.Request("公共选修课合成问题",List.of(
        new CampusCreditAgentService.CourseInput("A","公共选修课",new java.math.BigDecimal("1.5")),
        new CampusCreditAgentService.CourseInput("B","公共选修课",new java.math.BigDecimal("2"))));}
    static String toolReply(String name,String args) throws java.io.IOException {
        var message=JSON.createObjectNode().put("role","assistant");var call=message.putArray("tool_calls").addObject();call.put("id","local-"+UUID.randomUUID()).put("type","function");
        call.putObject("function").put("name",name).put("arguments",args);
        var root=JSON.createObjectNode().put("id","local").put("object","chat.completion").put("created",1).put("model","stub-chat");
        root.putArray("choices").addObject().put("index",0).put("finish_reason","tool_calls").set("message",message);return JSON.writeValueAsString(root);
    }
}
