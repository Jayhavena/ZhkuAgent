package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 两端共用合成合同样例，不代表实际政策、模型或学生验收。 */
class RuleAgentContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private JsonNode fixtures() throws Exception {
        try(var stream=getClass().getResourceAsStream("/ruleagent/contract-v3-fixtures.json")) {
            assertThat(stream).as("共享合同样例必须真实存在").isNotNull();return json.readTree(stream);
        }
    }
    private Object read(String method,String input) throws Throwable {
        return switch(method){
            case "readSnapshot"->RuleAgentJson.readSnapshot(input);
            case "readAction"->RuleAgentJson.readAction(input);
            case "readCreate"->RuleAgentJson.readCreate(input);
            case "readMessage"->RuleAgentJson.readMessage(input);
            case "readCancel"->RuleAgentJson.readCancel(input);
            case "readObservation"->RuleAgentJson.readObservation(input);
            default->throw new AssertionError("未知测试合同入口");
        };
    }
    @Test void sharedSnapshotsAreAcceptedAndRoundTripWithoutDroppingFields() throws Throwable {
        for(var row:fixtures().path("snapshots")) {
            Object parsed=read("readSnapshot",row.path("value").toString());
            // 比较真正的JSON语义，不把Java long/JsonNode int的内存类型差异当协议漂移。
            JsonNode emitted=json.readTree(json.writeValueAsBytes(parsed));
            assertThat(emitted).as(row.path("name").asText()).isEqualTo(row.path("value"));
        }
    }
    @Test void sharedActionsAndRequestsUseOnlyExplicitFields() throws Throwable {
        for(var row:fixtures().path("actions"))assertThat(read("readAction",row.toString())).isNotNull();
        for(var row:fixtures().path("requests"))assertThat(read(row.path("method").asText(),row.path("value").toString())).isNotNull();
    }
    @Test void observationsSeparateCandidatesVerifiedFactsAndToolFailures() throws Throwable {
        for(var row:fixtures().path("observations"))assertThat(read("readObservation",row.toString())).isNotNull();
    }
    @Test void semanticAndBoundaryMutationsAreRejected() throws Exception {
        for(var row:fixtures().path("invalid")) {
            assertThatThrownBy(()->read(row.path("method").asText(),row.path("value").toString()),row.path("name").asText())
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void malformedDuplicateAndTrailingJsonAreRejected() throws Exception {
        String valid=fixtures().path("snapshots").get(0).path("value").toString();
        for(String invalid:new String[]{valid+"{}",valid.replace("\"schemaVersion\":3","\"schemaVersion\":3,\"schemaVersion\":2"),"{}",valid.replace("\"terminated\":true","\"terminated\":null")})
            assertThatThrownBy(()->read("readSnapshot",invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->read("readSnapshot"," ".repeat(65537))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nullJsonCannotBecomeAValidTaskActionOrRequest() {
        for(String method:new String[]{"readSnapshot","readAction","readCreate","readMessage","readCancel","readObservation"})
            assertThatThrownBy(()->read(method,"null"),method).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void defaultBudgetAndTaskTransitionsDoNotPretendToExecute() throws Throwable {
        Object budget=RuleAgentContract.DEFAULT_BUDGET;
        assertThat(json.valueToTree(budget).path("maxModelAttemptsPerRun").asInt()).isEqualTo(4);
        assertThat(json.valueToTree(budget).path("maxRunMillis").asInt()).isEqualTo(RuleAgentContract.DEFAULT_BUDGET.maxRunMillis());
        assertThat(json.valueToTree(budget).path("maxModelMillis").asInt()).isEqualTo(RuleAgentContract.DEFAULT_BUDGET.maxModelMillis());
        assertThat(json.valueToTree(budget).path("maxTaskRuns").asInt()).isEqualTo(3);
        assertThat(RuleAgentContract.canTransition("WAITING_USER","ACTIVE")).isTrue();
        assertThat(RuleAgentContract.canTransition("RESOLVED","ACTIVE")).isFalse();
        assertThat(RuleAgentContract.canTransition("ACTIVE","RESOLVED")).isTrue();
    }
}
