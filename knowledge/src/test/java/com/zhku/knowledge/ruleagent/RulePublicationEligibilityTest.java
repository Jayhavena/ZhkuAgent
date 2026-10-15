package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.time.*;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
/** 真实批次输入+本机内存原文端口；不构造数据库，不调用模型，受控核验不代表在线理解。 */
class RulePublicationEligibilityTest {
 final ObjectMapper j=new ObjectMapper().findAndRegisterModules();
 final String pub="S10/RETAKE_ONLY:public-failed-no-retake",pro="S10/RETAKE_ONLY:professional-failed-retake-application";
 final List<RuleAgentEvidenceTools.VerifiedFact> facts=new ArrayList<>();
 final List<RuleCheckApplicationService.CatalogEntry> entries=new ArrayList<>();
 final RuleAgentRunner.Input input;final SemanticInput safe;final JsonNode wireReview;final Ask original;
 RulePublicationEligibilityTest()throws Exception{
  Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("knowledge/pom.xml")))root=root.getParent();
  Path dir=root.resolve("docs/evidence/rule-agent-provider/2026-10-15/t05-55-clarification-request/live-c284f31002e5419f8c4b9061dcf5a599");String run="7109d5ad-41ee-4841-ab9c-ee5c35587556";
  var snap=body(dir.resolve(run+"-4-REVIEW_INPUT_SNAPSHOT.json"));safe=j.treeToValue(snap.path("input"),SemanticInput.class);
  for(var f:snap.path("facts"))facts.add(j.treeToValue(f,RuleAgentEvidenceTools.VerifiedFact.class));
  var data=j.readTree(body(dir.resolve(run+"-3-REQUEST.json")).path("messages").get(1).path("content").asText());
  for(var c:data.path("ruleContexts")){
   String rule=c.path("ruleId").asText();var conditions=new ArrayList<RuleCheckRuleTemplate.Condition>();for(var x:c.path("conditions"))conditions.add(j.treeToValue(x,RuleCheckRuleTemplate.Condition.class));
   var t=new RuleCheckRuleTemplate(rule,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_FIXTURE_NOT_NEW_APPROVAL",LocalDate.of(2026,10,15),facts.stream().filter(f->f.ruleId().equals(rule)).map(RuleAgentEvidenceTools.VerifiedFact::definition).toList(),conditions,List.of(),List.of());
   entries.add(new RuleCheckApplicationService.CatalogEntry(t,c.path("questionScope").asText(),new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY)));
  }
  input=new RuleAgentRunner.Input(new Owner(new UUID(0,1),new UUID(0,2)),UUID.fromString(snap.path("taskId").asText()),snap.path("taskVersion").asLong(),safe.questions().getFirst().text(),safe.questions(),Map.of());
  original=(Ask)RuleAgentJson.readAction(body(dir.resolve(run+"-3-PROPOSED_ACTION.json")).toString());
  wireReview=j.readTree(body(dir.resolve(run+"-4-RESPONSE.json")).path("choices").get(0).path("message").path("content").asText());
  assertThat(RulePublicationContract.input(input,facts,RuleFactCapabilityCatalogue.fromReviewed(entries))).isEqualTo(safe);
 }
 JsonNode body(Path p)throws Exception{return j.readTree(j.readTree(Files.readAllBytes(p)).path("body").asText());}
 SemanticAssessment valid(SemanticInput s){var raw=j.convertValue(wireReview,SemanticAssessment.class);return new SemanticAssessment(REVISION,s.inputFingerprint(),raw.requirements().stream().map(q->new Requirement(q.id(),q.span(),q.dimension(),q.subject(),q.recognition(),List.of("courseType"))).toList(),raw.bindings(),List.of());}
 /** 条件已补齐后的合规核验形态：不再按条件值展开分支，只保留与已知条件一致的需求与CORE绑定。 */
 SemanticAssessment knownValid(SemanticInput s){
  var raw=j.convertValue(wireReview,SemanticAssessment.class);
  var only=raw.requirements().stream().filter(q->q.id().equals("req_q1_public_retake")).findFirst().orElseThrow();
  return new SemanticAssessment(REVISION,s.inputFingerprint(),List.of(new Requirement(only.id(),only.span(),only.dimension(),only.subject(),only.recognition(),List.of("courseType"))),raw.bindings().stream().filter(b->b.factId().equals(pub)).toList(),List.of());
 }
 RuleAgentEvidenceTools tools(){var b=facts.getFirst().definition().evidenceBindings().getFirst();var candidate=new RetrievedChunk(b.chunkId(),b.quote(),"LOCAL","LOCAL",1,b.revision(),b.checksum(),b.sourceId());var current=new RuleCheckEvidenceSnapshot("LOCAL",b.sourceId(),b.chunkId(),b.revision(),b.checksum(),b.quote(),true);return new RuleAgentEvidenceTools(input.owner(),input.taskId(),new RuleCheckRuntimeSettings("p","c","s","2025.9.2",20,5),()->entries,(q,n)->List.of(candidate),(s,c)->Optional.of(current),audit->{},Clock.systemUTC());}
 RuleAgentRunner.Outcome run(boolean eligible,AtomicInteger reviews,List<String> payloads)throws Exception{
  RuleAgentRunner.DecisionPort decision=(v,t)->{try{
   NextAction a;if(v.observations().isEmpty())a=new Search("q1","选修不合格重修");else if(v.facts().isEmpty())a=new Read("q1",List.of(v.observations().getLast().candidates().getFirst().handle()));else{
    assertThat(v.facts()).hasSize(6);
    try(var port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),input.taskId(),UUID.randomUUID(),()->"synthetic-key",x->{},new AtomicInteger(0))){payloads.add(port.payload(v));}
    a=eligible?new Ask("q1","courseType",original.reason(),List.of(pub,pro)):original;
   }return new RuleAgentRunner.Reply(j.writeValueAsString(a),false);
  }catch(Exception e){throw new IllegalStateException(e);}};
  return new RuleAgentRunner(decision,new RuleActionBroker(tools()),Clock.systemUTC(),(s,t)->{reviews.incrementAndGet();return valid(s);}).run(AgentRunContext.create(input.question()),input);
 }
 @Test void realReadUnregisteredProposalStopsBeforeReview()throws Exception{var reviews=new AtomicInteger();var out=run(false,reviews,new ArrayList<>());assertThat(reviews).hasValue(0);assertThat(out.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("ASK_NECESSARY_CONDITION");assertThat(e.publicationReason()).isEqualTo(Code.CAPABILITY_UNAVAILABLE);});assertThat(out.events()).noneMatch(e->e.action().equals("PUBLICATION_REVIEW"));assertThat(out.snapshot().result().items().getFirst().claims()).isEmpty();}
 @Test void realReadRetainsAllFactsButSchemaAndPayloadExposeOnlyEligibleIds()throws Exception{var payloads=new ArrayList<String>();run(true,new AtomicInteger(),payloads);var envelope=j.readTree(payloads.getFirst());var data=j.readTree(envelope.path("messages").get(1).path("content").asText());assertThat(data.path("facts").size()).isEqualTo(6);assertThat(data.path("publicationFactIds").toString()).contains(pub,pro).doesNotContain("S10/COMBINED:");var branches=envelope.path("response_format").path("json_schema").path("schema").path("oneOf");for(var b:branches)if(b.path("properties").path("type").path("const").asText().equals("ASK_NECESSARY_CONDITION"))assertThat(b.path("properties").path("supportingFactIds").path("items").path("enum").toString()).contains(pub,pro).doesNotContain("S10/COMBINED:");}
 @Test void eligiblePairStillUsesIndependentReviewAndFreshOriginalBeforeWaiting()throws Exception{var reviews=new AtomicInteger();var out=run(true,reviews,new ArrayList<>());assertThat(reviews).hasValue(1);assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.WAITING_USER);assertThat(out.snapshot().result().items().getFirst().claims()).isEmpty();assertThat(out.snapshot().result().items().getFirst().clarification().getFirst().allowedValues()).containsExactly("PUBLIC","PROFESSIONAL");}
 @Test void prefixedReviewConditionHasExplicitRejectionInsteadOfLateGateFailure(){assertThatThrownBy(()->RulePublicationProtocol.decode(wireReview.toString(),safe)).isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class,e->{assertThat(e.validation.stage()).isEqualTo(RuleAgentProtocol.Stage.REVIEW_BINDING);assertThat(e.validation.code().name()).isEqualTo("REVIEW_CONDITION_UNKNOWN");});}
 @Test void unknownReviewConditionIsNotSilentlyNormalized()throws Exception{var raw=j.valueToTree(valid(safe));((com.fasterxml.jackson.databind.node.ObjectNode)raw.path("requirements").get(0)).putArray("necessaryConditionIds").add("unknown");assertThatThrownBy(()->RulePublicationProtocol.decode(raw.toString(),safe)).isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class,e->assertThat(e.validation.code().name()).isEqualTo("REVIEW_CONDITION_UNKNOWN"));}
 RuleAgentRunner.DecisionPort decision(NextAction proposal){return (v,t)->{try{NextAction a=v.observations().isEmpty()?new Search("q1","选修重修"):v.facts().isEmpty()?new Read("q1",List.of(v.observations().getLast().candidates().getFirst().handle())):proposal;return new RuleAgentRunner.Reply(j.writeValueAsString(a),false);}catch(Exception e){throw new IllegalStateException(e);}};}
 @Test void realReadUnregisteredFinishStopsBeforeReviewWithKnownCondition()throws Exception{
  var known=new RuleAgentRunner.Input(input.owner(),input.taskId(),input.version(),input.question(),input.questions(),Map.of("courseType","PUBLIC"));var reviews=new AtomicInteger();
  var finish=new Finish(List.of(new Proposal("q1",List.of("S10/COMBINED:public-failed-no-retake"),List.of(),null)));
  var out=new RuleAgentRunner(decision(finish),new RuleActionBroker(tools()),Clock.systemUTC(),(s,t)->{reviews.incrementAndGet();return valid(s);}).run(AgentRunContext.create(known.question()),known);
  assertThat(reviews).hasValue(0);assertThat(out.events()).anySatisfy(e->{assertThat(e.action()).isEqualTo("FINISH_CHECK");assertThat(e.publicationReason()).isEqualTo(Code.CAPABILITY_UNAVAILABLE);});assertThat(out.events()).noneMatch(e->e.action().equals("PUBLICATION_REVIEW"));assertThat(out.snapshot().result().items().getFirst().claims()).isEmpty();
 }
 @Test void knownConditionFinishWithRegisteredFactPublishesComplete()throws Exception{
  var known=new RuleAgentRunner.Input(input.owner(),input.taskId(),input.version(),input.question(),input.questions(),Map.of("courseType","PUBLIC"));var reviews=new AtomicInteger();
  var expected=facts.stream().filter(f->f.id().equals(pub)).findFirst().orElseThrow().definition().text();
  var finish=new Finish(List.of(new Proposal("q1",List.of(pub),List.of(),null)));
  var out=new RuleAgentRunner(decision(finish),new RuleActionBroker(tools()),Clock.systemUTC(),(s,t)->{reviews.incrementAndGet();return knownValid(s);}).run(AgentRunContext.create(known.question()),known);
  assertThat(reviews).hasValue(1);
  assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
  assertThat(out.snapshot().runState()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED);
  assertThat(out.snapshot().result().coverage()).isEqualTo(Coverage.COMPLETE);
  var item=out.snapshot().result().items().getFirst();
  assertThat(item.claims()).extracting(Claim::text).containsExactly(expected);
  assertThat(item.issues()).isEmpty();
  assertThat(item.clarification()).isEmpty();
  assertThat(out.snapshot().continuationToken()).isNull();
  assertThat(out.snapshot().result().evidence()).isNotEmpty();
 }
 @Test void knownConditionFinishWithBothBranchRequirementsFailsWithoutClaimingComplete()throws Exception{
  var known=new RuleAgentRunner.Input(input.owner(),input.taskId(),input.version(),input.question(),input.questions(),Map.of("courseType","PUBLIC"));var reviews=new AtomicInteger();
  var finish=new Finish(List.of(new Proposal("q1",List.of(pub),List.of(),null)));
  var out=new RuleAgentRunner(decision(finish),new RuleActionBroker(tools()),Clock.systemUTC(),(s,t)->{reviews.incrementAndGet();return valid(s);}).run(AgentRunContext.create(known.question()),known);
  assertThat(reviews).hasValue(1);
  assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
  assertThat(out.snapshot().result().coverage()).isEqualTo(Coverage.PARTIAL);
  var item=out.snapshot().result().items().getFirst();
  assertThat(item.claims()).isNotEmpty();
  assertThat(item.issues()).extracting(Issue::reasonCode).containsExactly(ReasonCode.ANSWER_COVERAGE_INCOMPLETE);
 }
 @Test void driftedRegisteredDefinitionIsExcludedAndBothChecksReject(){
  var caps=RuleFactCapabilityCatalogue.fromReviewed(entries);var original=facts.stream().filter(f->f.id().equals(pub)).findFirst().orElseThrow();var d=original.definition();var changed=new RuleAgentEvidenceTools.VerifiedFact(pub,original.ruleId(),new RuleCheckRuleTemplate.Fact(d.id(),d.text()+"changed",d.independentlyAnswerable(),d.when(),d.evidenceBindings()));var all=new ArrayList<>(facts);all.set(all.indexOf(original),changed);
  var ask=new Ask("q1","courseType","controlled",List.of(pub,pro));assertThat(caps.publicationFactIds(all)).doesNotContain(pub).contains(pro);
  assertThatThrownBy(()->caps.requireProposal(ask,all)).hasMessage("CAPABILITY_UNAVAILABLE");var alteredSafe=RulePublicationContract.input(input,all,caps);assertThatThrownBy(()->new RulePublicationGate(caps).assess(input,ask,valid(alteredSafe),all)).hasMessage("CAPABILITY_UNAVAILABLE");
 }
 @Test void inputIdentityFailurePrecedesUnknownCondition()throws Exception{var raw=wireReview.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)raw).put("inputFingerprint","0".repeat(64));assertThatThrownBy(()->RulePublicationProtocol.decode(raw.toString(),safe)).isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class,e->assertThat(e.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_INPUT_MISMATCH));}

}
