package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 独立严格解码，不启用默认多态，也不改变旧回放的ObjectMapper。 */
public final class RuleAgentJson {
    private RuleAgentJson() {}
    private static final ObjectMapper JSON=JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
    public static TaskSnapshot readSnapshot(String input){return read(input,TaskSnapshot.class);}
    public static NextAction readAction(String input) {
        return RuleAgentProtocol.decode(input);
    }
    static JsonNode readTreeStrict(String input) {
        if(input==null||input.getBytes(StandardCharsets.UTF_8).length>DEFAULT_BUDGET.maxRequestBytes()) {
            throw new IllegalArgumentException("合同输入超出预算");
        }
        try {
            JsonNode value=JSON.readTree(input);
            if(value==null||value.isNull()) {
                throw new IllegalArgumentException("空合同");
            }
            return value;
        } catch(Exception invalid) {
            throw new IllegalArgumentException("非法规则查证JSON");
        }
    }
    static NextAction bindAction(JsonNode node) {
        return read(node.toString(),NextAction.class);
    }
    static RulePublicationContract.SemanticAssessment bindReview(JsonNode node) {
        return read(node.toString(),RulePublicationContract.SemanticAssessment.class);
    }
    public static CreateTask readCreate(String input){return read(input,CreateTask.class);}
    public static Message readMessage(String input){return read(input,Message.class);}
    public static Cancel readCancel(String input){return read(input,Cancel.class);}
    public static ToolObservation readObservation(String input){return read(input,ToolObservation.class);}
    private static <T> T read(String input,Class<T> type){
        if(input==null||input.getBytes(StandardCharsets.UTF_8).length>DEFAULT_BUDGET.maxRequestBytes())throw new IllegalArgumentException("合同输入超出预算");
        try{
            T value=JSON.readValue(input,type);
            // JSON null不会触发record构造校验，必须在统一入口拒绝，避免下游误归因。
            if(value==null)throw new IllegalArgumentException("空合同");
            return value;
        }catch(Exception invalid){throw new IllegalArgumentException("非法规则查证JSON");}
    }
}
