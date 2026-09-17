package com.zhku.knowledge.routing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一回答编排器：按冻结契约的五级顺序判定，严格短路。
 *
 * <p>判定顺序：澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工。
 * 前级命中即盖章返回，后级处理器根本不会被调用——"后级不得覆盖前级结论"
 * 由此在结构上成立，而不是依赖各级处理器自觉。</p>
 *
 * <p>编排器只做路由，不生成内容：各级回答由对应级别处理器给出，
 * 证据资格、引用白名单等校验发生在级别内部（检索增强侧由 AnswerEvidenceGuard 负责）。</p>
 */
public final class KnowledgeAnswerOrchestrator {

    private static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "当前没有可核验的已发布证据支持该问题的结论，请通过人工渠道咨询教务部门。";

    private final RoutingLevel clarification;
    private final RoutingLevel currentNotice;
    private final RoutingLevel highRiskRule;
    private final RoutingLevel retrievalAugmented;

    public KnowledgeAnswerOrchestrator(
            RoutingLevel clarification,
            RoutingLevel currentNotice,
            RoutingLevel highRiskRule,
            RoutingLevel retrievalAugmented
    ) {
        this.clarification = Objects.requireNonNull(clarification, "澄清级处理器不能为空");
        this.currentNotice = Objects.requireNonNull(currentNotice, "当期通知级处理器不能为空");
        this.highRiskRule = Objects.requireNonNull(highRiskRule, "高风险规则级处理器不能为空");
        this.retrievalAugmented = Objects.requireNonNull(retrievalAugmented, "检索增强级处理器不能为空");
    }

    public RoutedAnswer route(RoutingRequest request) {
        Objects.requireNonNull(request, "路由请求不能为空");

        Optional<LevelAnswer> answer = clarification.handle(request);
        if (answer.isPresent()) {
            return stamp(AnswerRoute.AWAITING_CLARIFICATION, answer.get());
        }
        answer = currentNotice.handle(request);
        if (answer.isPresent()) {
            return stamp(AnswerRoute.CURRENT_NOTICE, answer.get());
        }
        answer = highRiskRule.handle(request);
        if (answer.isPresent()) {
            return stamp(AnswerRoute.HIGH_RISK_RULE, answer.get());
        }
        answer = retrievalAugmented.handle(request);
        if (answer.isPresent()) {
            return stamp(AnswerRoute.RETRIEVAL_AUGMENTED, answer.get());
        }
        // 五级全部未命中：拒答并转人工。绝不返回无出处结论。
        return new RoutedAnswer(
                AnswerRoute.INSUFFICIENT_EVIDENCE,
                INSUFFICIENT_EVIDENCE_MESSAGE,
                List.of(),
                true
        );
    }

    private static RoutedAnswer stamp(AnswerRoute route, LevelAnswer answer) {
        return new RoutedAnswer(route, answer.message(), answer.citationIds(), answer.handoffRequired());
    }
}
