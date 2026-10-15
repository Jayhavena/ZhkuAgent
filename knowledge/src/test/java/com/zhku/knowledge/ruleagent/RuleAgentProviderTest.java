package com.zhku.knowledge.ruleagent;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
@Timeout(15)
class RuleAgentProviderTest {
    final List<RuleAgentProvider.Trace> traces=new CopyOnWriteArrayList<>();
    final AtomicInteger calls=new AtomicInteger();
    final AtomicReference<String> request=new AtomicReference<>();
    final AtomicReference<String> responseBody=new AtomicReference<>();
    final CountDownLatch requestReceived=new CountDownLatch(1);
    HttpServer server;
    ExecutorService workers;
    RuleAgentProvider port;
    RuleAgentRunner.View view(){
        return new RuleAgentRunner.View("选修课怎么区分？",List.of(new RuleAgentRunner.Question("q1","选修课怎么区分？")),Map.of(),List.of(),List.of(),List.of(),List.of(),4,6);
    }
    void start(int status,String body,long delay)throws Exception{
        start(status,body,delay,0);
    }
    void start(int status,String body,long delay,long bodyDelay)throws Exception{
        responseBody.set(body);
        workers=Executors.newFixedThreadPool(2);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(workers);
        server.createContext("/chat/completions",e->{
            calls.incrementAndGet();
            request.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            requestReceived.countDown();
            try{
                if(delay>0)Thread.sleep(delay);
                byte[] data=responseBody.get().getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().add("x-request-id","local-request-01");
                e.sendResponseHeaders(status,data.length);
                if(bodyDelay>0){
                    e.getResponseBody().write(data,0,1);
                    e.getResponseBody().flush();
                    Thread.sleep(bodyDelay);
                    e.getResponseBody().write(data,1,data.length-1);
                }
                else e.getResponseBody().write(data);
            }
            catch(Exception ignored){
            }
            finally{
                e.close();
            }
        });
        server.start();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",traces::add,new AtomicInteger(4));
    }
    /** 决策侧线协议：结构化输出仍以文本content返回单个动作JSON对象。 */
    String call(String action,String finish)throws Exception{
        return new ObjectMapper().writeValueAsString(Map.of("id","local-response","choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",action))),"usage",Map.of("prompt_tokens",25,"completion_tokens",12)));
    }
    String good()throws Exception{
        return call("{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":\"选修课程类型\"}","stop");
    }
    @AfterEach void close()throws Exception{
        if(port!=null){
            port.close();
            assertThat(port.isTerminated()).isTrue();
        }
        if(server!=null)server.stop(0);
        if(workers!=null){
            workers.shutdownNow();
            assertThat(workers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void actualHttpRequestUsesOneConnectionWithoutAnIndependentProbe()throws Exception{
        try(var listener=new java.net.ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))){
            var accepted=new AtomicInteger();workers=Executors.newSingleThreadExecutor();
            String body=good();
            var served=workers.submit(()->{
                try{
                    while(!listener.isClosed())try(var socket=listener.accept()){
                        accepted.incrementAndGet();socket.setSoTimeout(2000);
                        var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                        String first=reader.readLine();if(first==null)continue;
                        while(true){String line=reader.readLine();if(line==null||line.isEmpty())break;}
                        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().write(bytes);socket.getOutputStream().flush();return;
                    }
                }catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}
            });
            port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+listener.getLocalPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",traces::add,new AtomicInteger(5));
            port.decide(view(),Duration.ofSeconds(2));served.get(3,TimeUnit.SECONDS);
            assertThat(accepted).hasValue(1);
            assertThat(traces.getLast().diagnostics().connectedMillis()).isNull();
        }
    }
    @Test void credentialPreparationConsumesTheSameDeadlineAndCannotDispatchLate()throws Exception{
        start(200,good(),0);port.close();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->{
            try{Thread.sleep(600);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            return "local-test-placeholder";
        },traces::add,new AtomicInteger(5));
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(200))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(0);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(traces.getLast().diagnostics().dispatchedMillis()).isNull();
    }
    @Test void responseModelKeepsTheSelectedAliasWithoutInventingAnIdentity()throws Exception{
        var body=new ObjectMapper().readTree(good());((com.fasterxml.jackson.databind.node.ObjectNode)body).put("model","qwen3.8-flash");
        start(200,body.toString(),0);port.decide(view(),Duration.ofSeconds(2));
        assertThat(traces.getLast().diagnostics().responseModel()).isEqualTo("qwen3.8-flash");
    }
    @Test void actualConnectionFailureHasASafeCategoryWithoutExceptionText()throws Exception{
        start(200,good(),0);server.stop(0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        var diagnostics=new ObjectMapper().valueToTree(traces.getLast().diagnostics());
        assertThat(diagnostics.path("transportFailure").asText()).isEqualTo("CONNECTION_FAILURE");
        assertThat(diagnostics.toString()).doesNotContain("local-test-placeholder","exceptionMessage");
    }
    @Test void validActionAndUsageAreRecordedWithoutCredentials()throws Exception{
        start(200,good(),0);
        var reply=port.decide(view(),Duration.ofSeconds(1));
        assertThat(reply.providerResponseReceived()).isFalse();
        assertThat(RuleAgentJson.readAction(reply.actionJson())).isInstanceOf(Search.class);
        assertThat(calls).hasValue(1);
        assertThat(traces).hasSize(3);
        assertThat(traces.getLast().responseReceived()).isTrue();
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(new ObjectMapper().writeValueAsString(traces)).doesNotContain("local-test-placeholder");
        var json=new ObjectMapper().readTree(request.get());
        // 决策侧改用供应商结构化输出：不再下发工具列表，动作约束放在 response_format 的Schema里。
        assertThat(json.has("tools")).isFalse();
        assertThat(json.path("response_format").path("type").asText()).isEqualTo("json_schema");
        var format=json.path("response_format").path("json_schema");
        assertThat(format.path("name").asText()).isEqualTo("zhku_rule_agent_action");
        assertThat(format.path("strict").asBoolean()).isTrue();
        var branches=new ArrayList<String>();
        format.path("schema").path("oneOf").forEach(b->branches.add(b.path("properties").path("type").path("const").asText()));
        assertThat(branches).containsExactly("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","ASK_NECESSARY_CONDITION","FINISH_CHECK");
        assertThat(json.path("enable_thinking").asBoolean()).isFalse();
        assertThat(json.path("max_completion_tokens").asInt()).isEqualTo(1024);
        assertThat(json.path("model").asText()).isEqualTo(RuleAgentProvider.MODEL);
    }
    @Test void badJsonIsProcessingFailureWithoutRetry()throws Exception{
        start(200,"bad-json",0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(1);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);
    }
    @Test void unknownActionCannotBecomeATool()throws Exception{
        start(200,good().replace("SEARCH_REVIEWED_RULES","EXECUTE_SQL"),0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void rateLimitIsClassifiedWithoutRetry()throws Exception{
        start(429,"{}",0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_RATE_LIMIT);
        assertThat(calls).hasValue(1);
    }
    @Test void authorizationAndServerErrorsDoNotBecomeMissingEvidence()throws Exception{
        start(401,"{}",0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason().name()).isEqualTo("MODEL_AUTH_FAILURE");
        assertThat(calls).hasValue(1);
    }
    @Test void serverFailureIsClassified()throws Exception{
        start(503,"{}",0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason().name()).isEqualTo("MODEL_UNAVAILABLE");
    }
    @Test void timeoutIncludesWholeResponseAndConsumesAllowance()throws Exception{
        start(200,good(),500);
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(70))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(calls.get()).isLessThanOrEqualTo(1);
    }
    @Test void exhaustedAllowanceMakesNoHttpCall()throws Exception{
        start(200,good(),0);
        port.close();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",traces::add,new AtomicInteger(0));
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(0);
    }
    @Test void auditFailureBlocksExternalCall()throws Exception{
        start(200,good(),0);
        port.close();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",t->{
            throw new IllegalStateException("local");
        },new AtomicInteger(1));
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(0);
    }
    @Test void oversizedResponseIsRejected()throws Exception{
        start(200,"x".repeat(70000),0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void onlineEnvironmentRejectsReplayAndMissingAuthorization(){
        var env=new HashMap<String,String>();
        env.put("ZHKU_RULE_AGENT_MODE","ONLINE");
        env.put("DASHSCOPE_API_KEY","local-test-placeholder");
        assertThatThrownBy(()->RuleAgentProvider.Settings.environment(env)).isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_RULE_AGENT_AUTHORIZATION_ID","local-approved-package");
        env.put("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY","old-replay");
        assertThatThrownBy(()->RuleAgentProvider.Settings.environment(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlineEnvironmentPinsDestinationAndModel(){
        var env=Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","local-approved-package");
        var settings=RuleAgentProvider.Settings.environment(env);
        assertThat(settings.endpoint()).isEqualTo(RuleAgentProvider.BEIJING);
        assertThat(settings.model()).isEqualTo(RuleAgentProvider.MODEL);
    }
    @Test void truncatedCompletionIsRejected()throws Exception{
        start(200,call("{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":\"课程\"}","length"),0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);
    }
    @Test void missingKeyMakesNoHttpCall()throws Exception{
        start(200,good(),0);
        port.close();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->null,traces::add,new AtomicInteger(1));
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(0);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_AUTH_FAILURE);
    }
    @Test void disconnectedEndpointIsFailureWithoutRetry()throws Exception{
        start(200,good(),0);
        server.stop(0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_CONNECTION_FAILURE);
    }
    // 响应头已到不等于完整响应；正文超时仍须失败，不能把HTTP200当作动作成功。
    @Test void bodyTimeoutRetainsHeaderAndPartialBodyDiagnostics()throws Exception{
        start(200,good(),0,1500);
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(600))).isInstanceOf(RuleAgentProvider.Failure.class);
        var trace=new ObjectMapper().valueToTree(traces.getLast());
        var d=trace.path("diagnostics");
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(traces.getLast().responseReceived()).isFalse();
        assertThat(trace.path("status").asInt()).isEqualTo(200);
        assertThat(d.path("headersMillis").isNumber()).isTrue();
        assertThat(d.path("firstBodyMillis").isNumber()).isTrue();
        assertThat(d.path("bodyCompletedMillis").isNull()).isTrue();
        assertThat(d.path("responseBytes").asInt()).isEqualTo(1);
        assertThat(d.path("requestId").asText()).isEqualTo("local-request-01");
        assertThat(calls).hasValue(1);
    }
    @Test void headerTimeoutDoesNotInventReceivedResponse()throws Exception{
        start(200,good(),1500);
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(500))).isInstanceOf(RuleAgentProvider.Failure.class);
        var d=new ObjectMapper().valueToTree(traces.getLast()).path("diagnostics");
        assertThat(d.path("dispatchedMillis").isNumber()).isTrue();
        assertThat(d.path("headersMillis").isNull()).isTrue();
        assertThat(d.path("firstBodyMillis").isNull()).isTrue();
        assertThat(d.path("responseBytes").asInt()).isZero();
        assertThat(traces.getLast().responseReceived()).isFalse();
    }
    @Test void completeResponseRecordsMonotonicTimingAndExactPayloadBytes()throws Exception{
        start(200,good(),0);
        port.decide(view(),Duration.ofSeconds(2));
        var d=new ObjectMapper().valueToTree(traces.getLast()).path("diagnostics");
        assertThat(d.path("requestBytes").asInt()).isEqualTo(request.get().getBytes(StandardCharsets.UTF_8).length);
        assertThat(d.path("responseBytes").asInt()).isEqualTo(good().getBytes(StandardCharsets.UTF_8).length);
        assertThat(d.path("connectedMillis").isNull()).isTrue();
        assertThat(d.path("headersMillis").isNumber()).isTrue();
        assertThat(d.path("bodyCompletedMillis").isNumber()).isTrue();
        assertThat(d.path("elapsedMillis").asLong()).isGreaterThanOrEqualTo(d.path("bodyCompletedMillis").asLong());
        assertThat(d.path("bodyCompletedMillis").asLong()).isGreaterThanOrEqualTo(d.path("headersMillis").asLong());
        assertThat(d.path("headersMillis").asLong()).isGreaterThanOrEqualTo(d.path("dispatchedMillis").asLong());
        assertThat(d.path("dispatchedMillis").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(new ObjectMapper().writeValueAsString(traces)).doesNotContain("local-test-placeholder");
    }
    /**
     * 本地重建P01首决策，核对字节数和所列字段；不证明历史逐字节身份或供应商接受该schema。
     * 运行时剩余时间会变化，超时原因仍须真实交换证据判断。
     */
    @Test void firstDecisionPayloadReproducesRecordedShape()throws Exception{
        start(200,good(),0);
        String question="专业选修课和公共选修课在修读安排上有什么不同？";
        var state=new RuleAgentRunner.ActionState(List.of("SEARCH_REVIEWED_RULES"),List.of(),
            DEFAULT_BUDGET.maxEvidenceFragments(),DEFAULT_BUDGET.maxEvidenceBytes(),DEFAULT_BUDGET.maxRunMillis(),DEFAULT_BUDGET.maxModelMillis(),5000);
        var view=new RuleAgentRunner.View(question,List.of(new RuleAgentRunner.Question("q1",question)),Map.of(),List.of(),List.of(),List.of(),List.of(),
            RulePublicationContract.MAX_DECISION_CALLS,DEFAULT_BUDGET.maxToolStepsPerRun(),state);
        String payload=port.payload(view);
        assertThat(payload.getBytes(StandardCharsets.UTF_8).length).isEqualTo(4557);
        var wire=new ObjectMapper().readTree(payload);
        var format=wire.path("response_format").path("json_schema");
        assertThat(format.path("name").asText()).isEqualTo("zhku_rule_agent_action");
        assertThat(format.path("strict").asBoolean()).isTrue();
        var branches=new ArrayList<String>();
        format.path("schema").path("oneOf").forEach(b->branches.add(b.path("properties").path("type").path("const").asText()));
        // 首决策只允许 SEARCH：状态过滤后单分支，不得留下空 enum 或未声明分支。
        assertThat(branches).containsExactly("SEARCH_REVIEWED_RULES");
        assertThat(format.path("schema").toString()).doesNotContain("enum");
        var data=new ObjectMapper().readTree(wire.path("messages").get(1).path("content").asText());
        assertThat(data.path("question").asText()).isEqualTo(question);
        assertThat(data.path("candidateSetId").isNull()).isTrue();
        assertThat(data.path("currentCandidates").isEmpty()).isTrue();
        assertThat(data.path("observations").isEmpty()).isTrue();
        assertThat(data.path("facts").isEmpty()).isTrue();
        var allowed=new ArrayList<String>();
        data.path("executionState").path("allowedActions").forEach(n->allowed.add(n.asText()));
        assertThat(allowed).containsExactly("SEARCH_REVIEWED_RULES");
        assertThat(data.path("executionState").path("candidates").isEmpty()).isTrue();
        assertThat(data.path("executionState").path("reservedReviewMillis").asInt()).isEqualTo(DEFAULT_BUDGET.maxModelMillis());
        assertThat(data.path("executionState").path("reservedPublicationMillis").asInt()).isEqualTo(5000);
        assertThat(data.path("remainingBudget").path("modelAttemptsIncludingThisCall").asInt()).isEqualTo(RulePublicationContract.MAX_DECISION_CALLS);
        assertThat(data.path("remainingBudget").path("toolSteps").asInt()).isEqualTo(DEFAULT_BUDGET.maxToolStepsPerRun());
        assertThat(wire.path("model").asText()).isEqualTo(RuleAgentProvider.MODEL);
        assertThat(wire.path("stream").asBoolean()).isFalse();
        assertThat(wire.path("enable_thinking").asBoolean()).isFalse();
        assertThat(wire.path("max_completion_tokens").asInt()).isEqualTo(DEFAULT_BUDGET.maxOutputTokens());
    }
    /** 实际请求未取得响应头时只报告超时，不从独立连通性测试推断供应商状态。 */
    @Test void silentSupplierKeepsActualConnectionUnknownWithoutHeaders()throws Exception{
        start(200,good(),1500);
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(500))).isInstanceOf(RuleAgentProvider.Failure.class);
        var d=new ObjectMapper().valueToTree(traces.getLast()).path("diagnostics");
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(traces.getLast().responseReceived()).isFalse();
        assertThat(d.path("connectedMillis").isNull()).isTrue();
        assertThat(d.path("dispatchedMillis").isNumber()).isTrue();
        assertThat(d.path("headersMillis").isNull()).isTrue();
        assertThat(d.path("transportFailure").asText()).isIn("REQUEST_TIMEOUT","DEADLINE_EXCEEDED");
        assertThat(calls).hasValue(1);
    }
    /** 实际连接拒绝可分类；没有连接时刻回调，诊断必须保持未知。 */
    @Test void refusedConnectionLeavesProbeEmptyAndClassifiesFailure()throws Exception{
        start(200,good(),0);
        server.stop(0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        var d=new ObjectMapper().valueToTree(traces.getLast()).path("diagnostics");
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_CONNECTION_FAILURE);
        assertThat(d.path("connectedMillis").isNull()).isTrue();
        // 实际连接被拒：处理器侧零到达（calls==0），而宿主侧恰好一次尝试（BEGIN+FINISHED，无 RECEIVED）。
        assertThat(calls).hasValue(0);
        assertThat(traces).hasSize(2);
        assertThat(traces.get(0).phase()).isEqualTo(RuleAgentProvider.Phase.BEGIN);
        assertThat(traces.get(1).phase()).isEqualTo(RuleAgentProvider.Phase.FINISHED);
        assertThat(traces.stream().map(RuleAgentProvider.Trace::attempt).distinct().toList()).containsExactly(1);
    }
    @Test void revisedBudgetCanReceiveBeyondFiveSecondsWithoutAutomaticRetry()throws Exception{
        start(200,good(),5500);
        var reply=port.decide(view(),Duration.ofSeconds(7));
        assertThat(RuleAgentJson.readAction(reply.actionJson())).isInstanceOf(Search.class);
        assertThat(calls).hasValue(1);
        assertThat(traces.getLast().diagnostics().headersMillis()).isGreaterThanOrEqualTo(5000);
    }
    static java.util.stream.Stream<String[]> protocolFailures(){
        String id="aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String read="{\"type\":\"READ_VERIFIED_RULE\",\"subquestionId\":\"q1\",\"candidateSetId\":\"expired-set\",\"handles\":";
        return java.util.stream.Stream.of(
        new String[]{
            read+"[\""+id+"\",\""+id+"\",\""+id+"\",\""+id+"\"]}","stop","ACTION_PARAMETERS","READ_HANDLE_COUNT"
        },
        new String[]{
            read+"[\""+id+"\",\""+id+"\"]}","stop","ACTION_PARAMETERS","READ_REFERENCE_SET"
        },
        new String[]{
            read+"[\"bad-sensitive-uuid\"]}","stop","ACTION_PARAMETERS","READ_REFERENCE_SET"
        },
        new String[]{
            read+"[\""+id+"\"]}","stop","ACTION_PARAMETERS","READ_REFERENCE_SET"
        },
        new String[]{
            "{sensitive-invalid-json","stop","ACTION_JSON","ACTION_JSON_INVALID"
        },
        new String[]{
            "[{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":\"课程\"}]","stop","ACTION_FIELDS","ACTION_FIELDS_INVALID"
        },
        new String[]{
            "{\"type\":\"EXECUTE_SQL\",\"secret\":\"sensitive-free-body\"}","stop","ACTION_FIELDS","ACTION_TYPE_UNKNOWN"
        },
        new String[]{
            "{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\"}","stop","ACTION_FIELDS","ACTION_FIELDS_INVALID"
        },
        new String[]{
            "{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":\"课程\",\"extra\":\"sensitive-free-body\"}","stop","ACTION_FIELDS","ACTION_FIELDS_INVALID"
        },
        new String[]{
            "{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":7}","stop","ACTION_PARAMETERS","ACTION_PARAMETER_INVALID"
        },
        new String[]{
            "{\"type\":\"SEARCH_REVIEWED_RULES\",\"type\":\"FINISH_CHECK\"}","stop","ACTION_JSON","ACTION_JSON_INVALID"
        },
        new String[]{
            "{}","length","SUPPLIER_RESPONSE","FINISH_REASON_INVALID"
        });
    }
    String response(String action,String finish)throws Exception{
        return new ObjectMapper().writeValueAsString(Map.of("model",RuleAgentProvider.MODEL,"usage",Map.of("prompt_tokens",25,"completion_tokens",12),"choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",action)))));
    }
    @ParameterizedTest @MethodSource("protocolFailures")
    void failuresAreSafeClassifiedAndDoNotExecuteTools(String action,String finish,String stage,String code)throws Exception{
        start(200,call(action,finish),0);
        var fixture=new RuleAgentRunnerTest();
        var outcome=fixture.run(fixture.tools(true,true,false),port);
        assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(outcome.snapshot().execution().toolStepCount()).isZero();
        assertThat(outcome.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(outcome.events()).isEmpty();
        assertThat(port.isTerminated()).isTrue();
        var finalTrace=new ObjectMapper().valueToTree(traces.getLast());
        assertThat(finalTrace.path("validation").path("stage").asText()).isEqualTo(stage);
        assertThat(finalTrace.path("validation").path("code").asText()).isEqualTo(code);
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(traces.getLast().outputTokens()).isEqualTo(12);
        assertThat(traces.stream().map(t->new ObjectMapper().<com.fasterxml.jackson.databind.JsonNode>valueToTree(t)).anyMatch(t->t.path("phase").asText().equals("RECEIVED")&&t.path("inputTokens").asInt()==25)).isTrue();
        assertThat(new ObjectMapper().writeValueAsString(traces)).doesNotContain("sensitive", "local-test-placeholder");
        assertThat(calls).hasValue(1);
    }
    @Test void legalReadUsesCurrentCandidateAndAdvertisesAuthoritativeLimits()throws Exception{
        var handle=UUID.randomUUID();
        start(200,good(),0);
        var observation=new ToolObservation(UUID.randomUUID(),UUID.randomUUID(),1,ObservationStatus.SUCCEEDED,null,DataLevel.CANDIDATE,"synthetic",List.of(new Candidate(handle,"合成原文","2025.9.2")),List.of(),List.of());
        var base=view();
        var input=new RuleAgentRunner.View(base.question(),base.questions(),base.userConditions(),List.of(observation),List.of(),List.of(),List.of(),3,5);
        var projected=new ObjectMapper().readTree(new ObjectMapper().readTree(port.payload(input)).path("messages").get(1).path("content").asText());
        responseBody.set(call("{\"type\":\"READ_VERIFIED_RULE\",\"subquestionId\":\"q1\",\"candidateSetId\":\""+projected.path("candidateSetId").asText()+"\",\"handles\":[\"c1\"]}","stop"));
        assertThat(RuleAgentJson.readAction(port.decide(input,Duration.ofSeconds(1)).actionJson())).isEqualTo(new Read("q1",List.of(handle)));
        var wire=new ObjectMapper().readTree(request.get());
        var data=new ObjectMapper().readTree(wire.path("messages").get(1).path("content").asText());
        var read=java.util.stream.StreamSupport.stream(data.path("actionContract").path("actions").spliterator(),false).filter(n->n.path("type").asText().equals("READ_VERIFIED_RULE")).findFirst().orElseThrow();
        assertThat(read.path("readHandles").path("max").asInt()).isEqualTo(DEFAULT_BUDGET.maxReadHandles());
        assertThat(read.path("readHandles").path("min").asInt()).isEqualTo(1);
        assertThat(read.path("readHandles").path("unique").asBoolean()).isTrue();
        assertThat(read.path("requiredFields").toString()).contains("subquestionId","handles","candidateSetId");
        assertThat(data.toString()).doesNotContain(handle.toString());
        assertThat(data.path("remainingBudget").path("modelAttemptsIncludingThisCall").asInt()).isEqualTo(3);
        assertThat(data.path("remainingBudget").path("toolSteps").asInt()).isEqualTo(5);
        // 响应Schema里的候选枚举必须等于当次完整集合，且只含编号界面；1—3上限来自同一领域预算。
        var branches=wire.path("response_format").path("json_schema").path("schema").path("oneOf");
        var readBranch=java.util.stream.StreamSupport.stream(branches.spliterator(),false)
            .filter(b->b.path("properties").path("type").path("const").asText().equals("READ_VERIFIED_RULE")).findFirst().orElseThrow();
        assertThat(readBranch.path("additionalProperties").asBoolean()).isFalse();
        assertThat(readBranch.path("required").toString()).contains("type","subquestionId","candidateSetId","handles");
        var handles=readBranch.path("properties").path("handles");
        assertThat(handles.path("minItems").asInt()).isEqualTo(MIN_READ_HANDLES);
        assertThat(handles.path("maxItems").asInt()).isEqualTo(DEFAULT_BUDGET.maxReadHandles());
        var values=new ArrayList<String>();
        handles.path("items").path("enum").forEach(n->values.add(n.asText()));
        assertThat(values).containsExactly("c1");
        assertThat(wire.toString()).doesNotContain(handle.toString());
    }
    /** 非对象内容、非 stop 结束原因都不得被当成一个合法动作。 */
    @Test void wrappedOrTruncatedContentIsNeverAcceptedAsOneAction()throws Exception{
        start(200,response("{\"actions\":[]}","stop"),0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(new ObjectMapper().valueToTree(traces.getLast()).path("validation").path("code").asText()).isEqualTo("ACTION_FIELDS_INVALID");
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        responseBody.set(response("[{\"type\":\"FINISH_CHECK\",\"items\":[]}]","stop"));
        int before=calls.get();
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(new ObjectMapper().valueToTree(traces.getLast()).path("validation").path("code").asText()).isEqualTo("ACTION_FIELDS_INVALID");
        assertThat(calls.get()).isEqualTo(before+1);
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
    }
    @Test void invalidEnvelopeKeepsKnownUsageAndMissingUsageStaysUnknown()throws Exception{
        start(200,"{\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":2}}",0);
        assertThatThrownBy(()->port.decide(view(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        var trace=new ObjectMapper().valueToTree(traces.getLast());
        assertThat(trace.path("validation").path("code").asText()).isEqualTo("RESPONSE_STRUCTURE_INVALID");
        assertThat(traces.getLast().inputTokens()).isEqualTo(7);
        assertThat(traces.getLast().outputTokens()).isEqualTo(2);
    }

    RulePublicationContract.SemanticInput reviewInput(){
        return new RulePublicationContract.SemanticInput(RulePublicationContract.REVISION,RuleFactCapabilityCatalogue.VERSION,"a".repeat(64),
            List.of(new RuleAgentRunner.Question("q1","这项安排是指什么？")),Map.of(),List.of(),Map.of());
    }
    String reviewJson()throws Exception{
        var input=reviewInput();
        return new ObjectMapper().writeValueAsString(new RulePublicationContract.SemanticAssessment(input.protocolRevision(),input.inputFingerprint(),
            List.of(),List.of(),List.of(new RulePublicationContract.UnresolvedDemand(new RulePublicationContract.DemandSpan("q1",0,4),
                RulePublicationContract.UnresolvedReason.AMBIGUOUS_SCOPE))));
    }
    RuleSemanticReviewPort reviewPort(){
        assertThat((Object)port).isInstanceOf(RuleSemanticReviewPort.class);
        return (RuleSemanticReviewPort)(Object)port;
    }
    @Test void reviewUsesIndependentInputAndSharedAttemptsWithPurposeAudit()throws Exception{
        start(200,good(),0);
        port.close();
        var batchAllowance=new AtomicInteger(8);
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),
            UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",traces::add,batchAllowance);
        for(int i=0;i<RulePublicationContract.MAX_DECISION_CALLS;i++)port.decide(view(),Duration.ofSeconds(2));
        responseBody.set(response(reviewJson(),"stop"));
        var result=reviewPort().assess(reviewInput(),Duration.ofSeconds(2));
        assertThat(result.unresolvedDemands()).hasSize(1);
        assertThat(calls).hasValue(DEFAULT_BUDGET.maxModelAttemptsPerRun());
        assertThat(traces.getLast().attempt()).isEqualTo(DEFAULT_BUDGET.maxModelAttemptsPerRun());
        var trace=new ObjectMapper().valueToTree(traces.getLast());
        assertThat(trace.path("purpose").asText()).isEqualTo("PUBLICATION_REVIEW");
        assertThat(new ObjectMapper().valueToTree(traces.getFirst()).path("purpose").asText()).isEqualTo("DECISION");
        var data=new ObjectMapper().readTree(new ObjectMapper().readTree(request.get()).path("messages").get(1).path("content").asText());
        assertThat(data.path("input").path("inputFingerprint").asText()).isEqualTo(reviewInput().inputFingerprint());
        assertThat(data.path("reviewContract").path("maxRequirements").asInt()).isEqualTo(RulePublicationContract.MAX_REQUIREMENTS);
        assertThat(data.toString()).doesNotContain("proposal","supportingFactIds","originalToken","actorId","sessionNonce");
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(DEFAULT_BUDGET.maxModelAttemptsPerRun());
        assertThat(batchAllowance).hasValue(8-DEFAULT_BUDGET.maxModelAttemptsPerRun());
    }
    static List<String> invalidReviews(){return List.of("bad-json","{}","null",
        "{\"protocolRevision\":\"RULE_PUBLICATION_REVIEW_01\",\"protocolRevision\":\"duplicate\"}");}
    @ParameterizedTest @MethodSource("invalidReviews")
    void invalidReviewRetainsUsageAndNeverRetries(String content)throws Exception{
        start(200,response(content,"stop"),0);
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(1);
        assertThat(traces.getLast().inputTokens()).isEqualTo(25);
        assertThat(traces.getLast().outputTokens()).isEqualTo(12);
        assertThat(new ObjectMapper().valueToTree(traces.getLast()).path("validation").path("stage").asText()).isEqualTo("REVIEW_JSON");
        assertThat(new ObjectMapper().writeValueAsString(traces)).doesNotContain("bad-json","duplicate","local-test-placeholder");
    }
    @Test void wrongFingerprintUnknownFieldsAndInvalidEnumAreRejected()throws Exception{
        start(200,response(reviewJson().replace("a".repeat(64),"b".repeat(64)),"stop"),0);
        var reviewer=reviewPort();
        assertThatThrownBy(()->reviewer.assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(new ObjectMapper().valueToTree(traces.getLast()).path("validation").path("code").asText()).isEqualTo("REVIEW_INPUT_MISMATCH");
        responseBody.set(response(reviewJson().replace("AMBIGUOUS_SCOPE","invented"),"stop"));
        assertThatThrownBy(()->reviewer.assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        responseBody.set(response(reviewJson().replace("\"requirements\":[]","\"extra\":7,\"requirements\":[]"),"stop"));
        assertThatThrownBy(()->reviewer.assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(3);
    }
    @Test void reviewMissingUsageIsUnknownAndClosedPortDoesNotCall()throws Exception{
        start(200,response(reviewJson(),"stop"),0);
        // 通过树删除usage，不依赖Map序列化字段顺序。
        var outer=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(response(reviewJson(),"stop"));
        outer.remove("usage");
        responseBody.set(outer.toString());
        reviewPort().assess(reviewInput(),Duration.ofSeconds(2));
        assertThat(traces.getLast().inputTokens()).isNull();
        assertThat(traces.getLast().outputTokens()).isNull();
        port.close();
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls).hasValue(1);
    }
    @Test void reviewTimeoutAndExhaustedSharedAllowanceStopLocally()throws Exception{
        start(200,response(reviewJson(),"stop"),500);
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofMillis(60))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(calls.get()).isLessThanOrEqualTo(1);
        port.close();
        port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),
            UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",traces::add,new AtomicInteger(0));
        int before=calls.get();
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofSeconds(1))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(calls.get()).isEqualTo(before);
    }
    static List<String[]> reviewHttpFailures(){return List.of(new String[]{"401","MODEL_AUTH_FAILURE"},new String[]{"429","MODEL_RATE_LIMIT"},new String[]{"503","MODEL_UNAVAILABLE"});}
    @ParameterizedTest @MethodSource("reviewHttpFailures")
    void reviewHttpFailuresKeepPurposeAndDoNotRetry(String status,String reason)throws Exception{
        start(Integer.parseInt(status),"{}",0);
        assertThatThrownBy(()->reviewPort().assess(reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason().name()).isEqualTo(reason);
        assertThat(new ObjectMapper().valueToTree(traces.getLast()).path("purpose").asText()).isEqualTo("PUBLICATION_REVIEW");
        assertThat(calls).hasValue(1);
    }
    @Test void closingInFlightReviewCancelsAndDoesNotReturnLateAssessment()throws Exception{
        start(200,response(reviewJson(),"stop"),1500);
        var reviewer=reviewPort();
        var caller=Executors.newSingleThreadExecutor();
        try{
            var pending=caller.submit(()->{
                try{reviewer.assess(reviewInput(),Duration.ofSeconds(2));return "UNEXPECTED_SUCCESS";}
                catch(RuleAgentProvider.Failure failure){return failure.reason.name();}
            });
            assertThat(requestReceived.await(1,TimeUnit.SECONDS)).isTrue();
            port.close();
            assertThat(pending.get(2,TimeUnit.SECONDS)).isEqualTo("CANCELLED");
            assertThat(calls).hasValue(1);
            assertThat(port.isTerminated()).isTrue();
        }finally{caller.shutdownNow();assertThat(caller.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    /**
     * 诊断样例留档：把本地假服务上五类情形的可验证调用诊断（请求准备→客户端提交→响应头→响应体→
     * 异常类型→实际退出）写成脱敏样例，供人工核对。连接/TLS 时刻只在真实观测到时记录，
     * 未观测到一律 null（未知），不以派发或本地接收冒充供应商已收到。样例全部来自 LOCAL_TEST_ONLY，不判定在线行为。
     */
    @Test @Timeout(40) void diagnosticSamplesAreSavedForLocalFailureModes()throws Exception{
        var mapper=new ObjectMapper();
        var samples=mapper.createArrayNode();
        int callsBefore=0,tracesBefore=0;
        // 1) 正常响应：连接/响应头/首字节/完成时刻与 requestId、模型回传名齐全，单次调用。
        start(200,good(),0);
        callsBefore=calls.get();tracesBefore=traces.size();
        port.decide(view(),Duration.ofSeconds(2));
        assertThat(traces.getLast().responseReceived()).isTrue();
        assertThat(traces.getLast().diagnostics().headersMillis()).isNotNull();
        assertThat(traces.getLast().diagnostics().connectedMillis()).isNull();
        samples.add(diagnosticSample(mapper,"NORMAL_RESPONSE",callsBefore,tracesBefore,false,false));
        assertThat(calls.get()-callsBefore).isEqualTo(1);
        stopRound();
        // 2) 拒绝连接：连接未建立即失败；探测与响应头均未知，处理器零到达，宿主侧恰好一次尝试。
        start(200,good(),0);server.stop(0);
        callsBefore=calls.get();tracesBefore=traces.size();
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(800))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_CONNECTION_FAILURE);
        assertThat(traces.getLast().diagnostics().connectedMillis()).isNull();
        assertThat(calls.get()-callsBefore).isZero();
        samples.add(diagnosticSample(mapper,"CONNECTION_REFUSED",callsBefore,tracesBefore,false,false));
        stopRound();
        // 3) 接收后静默：连接已建立但无响应头；原因码 MODEL_TIMEOUT，不伪报 received。
        start(200,good(),1200);
        callsBefore=calls.get();tracesBefore=traces.size();
        assertThatThrownBy(()->port.decide(view(),Duration.ofMillis(400))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(traces.getLast().reason()).isEqualTo(ReasonCode.MODEL_TIMEOUT);
        assertThat(traces.getLast().diagnostics().connectedMillis()).isNull();
        assertThat(traces.getLast().diagnostics().headersMillis()).isNull();
        assertThat(calls.get()-callsBefore).isEqualTo(1);
        samples.add(diagnosticSample(mapper,"SILENT_AFTER_ACCEPT",callsBefore,tracesBefore,false,false));
        stopRound();
        // 4) 延迟响应：超过 5 秒仍收到完整响应，无自动重试。
        start(200,good(),5200);
        callsBefore=calls.get();tracesBefore=traces.size();
        port.decide(view(),Duration.ofSeconds(8));
        assertThat(traces.getLast().diagnostics().headersMillis()).isGreaterThanOrEqualTo(5000);
        assertThat(calls.get()-callsBefore).isEqualTo(1);
        samples.add(diagnosticSample(mapper,"DELAYED_RESPONSE",callsBefore,tracesBefore,false,false));
        stopRound();
        // 5) 取消与迟到响应：进行中关闭端口，不返回迟到结果，资源退出。
        start(200,response(reviewJson(),"stop"),1500);
        callsBefore=calls.get();tracesBefore=traces.size();
        var reviewer=reviewPort();
        var caller=Executors.newSingleThreadExecutor();
        try{
            var pending=caller.submit(()->{try{reviewer.assess(reviewInput(),Duration.ofSeconds(3));return "UNEXPECTED_SUCCESS";}catch(RuleAgentProvider.Failure failure){return failure.reason.name();}});
            // requestReceived 是实例级闩锁且已被本方法先前情形消耗，不能作为本轮的到达判据；改以处理器调用计数确认到达。
            var deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(calls.get()==callsBefore&&System.nanoTime()<deadline)Thread.sleep(5);
            assertThat(calls.get()).isGreaterThan(callsBefore);
            port.close();
            assertThat(pending.get(3,TimeUnit.SECONDS)).isEqualTo("CANCELLED");
            assertThat(calls.get()-callsBefore).isEqualTo(1);
            samples.add(diagnosticSample(mapper,"CANCELLED_LATE_RESPONSE",callsBefore,tracesBefore,true,port.isTerminated()));
        }finally{caller.shutdownNow();assertThat(caller.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
        var evidenceDir=System.getProperty("zhku.rulecheck.evidenceDir");
        assertThat(evidenceDir).as("诊断样例留档需要 -Dzhku.rulecheck.evidenceDir").isNotNull();
        java.nio.file.Files.write(java.nio.file.Path.of(evidenceDir,"provider-diagnostic-samples.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(samples),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        assertThat(samples).hasSize(5);
    }
    /** 一条脱敏诊断样例：只落盘本地假服务真实观测到的字段；未观测时刻保持 null。 */
    com.fasterxml.jackson.databind.node.ObjectNode diagnosticSample(ObjectMapper mapper,String scenario,int callsBefore,int tracesBefore,boolean cancelled,boolean resourcesClosed){
        var last=traces.getLast();var diag=last.diagnostics();
        var node=mapper.createObjectNode();
        node.put("scenario",scenario);
        node.put("source","LOCAL_TEST_ONLY_FAKE_SUPPLIER");
        if(last.reason()==null)node.putNull("reasonCode");else node.put("reasonCode",last.reason().name());
        node.put("lastPhase",last.phase().name());
        node.put("responseReceived",last.responseReceived());
        var phases=mapper.createArrayNode();for(int i=tracesBefore;i<traces.size();i++)phases.add(traces.get(i).phase().name());
        node.set("phases",phases);
        node.put("httpCallsThisScenario",calls.get()-callsBefore);
        node.put("cancelled",cancelled);
        node.put("resourcesClosed",resourcesClosed);
        var d=node.putObject("diagnostics");
        d.put("requestBytes",diag.requestBytes());
        d.put("elapsedMillis",diag.elapsedMillis());
        d.put("dispatchedMillis",diag.dispatchedMillis());
        d.put("connectedMillis",diag.connectedMillis());
        if(diag.transportFailure()==null)d.putNull("transportFailure");else d.put("transportFailure",diag.transportFailure().name());
        d.put("headersMillis",diag.headersMillis());
        d.put("firstBodyMillis",diag.firstBodyMillis());
        d.put("bodyCompletedMillis",diag.bodyCompletedMillis());
        d.put("responseBytes",diag.responseBytes());
        d.put("requestId",diag.requestId());
        d.put("responseModel",diag.responseModel());
        return node;
    }
    /** 结束一轮本地假服务与线程池，避免下一情形复用已停端口或泄漏执行器。 */
    void stopRound()throws Exception{
        if(port!=null)port.close();
        if(server!=null)server.stop(0);
        if(workers!=null){workers.shutdownNow();workers.awaitTermination(2,TimeUnit.SECONDS);}
    }
}
