package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
/** 只构造实际出站载荷并做本机合同对照；不调用HTTP、数据库或模型。 */
class RuleReviewConditionPromptTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 final Path root,live;
 final JsonNode recordedInput,recordedContract,original;
 final SemanticInput safe;
 RuleReviewConditionPromptTest()throws Exception {
  Path r=Path.of("").toAbsolutePath();while(!Files.exists(r.resolve("knowledge/pom.xml")))r=r.getParent();root=r;
  live=root.resolve("docs/evidence/rule-agent-provider/2026-10-15/t05-65-p02-eligibility-request/live-9e82970ee7d048adb14fa7f666e8019b");
  var request=body("-4-REQUEST.json");var data=json.readTree(request.path("messages").get(1).path("content").asText());recordedInput=data.path("input");recordedContract=data.path("reviewContract");safe=json.treeToValue(recordedInput,SemanticInput.class);
  original=json.readTree(body("-4-RESPONSE.json").path("choices").get(0).path("message").path("content").asText());
 }
 JsonNode body(String suffix)throws Exception {try(var files=Files.list(live)){return json.readTree(json.readTree(Files.readAllBytes(files.filter(f->f.getFileName().toString().endsWith(suffix)).findFirst().orElseThrow())).path("body").asText());}}
 String payload()throws Exception {
  var allowance=new AtomicInteger(0);var p=new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),UUID.randomUUID(),UUID.randomUUID(),()->"local-payload-only",trace->{throw new AssertionError("No dispatch expected");},allowance);
  try{return p.reviewPayload(safe);}finally{p.close();assertThat(p.isTerminated()).isTrue();assertThat(allowance).hasValue(0);}
 }
 JsonNode user(JsonNode envelope)throws Exception{return json.readTree(envelope.path("messages").get(1).path("content").asText());}
 @Test void actualPayloadSharesExplicitOriginalKeyRuleBetweenSystemAndContract()throws Exception {
  var envelope=json.readTree(payload());var contract=user(envelope).path("reviewContract");String form=contract.path("conditionIdForm").asText();
  assertThat(form).isNotBlank().contains("逐字复制 input.conditions[].conditionId","courseType","S10/RETAKE_ONLY:courseType","非法","subject");
  assertThat(envelope.path("messages").get(0).path("content").asText()).contains(form,"必要条件只列当前需求确实依赖的 when 键");
  assertThat(contract.path("conditionalBranches").asText()).doesNotContain("necessaryConditionIds使用原键");
 }
 @Test void actualPayloadPreservesInputFingerprintDuplicateConditionsAndRecordShapes()throws Exception {
  var envelope=json.readTree(payload());var data=user(envelope);assertThat(data.path("input")).isEqualTo(recordedInput);assertThat(data.path("input").path("inputFingerprint").asText()).isEqualTo(safe.inputFingerprint());
  var conditions=data.path("input").path("conditions");assertThat(conditions.size()).isEqualTo(2);for(var c:conditions)assertThat(c.path("conditionId").asText()).isEqualTo("courseType");
  // DemandSpan自2026-10-25改为excerpt回显定位；其余record形状仍须与T05录制契约逐字段一致。
  var records=data.path("reviewContract").path("records");var recorded=recordedContract.path("records");
  assertThat(records.path("DemandSpan").toString()).isEqualTo("[\"subquestionId\",\"excerpt\"]");
  for(var name:java.util.List.of("SemanticAssessment","Requirement","SemanticBinding","UnresolvedDemand"))assertThat(records.path(name)).isEqualTo(recorded.path(name));
  assertThat(data.path("reviewContract").path("spanCoordinates").asText()).contains("excerpt","首次出现");
  assertThat(envelope.path("response_format").path("type").asText()).isEqualTo("json_object");assertThat(envelope.path("max_completion_tokens").asInt()).isEqualTo(1024);
 }
 @Test void actualPayloadFitsExistingBudgetWithoutAnyProviderAllowance()throws Exception {
  String value=payload();int bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;assertThat(bytes).isLessThanOrEqualTo(DEFAULT_BUDGET.maxRequestBytes());
  Path out=Path.of(Objects.requireNonNull(System.getProperty("zhku.rulecheck.evidenceDir")));Files.createDirectories(out);Files.writeString(out.resolve("t05-65-review-prompt-request.json"),value,java.nio.charset.StandardCharsets.UTF_8);Files.write(out.resolve("t05-65-review-prompt-budget.json"),json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("actualRequestBytes",bytes,"originalRequestBytes",body("-4-REQUEST.json").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,"maximumRequestBytes",DEFAULT_BUDGET.maxRequestBytes(),"maximumOutputTokens",DEFAULT_BUDGET.maxOutputTokens(),"realProviderCalls",0)));
 }
 JsonNode keyOnly(){var copy=original.deepCopy();for(var r:copy.path("requirements")){var ids=(ArrayNode)r.path("necessaryConditionIds");assertThat(ids.size()).isEqualTo(1);assertThat(ids.get(0).asText()).isEqualTo("S10/RETAKE_ONLY:courseType");ids.set(0,TextNode.valueOf("courseType"));}return copy;}
 @Test void prefixedAndUnknownKeysRemainRejectedWithoutNormalization(){
  assertThatThrownBy(()->RulePublicationProtocol.decode(original.toString(),safe)).isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class,e->assertThat(e.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_CONDITION_UNKNOWN));
  var unknown=keyOnly();((ObjectNode)unknown.path("requirements").get(0)).putArray("necessaryConditionIds").add("unknown");assertThatThrownBy(()->RulePublicationProtocol.decode(unknown.toString(),safe)).isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class,e->assertThat(e.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_CONDITION_UNKNOWN));
 }
 @Test void t05_65KeyOnlyControlStillPassesTheExistingGate()throws Exception {
  var snapshot=body("-4-REVIEW_INPUT_SNAPSHOT.json");assertThat(snapshot.path("input")).isEqualTo(recordedInput);var facts=new ArrayList<RuleAgentEvidenceTools.VerifiedFact>();for(var f:snapshot.path("facts"))facts.add(json.treeToValue(f,RuleAgentEvidenceTools.VerifiedFact.class));
  var data=json.readTree(body("-3-REQUEST.json").path("messages").get(1).path("content").asText());var entries=new ArrayList<RuleCheckApplicationService.CatalogEntry>();
  for(var c:data.path("ruleContexts")){String id=c.path("ruleId").asText();var conditions=new ArrayList<RuleCheckRuleTemplate.Condition>();for(var item:c.path("conditions"))conditions.add(json.treeToValue(item,RuleCheckRuleTemplate.Condition.class));var gaps=new ArrayList<String>();c.path("evidenceGaps").forEach(x->gaps.add(x.asText()));var t=new RuleCheckRuleTemplate(id,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_FIXTURE_NOT_NEW_APPROVAL",LocalDate.of(2026,10,16),facts.stream().filter(f->f.ruleId().equals(id)).map(RuleAgentEvidenceTools.VerifiedFact::definition).toList(),conditions,gaps,List.of());entries.add(new RuleCheckApplicationService.CatalogEntry(t,c.path("questionScope").asText(),new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE))));}
  var input=new RuleAgentRunner.Input(new Owner(new UUID(0,1),new UUID(0,2)),UUID.fromString(snapshot.path("taskId").asText()),snapshot.path("taskVersion").asLong(),safe.questions().getFirst().text(),safe.questions(),safe.userConditions());var caps=RuleFactCapabilityCatalogue.fromReviewed(entries);assertThat(RulePublicationContract.input(input,facts,caps)).isEqualTo(safe);
  var response=RulePublicationProtocol.decode(keyOnly().toString(),safe);var ask=RuleAgentJson.readAction(body("-3-PROPOSED_ACTION.json").toString());var result=new RulePublicationGate(caps).assess(input,ask,response,facts);assertThat(result.verdict()).isEqualTo(Verdict.UNRESOLVED);assertThat(result.acceptedBindings()).hasSize(2);
 }
}
