package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.ruleagent.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 离线测试只读本地计划/合成快照，绝不分派Live或构造Provider。 */
class RuleAgentE13PreparationTest {
    Path root; ObjectNode plan;
    @BeforeEach void setup() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve(RuleAgentE13Policy.PLAN))) root=root.getParent();
        plan=(ObjectNode) RuleAgentThreeQuestionPackage.JSON.readTree(Files.readAllBytes(root.resolve(RuleAgentE13Policy.PLAN)));
    }
    @AfterEach void reset() { System.clearProperty("zhku.three.profile"); }
    @Test void explicitProfilesNeverFallBackToThree() throws Exception {
        for(String profile:List.of("E02","E13","Three")) {
            System.setProperty("zhku.three.profile",profile);
            assertThat(RuleAgentThreeQuestionPackage.profile()).isEqualTo(profile);
            assertThat(RuleAgentThreeQuestionPackage.plan(root).path("maximumProviderCalls").asInt()).isEqualTo(profile.equals("Three")?20:5);
            assertThat(RuleAgentThreeQuestionPackage.singlePageProfile()).isEqualTo(!profile.equals("Three"));
        }
        System.setProperty("zhku.three.profile","e13");assertThatThrownBy(()->RuleAgentThreeQuestionPackage.plan(root)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void strictPlanRejectsScopeAndBudgetDrift() {
        RuleAgentE13Policy.validate(plan);
        for(String key:List.of("maximumTasks","maximumRuns","maximumProviderCalls","maximumOutputTokensPerCall","maximumRequestBytes","automaticRetries")) {
            ObjectNode bad=plan.deepCopy();bad.put(key,99);assertThatThrownBy(()->RuleAgentE13Policy.validate(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        ObjectNode bad=plan.deepCopy();((ObjectNode)bad.path("cases").get(0)).put("reply","PUBLIC");assertThatThrownBy(()->RuleAgentE13Policy.validate(bad)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode wrongMoney=plan.deepCopy();((ObjectNode)wrongMoney.path("money")).put("hardBudget","16.10");assertThatThrownBy(()->RuleAgentE13Policy.validate(wrongMoney)).isInstanceOf(IllegalArgumentException.class);
        assertThat(RuleAgentThreeQuestionPackage.perCallReserve(plan).toPlainString()).isEqualTo("0.8027918");
    }
    TaskSnapshot snapshot() throws Exception {
        JsonNode snapshots=RuleAgentThreeQuestionPackage.JSON.readTree(Files.readAllBytes(root.resolve("knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json"))).path("snapshots");
        for(JsonNode row:snapshots) if(row.path("name").asText().equals("honest-unsupported-demand")) {
            ObjectNode value=row.path("value").deepCopy();
            ((ObjectNode)value.path("execution")).put("mode","ONLINE").put("providerResponseReceived",true).put("modelAttemptCount",3);
            return RuleAgentThreeQuestionPackage.JSON.treeToValue(value,TaskSnapshot.class);
        }
        throw new IllegalStateException("missing synthetic fixture");
    }
    @Test void safeNoneTerminalIsNotCompleteAndScopeCannotRefill() throws Exception {
        TaskSnapshot s=snapshot();RuleAgentE13Policy.terminal(s);
        var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
        var input=new RuleAgentRunner.Input(owner,s.taskId(),2,RuleAgentE13Policy.QUESTION,List.of(new RuleAgentRunner.Question("q1",RuleAgentE13Policy.QUESTION)),Map.of());
        var scope=new RuleAgentE02Scope(plan,"E13");scope.begin(input,input.question(),s.runId());
        // 完整合成快照版本独立于begin；改成合法同run/version的对象。
        var r=s.result();var result=new ResultEnvelope(2,s.runId(),r.catalogueVersion(),r.referenceRevision(),r.coverage(),r.items(),r.evidence(),r.evidenceRelations());
        var same=new TaskSnapshot(s.schemaVersion(),s.taskId(),s.turnId(),s.runId(),2,s.taskStatus(),s.runState(),true,result,null,s.execution());
        scope.complete(input,new RuleAgentRunner.Outcome(same,List.of()));
        assertThat(scope.closed()).isTrue();assertThat(scope.runs()).isEqualTo(1);
        assertThatThrownBy(()->scope.begin(input,input.question(),UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }
    @Test void wrongRawInputClosesBeforeAnyProvider() throws Exception {
        TaskSnapshot s=snapshot();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
        var input=new RuleAgentRunner.Input(owner,s.taskId(),2,RuleAgentE13Policy.QUESTION,List.of(new RuleAgentRunner.Question("q1",RuleAgentE13Policy.QUESTION)),Map.of());
        var scope=new RuleAgentE02Scope(plan,"E13");assertThatThrownBy(()->scope.begin(input,"另一题",s.runId())).isInstanceOf(IllegalStateException.class);assertThat(scope.closed()).isTrue();assertThat(scope.runs()).isZero();
    }
    @Test void mixedOrUnknownReviewCannotBeBlessedAsUnsupported() throws Exception {
        var json=RuleAgentThreeQuestionPackage.JSON;
        for(String other:List.of("AMBIGUOUS_SCOPE","UNCLASSIFIED_DEMAND")) {
            String content="{\"requirements\":[],\"unresolvedDemands\":[{\"reason\":\"UNSUPPORTED_DIMENSION\"},{\"reason\":\""+other+"\"}]}";
            JsonNode response=json.valueToTree(Map.of("choices",List.of(Map.of("message",Map.of("content",content)))));
            assertThatThrownBy(()->RuleAgentE13Policy.reviewBoundary(response)).isInstanceOf(IllegalStateException.class);
        }
        JsonNode good=json.valueToTree(Map.of("choices",List.of(Map.of("message",Map.of("content","{\"requirements\":[],\"unresolvedDemands\":[{\"reason\":\"UNSUPPORTED_DIMENSION\"}]}")))));
        RuleAgentE13Policy.reviewBoundary(good);
    }
}
