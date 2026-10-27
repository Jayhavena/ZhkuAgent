package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 核验模型只声明需求及对应关系；字段/枚举/容量从宿主合同生成，不维护第二份限制。 */
public final class RulePublicationProtocol {
    private RulePublicationProtocol() {}
    // 条件键引用审核目录原键；系统提示与合同共用说明，避免模型把规则或分支身份拼进引用。
    static final String CONDITION_ID_FORM="necessaryConditionIds 中的每个值必须逐字复制 input.conditions[].conditionId，不得拼接 ruleId 或任何前缀。"
        +"例如目录给出 courseType 时，只能填写 courseType，S10/RETAKE_ONLY:courseType 属于非法值，会被拒绝。"
        +"同名 conditionId 出现在多条规则下时仍使用该原键；需求分支通过各自的 id、subject 和事实绑定区分，不修改条件键。";
    private static final ObjectMapper JSON=new ObjectMapper();
    public static ObjectNode description() {
        var root=JSON.createObjectNode();
        root.put("revision",REVISION);
        root.put("maxRequirements",MAX_REQUIREMENTS);
        root.put("maxBindings",MAX_BINDINGS);
        root.put("maxUnresolvedDemands",MAX_REQUIREMENTS);
        root.put("additionalFieldsAllowed",false);
        root.put("spanCoordinates","DemandSpan只填subquestionId与excerpt：excerpt必须逐字复制该问题原text中一段连续原文（不改写、不翻译、不省略号、不从工具原文截取）；宿主取excerpt在原text中的首次出现为需求跨度（UTF-16左闭右开），模型不输出也不数坐标。");
        root.put("conditionalBranches","已识别意图但缺少一个审核条件时，按conditions的每个可行值分别声明IDENTIFIED需求，可共用原始跨度；具体subject/CORE绑定须匹配能力。分支不是用户已声明条件。真正未知保留unresolvedDemands，不截断分支以满足容量。");
        root.put("recognitionDimensionInvariant","IDENTIFIED表示已经确定且在审核能力内：dimension不得为UNKNOWN，subject不得为UNSPECIFIED。遇到当期实时通知、教务系统操作或其他无法支持的需求，不得使用IDENTIFIED+UNKNOWN，也不得把历史规则当实时事实；未知维度使用UNCLASSIFIED和UNKNOWN，并在unresolvedDemands写明UNSUPPORTED_DIMENSION，且不得给该未知需求绑定CORE事实。无法判断范围时使用AMBIGUOUS或unresolvedDemands，不编造审核能力。");
        root.put("conditionIdForm",CONDITION_ID_FORM);
        var records=root.putObject("records");
        for(Class<?> type:List.of(SemanticAssessment.class,Requirement.class,DemandSpan.class,SemanticBinding.class,UnresolvedDemand.class)) {
            var fields=records.putArray(type.getSimpleName());
            // 新响应用excerpt回显定位；坐标形态仅为兼容冻结的T05历史证据，不再向模型声明。
            if(type==DemandSpan.class){fields.add("subquestionId");fields.add("excerpt");continue;}
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
        var questions=new HashMap<String,String>();
        input.questions().forEach(q->questions.put(q.id(),q.text()));
        JsonNode tree;
        try {
            tree=RuleAgentJson.readTreeStrict(content);
        } catch(IllegalArgumentException invalid) {
            throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_JSON,RuleAgentProtocol.Code.REVIEW_JSON_INVALID);
        }
        // 模型不再数UTF-16坐标：回显原文excerpt，由宿主在原问题文本中定位，消除计数漂移导致的越界拒绝。
        // 解析在bind的IllegalArgumentException捕获之外，保留独立拒绝分支码。
        for(var group:List.of(tree.path("requirements"),tree.path("unresolvedDemands")))
            if(group.isArray())for(var item:group)if(item.isObject())resolveExcerptSpan((com.fasterxml.jackson.databind.node.ObjectNode)item,questions);
        try {
            result=RuleAgentJson.bindReview(tree);
        } catch(IllegalArgumentException invalid) {
            throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_JSON,RuleAgentProtocol.Code.REVIEW_JSON_INVALID);
        }
        var requirements=new HashMap<String,Requirement>();
        for(var requirement:result.requirements()) {
            checkSpan(requirement.span(),questions);
            requirements.put(requirement.id(),requirement);
        }
        for(var unresolved:result.unresolvedDemands())checkSpan(unresolved.span(),questions);
        check(result.inputFingerprint().equals(input.inputFingerprint()),RuleAgentProtocol.Code.REVIEW_INPUT_MISMATCH);
        for(String q:questions.keySet())check(result.requirements().stream().anyMatch(r->r.span().subquestionId().equals(q))
            ||result.unresolvedDemands().stream().anyMatch(r->r.span().subquestionId().equals(q)),RuleAgentProtocol.Code.REVIEW_QUESTION_UNCOVERED);
        for(var binding:result.bindings()) {
            check(requirements.containsKey(binding.requirementId()),RuleAgentProtocol.Code.REVIEW_REQUIREMENT_UNKNOWN);
            check(input.facts().stream().anyMatch(f->f.id().equals(binding.factId())),RuleAgentProtocol.Code.REVIEW_FACT_NOT_IN_INPUT);
        }
        // 原键属于当前审核输入；形状合法的带前缀ID仍不能被静默纠正。
        var conditionIds=input.conditions().stream().map(ReviewedCondition::conditionId).collect(java.util.stream.Collectors.toSet());
        for(var requirement:result.requirements())for(String id:requirement.necessaryConditionIds())
            check(conditionIds.contains(id),RuleAgentProtocol.Code.REVIEW_CONDITION_UNKNOWN);
        // 这里只验证结构和输入身份；事实能力/条件/提议覆盖仍由独立Gate决定，不能自报通过。
        return result;
    }
    private static void checkSpan(DemandSpan span,Map<String,String> questions) {
        check(questions.containsKey(span.subquestionId()),RuleAgentProtocol.Code.REVIEW_QUESTION_UNKNOWN);
        check(span.endExclusive()<=questions.get(span.subquestionId()).length(),RuleAgentProtocol.Code.REVIEW_SPAN_OUT_OF_RANGE);
    }
    /** excerpt形态：宿主以首次出现定位；改写、越界或未命中原文都视为无法绑定需求跨度。 */
    private static void resolveExcerptSpan(com.fasterxml.jackson.databind.node.ObjectNode item,Map<String,String> questions) {
        var span=item.path("span");
        if(!span.isObject()||!span.path("excerpt").isTextual()||span.path("excerpt").asText().isEmpty())return;
        String subquestionId=span.path("subquestionId").isTextual()?span.path("subquestionId").asText():null;
        if(subquestionId==null)return;
        if(!questions.containsKey(subquestionId)) {
            // 未知问题保持既有诊断分支：改写为可绑定的占位坐标，由checkSpan给出REVIEW_QUESTION_UNKNOWN。
            var placeholder=JSON.createObjectNode();placeholder.put("subquestionId",subquestionId);placeholder.put("startInclusive",0);placeholder.put("endExclusive",1);
            item.set("span",placeholder);return;
        }
        int start=questions.get(subquestionId).indexOf(span.path("excerpt").asText());
        if(start<0)throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_BINDING,RuleAgentProtocol.Code.REVIEW_SPAN_OUT_OF_RANGE);
        var resolved=JSON.createObjectNode();
        resolved.put("subquestionId",subquestionId);
        resolved.put("startInclusive",start);
        resolved.put("endExclusive",start+span.path("excerpt").asText().length());
        item.set("span",resolved);
    }
    // 同一拒绝边界保留独立分支码；不能靠响应SHA猜测是哪项关联失败。
    private static void check(boolean valid,RuleAgentProtocol.Code code) {
        if(!valid)throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_BINDING,code);
    }
}
