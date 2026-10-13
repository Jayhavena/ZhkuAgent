package com.zhku.knowledge.rulecheck;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

class SealedHybridReplayTest {
 @TempDir Path root;
 final ObjectMapper json=new ObjectMapper();
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 void save(Path p,Object value)throws Exception{Files.createDirectories(p.getParent());Files.write(p,json.writeValueAsBytes(value));}
 Map<String,Object> request(String id,String question,String source) {
  var body=Map.of("model","qwen-plus","max_tokens",512,"temperature",0,"enable_thinking",false,"stream",false,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content",BoundedRuleTaskPlanner.SYSTEM),Map.of("role","user","content","{\"question\":\""+question+"\",\"evidenceOptions\":[]}")));
  var row=new LinkedHashMap<String,Object>();row.put("caseId",id);row.put("body",body);if(source!=null)row.put("inferenceSource",source);return row;
 }
 Path prepare(String mutation)throws Exception {
  Path source=root.resolve("docs/evidence/source"),batch=root.resolve("docs/evidence/hybrid");
  var prior=request("R01","cached",null);save(source.resolve("request-package.json"),Map.of("requests",List.of(prior),"endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","model","qwen-plus","retryCount",0));
  save(source.resolve("external-transfer-authorization.json"),auth(source,1));
  var raw=new LinkedHashMap<String,Object>();raw.put("caseId","R01");raw.put("status","RESPONSE_RECEIVED");raw.put("actualHttpAttempt",true);raw.put("rawModelContent","{\"tasks\":[{\"ruleId\":\"\",\"action\":\"UNSUPPORTED\",\"operand\":\"\",\"value\":\"\"}]}");save(source.resolve("provider-capture/R01.json"),raw);
  String recordSha=sha(Files.readAllBytes(source.resolve("provider-capture/R01.json")));
  var cached=request("R01","cached","CACHED_PROVIDER_RESPONSE_SAME_STRUCTURED_REQUEST");var fresh=request("N28","fresh","NEW_PROVIDER_RESPONSE_REQUIRED");
  var cache=new LinkedHashMap<String,Object>();cache.put("caseId","R01");cache.put("recordPath","docs/evidence/source/provider-capture/R01.json");cache.put("recordSha256",recordSha);cache.put("sourceRequest",prior);cache.put("freshHttpCalls",0);
  if(mutation.equals("path"))cache.put("recordPath","../outside.json");
  if(mutation.equals("sourceRequest"))cache.put("sourceRequest",request("R01","different",null));
  var packet=new LinkedHashMap<String,Object>();packet.put("requests",List.of(fresh));packet.put("allRequests",List.of(cached,fresh));packet.put("cachedCases",List.of(cache));packet.put("retryCount",0);packet.put("endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");packet.put("model","qwen-plus");packet.put("maxCalls",1);packet.put("regressionDenominator",2);
  if(mutation.equals("newPayload"))packet.put("requests",List.of(request("N28","changed","NEW_PROVIDER_RESPONSE_REQUIRED")));
  save(batch.resolve("request-package.json"),packet);save(batch.resolve("external-transfer-authorization.json"),auth(batch,mutation.equals("callBudget")?2:1));
  var replayRaw=new LinkedHashMap<>(raw);replayRaw.put("actualHttpAttempt",false);replayRaw.put("inferenceSource",cached.get("inferenceSource"));replayRaw.put("cachedRecordSha256",recordSha);save(batch.resolve("provider-capture/R01.json"),replayRaw);
  var newRaw=new LinkedHashMap<>(raw);newRaw.put("caseId","N28");newRaw.put("inferenceSource",fresh.get("inferenceSource"));save(batch.resolve("provider-capture/N28.json"),newRaw);
  var records=new ArrayList<Map<String,Object>>();for(String id:List.of("R01","N28"))records.add(Map.of("caseId",id,"path",id+".json","sha256",sha(Files.readAllBytes(batch.resolve("provider-capture/"+id+".json")))));
  save(batch.resolve("provider-capture/summary.json"),Map.of("records",records,"completedAllRequests",true,"retryCount",0,"sourceUnchanged",true));
  if(mutation.equals("recordHash"))save(source.resolve("provider-capture/R01.json"),Map.of("status","CHANGED"));
  if(mutation.equals("sourceAuthorization"))save(source.resolve("external-transfer-authorization.json"),Map.of("authority","DIRECT_USER_MESSAGE","decision","DENIED"));
  return batch;
 }
 Map<String,Object> auth(Path base,int calls)throws Exception{return Map.of("authority","DIRECT_USER_MESSAGE","decision","CONFIRMED","packageSha256",sha(Files.readAllBytes(base.resolve("request-package.json"))),"maxCalls",calls,"endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","model","qwen-plus");}
 SealedRuleTaskReplay open(Path batch)throws Exception{return new SealedRuleTaskReplay(root,"docs/evidence/hybrid",sha(Files.readAllBytes(batch.resolve("request-package.json"))),sha(Files.readAllBytes(batch.resolve("provider-capture/summary.json"))));}
 @Test void hybridPreservesBothAuthorizedFreshAndExactCachedRequests()throws Exception{Path b=prepare("");assertThatCode(()->{try(var replay=open(b)) {}}).doesNotThrowAnyException();}
 @ParameterizedTest @ValueSource(strings={"path","sourceRequest","newPayload","callBudget","recordHash","sourceAuthorization"}) void tamperingDoesNotCreateOfflineAuthority(String mutation)throws Exception {Path b=prepare(mutation);assertThatThrownBy(()->{try(var replay=open(b)) {}}).isInstanceOf(Exception.class);}
}
