package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import java.security.Permission;
import java.net.SocketPermission;
import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.rulecheck.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 本批真实字节只读回放。人工变体仅隔离拒绝边界，不是模型输出或新的发布授权。 */
public final class Batch57BoundaryReplay {
 static final ObjectMapper J=new ObjectMapper().findAndRegisterModules();
 static final List<Map<String,Object>> checks=new ArrayList<>();
 static JsonNode read(Path p)throws Exception{return J.readTree(Files.readAllBytes(p));}
 static JsonNode body(Path p)throws Exception{
  var record=read(p);String text=record.path("body").asText();
  String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  if(record.path("redacted").asBoolean()||!sha.equals(record.path("bodySha256").asText()))throw new AssertionError("Unmodified original body required: "+p.getFileName());
  return J.readTree(text);
 }
 static JsonNode wire(Path p)throws Exception{return J.readTree(body(p).path("choices").get(0).path("message").path("content").asText());}
 interface Check {Object run()throws Exception;}
 static void check(String id,String changed,String expected,Check c)throws Exception{
  String actual;try{actual=String.valueOf(c.run());}catch(RulePublicationContract.Rejected e){actual=e.code.name();}catch(RuleAgentProtocol.Rejected e){actual=e.validation.stage()+":"+e.validation.code();}
  checks.add(Map.of("id",id,"changedOnly",changed,"expected",expected,"actual",actual,"matched",expected.equals(actual)));
  if(!expected.equals(actual))throw new AssertionError(id+": expected "+expected+", actual "+actual);
 }
 @SuppressWarnings("removal") public static void main(String[] args)throws Exception{
  // 先安装进程级socket拒绝器；不创建Provider、DataSource、检索器、在线Host或发布器。
  var guard=new SecurityManager(){@Override public void checkPermission(Permission p){if(p instanceof SocketPermission)throw new SecurityException("REPLAY_NETWORK_FORBIDDEN");}};
  System.setSecurityManager(guard);
  boolean blocked=false;try{guard.checkPermission(new SocketPermission("127.0.0.1:9","connect"));}catch(SecurityException expected){blocked=expected.getMessage().equals("REPLAY_NETWORK_FORBIDDEN");}
  if(!blocked)throw new AssertionError("Network deny guard not active");
  Path dir=Path.of(args[0]),out=Path.of(args[1]);String run="7109d5ad-41ee-4841-ab9c-ee5c35587556";
  JsonNode snapshot=body(dir.resolve(run+"-4-REVIEW_INPUT_SNAPSHOT.json"));
  SemanticInput recorded=J.treeToValue(snapshot.path("input"),SemanticInput.class);
  var facts=new ArrayList<RuleAgentEvidenceTools.VerifiedFact>();
  for(var node:snapshot.path("facts"))facts.add(J.treeToValue(node,RuleAgentEvidenceTools.VerifiedFact.class));
  JsonNode request3=body(dir.resolve(run+"-3-REQUEST.json"));
  JsonNode decisionInput=J.readTree(request3.path("messages").get(1).path("content").asText());
  var entries=new ArrayList<RuleCheckApplicationService.CatalogEntry>();
  for(var context:decisionInput.path("ruleContexts")){
   String rule=context.path("ruleId").asText();var cs=new ArrayList<RuleCheckRuleTemplate.Condition>();
   for(var c:context.path("conditions"))cs.add(J.treeToValue(c,RuleCheckRuleTemplate.Condition.class));
   var definitions=facts.stream().filter(f->f.ruleId().equals(rule)).map(RuleAgentEvidenceTools.VerifiedFact::definition).toList();
   // 仅恢复能力工厂消耗的已读事实/条件投影；不把回放元数据当作新审核、权限或fresh原文检查。
   var template=new RuleCheckRuleTemplate(rule,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"RECORDED_INPUT_PROJECTION_NOT_POLICY_APPROVAL",LocalDate.of(2026,10,15),definitions,cs,List.of(),List.of());
   entries.add(new RuleCheckApplicationService.CatalogEntry(template,"REPLAY_ONLY_NOT_PUBLICATION",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY)));
  }
  var caps=RuleFactCapabilityCatalogue.fromReviewed(entries);
  var owner=new Owner(new UUID(0,1),new UUID(0,2));
  var input=new RuleAgentRunner.Input(owner,UUID.fromString(snapshot.path("taskId").asText()),snapshot.path("taskVersion").asLong(),recorded.questions().getFirst().text(),recorded.questions(),recorded.userConditions());
  var rebuilt=RulePublicationContract.input(input,facts,caps);
  if(!rebuilt.equals(recorded))throw new AssertionError("Original full SemanticInput identity not exactly reconstructed");
  var proof=new ArrayList<Map<String,Object>>();
  for(var f:facts){var cap=caps.find(f.id());if(cap.isPresent()){
   String actual=RulePublicationContract.fingerprint(f.definition());
   if(!actual.equals(cap.get().sourceFingerprint())||!cap.get().equals(recorded.capabilities().get(f.id())))throw new AssertionError("Capability identity mismatch");
   proof.add(Map.of("factId",f.id(),"sourceFingerprint",actual,"matched",true));
  }}
  Ask original=(Ask)RuleAgentJson.readAction(wire(dir.resolve(run+"-3-RESPONSE.json")).toString());
  if(!original.equals(RuleAgentJson.readAction(body(dir.resolve(run+"-3-PROPOSED_ACTION.json")).toString())))throw new AssertionError("Wire/canonical ASK mismatch");
  JsonNode rawReview=wire(dir.resolve(run+"-4-RESPONSE.json"));
  SemanticAssessment review=RulePublicationProtocol.decode(rawReview.toString(),recorded);
  var gate=new RulePublicationGate(caps);
  var fixedKeys=new SemanticAssessment(review.protocolRevision(),review.inputFingerprint(),review.requirements().stream().map(q->new Requirement(q.id(),q.span(),q.dimension(),q.subject(),q.recognition(),List.of("courseType"))).toList(),review.bindings(),review.unresolvedDemands());
  // 使用同批已经读取、核验器实际绑定的两事实；绝不在原轨迹中自动替换/隐藏COMBINED。
  var selected=new Ask(original.subquestionId(),original.conditionId(),original.reason(),List.of("S10/RETAKE_ONLY:public-failed-no-retake","S10/RETAKE_ONLY:professional-failed-retake-application"));
  if(original.supportingFactIds().stream().anyMatch(id->caps.find(id).isPresent()))throw new AssertionError("Expected unregistered COMBINED facts");
  check("B01_original_review_decode","NONE","DECODED",()->{RulePublicationProtocol.decode(rawReview.toString(),recorded);return "DECODED";});
  check("B02_original_gate","NONE","CAPABILITY_UNAVAILABLE",()->gate.assess(input,original,review,facts).verdict());
  check("B03_only_condition_keys","review necessaryConditionIds -> raw courseType","CAPABILITY_UNAVAILABLE",()->gate.assess(input,original,fixedKeys,facts).verdict());
  check("B04_only_ask_support_ids","ASK supportingFactIds -> recorded RETAKE_ONLY pair","CONDITION_NOT_NECESSARY",()->gate.assess(input,selected,review,facts).verdict());
  check("B05_both_isolated_changes","B03+B04 (synthetic positive control)","UNRESOLVED",()->{
   var parsed=RulePublicationProtocol.decode(J.writeValueAsString(fixedKeys),recorded);
   var result=gate.assess(input,selected,parsed,facts);
   if(result.acceptedBindings().size()!=2||!result.questions().get("q1").coveredRequirementIds().isEmpty())throw new AssertionError("Gate accepted answer instead of pending clarification");
   return result.verdict();
  });
  check("B06_foreign_input","review fingerprint -> zeros","REVIEW_BINDING:REVIEW_INPUT_MISMATCH",()->{
   var altered=rawReview.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)altered).put("inputFingerprint","0".repeat(64));return RulePublicationProtocol.decode(altered.toString(),recorded);
  });
  check("B07_one_branch_omitted","B05 ASK supports only PUBLIC","CONDITION_NOT_NECESSARY",()->gate.assess(input,new Ask(selected.subquestionId(),selected.conditionId(),selected.reason(),List.of(selected.supportingFactIds().getFirst())),fixedKeys,facts).verdict());
  check("B08_original_after_controls","NONE; original objects unchanged","CAPABILITY_UNAVAILABLE",()->gate.assess(input,original,review,facts).verdict());
  var report=new LinkedHashMap<String,Object>();
  report.put("checks",checks);report.put("allMatched",true);report.put("exactOriginalSemanticInputReconstructed",true);report.put("originalInputFingerprint",recorded.inputFingerprint());report.put("capabilityProof",proof);report.put("readFactIds",facts.stream().map(RuleAgentEvidenceTools.VerifiedFact::id).toList());report.put("originalAsk",original);report.put("counterfactualAsk",selected);report.put("counterfactualReview",fixedKeys);report.put("fullFactsUnchangedAcrossVariants",true);report.put("sameFrozenGateForEveryVariant",true);
  report.put("gateCodeSource",RulePublicationGate.class.getProtectionDomain().getCodeSource().getLocation().toString());report.put("protocolCodeSource",RulePublicationProtocol.class.getProtectionDomain().getCodeSource().getLocation().toString());report.put("networkDeniedBySecurityManager",true);report.put("guardSelfTestOnlyNoSocketOpened",true);report.put("realProviderCalls",0);report.put("databaseOperations",0);report.put("freshReadOrPublicationExecuted",false);report.put("waitingUserOrAnswerPublished",false);report.put("allVariantReviewsAreControlledInputsNotNewModelOutputs",true);
  J.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),report);
  System.out.println("8/8 frozen-code boundary checks matched; full original identity exact; zero network/database/model calls; no publication.");
 }
}
