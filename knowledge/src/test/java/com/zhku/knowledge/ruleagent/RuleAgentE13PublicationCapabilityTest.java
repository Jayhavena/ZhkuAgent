package com.zhku.knowledge.ruleagent;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.rulecheck.RuleCheckApplicabilityGuard;
import com.zhku.knowledge.rulecheck.RuleCheckApplicationService;
import com.zhku.knowledge.rulecheck.RuleCheckEvidenceSnapshot;
import com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate;
import com.zhku.knowledge.rulecheck.RuleCheckRuntimeSettings;
import com.zhku.knowledge.rulecheck.RuleCheckTopicGuard;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import static com.zhku.knowledge.ruleagent.RuleAgentContract.Finish;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.IssueKind;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.NextAction;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.Owner;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.Proposal;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.Read;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.ReasonCode;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.Search;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.TaskStatus;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.BindingRole;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.DemandSpan;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.Dimension;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.REVISION;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.Recognition;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.Requirement;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.SemanticAssessment;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.SemanticBinding;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.Subject;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.UnresolvedDemand;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.UnresolvedReason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 08:42 真实失败的最小离线复现：可读但未登记发布能力的历史事实被 CORE 绑定。
 * 这里要求失败由真实门禁给出并保留精确码，而不是靠提示词或测试替身假装通过。
 * 不联网、不启动数据库、不调用模型或 Embedding。
 */
class RuleAgentE13PublicationCapabilityTest {
    static final String QUESTION = "这学期公选补选今晚几点关？现在还能帮我补上吗？";
    static final String RULE = "S06/GENERAL";
    static final String FACT = "学生应在学校规定时间内完成网上选课和补选。";
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;

    static com.fasterxml.jackson.databind.node.ObjectNode savedReview() throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(
                java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/test/resources/three-question-regression/e13-review-redacted-20261029T0842.json")));
    }

    static RulePublicationContract.SemanticAssessment decodeSaved(RulePublicationContract.SemanticInput safe) {
        try {
            var content = savedReview();
            content.put("inputFingerprint", safe.inputFingerprint());
            var response = new ObjectMapper().valueToTree(Map.of("choices", List.of(Map.of(
                    "message", Map.of("content", content.toString())))));
            com.zhku.knowledge.ruleagent.web.RuleAgentE13Policy.reviewBoundary(response);
            assertThat(safe.capabilities()).isEmpty();
            return RulePublicationProtocol.decode(content.toString(), safe);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    final UUID sourceId = UUID.randomUUID(), chunkId = UUID.randomUUID();
    final RuleCheckRuleTemplate.EvidenceBinding binding = new RuleCheckRuleTemplate.EvidenceBinding(
            sourceId, chunkId, "2025.9.2", "8D892CB3476CB71AB88A58A692811F02D37B6C2B645783118ECAB3CC0DB8AC3C", 0, FACT.length(), FACT);
    final RuleCheckRuleTemplate template = new RuleCheckRuleTemplate(RULE, "2025.9.2",
            RuleCheckRuleTemplate.ReviewState.APPROVED, "local-fixture", LocalDate.of(2025, 9, 2),
            // 合成本地证据只复现两个真实事实 ID；不宣称这段合成原文就是历史事实正文。
            List.of(new RuleCheckRuleTemplate.Fact("online-selection-plan", FACT, true, Map.of(), List.of(binding)),
                    new RuleCheckRuleTemplate.Fact("unenrolled-assessment-disjunction", FACT, true, Map.of(), List.of(binding))),
            List.of(), List.of(), List.of(), List.of());
    final RuleCheckApplicationService.CatalogEntry entry = new RuleCheckApplicationService.CatalogEntry(template,
            "仅解释 2025.9.2 的一般选课时间规则", new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),
            RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
    final Owner owner = new Owner(new UUID(0, 11), new UUID(0, 12));
    final UUID taskId = UUID.randomUUID();
    final RuleAgentRunner.Input input = new RuleAgentRunner.Input(owner, taskId, 3L, QUESTION,
            List.of(new RuleAgentRunner.Question("q1", QUESTION)), Map.of());

    RuleAgentEvidenceTools tools() {
        var candidate = new RetrievedChunk(binding.chunkId(), binding.quote(), "LOCAL", "LOCAL", 1,
                binding.revision(), binding.checksum(), binding.sourceId());
        var current = new RuleCheckEvidenceSnapshot("LOCAL", binding.sourceId(), binding.chunkId(),
                binding.revision(), binding.checksum(), binding.quote(), true);
        return new RuleAgentEvidenceTools(owner, taskId,
                new RuleCheckRuntimeSettings("p", "c", "s", "2025.9.2", 20, 5),
                () -> List.of(entry), (query, limit) -> List.of(candidate),
                (snapshot, candidate1) -> Optional.of(current), audit -> { }, Clock.systemUTC());
    }

    /**
     * 目录里没有该规则的能力登记，门禁必须在能力校验处拒绝绑定；
     * 可读、可检索不等于可发布，这条不能由提示词替代。
     */
    @Test
    void gateRejectsBindingToFactWithoutPublicationCapability() {
        var capabilities = RuleFactCapabilityCatalogue.fromReviewed(List.of(entry));
        var facts = template.facts().stream().map(fact ->
                new RuleAgentEvidenceTools.VerifiedFact(RULE + ":" + fact.id(), RULE, fact)).toList();
        var safe = RulePublicationContract.input(input, facts, capabilities);
        assertThat(capabilities.publicationFactIds(facts)).isEmpty();
        var review = decodeSaved(safe);
        var proposal = new Finish(List.of(new Proposal("q1", List.of(),
                List.of(new RuleAgentContract.ProposedIssue(IssueKind.DATA_GAP, ReasonCode.UNSUPPORTED_DEMAND)), null)));
        assertThatThrownBy(() -> new RulePublicationGate(capabilities).assess(input, proposal, review, facts))
                .hasMessageContaining(RulePublicationContract.Code.CAPABILITY_UNAVAILABLE.name());
    }

    /**
     * 端到端离线：真实 Runner 与发布门禁仍按 INVALID_MODEL_RESPONSE 失败关闭，
     * 同时把具体拒绝码放进可审计事件，避免只能靠源码反推当时是哪种拒绝。
     */
    RuleAgentRunner.Outcome rejectedOutcome() {
        RuleAgentRunner.DecisionPort decision = (view, timeout) -> {
            try {
                NextAction action;
                if (view.observations().isEmpty()) action = new Search("q1", "公选 补选 时间");
                else if (view.observations().size() == 1) action = new Read("q1",
                        List.of(view.state().candidates().getFirst().handle()));
                else action = new Finish(List.of(new Proposal("q1", List.of(),
                        List.of(new RuleAgentContract.ProposedIssue(IssueKind.DATA_GAP, ReasonCode.UNSUPPORTED_DEMAND)), null)));
                return new RuleAgentRunner.Reply(json.writeValueAsString(action), false);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        };
        RuleSemanticReviewPort reviewer = (safe, timeout) -> {
            assertThat(safe.facts()).hasSize(2);
            return decodeSaved(safe);
        };
        return new RuleAgentRunner(decision, new RuleActionBroker(tools()), Clock.systemUTC(), reviewer)
                .run(AgentRunContext.create(QUESTION), input);
    }

    @Test
    void unregisteredHistoryFactBindingIsRejectedWithPersistedCapabilityCode() throws Exception {
        var out = rejectedOutcome();
        var path = temporary.resolve("publication-rejections.jsonl");
        var recorded = com.zhku.knowledge.ruleagent.web.RuleAgentPublicationRejectionEvidence.persist(out,
                node -> java.nio.file.Files.writeString(path, node.toString() + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND));
        assertThat(recorded).isSameAs(out);
        var persisted = java.nio.file.Files.readAllLines(path).stream().map(line -> {
            try { return json.readTree(line); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }).toList();
        assertThat(persisted).isNotEmpty().allSatisfy(node ->
                assertThat(node.path("publicationReason").asText()).isEqualTo("CAPABILITY_UNAVAILABLE"));
        assertThat(java.nio.file.Files.readString(path)).doesNotContain("factId", "Bearer", QUESTION);
        assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(out.snapshot().result().items()).singleElement().satisfies(item ->
                assertThat(item.issues()).singleElement().satisfies(issue ->
                        assertThat(issue.reasonCode()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE)));
        // 决策事件与核验事件都带同一个精确码；证据按事件如实记录，不做去重掩盖。
        var rejections = com.zhku.knowledge.ruleagent.web.RuleAgentPublicationRejectionEvidence.nodes(out);
        assertThat(rejections).as("events=%s", out.events()).isNotEmpty()
                .allSatisfy(node -> assertThat(node.path("publicationReason").asText())
                        .isEqualTo("CAPABILITY_UNAVAILABLE"));
        assertThat(rejections).anySatisfy(node ->
                assertThat(node.path("action").asText()).isEqualTo("PUBLICATION_REVIEW"));
    }

    @Test void promptsAndReviewContractAgreeWithPublicationEligibility() {
        assertThat(RuleAgentProvider.REVIEW_SYSTEM).contains("input.capabilities", "capabilities为空时bindings必须为空",
                "可读或可检索不等于可发布", "不能标IDENTIFIED+UNKNOWN", "UNSUPPORTED_DIMENSION");
        assertThat(RuleAgentProvider.SYSTEM).contains("DATA_GAP/UNSUPPORTED_DEMAND", "不要自报SCOPE_LIMIT",
                "已读事实模板本身标注原文缺详情");
        assertThat(RulePublicationProtocol.description().toString()).contains("bindingEligibility", "capabilities");
    }

    @Test void actualJournalIOExceptionRemainsAuditUnavailable() throws Exception {
        var out = rejectedOutcome();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var recorded = com.zhku.knowledge.ruleagent.web.RuleAgentPublicationRejectionEvidence.persist(out, node -> {
            writes.incrementAndGet();
            // 写向目录造成真实 IOException，而不是模拟内容守卫拒绝。
            java.nio.file.Files.writeString(temporary, node.toString());
        });
        assertThat(writes.get()).isEqualTo(1);
        assertThat(recorded.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(recorded.snapshot().result().items()).allSatisfy(item ->
                assertThat(item.issues()).singleElement().satisfies(issue ->
                        assertThat(issue.reasonCode()).isEqualTo(ReasonCode.AUDIT_UNAVAILABLE)));
        assertThat(recorded.snapshot().result().evidence()).isEmpty();
        assertThat(recorded.snapshot().execution().modelAttemptCount()).isEqualTo(out.snapshot().execution().modelAttemptCount());
    }
}
