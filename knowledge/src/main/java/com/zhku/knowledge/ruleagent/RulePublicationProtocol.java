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
        root.put("responseShape","响应根字段是 protocolRevision、inputFingerprint、requirements、bindings、unresolvedDemands。protocolRevision复制合同的revision值；每层record的所有声明字段必填，空列表填[]，不得增加字段。");
        root.put("spanCoordinates","DemandSpan只填subquestionId与excerpt：excerpt必须逐字复制该问题原text中一段连续原文（不改写、不翻译、不省略号、不从工具原文截取）；宿主取excerpt在原text中的首次出现为需求跨度（UTF-16左闭右开），模型不输出也不数坐标。");
        root.put("conditionalBranches","已识别意图但缺少一个审核条件时，按conditions的每个可行值分别声明IDENTIFIED需求，可共用原始跨度；具体subject/CORE绑定须匹配能力。分支不是用户已声明条件。真正未知保留unresolvedDemands，不截断分支以满足容量。");
        root.put("recognitionDimensionInvariant","IDENTIFIED表示已经确定且在审核能力内：dimension不得为UNKNOWN，subject不得为UNSPECIFIED。遇到当期实时通知、教务系统操作或其他无法支持的需求，不得使用IDENTIFIED+UNKNOWN，也不得把历史规则当实时事实；未知维度使用UNCLASSIFIED和UNKNOWN，并在unresolvedDemands写明UNSUPPORTED_DIMENSION，且不得给该未知需求绑定CORE事实。无法判断范围时使用AMBIGUOUS或unresolvedDemands，不编造审核能力。");
        root.put("bindingEligibility","bindings[].factId 只能取 input.capabilities 中已登记发布能力的事实；capabilities 为空时 bindings 必须为空。可检索、可读取或出现在 input.facts 中，都不等于允许发布；历史条文不得用来证明当期时间或代办能力。");
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

    /**
     * 严格JSON Schema与description()同源：record字段、枚举与容量全部取自宿主合同，不维护第二份限制。
     * 供应商只在请求侧约束输出形状；跨度定位、指纹、事实引用与发布权限仍由本地解码和Gate独立核验。
     */
    public static ObjectNode schema() {
        var root=JSON.createObjectNode();
        root.put("type","object");
        root.put("additionalProperties",false);
        root.put("description","SemanticAssessment：根字段必填，空列表填[]，不得增加字段；span.excerpt逐字复制原问题text中的连续原文片段。");
        var properties=root.putObject("properties");
        var required=root.putArray("required");
        var revision=JSON.createObjectNode().put("type","string");
        revision.putArray("enum").add(REVISION);
        properties.set("protocolRevision",revision);
        properties.putObject("inputFingerprint").put("type","string");
        properties.set("requirements",array(record(REQUIREMENT_FIELDS,Map.of(
            "id",text(),"span",spanSchema(),
            "dimension",enumSchema(Dimension.class),"subject",enumSchema(Subject.class),
            "recognition",enumSchema(Recognition.class),
            "necessaryConditionIds",array(text(),0,16))),0,MAX_REQUIREMENTS));
        properties.set("bindings",array(record(BINDING_FIELDS,Map.of(
            "requirementId",text(),"factId",text(),"role",enumSchema(BindingRole.class))),0,MAX_BINDINGS));
        properties.set("unresolvedDemands",array(record(UNRESOLVED_FIELDS,Map.of(
            "span",spanSchema(),"reason",enumSchema(UnresolvedReason.class))),0,MAX_REQUIREMENTS));
        for(String field:ROOT_FIELDS)required.add(field);
        return root;
    }
    private static final Set<String> ROOT_FIELDS=Set.of("protocolRevision","inputFingerprint","requirements","bindings","unresolvedDemands");
    private static final Set<String> REQUIREMENT_FIELDS=Set.of("id","span","dimension","subject","recognition","necessaryConditionIds");
    private static final Set<String> BINDING_FIELDS=Set.of("requirementId","factId","role");
    private static final Set<String> UNRESOLVED_FIELDS=Set.of("span","reason");
    private static final Set<String> SPAN_EXCERPT_FIELDS=Set.of("subquestionId","excerpt");
    private static final Set<String> SPAN_COORDINATE_FIELDS=Set.of("subquestionId","startInclusive","endExclusive");

    private static ObjectNode spanSchema() {
        var node=record(SPAN_EXCERPT_FIELDS,Map.of("subquestionId",text(),"excerpt",text()));
        node.put("description","excerpt必须逐字复制该问题原text中一段连续原文；宿主取首次出现定位为需求跨度。");
        return node;
    }
    private static ObjectNode text(){return JSON.createObjectNode().put("type","string");}
    private static ObjectNode enumSchema(Class<? extends Enum<?>> type) {
        var node=JSON.createObjectNode();node.put("type","string");var values=node.putArray("enum");
        for(var value:type.getEnumConstants())values.add(value.name());return node;
    }
    private static ObjectNode array(JsonNode items,int min,int max) {
        var node=JSON.createObjectNode();node.put("type","array");node.set("items",items);
        if(min>0)node.put("minItems",min);node.put("maxItems",max);return node;
    }
    private static ObjectNode record(Set<String> fields,Map<String,JsonNode> declared) {
        var node=JSON.createObjectNode();node.put("type","object");node.put("additionalProperties",false);
        var properties=node.putObject("properties");var required=node.putArray("required");
        for(String field:fields){properties.set(field,declared.get(field));required.add(field);}
        return node;
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
        // 先做形状走查并携带脱敏字段路径；Qwen4-Max式“合法JSON但映射失败”不再只能落统一拒绝码。
        String violation=reviewShapeViolation(tree);
        if(violation!=null)
            throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_JSON,RuleAgentProtocol.Code.REVIEW_SCHEMA_INVALID,violation);
        try {
            result=RuleAgentJson.bindReview(tree);
        } catch(IllegalArgumentException invalid) {
            // record构造约束（指纹格式、唯一性、容量、识别不变式等）在此归因；无法定位时保留空字段路径。
            throw new RuleAgentProtocol.Rejected(RuleAgentProtocol.Stage.REVIEW_JSON,RuleAgentProtocol.Code.REVIEW_SCHEMA_INVALID,reviewConstraintViolation(tree));
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

    /** 脱敏形状走查：只记录字段路径，不记录模型原值，兼容excerpt与历史坐标两种span形态。 */
    private static String reviewShapeViolation(JsonNode tree) {
        if(!tree.isObject())return "(root)";
        for(String field:ROOT_FIELDS)if(!tree.has(field))return field;
        var rootNames=new ArrayList<String>();tree.fieldNames().forEachRemaining(rootNames::add);
        for(String field:rootNames)if(!ROOT_FIELDS.contains(field))return field;
        if(!text(tree.path("protocolRevision")))return "protocolRevision";
        if(!text(tree.path("inputFingerprint")))return "inputFingerprint";
        String violation=groupShape(tree.path("requirements"),MAX_REQUIREMENTS,"requirements",REQUIREMENT_FIELDS);
        if(violation!=null)return violation;
        violation=groupShape(tree.path("bindings"),MAX_BINDINGS,"bindings",BINDING_FIELDS);
        if(violation!=null)return violation;
        return groupShape(tree.path("unresolvedDemands"),MAX_REQUIREMENTS,"unresolvedDemands",UNRESOLVED_FIELDS);
    }
    private static String groupShape(JsonNode group,int max,String name,Set<String> fields) {
        if(!group.isArray())return name;
        if(group.size()>max)return name;
        for(int i=0;i<group.size();i++) {
            var item=group.get(i);
            String prefix=name+"["+i+"]";
            if(!item.isObject())return prefix;
            var names=new ArrayList<String>();item.fieldNames().forEachRemaining(names::add);
            for(String field:names)if(!fields.contains(field))return prefix+"."+field;
            for(String field:fields)if(!item.has(field))return prefix+"."+field;
            if(name.equals("requirements")) {
                if(!text(item.path("id")))return prefix+".id";
                if(!enumName(Dimension.class,item.path("dimension")))return prefix+".dimension";
                if(!enumName(Subject.class,item.path("subject")))return prefix+".subject";
                if(!enumName(Recognition.class,item.path("recognition")))return prefix+".recognition";
                var conditions=item.path("necessaryConditionIds");
                if(!conditions.isArray()||conditions.size()>16)return prefix+".necessaryConditionIds";
                for(int c=0;c<conditions.size();c++)if(!text(conditions.get(c)))return prefix+".necessaryConditionIds["+c+"]";
            }
            if(name.equals("bindings")) {
                if(!text(item.path("requirementId")))return prefix+".requirementId";
                if(!text(item.path("factId")))return prefix+".factId";
                if(!enumName(BindingRole.class,item.path("role")))return prefix+".role";
            }
            if(name.equals("unresolvedDemands")&&!enumName(UnresolvedReason.class,item.path("reason")))return prefix+".reason";
            // span只属于requirements与unresolvedDemands；bindings没有该字段，不得误报。
            if(!name.equals("bindings")) {
                String span=spanShape(item.path("span"),prefix);
                if(span!=null)return span;
            }
        }
        return null;
    }
    private static String spanShape(JsonNode span,String prefix) {
        if(!span.isObject())return prefix+".span";
        boolean excerpt=span.has("excerpt"),coordinates=span.has("startInclusive")&&span.has("endExclusive");
        Set<String> allowed=excerpt?SPAN_EXCERPT_FIELDS:SPAN_COORDINATE_FIELDS;
        var names=new ArrayList<String>();span.fieldNames().forEachRemaining(names::add);
        for(String field:names)if(!allowed.contains(field))return prefix+".span."+field;
        if(!text(span.path("subquestionId")))return prefix+".span.subquestionId";
        if(excerpt&&!text(span.path("excerpt")))return prefix+".span.excerpt";
        if(!excerpt&&(!span.path("startInclusive").isInt()||!span.path("endExclusive").isInt()))return prefix+".span.startInclusive";
        return null;
    }

    /** record构造约束的有限归因：覆盖合同里可静态判定的字段，其余保留空路径由拒绝码承载。 */
    private static String reviewConstraintViolation(JsonNode tree) {
        if(!REVISION.equals(tree.path("protocolRevision").asText()))return "protocolRevision";
        if(!tree.path("inputFingerprint").asText().matches("[a-f0-9]{64}"))return "inputFingerprint";
        if(tree.path("requirements").size()>MAX_REQUIREMENTS)return "requirements";
        if(tree.path("bindings").size()>MAX_BINDINGS)return "bindings";
        if(tree.path("unresolvedDemands").size()>MAX_REQUIREMENTS)return "unresolvedDemands";
        if(tree.path("requirements").isEmpty()&&tree.path("unresolvedDemands").isEmpty())return "(root)";
        var ids=new HashSet<String>();
        for(int i=0;i<tree.path("requirements").size();i++) {
            var item=tree.path("requirements").get(i);
            if(!ids.add(item.path("id").asText()))return "requirements["+i+"].id";
            if(item.path("recognition").asText().equals(Recognition.IDENTIFIED.name())
                &&(item.path("dimension").asText().equals(Dimension.UNKNOWN.name())
                    ||item.path("subject").asText().equals(Subject.UNSPECIFIED.name())))return "requirements["+i+"].dimension";
        }
        var bindings=new HashSet<String>();
        for(int i=0;i<tree.path("bindings").size();i++)
            if(!bindings.add(tree.path("bindings").get(i).toString()))return "bindings["+i+"]";
        var demands=new HashSet<String>();
        for(int i=0;i<tree.path("unresolvedDemands").size();i++)
            if(!demands.add(tree.path("unresolvedDemands").get(i).toString()))return "unresolvedDemands["+i+"]";
        return null;
    }
    private static boolean text(JsonNode node){return node.isTextual()&&!node.asText().isEmpty();}
    private static boolean enumName(Class<? extends Enum<?>> type,JsonNode node) {
        if(!node.isTextual())return false;
        for(var value:type.getEnumConstants())if(value.name().equals(node.asText()))return true;
        return false;
    }
}
