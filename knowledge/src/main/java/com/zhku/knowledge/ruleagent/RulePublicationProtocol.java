package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 核验模型只声明需求及对应关系；字段/枚举/容量从宿主合同生成，不维护第二份限制。 */
public final class RulePublicationProtocol {
    private RulePublicationProtocol() {}
    private static final ObjectMapper JSON=new ObjectMapper();
    public static ObjectNode description() {
        var root=JSON.createObjectNode();
        root.put("revision",REVISION);
        root.put("maxRequirements",MAX_REQUIREMENTS);
        root.put("maxBindings",MAX_BINDINGS);
        root.put("maxUnresolvedDemands",MAX_REQUIREMENTS);
        root.put("additionalFieldsAllowed",false);
        root.put("spanCoordinates","原questions.text的UTF-16字符坐标，左闭右开；不得从工具原文截取需求");
        var records=root.putObject("records");
        for(Class<?> type:List.of(SemanticAssessment.class,Requirement.class,DemandSpan.class,SemanticBinding.class,UnresolvedDemand.class)) {
            var fields=records.putArray(type.getSimpleName());
            for(var field:type.getRecordComponents())fields.add(field.getName());
        }
        var enums=root.putObject("enums");
        for(Class<? extends Enum<?>> type:List.of(Dimension.class,Subject.class,BindingRole.class,Recognition.class,UnresolvedReason.class)) {
            var values=enums.putArray(type.getSimpleName());
            for(var value:type.getEnumConstants())values.add(value.name());
        }
        return root;
    }
    public static SemanticAssessment decode(String content,SemanticInput input) {
        SemanticAssessment result;
        try {
            result=RuleAgentJson.bindReview(RuleAgentJson.readTreeStrict(content));
        } catch(IllegalArgumentException invalid) {
            throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_JSON,RuleAgentProtocol.Code.REVIEW_JSON_INVALID);
        }
        var questions=new HashMap<String,String>();
        input.questions().forEach(q->questions.put(q.id(),q.text()));
        var requirements=new HashMap<String,Requirement>();
        for(var requirement:result.requirements()) {
            checkSpan(requirement.span(),questions);
            requirements.put(requirement.id(),requirement);
        }
        for(var unresolved:result.unresolvedDemands())checkSpan(unresolved.span(),questions);
        check(result.inputFingerprint().equals(input.inputFingerprint()));
        for(String q:questions.keySet())check(result.requirements().stream().anyMatch(r->r.span().subquestionId().equals(q))
            ||result.unresolvedDemands().stream().anyMatch(r->r.span().subquestionId().equals(q)));
        for(var binding:result.bindings()) {
            check(requirements.containsKey(binding.requirementId()));
            check(input.facts().stream().anyMatch(f->f.id().equals(binding.factId())));
        }
        // 这里只验证结构和输入身份；事实能力/条件/提议覆盖仍由独立Gate决定，不能自报通过。
        return result;
    }
    private static void checkSpan(DemandSpan span,Map<String,String> questions) {
        check(questions.containsKey(span.subquestionId()));
        check(span.endExclusive()<=questions.get(span.subquestionId()).length());
    }
    private static void check(boolean valid) {
        if(!valid)throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_BINDING,RuleAgentProtocol.Code.REVIEW_BINDING_INVALID);
    }
}
