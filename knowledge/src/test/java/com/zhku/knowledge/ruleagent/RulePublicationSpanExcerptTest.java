package com.zhku.knowledge.ruleagent;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 2026-10-25真实在线验收定位的修复：核验模型不数UTF-16坐标，回显excerpt由宿主定位需求跨度。 */
@Timeout(15)
class RulePublicationSpanExcerptTest {
    final ObjectMapper json=new ObjectMapper();
    SemanticInput input(){return new SemanticInput(REVISION,RuleFactCapabilityCatalogue.VERSION,"a".repeat(64),
        List.of(new RuleAgentRunner.Question("q1","我这门选修课没过，能不能重修？")),Map.of(),
        List.of(new SemanticFact("f1","synthetic","合成规则",Map.of(),List.of())),Map.of());}
    ObjectNode valid(String spanJson)throws Exception{
        var out=json.createObjectNode();
        out.put("protocolRevision",REVISION);out.put("inputFingerprint","a".repeat(64));
        var item=out.putArray("requirements").addObject();
        item.put("id","r1");item.set("span",json.readTree(spanJson));
        item.put("dimension",Dimension.RETAKE.name());item.put("subject",Subject.PUBLIC_ELECTIVE.name());
        item.put("recognition",Recognition.IDENTIFIED.name());item.putArray("necessaryConditionIds");
        out.putArray("bindings").addObject().put("requirementId","r1").put("factId","f1").put("role",BindingRole.CORE.name());
        out.putArray("unresolvedDemands");
        return out;
    }
    @Test void excerptIsLocatedInQuestionTextByHost()throws Exception{
        var result=RulePublicationProtocol.decode(valid("{\"subquestionId\":\"q1\",\"excerpt\":\"能不能重修\"}").toString(),input());
        var span=result.requirements().getFirst().span();
        assertThat(span.startInclusive()).isEqualTo(9);assertThat(span.endExclusive()).isEqualTo(14);
    }
    @Test void paraphrasedExcerptCannotBindAndKeepsRangeCode()throws Exception{
        var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(valid("{\"subquestionId\":\"q1\",\"excerpt\":\"是否可以重修课程\"}").toString(),input()),RuleAgentProtocol.Rejected.class);
        assertThat(rejected).isNotNull();
        assertThat(rejected.validation.stage()).isEqualTo(RuleAgentProtocol.Stage.REVIEW_BINDING);
        assertThat(rejected.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_SPAN_OUT_OF_RANGE);
    }
    @Test void toolTextExcerptCannotPoseAsDemandSpan()throws Exception{
        // 工具原文片段不在问题文本中：与改写同样拒绝，不得冒充需求跨度。
        var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(valid("{\"subquestionId\":\"q1\",\"excerpt\":\"专业选修课成绩不合格者可申请重修学习\"}").toString(),input()),RuleAgentProtocol.Rejected.class);
        assertThat(rejected).isNotNull();
        assertThat(rejected.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_SPAN_OUT_OF_RANGE);
    }
    @Test void unknownSubquestionWithExcerptStillReportsQuestionUnknown()throws Exception{
        var rejected=catchThrowableOfType(()->RulePublicationProtocol.decode(valid("{\"subquestionId\":\"absent\",\"excerpt\":\"能不能重修\"}").toString(),input()),RuleAgentProtocol.Rejected.class);
        assertThat(rejected).isNotNull();
        assertThat(rejected.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_QUESTION_UNKNOWN);
    }
    @Test void legacyCoordinateSpansFromFrozenEvidenceStillDecode()throws Exception{
        var result=RulePublicationProtocol.decode(valid("{\"subquestionId\":\"q1\",\"startInclusive\":0,\"endExclusive\":5}").toString(),input());
        assertThat(result.requirements().getFirst().span().endExclusive()).isEqualTo(5);
    }
    @Test void excerptTakesPrecedenceOverStaleCoordinates()throws Exception{
        var result=RulePublicationProtocol.decode(valid("{\"subquestionId\":\"q1\",\"startInclusive\":0,\"endExclusive\":99,\"excerpt\":\"能不能重修\"}").toString(),input());
        assertThat(result.requirements().getFirst().span().startInclusive()).isEqualTo(9);
    }
    @Test void contractOnlyDeclaresExcerptFieldsToTheModel(){
        var records=RulePublicationProtocol.description().path("records");
        assertThat(records.path("DemandSpan").toString()).isEqualTo("[\"subquestionId\",\"excerpt\"]");
        assertThat(records.path("Requirement").toString()).doesNotContain("excerpt");
    }
}
