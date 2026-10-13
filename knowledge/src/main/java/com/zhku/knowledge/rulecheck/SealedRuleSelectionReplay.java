package com.zhku.knowledge.rulecheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** 显式离线模式：只复用已有真实选题响应，不伪称在线模型或支持任意新问题。 */
public final class SealedRuleSelectionReplay implements RuleCheckApplicationService.RuleSelector,AutoCloseable {
    private static final String BASE="docs/evidence/rule-check-refactor/2026-10-10/package-01/";
    private static final String PACKAGE_SHA="62d759d92163ea17aaa985bb1dae73b7a0208e6184df2b2bd58ffdec159bf168";
    private static final String SUMMARY_SHA="3fb4bca69ce42a52fdb4b04d1c53a7f8b9e3a8d09107d5c31a2f8d6906a30092";
    private final BoundedRuleSelector bounded;
    public SealedRuleSelectionReplay(Path root) throws Exception {
        var json=new ObjectMapper();Path base=root.resolve(BASE);
        byte[] packageBytes=Files.readAllBytes(base.resolve("request-package.json"));
        byte[] summaryBytes=Files.readAllBytes(base.resolve("provider-capture/summary.json"));
        require(sha(packageBytes).equals(PACKAGE_SHA)&&sha(summaryBytes).equals(SUMMARY_SHA));
        var pack=json.readTree(packageBytes);var summary=json.readTree(summaryBytes);
        var responseById=new HashMap<String,String>();
        for(var row:summary.path("records")) {
            String id=row.path("caseId").asText();require(id.matches("Q[0-9]{2}|O04"));
            byte[] response=Files.readAllBytes(base.resolve("provider-capture/"+id+".json"));require(sha(response).equals(row.path("sha256").asText()));
            responseById.put(id,json.readTree(response).path("rawModelContent").asText());
        }
        var byPayload=new HashMap<String,String>();
        for(var request:pack.path("requests")) {
            var messages=request.path("body").path("messages");
            byPayload.put(messages.get(1).path("content").asText(),responseById.get(request.path("caseId").asText()));
        }
        bounded=new BoundedRuleSelector(request->{
            // 候选与问句必须同时一致；不能按问题ID强行返回答案，也不在回放未命中时偷调供应商。
            try {
                JsonNode payload=json.readTree(request.userPayload());
                for(var entry:byPayload.entrySet())if(json.readTree(entry.getKey()).equals(payload))return entry.getValue();
                return "{\"ruleId\":null}";
            }catch(Exception invalid){throw new IllegalArgumentException("回放载荷不合法");}
        },Duration.ofSeconds(2),128);
    }
    private static String sha(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("已封存回放输入不一致");}
    @Override public boolean requiresScopedCandidates(){return true;}
    @Override public String select(String question,List<RuleCheckApplicationService.RuleOption> options){return bounded.select(question,options);}
    public int attempts(){return bounded.attempts();}
    public int liveWorkers(){return bounded.liveWorkers();}
    @Override public void close(){bounded.close();}
}
