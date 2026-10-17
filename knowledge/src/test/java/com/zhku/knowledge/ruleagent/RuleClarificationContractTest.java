package com.zhku.knowledge.ruleagent;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
/** 宿主合同验收；核验输出为受控输入，不代表模型判断能力。 */
class RuleClarificationContractTest {
 final RulePublicationGateTest f=new RulePublicationGateTest();
 @Test void oneSideCannotAuthorizeClarificationWhenDeclaredConditionHasTwoValues(){
  var fact=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));var facts=List.of(fact);var caps=f.catalogue(facts);var input=f.input("选修没过能再修吗？");
  var need=new Requirement("r",new DemandSpan("q1",0,input.question().length()),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"));
  var review=new SemanticAssessment(REVISION,RulePublicationContract.input(input,facts,caps).inputFingerprint(),List.of(need),List.of(new SemanticBinding("r",fact.id(),BindingRole.CORE)),List.of());
  assertThatThrownBy(()->new RulePublicationGate(caps).assess(input,new Ask("q1","courseType","理由不是证据",List.of(fact.id())),review,facts)).hasMessage("CONDITION_NOT_NECESSARY");
 }
 @Test void reviewIncludesAuditedConditionDirectoryAndFingerprintChangesWithIt()throws Exception {
  var fact=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));var facts=List.of(fact);var caps=f.catalogue(facts);var input=f.input("选修没过能再修吗？");
  var tree=new ObjectMapper().valueToTree(RulePublicationContract.input(input,facts,caps));assertThat(tree.path("conditions").isArray()).isTrue();assertThat(tree.path("conditions").get(0).path("conditionId").asText()).isEqualTo("courseType");
  var template=new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate(fact.ruleId(),"2025.9.2",com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.ReviewState.APPROVED,"TEST",java.time.LocalDate.of(2026,10,9),List.of(fact.definition()),List.of(new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Condition("courseType","不同的审核条件标签",List.of("PUBLIC","PROFESSIONAL"))),List.of(),List.of());
  var entry=new com.zhku.knowledge.rulecheck.RuleCheckApplicationService.CatalogEntry(template,"TEST",new com.zhku.knowledge.rulecheck.RuleCheckApplicabilityGuard.Coverage(Set.of(com.zhku.knowledge.rulecheck.RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Coverage.declared(com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.CATEGORY));
  var changed=RuleFactCapabilityCatalogue.fromReviewed(List.of(entry));assertThat(RulePublicationContract.input(input,facts,changed).inputFingerprint()).isNotEqualTo(RulePublicationContract.input(input,facts,caps).inputFingerprint());
 }
 @Test void deterministicRejectionFeedbackContainsNoFreeTextReason()throws Exception {
  var event=new RuleAgentRunner.Event(3,"ASK_NECESSARY_CONDITION",null,ReasonCode.INVALID_MODEL_RESPONSE);
  var view=new RuleAgentRunner.View("问题",List.of(new RuleAgentRunner.Question("q1","问题")),Map.of(),List.of(),List.of(),List.of(),List.of(event),1,3);
  try(var port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"synthetic-key",t->{},new AtomicInteger(1))){
   var data=new ObjectMapper().readTree(new ObjectMapper().readTree(port.payload(view)).path("messages").get(1).path("content").asText());assertThat(data.path("rejectedActions").isArray()).isTrue();assertThat(data.path("rejectedActions").get(0).path("reasonCode").asText()).isEqualTo("INVALID_MODEL_RESPONSE");
  }
 }
 @Test void fullFactSnapshotRetainsChecksumIdentityButNeverEntersOutboundPayload()throws Exception {
  var low=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));var binding=low.definition().evidenceBindings().getFirst();
  var raw=new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.EvidenceBinding(binding.sourceId(),binding.chunkId(),binding.revision(),"ABCDEF".repeat(10)+"ABCD",binding.startInclusive(),binding.endExclusive(),binding.quote());
  var definition=new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Fact(low.definition().id(),low.definition().text(),true,low.definition().when(),List.of(raw));
  var fact=new RuleAgentEvidenceTools.VerifiedFact(low.id(),low.ruleId(),definition);var facts=List.of(fact);var caps=f.catalogue(facts);var input=f.input("重修条件是什么？");var safe=RulePublicationContract.input(input,facts,caps);
  var exchanges=new ArrayList<RuleAgentProvider.Exchange>();var allowance=new AtomicInteger(1);
  try(var port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),input.taskId(),UUID.randomUUID(),()->"synthetic-key",t->{},exchanges::add,allowance)){
   port.captureInput(input,facts,caps);assertThat(allowance).hasValue(1);assertThat(exchanges).hasSize(1);var j=new ObjectMapper();var snapshot=j.readTree(exchanges.getFirst().body());
   var restored=j.treeToValue(snapshot.path("facts").get(0),RuleAgentEvidenceTools.VerifiedFact.class);assertThat(restored).isEqualTo(fact);assertThat(restored.definition().evidenceBindings().getFirst().checksum()).isEqualTo(raw.checksum());
   assertThat(RulePublicationContract.input(input,List.of(restored),caps)).isEqualTo(safe);
   assertThat(port.reviewPayload(safe)).doesNotContain("sourceId","chunkId","taskId","taskVersion","independentlyAnswerable","checksum");
  }
 }
 @Test void completeConditionalPairIsAcceptedWithoutTreatingUserConditionAsKnown(){
  var pub=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));var pro=f.fact("S10/RETAKE_ONLY","professional-failed-retake-application",Map.of("courseType","PROFESSIONAL"));var facts=List.of(pub,pro);var caps=f.catalogue(facts);var input=f.input("没及格还可再选修吗？");var span=new DemandSpan("q1",0,input.question().length());
  var rs=List.of(new Requirement("a",span,Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType")),new Requirement("b",span,Dimension.RETAKE,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType")));
  var review=new SemanticAssessment(REVISION,RulePublicationContract.input(input,facts,caps).inputFingerprint(),rs,List.of(new SemanticBinding("a",pub.id(),BindingRole.CORE),new SemanticBinding("b",pro.id(),BindingRole.CORE)),List.of());
  var result=new RulePublicationGate(caps).assess(input,new Ask("q1","courseType","not authority",List.of(pub.id(),pro.id())),review,facts);assertThat(result.verdict()).isEqualTo(Verdict.UNRESOLVED);assertThat(input.userConditions()).isEmpty();assertThat(result.questions().get("q1").coveredRequirementIds()).isEmpty();
 }
 @Test void expandedRequirementsAndBindingsRefuseOverCapacityWithoutTruncation(){
  var span=new DemandSpan("q1",0,1);var eight=java.util.stream.IntStream.range(0,8).mapToObj(i->new Requirement("r"+i,span,Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"))).toList();
  var sixteen=java.util.stream.IntStream.range(0,16).mapToObj(i->new SemanticBinding("r"+(i/2),"fact"+i,BindingRole.CORE)).toList();
  assertThat(new SemanticAssessment(REVISION,"a".repeat(64),eight,sixteen,List.of()).bindings()).hasSize(16);
  var nine=new ArrayList<>(eight);nine.add(new Requirement("r8",span,Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType")));assertThatThrownBy(()->new SemanticAssessment(REVISION,"a".repeat(64),nine,sixteen,List.of())).hasMessage("REVIEW_SCHEMA_INVALID");
  var seventeen=new ArrayList<>(sixteen);seventeen.add(new SemanticBinding("r0","extra",BindingRole.CORE));assertThatThrownBy(()->new SemanticAssessment(REVISION,"a".repeat(64),eight,seventeen,List.of())).hasMessage("REVIEW_SCHEMA_INVALID");
 }
 @Test void snapshotArchiveFailureStopsBeforeAnyCallOrQuotaConsumption(){
  var fact=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));var facts=List.of(fact);var input=f.input("查询重修");var caps=f.catalogue(facts);var allowance=new AtomicInteger(1);
  try(var port=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),input.taskId(),UUID.randomUUID(),()->"synthetic-key",t->{},e->{throw new IllegalStateException("controlled archive failure");},allowance)){
   assertThatThrownBy(()->port.captureInput(input,facts,caps)).isInstanceOfSatisfying(RuleAgentProvider.Failure.class,e->assertThat(e.reason).isEqualTo(ReasonCode.AUDIT_UNAVAILABLE));assertThat(allowance).hasValue(1);
  }
 }
 @Test void knownOtherConditionExcludesOnlyExplicitlyConflictingBranch(){
  var pub=f.fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC","track","A"));var pro=f.fact("S10/RETAKE_ONLY","professional-failed-retake-application",Map.of("courseType","PROFESSIONAL","track","B"));var facts=List.of(pub,pro);
  var rule=new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate(pub.ruleId(),"2025.9.2",com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.ReviewState.APPROVED,"TEST",java.time.LocalDate.of(2026,10,9),facts.stream().map(RuleAgentEvidenceTools.VerifiedFact::definition).toList(),List.of(new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Condition("courseType","类别",List.of("PUBLIC","PROFESSIONAL")),new com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Condition("track","类别条件",List.of("A","B"))),List.of(),List.of());
  var entry=new com.zhku.knowledge.rulecheck.RuleCheckApplicationService.CatalogEntry(rule,"TEST",new com.zhku.knowledge.rulecheck.RuleCheckApplicabilityGuard.Coverage(Set.of(com.zhku.knowledge.rulecheck.RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Coverage.declared(com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.CATEGORY));var caps=RuleFactCapabilityCatalogue.fromReviewed(List.of(entry));var original=f.input("重修政策？");var input=new RuleAgentRunner.Input(original.owner(),original.taskId(),1,original.question(),original.questions(),Map.of("track","A"));
  var req=new Requirement("r",new DemandSpan("q1",0,input.question().length()),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"));var review=new SemanticAssessment(REVISION,RulePublicationContract.input(input,facts,caps).inputFingerprint(),List.of(req),List.of(new SemanticBinding("r",pub.id(),BindingRole.CORE)),List.of());
  assertThat(new RulePublicationGate(caps).assess(input,new Ask("q1","courseType","TEST",List.of(pub.id())),review,facts).verdict()).isEqualTo(Verdict.UNRESOLVED);
  var onlyRead=List.of(pub);var onlyReview=new SemanticAssessment(REVISION,RulePublicationContract.input(input,onlyRead,caps).inputFingerprint(),review.requirements(),review.bindings(),List.of());
  assertThatThrownBy(()->new RulePublicationGate(caps).assess(input,new Ask("q1","courseType","TEST",List.of(pub.id())),onlyReview,onlyRead)).hasMessage("CONDITION_NOT_NECESSARY");
 }
}
