package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 模型约束投影与解码使用同一动作类型/record字段及领域预算，不另建宽松协议。 */
public final class RuleAgentProtocol {
    public static final String REVISION="RULE_AGENT_ACTION_CONTRACT_03";
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
        NONE, RESPONSE_JSON_INVALID, RESPONSE_STRUCTURE_INVALID, FINISH_REASON_INVALID, CONTENT_INVALID,
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
