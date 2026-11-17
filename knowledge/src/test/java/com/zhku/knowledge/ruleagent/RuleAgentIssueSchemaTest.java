package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

class RuleAgentIssueSchemaTest {
    final ObjectMapper json=new ObjectMapper();
    JsonNode issueSchema(){
        for(var action:RuleAgentProtocol.decisionSchema(List.of()).path("oneOf"))
            if(action.path("properties").path("type").path("const").asText().equals("FINISH_CHECK"))
                return action.path("properties").path("items").path("items").path("properties").path("issues").path("items");
        throw new AssertionError("缺少结束动作");
    }
    // 只解释此Schema使用的对象、必填、闭合、枚举、const和oneOf；不宣称供应商遵守Schema。
    boolean accepts(JsonNode schema,JsonNode value){
        if(schema.has("const")&&!schema.get("const").equals(value))return false;
        if(schema.has("enum")){boolean found=false;for(var item:schema.get("enum"))found|=item.equals(value);if(!found)return false;}
        if(schema.has("oneOf")){int count=0;for(var branch:schema.get("oneOf"))if(accepts(branch,value))count++;if(count!=1)return false;}
        if(schema.path("type").asText().equals("object")){
            if(!value.isObject())return false;
            for(var key:schema.path("required"))if(!value.has(key.asText()))return false;
            var fields=value.fieldNames();while(fields.hasNext()){
                String field=fields.next();var property=schema.path("properties").get(field);
                if(property==null){if(schema.path("additionalProperties").isBoolean()&&!schema.path("additionalProperties").asBoolean())return false;}
                else if(!accepts(property,value.get(field)))return false;
            }
        }
        return true;
    }
    @Test void liveConflictCancelledPairIsRejectedBySchemaAndDomain(){
        var invalid=json.valueToTree(Map.of("kind","CONFLICT","reasonCode","CANCELLED"));
        assertThat(accepts(issueSchema(),invalid)).isFalse();
        assertThatThrownBy(()->new ProposedIssue(IssueKind.CONFLICT,ReasonCode.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bothIssueFieldsAreRequired(){
        assertThat(accepts(issueSchema(),json.valueToTree(Map.of("kind","CONFLICT")))).isFalse();
        assertThat(accepts(issueSchema(),json.valueToTree(Map.of("reasonCode","CANCELLED")))).isFalse();
        assertThat(accepts(issueSchema(),json.createObjectNode())).isFalse();
    }
    @Test void everyDomainPairHasExactlyTheSameSchemaAcceptance(){
        for(var kind:IssueKind.values())for(var reason:ReasonCode.values()){
            boolean expected=reason.kind==kind;
            assertThat(accepts(issueSchema(),json.valueToTree(Map.of("kind",kind.name(),"reasonCode",reason.name())))).as("%s/%s",kind,reason).isEqualTo(expected);
        }
        assertThat(accepts(issueSchema(),json.valueToTree(Map.of("kind","PROCESSING_FAILURE","reasonCode","CANCELLED","extra",true)))).isFalse();
    }
}
