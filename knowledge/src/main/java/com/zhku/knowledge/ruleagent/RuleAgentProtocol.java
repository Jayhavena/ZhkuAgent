package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 模型约束投影与解码使用同一动作类型/record字段及领域预算，不另建宽松协议。 */
public final class RuleAgentProtocol {
    public static final String REVISION="RULE_AGENT_ACTION_CONTRACT_04";
    public static final String READ_REFERENCE_PATTERN="c[1-9][0-9]*";
    static Set<String> modelReadFields() {
        var fields=new HashSet<String>();
        fields.add("type");
        fields.add("candidateSetId");
        for(var field:Read.class.getRecordComponents())fields.add(field.getName());
        return Set.copyOf(fields);
    }
    private RuleAgentProtocol() {}
    public enum Stage { NONE, SUPPLIER_RESPONSE, ACTION_JSON, ACTION_FIELDS, ACTION_PARAMETERS, REVIEW_JSON, REVIEW_BINDING, PRE_EXECUTION, TRANSPORT, AUDIT }
    public enum Code {
        NONE, RESPONSE_JSON_INVALID, RESPONSE_STRUCTURE_INVALID, FINISH_REASON_INVALID, CONTENT_INVALID, ACTION_CALLS_INVALID,
        ACTION_JSON_INVALID, ACTION_TYPE_UNKNOWN, ACTION_FIELDS_INVALID, ACTION_PARAMETER_INVALID,
        READ_HANDLE_COUNT, READ_HANDLE_DUPLICATE, READ_HANDLE_UUID, READ_HANDLE_NOT_CURRENT,
        READ_HANDLE_ARRAY_REQUIRED, READ_HANDLE_COUNT_BELOW_MIN, READ_HANDLE_COUNT_ABOVE_MAX,
        READ_REFERENCE_FORMAT, READ_REFERENCE_SET,
        SUBQUESTION_NOT_CURRENT, OPERATION_NOT_ALLOWED, REVIEW_JSON_INVALID, REVIEW_BINDING_INVALID, BUDGET_UNAVAILABLE, TRANSPORT_FAILURE, AUDIT_FAILURE
    }
    public record ReadSelection(Integer elementIndex,String jsonType,Integer stringLength,Code failureCategory,Boolean currentCandidate) {}
    public record Validation(Stage stage, Code code,ReadSelection readSelection) {
        public Validation(Stage stage,Code code) {this(stage,code,null);}
    }
    public static final Validation VALID=new Validation(Stage.NONE,Code.NONE);
    public static final class Rejected extends IllegalArgumentException {
        final Validation validation;
        Rejected(Stage stage,Code code) {
            super(code.name());
            validation=new Validation(stage,code);
        }
        Rejected(Code code,Integer index,JsonNode value,Boolean current) {
            this(code,index,value,current,code);
        }
        Rejected(Code code,Integer index,JsonNode value,Boolean current,Code category) {
            super(code.name());
            validation=new Validation(Stage.ACTION_PARAMETERS,code,new ReadSelection(index,
                value==null?null:value.getNodeType().name(),value!=null&&value.isTextual()?value.textValue().length():null,category,current));
        }
    }
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final boolean ALLOW_OPERATIONS=false;
    private static Map<String,Class<?>> actions() {
        var result=new LinkedHashMap<String,Class<?>>();
        for(var type:NextAction.class.getAnnotation(JsonSubTypes.class).value()) {
            result.put(type.name(),type.value());
        }
        return result;
    }
    public static ObjectNode description() {
        var root=JSON.createObjectNode();
        root.put("revision",REVISION);
        root.set("publicationReviewPolicy",JSON.valueToTree(RulePublicationContract.policy()));
        var rows=root.putArray("actions");
        actions().forEach((name,type)->{
            var row=rows.addObject();
            row.put("type",name);
            row.put("additionalFieldsAllowed",false);
            var fields=row.putArray("requiredFields");
            fields.add("type");
            for(var field:type.getRecordComponents()) {
                fields.add(field.getName());
            }
            if(type==Read.class) {
                fields.add("candidateSetId");
                row.putObject("readHandles")
                    .put("min",MIN_READ_HANDLES)
                    .put("max",DEFAULT_BUDGET.maxReadHandles())
                    .put("unique",true)
                    .put("format",READ_REFERENCE_PATTERN)
                    .put("source","currentCandidates.handle；必须同时回传当前candidateSetId。仅宿主映射实际UUID，禁止提交UUID或自造编号");
            }
            if(type==Finish.class) {
                row.put("operationMustBeNull",!ALLOW_OPERATIONS);
                row.set("itemRequiredFields",fields(Proposal.class));
                row.set("issueRequiredFields",fields(ProposedIssue.class));
            }
        });
        return root;
    }
    /** 动作名称的模型可见说明；字段与限制仍取自record与领域预算，不在这里另立一套。 */
    private static String actionDescription(String name) {
        return switch(name) {
            case "SEARCH_REVIEWED_RULES" -> "按自然语言query检索已审核规则；检索成功后宿主签发新的候选集合，重新检索会使旧c编号失效。";
            case "READ_VERIFIED_RULE" -> "读取当前候选集合中的1—3个唯一c编号，并原样回传当次candidateSetId；只能使用currentCandidates给出的编号，实际UUID由宿主映射，禁止提交UUID或自造编号。";
            case "ASK_NECESSARY_CONDITION" -> "就影响结论且学生能补充的必要条件追问；使用审核conditionId、reason与supportingFactIds，不重复已知条件。";
            default -> "提交最终结论提议；items须覆盖所有子问题，每项给出subquestionId、factIds、issues与operation，operation本批必须为null。";
        };
    }
    /**
     * 由现有权威合同派生机器可读动作定义：动作名称取自同一组JsonSubTypes，
     * 字段与必填取自record组件，编号下限/上限与枚举取自领域预算与领域枚举。
     * 供应商是否强制这些约束不由本方法决定，宿主校验不因引入本方法而放宽。
     */
    public static ArrayNode tools(List<String> readReferences) {
        var rows=JSON.createArrayNode();
        actions().forEach((name,type)->{
            var function=rows.addObject().put("type","function").putObject("function");
            function.put("name",name);
            function.put("description",actionDescription(name));
            function.set("parameters",parameters(type,readReferences));
        });
        return rows;
    }
    private static ObjectNode parameters(Class<?> action,List<String> readReferences) {
        var schema=JSON.createObjectNode();
        schema.put("type","object");
        // 宿主按record组件做字段闭合校验；这里必须同样禁止额外字段，避免鼓励模型输出无效键。
        schema.put("additionalProperties",false);
        var properties=schema.putObject("properties");
        var required=schema.putArray("required");
        for(var field:action.getRecordComponents()) {
            required.add(field.getName());
            properties.set(field.getName(),field(action,field.getName(),readReferences));
        }
        if(action==Read.class) {
            required.add("candidateSetId");
            properties.set("candidateSetId",text("本次检索的集合代号，必须原样回传",null));
        }
        return schema;
    }
    private static JsonNode field(Class<?> owner,String name,List<String> readReferences) {
        if(owner==Read.class&&name.equals("handles")) {
            var items=text("当前候选编号",READ_REFERENCE_PATTERN);
            if(!readReferences.isEmpty()) {
                var values=items.putArray("enum");
                readReferences.forEach(values::add);
            }
            // 1—3来自同一领域预算，不在此处重新定义上限。
            return array(items,MIN_READ_HANDLES,DEFAULT_BUDGET.maxReadHandles());
        }
        if(owner==Finish.class&&name.equals("items"))return array(object(Proposal.class,readReferences),1,MAX_FINISH_PROPOSALS);
        if(owner==Proposal.class&&name.equals("issues"))return array(object(ProposedIssue.class,readReferences),0,MAX_PROPOSED_ISSUES);
        if(owner==Proposal.class&&name.equals("factIds"))return array(text("已核验事实id",IDENTIFIER_PATTERN),0,MAX_FACT_IDS);
        if(owner==Proposal.class&&name.equals("operation"))return JSON.createObjectNode().put("type","null");
        if(owner==Ask.class&&name.equals("supportingFactIds"))return array(text("已核验事实id",IDENTIFIER_PATTERN),0,MAX_SUPPORTING_FACT_IDS);
        if(owner==ProposedIssue.class&&name.equals("kind"))return enumField(IssueKind.values());
        if(owner==ProposedIssue.class&&name.equals("reasonCode"))return enumField(ReasonCode.values());
        if(name.equals("subquestionId"))return text("子问题id，须对应questions.id",IDENTIFIER_PATTERN);
        if(name.equals("conditionId"))return text("审核条件id",IDENTIFIER_PATTERN);
        if(name.equals("query"))return text("自然语言检索词",null);
        if(name.equals("reason"))return text("追问理由",null);
        return text(null,null);
    }
    private static ObjectNode object(Class<?> type,List<String> readReferences) {
        var node=JSON.createObjectNode();
        node.put("type","object");
        node.put("additionalProperties",false);
        var properties=node.putObject("properties");
        var required=node.putArray("required");
        for(var field:type.getRecordComponents()) {
            required.add(field.getName());
            properties.set(field.getName(),field(type,field.getName(),readReferences));
        }
        return node;
    }
    private static ObjectNode array(JsonNode items,int min,int max) {
        var node=JSON.createObjectNode();
        node.put("type","array");
        node.set("items",items);
        if(min>0)node.put("minItems",min);
        node.put("maxItems",max);
        return node;
    }
    private static ObjectNode enumField(Enum<?>[] values) {
        var node=JSON.createObjectNode();
        node.put("type","string");
        var names=node.putArray("enum");
        for(var value:values)names.add(value.name());
        return node;
    }
    private static ObjectNode text(String note,String pattern) {
        var node=JSON.createObjectNode();
        node.put("type","string");
        if(pattern!=null)node.put("pattern",pattern);
        if(note!=null)node.put("description",note);
        return node;
    }
    /**
     * 供应商动作名称+参数 → 原领域动作JSON。
     * 参数里再出现type说明模型在自造第二套协议，必须拒绝而不是覆盖。
     */
    public static String actionJson(String name,JsonNode arguments) {
        if(name==null||!actions().containsKey(name)) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_TYPE_UNKNOWN);
        }
        if(arguments==null||!arguments.isObject()||arguments.has("type")) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_FIELDS_INVALID);
        }
        var node=JSON.createObjectNode();
        node.put("type",name);
        arguments.fields().forEachRemaining(entry->node.set(entry.getKey(),entry.getValue()));
        return node.toString();
    }
    public static ObjectNode remainingBudget(int modelAttempts,int toolSteps) {
        return JSON.createObjectNode()
            .put("modelAttemptsIncludingThisCall",modelAttempts)
            .put("toolSteps",toolSteps);
    }
    private static JsonNode fields(Class<?> type) {
        var values=JSON.createArrayNode();
        for(var c:type.getRecordComponents()) {
            values.add(c.getName());
        }
        return values;
    }
    public static NextAction decode(String input) {
        JsonNode node;
        try {
            node=RuleAgentJson.readTreeStrict(input);
        } catch(IllegalArgumentException invalid) {
            throw new Rejected(Stage.ACTION_JSON,Code.ACTION_JSON_INVALID);
        }
        if(!node.isObject()||!node.path("type").isTextual()) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_FIELDS_INVALID);
        }
        Class<?> type=actions().get(node.path("type").textValue());
        if(type==null) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_TYPE_UNKNOWN);
        }
        checkFields(node,type,true);
        if(type==Read.class) {
            checkRead(node.path("handles"));
        }
        if(type==Finish.class&&node.path("items").isArray()) {
            for(var item:node.path("items")) {
                checkFields(item,Proposal.class,false);
                if(item.path("issues").isArray()) {
                    for(var issue:item.path("issues")) {
                        checkFields(issue,ProposedIssue.class,false);
                    }
                }
            }
        }
        try {
            // record构造器仍是值约束的最终守卫；不记录Jackson异常消息或自由输入。
            return RuleAgentJson.bindAction(node);
        } catch(IllegalArgumentException invalid) {
            throw new Rejected(Stage.ACTION_PARAMETERS,Code.ACTION_PARAMETER_INVALID);
        }
    }
    private static void checkFields(JsonNode node,Class<?> type,boolean action) {
        var expected=new HashSet<String>();
        if(action) {
            expected.add("type");
        }
        for(var c:type.getRecordComponents()) {
            expected.add(c.getName());
        }
        var actual=new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if(!node.isObject()||!actual.equals(expected)) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_FIELDS_INVALID);
        }
        for(var c:type.getRecordComponents()) {
            var value=node.path(c.getName());
            // Jackson的String绑定可能接受数字；先按record声明检查，禁止动作参数隐式转换。
            if(c.getType()==String.class&&!value.isTextual()) {
                throw new Rejected(Stage.ACTION_PARAMETERS,Code.ACTION_PARAMETER_INVALID);
            }
            if(c.getType()==List.class&&!value.isArray()) {
                throw new Rejected(Stage.ACTION_PARAMETERS,Code.ACTION_PARAMETER_INVALID);
            }
            if(c.getGenericType() instanceof java.lang.reflect.ParameterizedType generic&&generic.getActualTypeArguments()[0]==String.class) {
                for(var item:value) {
                    if(!item.isTextual()) {
                        throw new Rejected(Stage.ACTION_PARAMETERS,Code.ACTION_PARAMETER_INVALID);
                    }
                }
            }
        }
    }
    private static void checkRead(JsonNode handles) {
        checkReadCount(handles);
        var unique=new HashSet<UUID>();
        for(var handle:handles) {
            UUID uuid;
            try {
                uuid=UUID.fromString(handle.textValue());
                if(!uuid.toString().equalsIgnoreCase(handle.textValue())) {
                    throw new IllegalArgumentException();
                }
            } catch(Exception invalid) {
                throw new Rejected(Stage.ACTION_PARAMETERS,Code.READ_HANDLE_UUID);
            }
            if(!unique.add(uuid)) {
                throw new Rejected(Stage.ACTION_PARAMETERS,Code.READ_HANDLE_DUPLICATE);
            }
        }
    }
    static void checkReadCount(JsonNode handles) {
        Code category=null;
        if(!handles.isArray())category=Code.READ_HANDLE_ARRAY_REQUIRED;
        else if(handles.size()<MIN_READ_HANDLES)category=Code.READ_HANDLE_COUNT_BELOW_MIN;
        else if(handles.size()>DEFAULT_BUDGET.maxReadHandles())category=Code.READ_HANDLE_COUNT_ABOVE_MAX;
        if(category!=null) {
            // 顶层旧原因码保持兼容；只细分受控类别，不记录数组数量、编号、UUID或正文。
            throw new Rejected(Code.READ_HANDLE_COUNT,null,handles,null,category);
        }
    }
    public static void preflight(NextAction action,RuleAgentRunner.View view) {
        if(view.attemptsRemaining()<1||view.stepsRemaining()<1) {
            throw new Rejected(Stage.PRE_EXECUTION,Code.BUDGET_UNAVAILABLE);
        }
        String question=action instanceof Search s?s.subquestionId():action instanceof Read r?r.subquestionId():action instanceof Ask a?a.subquestionId():null;
        if(question!=null&&view.questions().stream().noneMatch(q->q.id().equals(question))) {
            throw new Rejected(Stage.PRE_EXECUTION,Code.SUBQUESTION_NOT_CURRENT);
        }
        if(action instanceof Read read) {
            var current=new HashSet<UUID>();
            view.observations().stream()
                .filter(o->o.status()==ObservationStatus.SUCCEEDED&&o.dataLevel()==DataLevel.CANDIDATE)
                .flatMap(o->o.candidates().stream())
                .forEach(c->current.add(c.handle()));
            if(!current.containsAll(read.handles())) {
                throw new Rejected(Stage.PRE_EXECUTION,Code.READ_HANDLE_NOT_CURRENT);
            }
        }
        if(action instanceof Finish finish&&finish.items().stream().anyMatch(p->view.questions().stream().noneMatch(q->q.id().equals(p.subquestionId())))) {
            throw new Rejected(Stage.PRE_EXECUTION,Code.SUBQUESTION_NOT_CURRENT);
        }
        if(!ALLOW_OPERATIONS&&action instanceof Finish finish&&finish.items().stream().anyMatch(p->p.operation()!=null)) {
            throw new Rejected(Stage.PRE_EXECUTION,Code.OPERATION_NOT_ALLOWED);
        }
        // 这层只校验模型可见动作权限；owner、修订、审核资格和证据支持仍由broker/发布守卫验证。
    }
}
