package com.zhku.knowledge.ruleagent;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import com.zhku.agent.agent.runtime.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
@Timeout(15)
class RuleAgentOnlineFactoryTest {
    HttpServer server;
    ExecutorService workers;
    RuleAgentOnlineFactory factory;
    final AtomicInteger calls=new AtomicInteger();
    final List<RuleAgentProvider.Trace> traces=new CopyOnWriteArrayList<>();
    final RuleAgentRunnerTest fixture=new RuleAgentRunnerTest();
    void start(int quota)throws Exception{
        var tools=fixture.tools(true,true,false);
        workers=Executors.newFixedThreadPool(2);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(workers);
        server.createContext("/chat/completions",e->{
            try{
                calls.incrementAndGet();
                var json=new ObjectMapper();
                var request=json.readTree(e.getRequestBody());
                var view=json.readTree(request.path("messages").path(1).path("content").asText());
                NextAction action;
                if(view.path("observations").isEmpty())action=new Search("q1","课程类别");
                else if(view.path("facts").isEmpty())action=new Read("q1",List.of(UUID.fromString(view.path("observations").get(0).path("candidates").get(0).path("handle").asText())));
                else action=new Finish(List.of(new Proposal("q1",List.of(view.path("facts").get(0).path("id").asText()),List.of(),null)));
                byte[] bytes=json.writeValueAsBytes(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",json.writeValueAsString(action))))));
                e.sendResponseHeaders(200,bytes.length);
                e.getResponseBody().write(bytes);
            }
            catch(Exception ignored){
            }
            finally{
                e.close();
            }
        });
        server.start();
        factory=new RuleAgentOnlineFactory(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),"qwen-plus","LOCAL_TEST_ONLY"),()->"local-test-placeholder",(owner,task)->tools,traces::add,quota,Clock.systemUTC());
    }
    @AfterEach void cleanup()throws Exception{
        if(factory!=null){
            factory.close();
            assertThat(factory.resourcesClosed()).isTrue();
        }
        if(server!=null)server.stop(0);
        if(workers!=null){
            workers.shutdownNow();
            assertThat(workers.awaitTermination(2,TimeUnit.SECONDS)).isTrue();
        }
    }
    RuleAgentRunner.Input input(){
        return RuleAgentRunner.Input.single(fixture.owner,fixture.task,"选修课程类别有哪些？");
    }
    @Test void localProviderAndActualDomainToolsFollowObservations()throws Exception{
        start(4);
        var work=factory.open(fixture.owner,fixture.task);
        var result=work.run(AgentRunContext.create(input().question()),input());
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(result.events()).extracting(RuleAgentRunner.Event::action).containsExactly("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","FINISH_CHECK");
        assertThat(calls).hasValue(3);
        assertThat(result.snapshot().execution().providerResponseReceived()).isFalse();
        assertThat(factory.resourcesClosed()).isTrue();
        assertThat(work.revalidate(result.snapshot().result())).isTrue();
        assertThat(traces.getLast().inputTokens()).isNull();
        assertThat(traces.getLast().outputTokens()).isNull();
    }
    @Test void quotaIsSharedAcrossNewRunsAndNeverReset()throws Exception{
        start(2);
        var work=factory.open(fixture.owner,fixture.task);
        var first=work.run(AgentRunContext.create(input().question()),input());
        assertThat(first.snapshot().taskStatus()).isEqualTo(TaskStatus.STOPPED);
        work.run(AgentRunContext.create(input().question()),input());
        assertThat(calls).hasValue(2);
        assertThat(factory.remainingCalls()).isZero();
        assertThat(traces.stream().filter(t->t.diagnostics().dispatchedMillis()!=null&&t.phase()==RuleAgentProvider.Phase.RECEIVED).count()).isEqualTo(2);
        assertThat(traces.stream().filter(t->t.validation().code()==RuleAgentProtocol.Code.BUDGET_UNAVAILABLE).count()).isEqualTo(2);
    }
    @Test void probeStartupRequiresSpecificPackageApproval(){
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","probe-approved","ZHKU_RULE_AGENT_PROBE_CASE","P01"));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64));
        env.put("ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64));
        var result=RuleAgentOnlineProbeHost.environmentCheck(env);
        assertThat(result.get("maximumProviderCalls")).isEqualTo(4);
        assertThat(result.get("providerCalls")).isEqualTo(0);
        assertThat(new ObjectMapper().valueToTree(result).toString()).doesNotContain("local-test-placeholder");
    }
    @Test void selectiveManualProbeCannotRepeatSuccessfulCaseOrResetFullQuota(){
        var env=new HashMap<>(Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_CASE","P01"));
        assertThat(RuleAgentOnlineProbeHost.environmentCheck(env).get("maximumProviderCalls")).isEqualTo(4);
        env.put("ZHKU_RULE_AGENT_PROBE_CASE","P02");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void revisedProbeCannotSilentlyUseOldAllCaseAuthorization(){
        var env=Map.of("ZHKU_RULE_AGENT_MODE","ONLINE","DASHSCOPE_API_KEY","local-test-placeholder","ZHKU_RULE_AGENT_AUTHORIZATION_ID","a".repeat(64),"ZHKU_RULE_AGENT_PROBE_SHA256","a".repeat(64));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.environmentCheck(env)).isInstanceOf(IllegalArgumentException.class);
    }
    com.fasterxml.jackson.databind.node.ObjectNode packet(){
        var p=new ObjectMapper().createObjectNode();
        p.put("endpoint",RuleAgentProvider.BEIJING.toString());
        p.put("model","qwen-plus");
        p.put("budgetRevision","T05_READ_BUDGET_02");
        p.put("maximumProviderCalls",4);
        p.put("perRunCalls",4);
        p.put("timeoutMillis",10000);
        p.put("runTimeoutMillis",45000);
        p.put("maximumOutputTokens",1024);
        p.put("automaticRetries",0);
        p.put("systemPrompt",RuleAgentProvider.SYSTEM);
        p.put("protocolRevision","RULE_AGENT_ACTION_CONTRACT_01");
        p.set("actionContract",RuleAgentProtocol.description());
        p.putObject("remainingBudgetMaximum").put("modelAttemptsIncludingThisCall",DEFAULT_BUDGET.maxModelAttemptsPerRun()).put("toolSteps",DEFAULT_BUDGET.maxToolStepsPerRun());
        p.set("budget",new ObjectMapper().valueToTree(DEFAULT_BUDGET));
        p.putArray("cases").addObject().put("id","P01").put("question","专业选修课和公共选修课在修读安排上有什么不同？");
        return p;
    }
    void validate(JsonNode p)throws Throwable{
        try{
            RuleAgentOnlineProbeHost.class.getDeclaredMethod("validatePackage",JsonNode.class).invoke(null,p);
        }
        catch(java.lang.reflect.InvocationTargetException e){
            throw e.getCause();
        }
    }
    @Test void revisedPacketBindsCurrentBudgetAndRejectsOldBudgetOrExpandedScope()throws Throwable{
        validate(packet());
        for(String field:List.of("timeoutMillis","runTimeoutMillis","maximumProviderCalls","automaticRetries")){
            var p=packet();
            p.put(field,field.equals("timeoutMillis")?5000:1);
            assertThatThrownBy(()->validate(p)).isInstanceOf(IllegalArgumentException.class);
        }
        var wrong=packet();
        wrong.putArray("cases").addObject().put("id","P02").put("question","old successful case");
        assertThatThrownBy(()->validate(wrong)).isInstanceOf(IllegalArgumentException.class);
        var budget=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)budget.path("budget")).put("maxReadHandles",4);
        assertThatThrownBy(()->validate(budget)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void packetRejectsEditedActionContractAndDynamicBudget()throws Throwable {
        var edited=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)edited.path("actionContract")).put("unexpected","changed");
        assertThatThrownBy(()->validate(edited)).isInstanceOf(IllegalArgumentException.class);
        var budget=packet();
        ((com.fasterxml.jackson.databind.node.ObjectNode)budget.path("remainingBudgetMaximum")).put("toolSteps",99);
        assertThatThrownBy(()->validate(budget)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void packetRejectsMissingOrOldProtocolIdentity()throws Throwable {
        var old=packet();
        old.remove("protocolRevision");
        assertThatThrownBy(()->validate(old)).isInstanceOf(IllegalArgumentException.class);
        var prompt=packet();
        prompt.put("systemPrompt","historical prompt");
        assertThatThrownBy(()->validate(prompt)).isInstanceOf(IllegalArgumentException.class);
    }
}
