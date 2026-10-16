package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import java.security.Permission;
import java.net.SocketPermission;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
/** 只对历史响应做显式对照；不向生产解码器加入归一化，不调用Provider或数据库。 */
public final class T05ConditionKeyReplay {
 static final ObjectMapper J=new ObjectMapper().findAndRegisterModules();
 static Path root,out,live;static JsonNode original,edited;static SemanticInput safe;static RuleAgentRunner.Input input;static Ask ask;
 static final List<RuleAgentEvidenceTools.VerifiedFact> facts=new ArrayList<>();static final List<RuleCheckApplicationService.CatalogEntry> entries=new ArrayList<>();
 static RuleFactCapabilityCatalogue caps;static RuleCheckRuleTemplate.EvidenceBinding binding;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JsonNode read(Path p)throws Exception{return J.readTree(Files.readAllBytes(p));}
 static JsonNode body(String suffix)throws Exception {try(var paths=Files.list(live)){Path p=paths.filter(f->f.getFileName().toString().endsWith(suffix)).findFirst().orElseThrow();return J.readTree(read(p).path("body").asText());}}
 static void save(String name,Object value)throws Exception{Files.write(out.resolve(name),J.writerWithDefaultPrettyPrinter().writeValueAsBytes(value),StandardOpenOption.CREATE_NEW);}
 static void diff(JsonNode a,JsonNode b,String path,List<String> changes){if(a.equals(b))return;if(a.isObject()&&b.isObject()){var names=new TreeSet<String>();a.fieldNames().forEachRemaining(names::add);b.fieldNames().forEachRemaining(names::add);for(String n:names)diff(a.path(n),b.path(n),path+"/"+n,changes);}else if(a.isArray()&&b.isArray()&&a.size()==b.size()){for(int i=0;i<a.size();i++)diff(a.get(i),b.get(i),path+"/"+i,changes);}else changes.add(path);}
 static RuleAgentEvidenceTools tools(){
  // 原文与身份取自本批完整Fact；此处仅使用内存端口，不声称重新验证了在线检索或临时库。
  var candidate=new RetrievedChunk(binding.chunkId(),binding.quote(),"LOCAL_REPLAY","LOCAL_REPLAY",1,binding.revision(),binding.checksum(),binding.sourceId());
  var current=new RuleCheckEvidenceSnapshot("LOCAL_REPLAY",binding.sourceId(),binding.chunkId(),binding.revision(),binding.checksum(),binding.quote(),true);
  return new RuleAgentEvidenceTools(input.owner(),input.taskId(),new RuleCheckRuntimeSettings("local","local","local","2025.9.2",20,5),()->entries,(q,n)->List.of(candidate),(s,c)->{require(s.equals(binding.sourceId())&&c.equals(binding.chunkId()),"Unexpected read identity");return Optional.of(current);},audit->{},Clock.systemUTC());
 }
 static Map<String,Object> run(boolean corrected)throws Exception {
  var reviews=new AtomicInteger();var validation=new ArrayList<Object>();var decisions=new AtomicInteger();
  var search=RuleAgentJson.readAction(body("-1-PROPOSED_ACTION.json").toString());
  RuleAgentRunner.DecisionPort decision=(v,t)->{decisions.incrementAndGet();NextAction action=v.observations().isEmpty()?search:v.facts().isEmpty()?new Read("q1",List.of(v.observations().getLast().candidates().getFirst().handle())):ask;try{return new RuleAgentRunner.Reply(J.writeValueAsString(action),false);}catch(Exception e){throw new IllegalStateException(e);}};
  RuleSemanticReviewPort reviewer=(actual,t)->{reviews.incrementAndGet();require(actual.equals(safe),"Runner semantic input differs from actual t05-65 input");try{return RulePublicationProtocol.decode((corrected?edited:original).toString(),actual);}catch(RuleAgentProtocol.Rejected invalid){validation.add(invalid.validation);throw new RuleAgentProvider.Failure(ReasonCode.INVALID_MODEL_RESPONSE,false);}};
  var result=new RuleAgentRunner(decision,new RuleActionBroker(tools()),Clock.systemUTC(),reviewer).run(AgentRunContext.create(input.question()),input);
  save(corrected?"key-only-controlled-outcome.json":"original-controlled-outcome.json",result);
  var snapshot=result.snapshot();var summary=new LinkedHashMap<String,Object>();summary.put("taskStatus",snapshot.taskStatus());summary.put("runState",snapshot.runState());summary.put("coverage",snapshot.result().coverage());summary.put("answerSource",snapshot.execution().answerSource());summary.put("providerResponseReceived",snapshot.execution().providerResponseReceived());summary.put("continuationPresent",snapshot.continuationToken()!=null);summary.put("decisionPortInvocations",decisions.get());summary.put("reviewPortInvocations",reviews.get());summary.put("validation",validation);summary.put("evidenceCount",snapshot.result().evidence().size());summary.put("evidenceRelations",snapshot.result().evidenceRelations());summary.put("items",snapshot.result().items());summary.put("terminated",snapshot.terminated());
  require(reviews.get()==1,"Expected exactly one replayed review");
  if(!corrected){require(snapshot.taskStatus()==TaskStatus.FAILED,"Original response must fail");require(validation.size()==1,"Original rejection missing");}
  else {require(snapshot.taskStatus()==TaskStatus.WAITING_USER,"Key-only response did not publish WAITING_USER");require(snapshot.result().items().getFirst().claims().isEmpty(),"Clarification leaked claims");require(snapshot.result().items().getFirst().clarification().size()==1,"Expected one clarification");require(snapshot.result().items().getFirst().clarification().getFirst().conditionId().equals("courseType"),"Wrong clarification key");require(new HashSet<>(snapshot.result().items().getFirst().clarification().getFirst().allowedValues()).equals(Set.of("PUBLIC","PROFESSIONAL")),"Wrong clarification values");require(!snapshot.result().evidence().isEmpty(),"No fresh proof");require(!snapshot.execution().providerResponseReceived(),"Replay must not claim live response");}
  return summary;
 }
 @SuppressWarnings("removal") public static void main(String[] args)throws Exception {
  System.setSecurityManager(new SecurityManager(){@Override public void checkPermission(Permission p){if(p instanceof SocketPermission)throw new SecurityException("ZERO_EGRESS_REPLAY_NETWORK_FORBIDDEN");}});
  root=Path.of(args[0]);out=Path.of(args[1]);live=Path.of(args[2]);var snap=body("-4-REVIEW_INPUT_SNAPSHOT.json");safe=J.treeToValue(snap.path("input"),SemanticInput.class);
  var request=body("-4-REQUEST.json");var wireSafe=J.readTree(request.path("messages").get(1).path("content").asText()).path("input");require(wireSafe.equals(snap.path("input")),"Snapshot/wire input mismatch");
  for(var f:snap.path("facts"))facts.add(J.treeToValue(f,RuleAgentEvidenceTools.VerifiedFact.class));require(facts.size()==6,"Unexpected fixture facts");binding=facts.getFirst().definition().evidenceBindings().getFirst();
  for(var f:facts)for(var b:f.definition().evidenceBindings())require(b.equals(binding),"Memory original port requires identical saved article binding");require(binding.startInclusive()==0&&binding.endExclusive()==binding.quote().length(),"Saved quote not full bound article");
  var decision=J.readTree(body("-3-REQUEST.json").path("messages").get(1).path("content").asText());
  for(var c:decision.path("ruleContexts")){
   String id=c.path("ruleId").asText();var conditions=new ArrayList<RuleCheckRuleTemplate.Condition>();for(var item:c.path("conditions"))conditions.add(J.treeToValue(item,RuleCheckRuleTemplate.Condition.class));var gaps=new ArrayList<String>();c.path("evidenceGaps").forEach(x->gaps.add(x.asText()));
   var template=new RuleCheckRuleTemplate(id,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_REPLAY_NOT_NEW_POLICY_APPROVAL",LocalDate.of(2026,10,16),facts.stream().filter(f->f.ruleId().equals(id)).map(RuleAgentEvidenceTools.VerifiedFact::definition).toList(),conditions,gaps,List.of());
   entries.add(new RuleCheckApplicationService.CatalogEntry(template,c.path("questionScope").asText(),new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY)));
  }
  input=new RuleAgentRunner.Input(new Owner(new UUID(0,1),new UUID(0,2)),UUID.fromString(snap.path("taskId").asText()),snap.path("taskVersion").asLong(),safe.questions().getFirst().text(),safe.questions(),safe.userConditions());caps=RuleFactCapabilityCatalogue.fromReviewed(entries);
  require(RulePublicationContract.input(input,facts,caps).equals(safe),"Full original semantic identity reconstruction failed");ask=(Ask)RuleAgentJson.readAction(body("-3-PROPOSED_ACTION.json").toString());original=J.readTree(body("-4-RESPONSE.json").path("choices").get(0).path("message").path("content").asText());
  boolean rejected=false;try{RulePublicationProtocol.decode(original.toString(),safe);}catch(RuleAgentProtocol.Rejected e){require(e.validation.stage()==RuleAgentProtocol.Stage.REVIEW_BINDING&&e.validation.code()==RuleAgentProtocol.Code.REVIEW_CONDITION_UNKNOWN,"Unexpected original rejection");save("original-decode-rejection.json",e.validation);rejected=true;}require(rejected,"Original unexpectedly accepted");
  edited=original.deepCopy();int replacements=0;for(var r:edited.path("requirements")){var ids=(ArrayNode)r.path("necessaryConditionIds");for(int i=0;i<ids.size();i++){require(ids.get(i).asText().equals("S10/RETAKE_ONLY:courseType"),"Unexpected original key");ids.set(i,TextNode.valueOf("courseType"));replacements++;}}
  var changes=new ArrayList<String>();diff(original,edited,"",changes);require(replacements==2&&changes.equals(List.of("/requirements/0/necessaryConditionIds/0","/requirements/1/necessaryConditionIds/0")),"Control changed more than the two condition keys");save("control-response-key-only.json",edited);save("control-diff.json",Map.of("changedJsonPointers",changes,"oldValue","S10/RETAKE_ONLY:courseType","newValue","courseType","allOtherFieldsUnchanged",true));
  var decoded=RulePublicationProtocol.decode(edited.toString(),safe);var gate=new RulePublicationGate(caps).assess(input,ask,decoded,facts);save("key-only-gate-assessment.json",gate);
  var result=new LinkedHashMap<String,Object>();result.put("original",run(false));result.put("keyOnly",run(true));result.put("directGateVerdict",gate.verdict());result.put("safeInputReconstructedExactly",true);result.put("onlyTwoKeysChanged",true);result.put("realModelCalls",0);result.put("databaseOperations",0);result.put("dockerOperations",0);result.put("socketConnectionsDenied",true);result.put("secondRunExecuted",false);result.put("onlineAcceptancePassed",false);
  var origins=new LinkedHashMap<String,String>();for(Class<?> c:List.of(RuleAgentRunner.class,RulePublicationProtocol.class,RulePublicationGate.class,RuleResultPublisher.class,RuleFactCapabilityCatalogue.class)){String source=c.getResource(c.getSimpleName()+".class").toString();require(source.contains("knowledge-0.1.0-rc.1-controlled.jar")&&source.contains("BOOT-INF/classes"),"Production class shadowed");origins.put(c.getSimpleName(),source);}result.put("productionClassOrigins",origins);result.put("checksPassed",true);save("replay-result.json",result);System.out.println(J.writeValueAsString(Map.of("checksPassed",true,"originalRejected",true,"keyOnlyTaskStatus",((Map<?,?>)result.get("keyOnly")).get("taskStatus"),"directGateVerdict",gate.verdict(),"realModelCalls",0)));
 }
}
