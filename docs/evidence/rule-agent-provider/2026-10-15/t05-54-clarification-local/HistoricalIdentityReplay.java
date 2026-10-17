package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import java.security.Permission;
import java.net.SocketPermission;
import java.lang.reflect.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.knowledge.rulecheck.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
/** 仅复现冻结域代码；人工变体验证宿主可表示性，不代表模型能力或fresh原文发布验收。 */
public final class HistoricalIdentityReplay {
 static final ObjectMapper J=new ObjectMapper().findAndRegisterModules(); static final List<Map<String,Object>> results=new ArrayList<>();
 static SemanticInput safe;static RuleAgentRunner.Input original;static List<RuleAgentEvidenceTools.VerifiedFact> facts;static RuleFactCapabilityCatalogue caps;static RulePublicationGate gate;static Ask ask;
 static JsonNode read(Path p)throws Exception{return J.readTree(Files.readString(p));}
 static JsonNode exchange(Path dir,int a,String d)throws Exception {try(var paths=Files.list(dir)){var p=paths.filter(x->x.getFileName().toString().endsWith("-"+a+"-"+d+".json")).findFirst().orElseThrow();return J.readTree(read(p).path("body").asText());}}
 static JsonNode wire(Path dir,int a)throws Exception{return J.readTree(exchange(dir,a,"RESPONSE").path("choices").get(0).path("message").path("content").asText());}
 interface Probe {Object run()throws Exception;}
 static void test(String id,String expected,Probe p)throws Exception {
  String actual;try{actual=String.valueOf(p.run());}catch(RulePublicationContract.Rejected e){actual=e.code.name();}catch(RuleAgentProtocol.Rejected e){actual=e.validation.stage()+":"+e.validation.code();}catch(RuleAgentRunner.Rejected e){actual=e.reason.name();}
  results.add(Map.of("id",id,"expected",expected,"actual",actual,"matched",expected.equals(actual)));if(!expected.equals(actual))throw new AssertionError(id+" expected "+expected+" actual "+actual);
 }
 static Requirement req(String id,Subject subject,List<String> conditions){return new Requirement(id,new DemandSpan("q1",0,15),Dimension.RETAKE,subject,Recognition.IDENTIFIED,conditions);}
 static SemanticAssessment review(List<Requirement> rs,List<SemanticBinding> bs,List<UnresolvedDemand> us){return new SemanticAssessment(REVISION,safe.inputFingerprint(),rs,bs,us);}
 static SemanticBinding bind(String r,String f){return new SemanticBinding(r,f,BindingRole.CORE);}
 @SuppressWarnings({"removal","unchecked"}) public static void main(String[] args)throws Exception {
  // 进程内拒绝所有socket权限；本复现没有Provider、DataSource、数据库安装器或在线Host实例。
  System.setSecurityManager(new SecurityManager(){@Override public void checkPermission(Permission p){if(p instanceof SocketPermission)throw new SecurityException("REPLAY_NETWORK_FORBIDDEN");}});
  Path dir=Path.of(args[0]),out=Path.of(args[1]);JsonNode data=J.readTree(exchange(dir,5,"REQUEST").path("messages").get(1).path("content").asText());safe=J.treeToValue(data.path("input"),SemanticInput.class);
  JsonNode outcome;try(var paths=Files.list(dir)){outcome=read(paths.filter(x->x.toString().endsWith("-outcome.json")).findFirst().orElseThrow());}
  JsonNode proof=outcome.path("events").get(1).path("observation").path("evidence").get(0);
  var eb=new RuleCheckRuleTemplate.EvidenceBinding(UUID.fromString(proof.path("sourceId").asText()),UUID.fromString(proof.path("chunkId").asText()),proof.path("revision").asText(),proof.path("checksum").asText().toUpperCase(Locale.ROOT),proof.path("startInclusive").asInt(),proof.path("endExclusive").asInt(),proof.path("quote").asText());
  facts=safe.facts().stream().map(f->new RuleAgentEvidenceTools.VerifiedFact(f.id(),f.ruleId(),new RuleCheckRuleTemplate.Fact(f.id().substring(f.ruleId().length()+1),f.text(),true,f.when(),List.of(eb)))).toList();
  var entries=facts.stream().collect(java.util.stream.Collectors.groupingBy(RuleAgentEvidenceTools.VerifiedFact::ruleId)).entrySet().stream().map(e->{
   var template=new RuleCheckRuleTemplate(e.getKey(),"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_RECONSTRUCTION_NOT_NEW_POLICY_APPROVAL",LocalDate.of(2026,10,9),e.getValue().stream().map(RuleAgentEvidenceTools.VerifiedFact::definition).toList(),List.of(new RuleCheckRuleTemplate.Condition("courseType","这门课属于公共选修还是专业选修？",List.of("PUBLIC","PROFESSIONAL"))),List.of(),List.of());
   return new RuleCheckApplicationService.CatalogEntry(template,"REPLAY_ONLY",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));}).toList();
  caps=RuleFactCapabilityCatalogue.fromReviewed(entries);gate=new RulePublicationGate(caps);
  var owner=new Owner(UUID.fromString("00000000-0000-0000-0000-000000000001"),UUID.fromString("00000000-0000-0000-0000-000000000002"));
  original=new RuleAgentRunner.Input(owner,UUID.fromString(outcome.path("snapshot").path("taskId").asText()),2,safe.questions().getFirst().text(),safe.questions(),safe.userConditions());
  SemanticInput recordedSafe=safe; var recordedReview=RulePublicationProtocol.decode(wire(dir,5).toString(),recordedSafe);
  var reconstructed=RulePublicationContract.input(original,facts,caps);
  if(!reconstructed.equals(recordedSafe))throw new AssertionError("Exact original SemanticInput not reconstructed");
  ask=(Ask)RuleAgentJson.readAction(exchange(dir,4,"PROPOSED_ACTION").toString());
  String pub="S10/RETAKE_ONLY:public-failed-no-retake",pro="S10/RETAKE_ONLY:professional-failed-retake-application";
  var raw=new SemanticAssessment(REVISION,safe.inputFingerprint(),recordedReview.requirements(),recordedReview.bindings(),recordedReview.unresolvedDemands());
  test("R01_original_decode","DECODED",()->{RulePublicationProtocol.decode(wire(dir,5).toString(),recordedSafe);return "DECODED";});
  test("R02_original_gate","CONDITION_NOT_NECESSARY",()->gate.assess(original,ask,raw,facts));
  var tools=new RuleAgentEvidenceTools(owner,original.taskId(),new RuleCheckRuntimeSettings("REPLAY","REPLAY","REPLAY","2025.9.2",20,5),()->entries,(q,l)->{throw new AssertionError("Retrieval forbidden");},(s,c)->{throw new AssertionError("Fresh read forbidden");},rec->{throw new AssertionError("Audit write forbidden");},Clock.systemUTC());
  var broker=new RuleActionBroker(tools);Field ff=RuleActionBroker.class.getDeclaredField("facts");ff.setAccessible(true);var map=(Map<String,Map<String,RuleAgentEvidenceTools.VerifiedFact>>)ff.get(broker);var fm=new LinkedHashMap<String,RuleAgentEvidenceTools.VerifiedFact>();facts.forEach(f->fm.put(f.id(),f));map.put("q1",fm);
  test("R03_original_prefixed_condition_precheck","INVALID_MODEL_RESPONSE",()->{new RuleResultPublisher(broker,original,UUID.randomUUID()).precheckAsk((Ask)RuleAgentJson.readAction(exchange(dir,3,"PROPOSED_ACTION").toString()));return "ACCEPTED";});
  var needOnly=new Requirement("r1",new DemandSpan("q1",0,15),Dimension.RETAKE,Subject.UNSPECIFIED,Recognition.AMBIGUOUS,List.of("courseType"));
  test("R04_add_condition_only","CONDITION_NOT_NECESSARY",()->gate.assess(original,ask,review(List.of(needOnly),List.of(),raw.unresolvedDemands()),facts));
  var invalid=wire(dir,5).deepCopy();((ObjectNode)invalid).put("inputFingerprint",safe.inputFingerprint());((ObjectNode)invalid.path("requirements").get(0)).put("recognition","IDENTIFIED");
  test("R05_identified_unspecified","REVIEW_JSON:REVIEW_JSON_INVALID",()->RulePublicationProtocol.decode(invalid.toString(),safe));
  var one=req("r_public",Subject.PUBLIC_ELECTIVE,List.of("courseType"));var two=req("r_professional",Subject.PROFESSIONAL_ELECTIVE,List.of("courseType"));
  test("R06_one_subject_two_facts","REVIEW_BINDING_INVALID",()->gate.assess(original,ask,review(List.of(one),List.of(bind(one.id(),pub),bind(one.id(),pro)),List.of()),facts));
  var split=review(List.of(one,two),List.of(bind(one.id(),pub),bind(two.id(),pro)),List.of());
  test("R07_two_conditional_branches","UNRESOLVED",()->gate.assess(original,ask,RulePublicationProtocol.decode(J.writeValueAsString(split),safe),facts).verdict());
  var accepted=gate.assess(original,ask,split,facts);if(!accepted.questions().get("q1").coveredRequirementIds().isEmpty()||accepted.acceptedBindings().size()!=2)throw new AssertionError("Conditional facts marked covered");
  test("R08_keep_unresolved_demand","CONDITION_NOT_NECESSARY",()->gate.assess(original,ask,review(split.requirements(),split.bindings(),raw.unresolvedDemands()),facts));
  var known=new RuleAgentRunner.Input(owner,original.taskId(),2,original.question(),original.questions(),Map.of("courseType","PUBLIC"));var kr=new SemanticAssessment(REVISION,RulePublicationContract.input(known,facts,caps).inputFingerprint(),split.requirements(),split.bindings(),List.of());
  test("R09_known_condition_no_reask","CONDITION_ALREADY_PROVIDED",()->gate.assess(known,ask,kr,facts));
  var partialAsk=new Ask("q1","courseType","REPLAY",List.of(pub));
  test("R10_omit_one_support_for_two_requirements","CONDITION_NOT_NECESSARY",()->gate.assess(original,partialAsk,split,facts));
  test("R11_finish_before_condition","WHEN_NOT_SATISFIED",()->gate.assess(original,new Finish(List.of(new Proposal("q1",List.of(pub,pro),List.of(),null))),split,facts));
  test("R12_single_branch_admitted_gap","UNRESOLVED",()->gate.assess(original,partialAsk,review(List.of(one),List.of(bind(one.id(),pub)),List.of()),facts).verdict());
  var wrong=review(List.of(one,two),List.of(bind(one.id(),"S10/DEFERRED_EXAM_ONLY:public-no-deferred-exam"),bind(two.id(),pro)),List.of());
  test("R13_wrong_dimension","REVIEW_BINDING_INVALID",()->gate.assess(original,ask,wrong,facts));
  var missing=review(List.of(req("r_public",Subject.PUBLIC_ELECTIVE,List.of()),req("r_professional",Subject.PROFESSIONAL_ELECTIVE,List.of())),split.bindings(),List.of());
  test("R14_no_necessary_condition","CONDITION_NOT_NECESSARY",()->gate.assess(original,ask,missing,facts));
  ObjectNode foreign=(ObjectNode)J.valueToTree(split);foreign.put("inputFingerprint","0".repeat(64));
  test("R15_foreign_fingerprint","REVIEW_BINDING:REVIEW_INPUT_MISMATCH",()->RulePublicationProtocol.decode(foreign.toString(),safe));
  ((ObjectNode)((ArrayNode)foreign.path("bindings")).get(0)).put("factId","UNKNOWN:fact");foreign.put("inputFingerprint",safe.inputFingerprint());
  test("R16_unread_fact","REVIEW_BINDING:REVIEW_FACT_NOT_IN_INPUT",()->RulePublicationProtocol.decode(foreign.toString(),safe));
  // 输出仅记录人工分支变体；不调用Publisher.ask，不声明fresh引用或WAITING_USER发布成功。
  J.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("checks",results,"allMatched",true,"fullOriginalSemanticInputReconstructed",true,"actualFrozenClasses",true,"networkDeniedBySecurityManager",true,"realProviderCalls",0,"databaseOperations",0,"freshPublicationExecuted",false,"syntheticReviewVariant",split,"gateAcceptsSingleAlternativeWithoutBranchCoverage",true));
 }
}
