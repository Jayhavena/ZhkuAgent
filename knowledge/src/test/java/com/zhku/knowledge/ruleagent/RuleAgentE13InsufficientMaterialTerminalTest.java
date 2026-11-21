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
import static com.zhku.knowledge.ruleagent.RuleAgentContract.ReasonCode;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.Search;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.TaskStatus;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.DemandSpan;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.REVISION;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.SemanticAssessment;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.UnresolvedDemand;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.UnresolvedReason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E13“今晚补选几点关？帮我补上”的离线终态：正常查证完成但缺少当期依据时，
 * 既不能凭历史条款编造截止时间或声称已代办，也不能把资料不足伪装成技术失败。
 * 不连接网络、不启动数据库、不调用模型或 Embedding。
 */
class RuleAgentE13InsufficientMaterialTerminalTest {
    static final String QUESTION = "这学期公选补选今晚几点关？现在还能帮我补上吗？";
    static final String RULE = "S06/ELECTION_TIME";
    static final String FACT = "学生应在学校规定时间内完成网上选课和补选。";
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final UUID sourceId = UUID.randomUUID(), chunkId = UUID.randomUUID();
    final RuleCheckRuleTemplate.EvidenceBinding binding = new RuleCheckRuleTemplate.EvidenceBinding(
            sourceId, chunkId, "2025.9.2", "8D892CB3476CB71AB88A58A692811F02D37B6C2B645783118ECAB3CC0DB8AC3C", 0, FACT.length(), FACT);
    final RuleCheckRuleTemplate template = new RuleCheckRuleTemplate(RULE, "2025.9.2",
            RuleCheckRuleTemplate.ReviewState.APPROVED, "local-fixture", LocalDate.of(2025, 9, 2),
            List.of(new RuleCheckRuleTemplate.Fact("TIME", FACT, true, Map.of(), List.of(binding))),
            List.of(), List.of(), List.of(), List.of());
    final RuleCheckApplicationService.CatalogEntry entry = new RuleCheckApplicationService.CatalogEntry(template,
            "仅解释 2025.9.2 的一般选课时间规则", new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),
            RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
    final Owner owner = new Owner(new UUID(0, 11), new UUID(0, 12));
    final UUID taskId = UUID.randomUUID();
    final RuleAgentRunner.Input input = new RuleAgentRunner.Input(owner, taskId, 3L, QUESTION,
            List.of(new RuleAgentRunner.Question("q1", QUESTION)), Map.of());

    RuleAgentEvidenceTools tools() { return tools(true); }

    RuleAgentEvidenceTools tools(boolean match) {
        var candidate = new RetrievedChunk(binding.chunkId(), binding.quote(), "LOCAL", "LOCAL", 1,
                binding.revision(), binding.checksum(), binding.sourceId());
        var current = new RuleCheckEvidenceSnapshot("LOCAL", binding.sourceId(), binding.chunkId(),
                binding.revision(), binding.checksum(), binding.quote(), true);
        return new RuleAgentEvidenceTools(owner, taskId,
                new RuleCheckRuntimeSettings("p", "c", "s", "2025.9.2", 20, 5),
                () -> List.of(entry), (query, limit) -> match ? List.of(candidate) : List.of(),
                (snapshot, candidate1) -> Optional.of(current), audit -> { }, Clock.systemUTC());
    }

    RuleAgentRunner.DecisionPort decision(UnresolvedReason reason) {
        // 模型在资料不足时按协议提出数据缺口，而不是编造事实；理解未完成时只能如实申报检索未命中。
        ReasonCode proposed = reason == UnresolvedReason.UNSUPPORTED_DIMENSION
                ? ReasonCode.UNSUPPORTED_DEMAND : ReasonCode.RETRIEVAL_NO_MATCH;
        return (view, timeout) -> {
            try {
                NextAction action = view.observations().isEmpty()
                        ? new Search("q1", "公选 补选 时间")
                        : new Finish(List.of(new Proposal("q1", List.of(),
                                List.of(new RuleAgentContract.ProposedIssue(IssueKind.DATA_GAP, proposed)), null)));
                return new RuleAgentRunner.Reply(json.writeValueAsString(action), false);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        };
    }

    RuleSemanticReviewPort review(UnresolvedReason reason) {
        return new RuleSemanticReviewPort() {
            @Override
            public SemanticAssessment assess(RulePublicationContract.SemanticInput safe, Duration timeout) {
                return new SemanticAssessment(REVISION, safe.inputFingerprint(), List.of(), List.of(),
                        List.of(new UnresolvedDemand(new DemandSpan("q1", 0, QUESTION.length()), reason)));
            }
        };
    }

    RuleAgentRunner.Outcome run(UnresolvedReason reason) {
        return new RuleAgentRunner(decision(reason), new RuleActionBroker(tools(reason != UnresolvedReason.UNCLASSIFIED_DEMAND)), Clock.systemUTC(), review(reason))
                .run(AgentRunContext.create(QUESTION), input);
    }

    @Test
    void unsupportedRealtimeDemandEndsAsDataGapRatherThanTechnicalFailure() {
        var out = run(UnresolvedReason.UNSUPPORTED_DIMENSION);
        var snapshot = out.snapshot();
        assertThat(snapshot.taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(snapshot.runState()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.INCOMPLETE);
        assertThat(snapshot.result().coverage()).isEqualTo(RuleAgentContract.Coverage.NONE);
        var item = snapshot.result().items().getFirst();
        assertThat(item.claims()).isEmpty();
        assertThat(item.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.kind()).isEqualTo(IssueKind.DATA_GAP);
            assertThat(issue.reasonCode()).isEqualTo(ReasonCode.UNSUPPORTED_DEMAND);
            assertThat(issue.message()).contains("当期").doesNotContain("处理失败", "资料不存在");
            assertThat(issue.nextStep()).contains("教务", "代办");
        });
    }

    @Test
    void unresolvedUnderstandingStillFailsClosed() {
        var out = run(UnresolvedReason.UNCLASSIFIED_DEMAND);
        var snapshot = out.snapshot();
        assertThat(snapshot.taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(snapshot.runState()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.FAILED);
        var item = snapshot.result().items().getFirst();
        assertThat(item.claims()).isEmpty();
        // 理解失败仍按技术失败收敛；检索未命中的数据缺口可以同时存在，不能被它掩盖为“正常资料不足”。
        assertThat(item.issues()).anySatisfy(issue -> {
            assertThat(issue.reasonCode()).isEqualTo(ReasonCode.QUESTION_UNDERSTANDING_UNRESOLVED);
            assertThat(issue.kind()).isEqualTo(IssueKind.PROCESSING_FAILURE);
        });
        assertThat(item.issues()).noneSatisfy(issue ->
                assertThat(issue.reasonCode()).isEqualTo(ReasonCode.UNSUPPORTED_DEMAND));
    }

    @Test
    void dataGapTerminalDoesNotRepeatRetrievalOrLoop() {
        var out = run(UnresolvedReason.UNSUPPORTED_DIMENSION);
        assertThat(out.events()).hasSizeLessThanOrEqualTo(3);
        assertThat(out.events().stream().filter(event -> event.action().equals("SEARCH_REVIEWED_RULES")))
                .hasSize(1);
        assertThat(out.snapshot().execution().modelAttemptCount()).isLessThanOrEqualTo(3);
    }
    @Test
    void savedMixedReviewPassesGuardAndRealPublicationPipeline() throws Exception {
        var content = unsupportedReview(true);
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(Map.of("choices",
                List.of(Map.of("message", Map.of("content", content.toString())))));
        // 仅修改本地副本：两个独立需求都明确声明自身能力边界，不能借第一个片段掩盖第二个。

        RuleSemanticReviewPort reviewer = (safe, timeout) -> {
            try {
                content.put("inputFingerprint", safe.inputFingerprint());
                ((com.fasterxml.jackson.databind.node.ObjectNode) response.path("choices").get(0).path("message")).put("content", content.toString());
                com.zhku.knowledge.ruleagent.web.RuleAgentE13Policy.reviewBoundary(response);
                return RulePublicationProtocol.decode(content.toString(), safe);
            } catch (Exception e) { throw new IllegalStateException(e); }
        };
        var out = new RuleAgentRunner(decision(UnresolvedReason.UNSUPPORTED_DIMENSION), new RuleActionBroker(tools()), Clock.systemUTC(), reviewer)
                .run(AgentRunContext.create(QUESTION), input);
        assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(out.snapshot().runState()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.INCOMPLETE);
        assertThat(out.snapshot().result().coverage()).isEqualTo(RuleAgentContract.Coverage.NONE);
        assertThat(out.snapshot().result().items()).singleElement().satisfies(item -> {
            assertThat(item.claims()).isEmpty();
            assertThat(item.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.kind()).isEqualTo(IssueKind.DATA_GAP);
                assertThat(issue.reasonCode()).isEqualTo(ReasonCode.UNSUPPORTED_DEMAND);
            });
        });
    }

    com.fasterxml.jackson.databind.node.ObjectNode unsupportedReview(boolean coverSecond) throws Exception {
        var content = RuleAgentE13PublicationCapabilityTest.savedReview();
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("bindings")).removeAll();
        var second = (com.fasterxml.jackson.databind.node.ObjectNode) content.path("requirements").get(1);
        second.put("dimension", "UNKNOWN").put("subject", "UNSPECIFIED").put("recognition", "UNCLASSIFIED");
        if (coverSecond) ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("unresolvedDemands"))
                .add(json.valueToTree(Map.of("span", Map.of("subquestionId", "q1", "excerpt", "现在还能帮我补上吗"),
                        "reason", "UNSUPPORTED_DIMENSION")));
        return content;
    }

    @Test void oneUnsupportedSpanCannotBlessAnotherUnclassifiedDemand() throws Exception {
        var content = unsupportedReview(false);
        var response = json.valueToTree(Map.of("choices", List.of(Map.of("message", Map.of("content", content.toString())))));
        assertThatThrownBy(() -> com.zhku.knowledge.ruleagent.web.RuleAgentE13Policy.reviewBoundary(response))
                .isInstanceOf(IllegalStateException.class);
        // 即使绕过测试宿主守卫，生产 Gate + Publisher 也不能把缺自身覆盖的需求发布成正常资料不足。
        RuleSemanticReviewPort reviewer = (safe, timeout) -> {
            content.put("inputFingerprint", safe.inputFingerprint());
            return RulePublicationProtocol.decode(content.toString(), safe);
        };
        var out = new RuleAgentRunner(decision(UnresolvedReason.UNCLASSIFIED_DEMAND),
                new RuleActionBroker(tools(false)), Clock.systemUTC(), reviewer).run(AgentRunContext.create(QUESTION), input);
        assertThat(out.snapshot().taskStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(out.snapshot().result().items().getFirst().issues()).anySatisfy(issue ->
                assertThat(issue.reasonCode()).isEqualTo(ReasonCode.QUESTION_UNDERSTANDING_UNRESOLVED));
        assertThat(out.snapshot().result().items().getFirst().claims()).isEmpty();
    }

    @Test
    void partialOrMixedGapCannotBecomeResolvedDataGapThroughRunnerAndPublisher() throws Exception {
        for (boolean mixed : List.of(false,true)) {
            var content = RuleAgentE13PublicationCapabilityTest.savedReview();
            ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("bindings")).removeAll();
            if (mixed) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("unresolvedDemands")).add(
                    json.valueToTree(Map.of("span", Map.of("subquestionId","q1",
                        "excerpt","现在还能帮我补上吗"),"reason","AMBIGUOUS_SCOPE")));
            } else {
                ((com.fasterxml.jackson.databind.node.ObjectNode) content.path("unresolvedDemands").get(0).path("span"))
                    .put("excerpt","今晚");
            }
            RuleSemanticReviewPort reviewer = (safe,timeout) -> {
                content.put("inputFingerprint",safe.inputFingerprint());
                return RulePublicationProtocol.decode(content.toString(),safe);
            };
            var out = new RuleAgentRunner(decision(UnresolvedReason.UNCLASSIFIED_DEMAND),
                new RuleActionBroker(tools(false)),Clock.systemUTC(),reviewer)
                .run(AgentRunContext.create(QUESTION),input);
            assertThat(out.snapshot().taskStatus()).as("mixed="+mixed).isEqualTo(TaskStatus.FAILED);
            assertThat(out.snapshot().result().items().getFirst().issues()).anySatisfy(issue ->
                assertThat(issue.reasonCode()).isEqualTo(ReasonCode.QUESTION_UNDERSTANDING_UNRESOLVED));
            assertThat(out.snapshot().result().items().getFirst().issues()).noneSatisfy(issue ->
                assertThat(issue.reasonCode()).isEqualTo(ReasonCode.UNSUPPORTED_DEMAND));
            assertThat(out.snapshot().result().items().getFirst().claims()).isEmpty();
        }
    }

    /**
     * 决策提示曾让模型自报 SCOPE_LIMIT/CURRENT_APPLICABILITY_UNKNOWN，但发布器并不接受该组合。
     * 该拒绝行为保持：未获允许的适用性声明不能被发布成资料不足，也不能反过来变成技术成功。
     */
    @Test
    void selfReportedApplicabilityLimitIsRejectedByPublisher() {
        var capabilities = RuleFactCapabilityCatalogue.fromReviewed(List.of(entry));
        var safe = RulePublicationContract.input(input, List.of(), capabilities);
        var assessment = review(UnresolvedReason.UNSUPPORTED_DIMENSION).assess(safe, Duration.ofSeconds(1));
        var proposal = new Finish(List.of(new Proposal("q1", List.of(),
                List.of(new RuleAgentContract.ProposedIssue(IssueKind.SCOPE_LIMIT,
                        ReasonCode.CURRENT_APPLICABILITY_UNKNOWN)), null)));
        var coverage = new RulePublicationGate(capabilities).assess(input, proposal, assessment, List.of());
        assertThatThrownBy(() -> new RuleResultPublisher(new RuleActionBroker(tools()), input,
                UUID.randomUUID(), coverage).finish(proposal))
                .isInstanceOfSatisfying(RuleAgentRunner.Rejected.class,
                        rejected -> assertThat(rejected.reason).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE));
    }

}
