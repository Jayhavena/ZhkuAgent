package com.zhku.knowledge.ruleagent;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.agent.agent.runtime.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
@Timeout(15)
class RuleAgentReviewDiagnosticsTest {
    final ObjectMapper json=new ObjectMapper();
    final RuleAgentProviderTest http=new RuleAgentProviderTest();
    final List<JsonNode> exchanges=new ArrayList<>();
    SemanticInput input(boolean two){return new SemanticInput(REVISION,RuleFactCapabilityCatalogue.VERSION,"a".repeat(64),
        two?List.of(new RuleAgentRunner.Question("q1","合成问题？"),new RuleAgentRunner.Question("q2","另一问题？")):List.of(new RuleAgentRunner.Question("q1","合成问题？")),Map.of(),
        List.of(new SemanticFact("f1","synthetic","合成规则",Map.of(),List.of())),Map.of());}
    ObjectNode valid() {return (ObjectNode)json.valueToTree(new SemanticAssessment(REVISION,"a".repeat(64),List.of(
        new Requirement("r1",new DemandSpan("q1",0,5),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
        List.of(new SemanticBinding("r1","f1",BindingRole.CORE)),List.of()));}
    @ParameterizedTest @ValueSource(strings={"REVIEW_QUESTION_UNKNOWN","REVIEW_SPAN_OUT_OF_RANGE","REVIEW_INPUT_MISMATCH","REVIEW_QUESTION_UNCOVERED","REVIEW_REQUIREMENT_UNKNOWN","REVIEW_FACT_NOT_IN_INPUT"})
    void eachBindingFailureHasItsOwnCode(String code)throws Exception{
        var out=valid();var span=(ObjectNode)out.path("requirements").get(0).path("span");var binding=(ObjectNode)out.path("bindings").get(0);
        switch(code){case "REVIEW_QUESTION_UNKNOWN"->span.put("subquestionId","absent");case "REVIEW_SPAN_OUT_OF_RANGE"->span.put("endExclusive",6);
            case "REVIEW_INPUT_MISMATCH"->out.put("inputFingerprint","b".repeat(64));case "REVIEW_REQUIREMENT_UNKNOWN"->binding.put("requirementId","absent");case "REVIEW_FACT_NOT_IN_INPUT"->binding.put("factId","absent");default->{}}
        var in=input(code.equals("REVIEW_QUESTION_UNCOVERED"));
        var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(out.toString(),in),RuleAgentProtocol.Rejected.class);
        assertThat(rejected).isNotNull();assertThat(rejected.validation.stage()).isEqualTo(RuleAgentProtocol.Stage.REVIEW_BINDING);
        save(code,Map.of("synthetic",true,"input",in,"response",out,"observedCode",rejected.validation.code().name()));
        assertThat(rejected.validation.code().name()).isEqualTo(code);
        http.start(200,http.response(out.toString(),"stop"),0);capture(this::collect);
        assertThatThrownBy(()->http.port.assess(in,Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(http.traces.getLast().validation().code().name()).isEqualTo(code);assertThat(http.calls).hasValue(1);
        assertThat(exchanges).extracting(e->e.path("direction").asText()).containsExactly("REQUEST","RESPONSE");
        save(code,Map.of("synthetic",true,"input",in,"response",out,"observedCode",rejected.validation.code().name(),"exchanges",exchanges));
    }
    @Test void validReviewStillPassesWithoutRepairingResponse(){assertThat(RulePublicationProtocol.decode(valid().toString(),input(false)).bindings()).hasSize(1);}
    void capture(Consumer<Object> consumer)throws Exception{
        http.port.close();
        var constructor=Arrays.stream(RuleAgentProvider.class.getConstructors()).filter(c->c.getParameterCount()==7).findFirst();
        assertThat(constructor).as("explicit controlled-exchange recorder constructor").isPresent();
        http.port=(RuleAgentProvider)constructor.orElseThrow().newInstance(new RuleAgentProvider.Settings(URI(),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),
            (Supplier<String>)()->"local-test-placeholder",(Consumer<RuleAgentProvider.Trace>)http.traces::add,consumer,new AtomicInteger(5));
    }
    java.net.URI URI(){return java.net.URI.create("http://127.0.0.1:"+http.server.getAddress().getPort()+"/chat/completions");}
    void collect(Object exchange){exchanges.add(json.valueToTree(exchange));}
    @Test void rejectedReviewRetainsControlledBodiesAndRedactsCredential()throws Exception{
        String body=http.response(http.reviewJson().replace("a".repeat(64),"b".repeat(64)),"stop");
        var outer=(ObjectNode)json.readTree(body);outer.put("diagnosticEcho","local-test-placeholder");body=outer.toString();http.start(200,body,0);capture(this::collect);
        assertThatThrownBy(()->http.port.assess(http.reviewInput(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(exchanges).extracting(e->e.path("direction").asText()).containsExactly("REQUEST","RESPONSE");
        assertThat(exchanges.toString()).doesNotContain("local-test-placeholder","Bearer ");
        assertThat(exchanges.get(1).path("redacted").asBoolean()).isTrue();
        assertThat(exchanges.get(1).path("bodySha256").asText()).isEqualTo(http.traces.getLast().responseSha256());
        assertThat(json.readTree(exchanges.getFirst().path("body").asText()).path("messages")).hasSize(2);
        assertThat(http.calls).hasValue(1);save("controlled-rejected-review",exchanges);
    }
    @Test void validDecisionAlsoRecordsCanonicalProposal()throws Exception{
        http.start(200,http.good(),0);capture(this::collect);http.port.decide(http.view(),Duration.ofSeconds(2));
        assertThat(exchanges).extracting(e->e.path("direction").asText()).containsExactly("REQUEST","RESPONSE","PROPOSED_ACTION");
        assertThat(json.readTree(exchanges.getLast().path("body").asText()).path("type").asText()).isEqualTo("SEARCH_REVIEWED_RULES");save("controlled-proposed-action",exchanges);
    }
    @Test void requestArchiveFailurePreventsDispatch()throws Exception{
        http.start(200,http.good(),0);capture(e->{throw new IllegalStateException("synthetic disk failure");});
        assertThatThrownBy(()->http.port.decide(http.view(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(http.calls).hasValue(0);assertThat(http.traces.getLast().reason()).isEqualTo(ReasonCode.AUDIT_UNAVAILABLE);
    }
    @Test void responseArchiveFailurePreventsAcceptedReply()throws Exception{
        http.start(200,http.good(),0);capture(e->{collect(e);if(exchanges.size()==2)throw new IllegalStateException("synthetic disk failure");});
        assertThatThrownBy(()->http.port.decide(http.view(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        assertThat(http.calls).hasValue(1);assertThat(http.traces.getLast().reason()).isEqualTo(ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(http.traces.getLast().inputTokens()).isEqualTo(25);assertThat(http.traces.getLast().outputTokens()).isEqualTo(12);
    }
    @ParameterizedTest @ValueSource(strings={"VALUE","FIELD","INVALID_JSON"})
    void unicodeEscapedCredentialCannotBeRecoveredFromControlledRecord(String variant)throws Exception{
        var outer=(ObjectNode)json.readTree(http.good());
        if(variant.equals("FIELD"))outer.put("local-test-placeholder","echo");else outer.put("diagnosticEcho","local-test-placeholder");
        String body=outer.toString().replace("local-test-placeholder",String.valueOf((char)92)+"u006cocal-test-placeholder");
        if(variant.equals("INVALID_JSON"))body=body.substring(0,body.length()-1);
        http.start(200,body,0);capture(this::collect);
        if(variant.equals("INVALID_JSON"))assertThatThrownBy(()->http.port.decide(http.view(),Duration.ofSeconds(2))).isInstanceOf(RuleAgentProvider.Failure.class);
        else http.port.decide(http.view(),Duration.ofSeconds(2));
        var response=exchanges.stream().filter(e->e.path("direction").asText().equals("RESPONSE")).findFirst().orElseThrow();
        assertThat(response.path("redacted").asBoolean()).isTrue();
        if(!variant.equals("INVALID_JSON"))assertThat(json.readTree(response.path("body").asText()).toString()).doesNotContain("local-test-placeholder");
        save("escaped-credential-"+variant,exchanges);
    }
    @Test void providerReviewFailureKeepsProposedActionAndReviewEvent()throws Exception{
        var f=new RuleAgentRunnerTest();var in=RuleAgentRunner.Input.single(f.owner,f.task,"选修课程类别有哪些？");
        RuleSemanticReviewPort review=(safe,timeout)->{throw new RuleAgentProvider.Failure(ReasonCode.INVALID_MODEL_RESPONSE,true);};
        var result=new RuleAgentRunner(f.responding(true),new RuleActionBroker(f.tools(true,true,true)),Clock.systemUTC(),review).run(AgentRunContext.create(in.question()),in);
        assertThat(result.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("ASK_NECESSARY_CONDITION");assertThat(e.reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);});
        assertThat(result.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("PUBLICATION_REVIEW");assertThat(e.reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);});save("runner-failed-proposal",result);
    }
    void save(String name,Object value)throws Exception{
        String output=System.getProperty("zhku.ruleagent.diagnostics.output");if(output==null)return;
        Path directory=Path.of(output);Files.createDirectories(directory);Files.write(directory.resolve(name+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }
    @AfterEach void close()throws Exception{http.close();}
}
