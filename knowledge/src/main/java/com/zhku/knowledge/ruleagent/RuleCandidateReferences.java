package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentProtocol.*;

/** 每个在线task/run端口独占此表；编号只是模型选择界面，不授予底层读取权限。 */
final class RuleCandidateReferences {
    private UUID observationId;
    private String setId;
    private List<Candidate> candidates=List.of();
    private final Map<String,UUID> handles=new LinkedHashMap<>();

    synchronized void refresh(RuleAgentRunner.View view) {
        ToolObservation latest=null;
        for(var event:view.events()) {
            if(event.action().equals("SEARCH_REVIEWED_RULES"))latest=event.observation();
        }
        if(latest==null) {
            for(var observation:view.observations()) {
                if(observation.dataLevel()==DataLevel.CANDIDATE)latest=observation;
            }
        }
        if(latest==null) {
            clear();
            return;
        }
        if(latest.actionId().equals(observationId))return;
        clear();
        observationId=latest.actionId();
        if(latest.status()!=ObservationStatus.SUCCEEDED||latest.dataLevel()!=DataLevel.CANDIDATE)return;
        byte[] nonce=new byte[12];
        new SecureRandom().nextBytes(nonce);
        setId=Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        candidates=List.copyOf(latest.candidates());
        for(int i=0;i<candidates.size();i++)handles.put("c"+(i+1),candidates.get(i).handle());
    }

    synchronized void project(ObjectNode data) {
        if(setId==null)data.putNull("candidateSetId");
        else data.put("candidateSetId",setId);
        var rows=data.putArray("currentCandidates");
        for(int i=0;i<candidates.size();i++) {
            var candidate=candidates.get(i);
            rows.addObject().put("handle","c"+(i+1)).put("excerpt",candidate.excerpt()).put("revision",candidate.revision());
        }
    }

    synchronized void annotateReadState(ObjectNode data,Map<UUID,Boolean> read){
        var rows=data.path("currentCandidates");for(int i=0;i<candidates.size();i++)((ObjectNode)rows.get(i)).put("alreadyRead",read.getOrDefault(candidates.get(i).handle(),false));
    }
    /** 机器可读动作定义里的候选枚举；只暴露编号界面，UUID仍留在宿主。 */
    synchronized List<String> references() {
        return List.copyOf(handles.keySet());
    }

    synchronized NextAction decode(String content) {
        JsonNode parsed;
        try {
            parsed=RuleAgentJson.readTreeStrict(content);
        }catch(IllegalArgumentException invalid) {
            throw new Rejected(Stage.ACTION_JSON,Code.ACTION_JSON_INVALID);
        }
        if(!parsed.path("type").asText().equals("READ_VERIFIED_RULE"))return RuleAgentProtocol.decode(content);
        var fields=new HashSet<String>();
        parsed.fieldNames().forEachRemaining(fields::add);
        if(!parsed.isObject()||!fields.equals(modelReadFields())) {
            throw new Rejected(Stage.ACTION_FIELDS,Code.ACTION_FIELDS_INVALID);
        }
        var selected=parsed.path("handles");
        // 模型编号与内部UUID动作共享数量校验；投影上下限仍来自同一领域预算。
        checkReadCount(selected);
        // 集合代号先绑定宿主当前表；同名c1不能跨任务或沿用旧检索集合。
        if(setId==null||!parsed.path("candidateSetId").isTextual()||!setId.equals(parsed.path("candidateSetId").textValue())) {
            throw new Rejected(Code.READ_REFERENCE_SET,0,selected.get(0),false);
        }
        var unique=new HashSet<String>();
        var normalized=((ObjectNode)parsed).deepCopy();
        normalized.remove("candidateSetId");
        var resolved=normalized.putArray("handles");
        for(int i=0;i<selected.size();i++) {
            var value=selected.get(i);
            if(!value.isTextual()||!value.textValue().matches(READ_REFERENCE_PATTERN)) {
                throw new Rejected(Code.READ_REFERENCE_FORMAT,i,value,false);
            }
            String reference=value.textValue();
            boolean current=handles.containsKey(reference);
            if(!current)throw new Rejected(Code.READ_HANDLE_NOT_CURRENT,i,value,false);
            if(!unique.add(reference))throw new Rejected(Code.READ_HANDLE_DUPLICATE,i,value,true);
            resolved.add(handles.get(reference).toString());
        }
        // 仅改变模型线协议；内部UUID动作继续经过原严格解码、broker权限及证据核验。
        return RuleAgentProtocol.decode(normalized.toString());
    }

    synchronized void clear() {
        observationId=null;
        setId=null;
        candidates=List.of();
        handles.clear();
    }
}
