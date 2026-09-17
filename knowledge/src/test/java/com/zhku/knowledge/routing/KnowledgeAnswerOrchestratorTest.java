package com.zhku.knowledge.routing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 五级路由顺序的契约测试：澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工。
 *
 * <p>该顺序与"严格短路"语义已冻结（SYNC-STATUS 第五节）：
 * 前级命中即返回，后级处理器不得被调用，更不得覆盖前级结论。</p>
 */
class KnowledgeAnswerOrchestratorTest {

    private static final RoutingRequest REQUEST = new RoutingRequest("本学期选课什么时候结束？");

    @Test
    void shouldRouteToClarificationWhenPendingClarificationExists() {
        TrackingLevel notice = TrackingLevel.hit("通知回答");
        TrackingLevel rule = TrackingLevel.hit("规则回答");
        TrackingLevel retrieval = TrackingLevel.hit("检索回答");
        KnowledgeAnswerOrchestrator orchestrator = new KnowledgeAnswerOrchestrator(
                request -> Optional.of(new LevelAnswer("请补充你的年级", List.of(), false)),
                notice, rule, retrieval
        );

        RoutedAnswer answer = orchestrator.route(REQUEST);

        assertThat(answer.route()).isEqualTo(AnswerRoute.AWAITING_CLARIFICATION);
        assertThat(answer.message()).isEqualTo("请补充你的年级");
        assertThat(answer.handoffRequired()).isFalse();
        // 严格短路：澄清命中后，后三级一律不得执行。
        notice.assertNeverInvoked();
        rule.assertNeverInvoked();
        retrieval.assertNeverInvoked();
    }

    @Test
    void shouldRouteToCurrentNoticeBeforeRulesAndRetrieval() {
        TrackingLevel rule = TrackingLevel.hit("规则回答");
        TrackingLevel retrieval = TrackingLevel.hit("检索回答");
        KnowledgeAnswerOrchestrator orchestrator = new KnowledgeAnswerOrchestrator(
                TrackingLevel.miss(),
                request -> Optional.of(new LevelAnswer("退改选截止 9 月 15 日", List.of("N1"), false)),
                rule, retrieval
        );

        RoutedAnswer answer = orchestrator.route(REQUEST);

        assertThat(answer.route()).isEqualTo(AnswerRoute.CURRENT_NOTICE);
        assertThat(answer.citationIds()).containsExactly("N1");
        rule.assertNeverInvoked();
        retrieval.assertNeverInvoked();
    }

    @Test
    void shouldRouteToHighRiskRuleBeforeRetrieval() {
        TrackingLevel retrieval = TrackingLevel.hit("检索回答");
        KnowledgeAnswerOrchestrator orchestrator = new KnowledgeAnswerOrchestrator(
                TrackingLevel.miss(),
                TrackingLevel.miss(),
                request -> Optional.of(new LevelAnswer("缓考不办理，须重修", List.of("R7"), false)),
                retrieval
        );

        RoutedAnswer answer = orchestrator.route(REQUEST);

        assertThat(answer.route()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(answer.citationIds()).containsExactly("R7");
        retrieval.assertNeverInvoked();
    }

    @Test
    void shouldRouteToRetrievalAugmentedWhenEarlierLevelsMiss() {
        KnowledgeAnswerOrchestrator orchestrator = new KnowledgeAnswerOrchestrator(
                TrackingLevel.miss(),
                TrackingLevel.miss(),
                TrackingLevel.miss(),
                request -> Optional.of(new LevelAnswer("根据已发布资料……", List.of("C1", "C2"), false))
        );

        RoutedAnswer answer = orchestrator.route(REQUEST);

        assertThat(answer.route()).isEqualTo(AnswerRoute.RETRIEVAL_AUGMENTED);
        assertThat(answer.citationIds()).containsExactly("C1", "C2");
        assertThat(answer.handoffRequired()).isFalse();
    }

    @Test
    void shouldRefuseWithHandoffWhenNoLevelHasEvidence() {
        KnowledgeAnswerOrchestrator orchestrator = new KnowledgeAnswerOrchestrator(
                TrackingLevel.miss(),
                TrackingLevel.miss(),
                TrackingLevel.miss(),
                TrackingLevel.miss()
        );

        RoutedAnswer answer = orchestrator.route(REQUEST);

        assertThat(answer.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        // 无证据时必须拒答并给出人工渠道，不得让后级编造结论。
        assertThat(answer.handoffRequired()).isTrue();
        assertThat(answer.citationIds()).isEmpty();
        assertThat(answer.message()).isNotBlank();
    }

    @Test
    void shouldRejectBlankMessage() {
        KnowledgeAnswerOrchestrator orchestrator = allMiss();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> orchestrator.route(new RoutingRequest("   ")));
        assertThatNullPointerException()
                .isThrownBy(() -> orchestrator.route(new RoutingRequest(null)));
    }

    @Test
    void shouldRejectNullLevels() {
        assertThatNullPointerException()
                .isThrownBy(() -> new KnowledgeAnswerOrchestrator(
                        null, TrackingLevel.miss(), TrackingLevel.miss(), TrackingLevel.miss()));
    }

    private static KnowledgeAnswerOrchestrator allMiss() {
        return new KnowledgeAnswerOrchestrator(
                TrackingLevel.miss(), TrackingLevel.miss(), TrackingLevel.miss(), TrackingLevel.miss());
    }

    /** 记录调用次数的路由处理器，用于验证严格短路：命中前级后本处理器不应再被调用。 */
    private static final class TrackingLevel implements RoutingLevel {

        private final Optional<LevelAnswer> result;
        private final List<RoutingRequest> invocations = new ArrayList<>();

        private TrackingLevel(Optional<LevelAnswer> result) {
            this.result = result;
        }

        static TrackingLevel hit(String message) {
            return new TrackingLevel(Optional.of(new LevelAnswer(message, List.of(), false)));
        }

        static TrackingLevel miss() {
            return new TrackingLevel(Optional.empty());
        }

        @Override
        public Optional<LevelAnswer> handle(RoutingRequest request) {
            invocations.add(request);
            return result;
        }

        void assertNeverInvoked() {
            assertThat(invocations).as("严格短路：前级命中后本处理器不应被调用").isEmpty();
        }
    }
}
