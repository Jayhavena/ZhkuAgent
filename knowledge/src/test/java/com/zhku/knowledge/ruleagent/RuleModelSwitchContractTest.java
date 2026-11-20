package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 模型切换合同验收（离线部分）：决策与核验共用同一合同、请求侧按模型能力选择约束方式、
 * 本地校验带脱敏字段路径。换模型先过本套，再做小题实测；本套不代表真实供应商行为。
 */
class RuleModelSwitchContractTest {
    final ObjectMapper json=new ObjectMapper();

    SemanticInput input(){return new SemanticInput(REVISION,RuleFactCapabilityCatalogue.VERSION,"a".repeat(64),
        List.of(new RuleAgentRunner.Question("q1","合成问题？")),java.util.Map.of(),
        List.of(new SemanticFact("f1","synthetic","合成规则",java.util.Map.of(),List.of())),java.util.Map.of());}

    RuleAgentProvider provider(String model){
        return new RuleAgentProvider(new RuleAgentProvider.Settings(java.net.URI.create("http://127.0.0.1:1/chat/completions"),model,"LOCAL_TEST_ONLY"),
            UUID.randomUUID(),UUID.randomUUID(),()->"local-key",trace->{},new AtomicInteger(5));
    }

    @Test void reviewSchemaMirrorsTheContractDescriptionFieldByField() {
        var schema=RulePublicationProtocol.schema();
        var description=RulePublicationProtocol.description();
        for(String record:List.of("SemanticAssessment","Requirement","SemanticBinding","UnresolvedDemand")) {
            var declared=(ArrayNode)description.path("records").path(record);
            var rootLevel=record.equals("SemanticAssessment");
            var properties=rootLevel?schema.path("properties"):(JsonNode)requirementItems(schema,record);
            var required=rootLevel?schema.path("required"):groupRequired(schema,record);
            for(var field:declared){
                assertThat(properties.path(field.asText()).isMissingNode()).as(record+"."+field).isFalse();
                assertThat(required.toString()).as(record+"."+field).contains(field.asText());
            }
            var names=new java.util.ArrayList<String>();properties.fieldNames().forEachRemaining(names::add);
            var declaredNames=new java.util.ArrayList<String>();declared.forEach(field->declaredNames.add(field.asText()));
            assertThat(names).as(record+"无多余字段").containsExactlyInAnyOrderElementsOf(declaredNames);
        }
        // DemandSpan沿用excerpt回显形态；坐标形态仅供归档证据兼容，不进入请求Schema。
        var span=requirementItems(schema,"Requirement").path("span").path("properties");
        assertThat(span.toString()).contains("subquestionId","excerpt").doesNotContain("startInclusive");
        // 枚举与容量同源。
        assertThat(enumOf(schema,"requirements","dimension").size()).isEqualTo(Dimension.values().length);
        assertThat(enumOf(schema,"requirements","subject").size()).isEqualTo(Subject.values().length);
        assertThat(enumOf(schema,"requirements","recognition").size()).isEqualTo(Recognition.values().length);
        assertThat(enumOf(schema,"bindings","role").size()).isEqualTo(BindingRole.values().length);
        assertThat(enumOf(schema,"unresolvedDemands","reason").size()).isEqualTo(UnresolvedReason.values().length);
        assertThat(schema.path("properties").path("requirements").path("maxItems").asInt()).isEqualTo(MAX_REQUIREMENTS);
        assertThat(schema.path("properties").path("bindings").path("maxItems").asInt()).isEqualTo(MAX_BINDINGS);
        assertThat(schema.path("properties").path("unresolvedDemands").path("maxItems").asInt()).isEqualTo(MAX_REQUIREMENTS);
        assertThat(schema.path("properties").path("protocolRevision").path("enum").get(0).asText()).isEqualTo(REVISION);
    }
    private JsonNode requirementItems(JsonNode schema,String record){
        String group=record.equals("SemanticBinding")?"bindings":record.equals("Requirement")?"requirements":"unresolvedDemands";
        return schema.path("properties").path(group).path("items").path("properties");
    }
    private JsonNode groupRequired(JsonNode schema,String record){
        String group=record.equals("SemanticBinding")?"bindings":record.equals("Requirement")?"requirements":"unresolvedDemands";
        return schema.path("properties").path(group).path("items").path("required");
    }
    private JsonNode enumOf(JsonNode schema,String group,String field){
        return requirementItems(schema,group.equals("requirements")?"Requirement":group.equals("bindings")?"SemanticBinding":"UnresolvedDemand").path(field).path("enum");
    }

    @Test void reviewRequestModeFollowsTheModelRegistry()throws Exception {
        for(String model:List.of(RuleAgentProvider.MODEL,RuleAgentProvider.QWEN4_MAX)) {
            try(var port=provider(model)) {
                var format=json.readTree(port.reviewPayload(input())).path("response_format");
                assertThat(format.path("type").asText()).as(model).isEqualTo("json_schema");
                assertThat(format.path("json_schema").path("name").asText()).isEqualTo("zhku_rule_publication_review");
                assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
            }
        }
        try(var port=provider(RuleAgentProvider.DEEPSEEK_V3)) {
            var format=json.readTree(port.reviewPayload(input())).path("response_format");
            assertThat(format.path("type").asText()).isEqualTo("json_object");
            assertThat(format.has("json_schema")).isFalse();
        }
    }

    @Test void unregisteredModelIsRejectedAtConfigurationNotAtRuntime() {
        assertThatThrownBy(()->new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING,"gpt-4o","batch-auth"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(RuleAgentProvider.REVIEW_OUTPUT_MODES).containsKeys(RuleAgentProvider.MODEL,RuleAgentProvider.QWEN4_MAX,RuleAgentProvider.DEEPSEEK_V3);
    }

    @Test void shapeViolationCarriesRedactedFieldPathWithoutModelValues()throws Exception {
        var valid=json.readTree(wireReview("合成")).toString();
        for(String mutation:List.of("dropBindings","badEnum","extraRootField","missingSpanExcerpt","unknownRequirementField")) {
            var tree=(ObjectNode)json.readTree(valid);
            switch(mutation){
                case "dropBindings"->tree.remove("bindings");
                case "badEnum"->((ObjectNode)tree.path("requirements").get(0)).put("dimension","SCHEDULE_BANANA");
                case "extraRootField"->tree.put("extra","value");
                case "missingSpanExcerpt"->((ObjectNode)tree.path("requirements").get(0).path("span")).remove("excerpt");
                case "unknownRequirementField"->((ObjectNode)tree.path("requirements").get(0)).put("priority","high");
                default->{}
            }
            var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(tree.toString(),input()),RuleAgentProtocol.Rejected.class);
            assertThat(rejected).as(mutation).isNotNull();
            assertThat(rejected.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_SCHEMA_INVALID);
            assertThat(rejected.validation.field()).as(mutation).isNotBlank();
            // 只留字段路径，不回显模型原值或问题文本。
            assertThat(rejected.validation.field()).as(mutation).doesNotContain("SCHEDULE_BANANA","value","high","合成问题");
        }
        // 合法excerpt形态继续通过：供应商严格Schema与本地解码使用同一形状。
        assertThat(RulePublicationProtocol.decode(valid,input()).bindings()).isEmpty();
    }

    @Test void identifiedWithUnknownDimensionKeepsConstraintAttribution()throws Exception {
        var tree=(ObjectNode)json.readTree(wireReview("合成"));
        ((ObjectNode)tree.path("requirements").get(0)).put("dimension","UNKNOWN");
        var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(tree.toString(),input()),RuleAgentProtocol.Rejected.class);
        assertThat(rejected).isNotNull();
        assertThat(rejected.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_SCHEMA_INVALID);
        assertThat(rejected.validation.field()).isEqualTo("requirements[0].dimension");
    }

    String wireReview(String excerpt){
        var span=json.createObjectNode();span.put("subquestionId","q1");span.put("excerpt",excerpt);
        var requirement=json.createObjectNode();
        requirement.put("id","r1");requirement.set("span",span);
        requirement.put("dimension","STUDY_ARRANGEMENT");requirement.put("subject","GENERAL_UNDERGRADUATE");
        requirement.put("recognition","IDENTIFIED");requirement.putArray("necessaryConditionIds");
        var root=json.createObjectNode();
        root.put("protocolRevision",REVISION);root.put("inputFingerprint","a".repeat(64));
        root.putArray("requirements").add(requirement);root.putArray("bindings");root.putArray("unresolvedDemands");
        return root.toString();
    }
}
