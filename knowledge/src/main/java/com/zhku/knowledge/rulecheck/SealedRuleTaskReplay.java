package com.zhku.knowledge.rulecheck;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** 新协议显式离线回放；启动方必须钉住已授权请求与采集摘要，未命中永不外发。 */
public final class SealedRuleTaskReplay implements RuleCheckTaskPlan.Planner,AutoCloseable {
    public static final String BASE="docs/evidence/rule-check-quality/2026-10-11/";
    private final BoundedRuleTaskPlanner bounded;
    public SealedRuleTaskReplay(Path root,String packageSha,String summarySha)throws Exception {
        this(root,BASE,packageSha,summarySha);
    }
    public SealedRuleTaskReplay(Path root,String directory,String packageSha,String summarySha)throws Exception {
        require(packageSha!=null&&packageSha.matches("[0-9a-f]{64}")&&summarySha!=null&&summarySha.matches("[0-9a-f]{64}"));
        var json=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        // 批次可配置，身份匹配不能放宽；拒绝路径逃逸，避免读取另一批或仓库外响应。
        require(directory!=null&&!Path.of(directory).isAbsolute()&&!Arrays.asList(directory.replace('\\','/').split("/")).contains(".."));
        Path evidence=root.resolve("docs/evidence").toRealPath(),base=root.resolve(directory).normalize();
        require(base.startsWith(root.resolve("docs/evidence").normalize()));
        base=base.toRealPath();require(base.startsWith(evidence));
        byte[] packetBytes=Files.readAllBytes(base.resolve("request-package.json")),summaryBytes=Files.readAllBytes(base.resolve("provider-capture/summary.json"));
        require(sha(packetBytes).equals(packageSha)&&sha(summaryBytes).equals(summarySha));
        var packet=json.readTree(packetBytes);var summary=json.readTree(summaryBytes);
        var authorization=json.readTree(Files.readAllBytes(base.resolve("external-transfer-authorization.json")));
        require(authorization.path("authority").asText().equals("DIRECT_USER_MESSAGE")&&authorization.path("decision").asText().equals("CONFIRMED")
            &&authorization.path("packageSha256").asText().equals(packageSha)&&authorization.path("maxCalls").asInt()==packet.path("requests").size()
            &&authorization.path("endpoint").asText().equals(packet.path("endpoint").asText())&&authorization.path("model").asText().equals("qwen-plus"));
        require(packet.path("retryCount").asInt(-1)==0&&packet.path("requests").size()>0&&packet.path("requests").size()<=43
            &&summary.path("completedAllRequests").asBoolean()&&summary.path("retryCount").asInt(-1)==0&&summary.path("sourceUnchanged").asBoolean());
        var responses=new HashMap<String,String>();
        for(var row:summary.path("records")) {
            String id=row.path("caseId").asText();require(id.matches("[RNT][0-9]{2}")&&row.path("path").asText().equals(id+".json"));
            byte[] raw=Files.readAllBytes(base.resolve("provider-capture/"+id+".json"));require(sha(raw).equals(row.path("sha256").asText()));
            var response=json.readTree(raw);require(response.path("status").asText().equals("RESPONSE_RECEIVED")&&response.path("caseId").asText().equals(id));
            require(responses.putIfAbsent(id,response.path("rawModelContent").asText())==null);
        }
        JsonNode logical=packet.has("allRequests")?packet.path("allRequests"):packet.path("requests");
        if(packet.has("allRequests"))validateHybrid(root,evidence,base,packet,responses,json);
        require(logical.isArray()&&logical.size()>0&&logical.size()<=43&&responses.size()==logical.size());
        var payloads=new ArrayList<Map.Entry<JsonNode,String>>();String system=null;
        for(var row:logical) {
            var messages=row.path("body").path("messages");require(messages.size()==2);
            String constraints=messages.get(0).path("content").asText();require(constraints.equals(BoundedRuleTaskPlanner.SYSTEM));system=constraints;
            String content=responses.get(row.path("caseId").asText());require(content!=null);
            payloads.add(Map.entry(json.readTree(messages.get(1).path("content").asText()),content));
        }
        require(system!=null);
        bounded=new BoundedRuleTaskPlanner(request->{
            try {
                JsonNode payload=json.readTree(request.userPayload());
                for(var entry:payloads)if(entry.getKey().equals(payload))return entry.getValue();
                return null;
            }catch(java.io.IOException invalid){throw new IllegalArgumentException("回放请求无法解析");}
        },Duration.ofSeconds(2),128);
    }
    private static void validateHybrid(Path root,Path evidence,Path base,JsonNode packet,Map<String,String> responses,ObjectMapper json)throws Exception {
        var logical=packet.path("allRequests");var fresh=packet.path("requests");var cached=packet.path("cachedCases");
        require(logical.isArray()&&cached.isArray()&&packet.path("maxCalls").asInt(-1)==fresh.size()
            &&packet.path("regressionDenominator").asInt(-1)==logical.size()&&logical.size()==fresh.size()+cached.size());
        var ids=new HashSet<String>();var freshIds=new HashSet<String>();var cachedIds=new HashSet<String>();
        for(var row:fresh)require(freshIds.add(row.path("caseId").asText()));
        for(var row:logical) {
            String id=row.path("caseId").asText();require(ids.add(id)&&responses.containsKey(id));
            var record=json.readTree(Files.readAllBytes(base.resolve("provider-capture/"+id+".json")));
            String mode=row.path("inferenceSource").asText();require(record.path("inferenceSource").asText().equals(mode));
            if(mode.equals("NEW_PROVIDER_RESPONSE_REQUIRED")) {
                require(freshIds.contains(id)&&record.path("actualHttpAttempt").asBoolean());
                JsonNode matching=null;for(var candidate:fresh)if(candidate.path("caseId").asText().equals(id))matching=candidate;
                require(row.equals(matching));
            }else {
                require(mode.equals("CACHED_PROVIDER_RESPONSE_SAME_STRUCTURED_REQUEST")&&!freshIds.contains(id)&&cachedIds.add(id)
                    &&!record.path("actualHttpAttempt").asBoolean());
                JsonNode origin=null;for(var c:cached)if(c.path("caseId").asText().equals(id)){require(origin==null);origin=c;}
                require(origin!=null&&origin.path("freshHttpCalls").asInt(-1)==0);
                // 缓存不是新外发授权：必须追溯原批直接许可及完整请求，不能仅凭相同问题复用。
                String relative=origin.path("recordPath").asText();require(!Path.of(relative).isAbsolute()
                    &&!Arrays.asList(relative.replace('\\','/').split("/")).contains(".."));
                Path file=root.resolve(relative).toRealPath();require(file.startsWith(evidence)&&file.getParent().getFileName().toString().equals("provider-capture")
                    &&file.getFileName().toString().equals(id+".json"));
                byte[] raw=Files.readAllBytes(file);require(sha(raw).equals(origin.path("recordSha256").asText())
                    &&record.path("cachedRecordSha256").asText().equals(origin.path("recordSha256").asText()));
                var priorRecord=json.readTree(raw);require(priorRecord.path("caseId").asText().equals(id)
                    &&priorRecord.path("status").asText().equals("RESPONSE_RECEIVED")&&priorRecord.path("actualHttpAttempt").asBoolean()
                    &&priorRecord.path("rawModelContent").asText().equals(record.path("rawModelContent").asText()));
                Path prior=file.getParent().getParent();byte[] priorBytes=Files.readAllBytes(prior.resolve("request-package.json"));
                var priorPacket=json.readTree(priorBytes);var auth=json.readTree(Files.readAllBytes(prior.resolve("external-transfer-authorization.json")));
                require(auth.path("authority").asText().equals("DIRECT_USER_MESSAGE")&&auth.path("decision").asText().equals("CONFIRMED")
                    &&auth.path("packageSha256").asText().equals(sha(priorBytes))&&auth.path("maxCalls").asInt(-1)==priorPacket.path("requests").size()
                    &&auth.path("endpoint").equals(packet.path("endpoint"))&&auth.path("model").equals(packet.path("model"))
                    &&priorPacket.path("endpoint").equals(packet.path("endpoint"))&&priorPacket.path("model").equals(packet.path("model"))
                    &&priorPacket.path("retryCount").asInt(-1)==0);
                JsonNode priorRequest=null;for(var r:priorPacket.path("requests"))if(r.path("caseId").asText().equals(id)){require(priorRequest==null);priorRequest=r;}
                require(priorRequest!=null&&priorRequest.equals(origin.path("sourceRequest")));
                var oldBody=priorRequest.path("body").deepCopy();var newBody=row.path("body").deepCopy();
                require(oldBody.isObject()&&newBody.isObject());
                var oldMessages=((com.fasterxml.jackson.databind.node.ObjectNode)oldBody).remove("messages");
                var newMessages=((com.fasterxml.jackson.databind.node.ObjectNode)newBody).remove("messages");
                require(oldBody.equals(newBody)&&oldMessages.size()==2&&newMessages.size()==2&&oldMessages.get(0).equals(newMessages.get(0))
                    &&oldMessages.get(1).path("role").asText().equals("user")&&newMessages.get(1).path("role").asText().equals("user")
                    &&json.readTree(oldMessages.get(1).path("content").asText()).equals(json.readTree(newMessages.get(1).path("content").asText())));
            }
        }
        require(ids.size()==logical.size()&&cachedIds.size()==cached.size()&&ids.containsAll(freshIds));
    }
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("任务回放批次未封存或与显式身份不符");}
    @Override public RuleCheckTaskPlan plan(String question,List<RuleCheckTaskPlan.Option> options){return bounded.plan(question,options);}
    @Override public void close(){bounded.close();}
    public int liveWorkers(){return bounded.liveWorkers();}
}
