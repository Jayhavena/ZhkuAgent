package com.zhku.knowledge.ruleagent;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleAgentKnownConditionActionTest {
    @Test void providerSchemaAndFeedbackBothExcludeKnownAskWithoutRemovingFinish() throws Exception {
        var known=Map.of("courseType","PUBLIC");
        var constrained=RuleAgentRunner.constrainClarification(state,conditions,known);
        try(var port=new RuleAgentProvider(new RuleAgentProvider.Settings(java.net.URI.create("http://127.0.0.1:1"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),
            UUID.randomUUID(),UUID.randomUUID(),()->{throw new AssertionError("序列化不得读取密钥");},t->{throw new AssertionError("不得外发");},new java.util.concurrent.atomic.AtomicInteger(0))) {
            var view=new RuleAgentRunner.View("公共选修课规则",List.of(new RuleAgentRunner.Question("q1","公共选修课规则")),known,
                List.of(),List.of(),List.of(),List.of(),2,4,constrained,List.of());
            var json=new com.fasterxml.jackson.databind.ObjectMapper();var payload=json.readTree(port.payload(view));
            String schema=payload.path("response_format").path("json_schema").path("schema").toString();
            assertFalse(schema.contains("ASK_NECESSARY_CONDITION"));assertTrue(schema.contains("FINISH_CHECK"));
            var feedback=json.readTree(payload.path("messages").get(1).path("content").asText());
            assertFalse(feedback.path("executionState").path("allowedActions").toString().contains("ASK_NECESSARY_CONDITION"));
        }
    }
    final RuleAgentRunner.ActionState state=new RuleAgentRunner.ActionState(
        List.of("ASK_NECESSARY_CONDITION","FINISH_CHECK"),List.of(),8,32768,80000,20000,5000);
    final List<RulePublicationContract.ReviewedCondition> conditions=List.of(
        new RulePublicationContract.ReviewedCondition("r","courseType","课程类别？",List.of("PUBLIC","PROFESSIONAL"),List.of("r:f")));
    @Test void knownConditionCannotSpendAnotherDecisionOnClarification() {
        var constrained=RuleAgentRunner.constrainClarification(state,conditions,Map.of("courseType","PUBLIC"));
        assertEquals(List.of("FINISH_CHECK"),constrained.allowedActions());
        assertEquals(state.runMillisRemaining(),constrained.runMillisRemaining());
    }
    @Test void missingReviewedConditionRemainsAvailable() {
        assertEquals(state,RuleAgentRunner.constrainClarification(state,conditions,Map.of()));
    }
    @Test void unregisteredOrAbsentConditionCannotCreateAnAskCapability() {
        assertFalse(RuleAgentRunner.constrainClarification(state,List.of(),Map.of()).allowedActions().contains("ASK_NECESSARY_CONDITION"));
        var finishOnly=new RuleAgentRunner.ActionState(List.of("FINISH_CHECK"),List.of(),8,32768,80000,20000,5000);
        assertEquals(finishOnly,RuleAgentRunner.constrainClarification(finishOnly,conditions,Map.of()));
    }
}
