package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.PendingIntent;
import com.zhku.knowledge.conversation.StoreResult;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.notice.CourseSelectionNoticeService;
import com.zhku.knowledge.rag.ComposedAnswer;
import com.zhku.knowledge.rag.ComposedAnswerCitation;
import com.zhku.knowledge.rag.ComposedAnswerStatus;
import com.zhku.knowledge.rag.RetrievalAnswer;
import com.zhku.knowledge.rag.RetrievalAugmentedAnswerService;
import com.zhku.knowledge.routing.AnswerRoute;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 本机预览的回答链路装配：按冻结的五级顺序（澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工）
 * 依次判定，命中即返回，严格短路。
 *
 * <p>装配取向：**不重造既有能力**。澄清与规则两级复用既有 {@link PublicElectiveConversationRouter}
 * （其内部已实现"待澄清优先、通知优先于静态规则"），当期通知级单列以取得精确路由结论，
 * 检索增强级接阶段 8 的受控检索链路；任一级命中即返回，后级不再执行。</p>
 *
 * <p>与既有实现的差异只在"可见信息"：本链路额外产出 route / handoffRequired / citationIds
 * 三个**只追加**字段，旧字段（type/message/answer）语义不变。阶段 10 追加两个
 * **归因事实**（不对外暴露、不进 HTTP 契约）：{@code retrievalEvidenceAvailable}
 * 与 {@code generationDegraded}——遥测归因必须依据它们，而不是靠 route 反推。</p>
 */
public final class LocalPreviewAnswerChain {

    private static final String REFUSAL_MESSAGE =
            "当前没有可核验的已发布证据支持该问题的结论，请通过人工渠道咨询教务部门。";

    private final ConversationStore store;
    private final PublicElectiveConversationRouter conversationRouter;
    private final CourseSelectionNoticeService noticeService;
    private final RetrievalAugmentedAnswerService retrievalService;
    private final Clock clock;

    public LocalPreviewAnswerChain(
            ConversationStore store,
            PublicElectiveConversationRouter conversationRouter,
            CourseSelectionNoticeService noticeService,
            RetrievalAugmentedAnswerService retrievalService,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.conversationRouter = Objects.requireNonNull(conversationRouter, "会话规则路由不能为空");
        this.noticeService = noticeService;
        this.retrievalService = retrievalService;
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public RoutedReply reply(UUID sessionId, String message) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(message, "消息不能为空");

        // 第一级：澄清优先——存在待澄清意图时，由澄清服务决定继续追问还是给出结论。
        if (hasPendingClarification(sessionId)) {
            ElectiveRetakeReply clarificationReply = conversationRouter.route(sessionId, message);
            AnswerRoute route = clarificationReply.type() == ElectiveRetakeReplyType.NEED_CLARIFICATION
                    ? AnswerRoute.AWAITING_CLARIFICATION
                    : routeOf(clarificationReply.type());
            return routed(route, clarificationReply, route != AnswerRoute.INSUFFICIENT_EVIDENCE);
        }

        // 第二级：当期通知优先于静态规则，避免旧手册被当作当期办理依据。
        if (noticeService != null) {
            Optional<RoutedReply> noticeReply = noticeLevel(message);
            if (noticeReply.isPresent()) {
                return noticeReply.get();
            }
        }

        // 第三级：高风险规则。
        ElectiveRetakeReply ruleReply = conversationRouter.route(sessionId, message);
        if (ruleReply.type() != ElectiveRetakeReplyType.OUT_OF_SCOPE) {
            AnswerRoute route = routeOf(ruleReply.type());
            return routed(route, ruleReply, route != AnswerRoute.INSUFFICIENT_EVIDENCE);
        }

        // 第四级：检索增强（受控检索 + 受控生成；未配置时整级跳过）。
        // 这里必须记下**检索侧是否真的给出了可用证据**：生成侧降级与检索侧未召回是两种不同的失败，
        // 只看 route 无法区分（两者都落到拒答），归因会错。
        boolean retrievalEvidenceAvailable = false;
        if (retrievalService != null) {
            Optional<RetrievalAnswer> retrieval = retrievalService.attemptWithCitations(message);
            if (retrieval.isPresent()) {
                // 引用明细来自检索侧：非空即"检索给出了可用证据"，与生成结论无关。
                retrievalEvidenceAvailable = !retrieval.get().citations().isEmpty();
                if (retrieval.get().composed().status() == ComposedAnswerStatus.ANSWERED) {
                    ComposedAnswer answer = retrieval.get().composed();
                    return new RoutedReply(
                            ElectiveRetakeReplyType.ANSWER,
                            answer.answer(),
                            null,
                            AnswerRoute.RETRIEVAL_AUGMENTED,
                            answer.handoffRequired(),
                            answer.citationIds(),
                            retrieval.get().citations().stream().map(CitationView::from).toList(),
                            retrievalEvidenceAvailable,
                            false
                    );
                }
            }
        }

        // 第五级：拒答 + 转人工。绝不返回无出处结论。
        // 注意：type 沿用既有语义（OUT_OF_SCOPE = "不在可作答范围"），不因链路改造而变化；
        // 新的路由结论落在 route/handoffRequired 两个只追加字段里。
        // 归因事实沿用第四级的观测：若无证据则 RETRIEVAL_MISS，有证据但生成侧没给出结论则 MODEL_DEGRADED。
        return new RoutedReply(
                ElectiveRetakeReplyType.OUT_OF_SCOPE,
                REFUSAL_MESSAGE,
                null,
                AnswerRoute.INSUFFICIENT_EVIDENCE,
                true,
                List.of(),
                List.of(),
                retrievalEvidenceAvailable,
                true
        );
    }

    private boolean hasPendingClarification(UUID sessionId) {
        StoreResult visible = store.find(sessionId, clock);
        return visible.outcome() == StoreResult.Outcome.FOUND
                && visible.state().pendingIntent() == PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE;
    }

    private Optional<RoutedReply> noticeLevel(String message) {
        ElectiveRetakeReply noticeReply = noticeService.handle(message);
        if (noticeReply.type() == ElectiveRetakeReplyType.OUT_OF_SCOPE) {
            return Optional.empty();
        }
        // 通知级明确"无有效证据"时必须短路为拒答+转人工，不能回落给规则级或检索级。
        AnswerRoute route = noticeReply.type() == ElectiveRetakeReplyType.ANSWER
                ? AnswerRoute.CURRENT_NOTICE
                : routeOf(noticeReply.type());
        return Optional.of(routed(route, noticeReply, route != AnswerRoute.INSUFFICIENT_EVIDENCE));
    }

    private static AnswerRoute routeOf(ElectiveRetakeReplyType type) {
        return switch (type) {
            case NEED_CLARIFICATION -> AnswerRoute.AWAITING_CLARIFICATION;
            case ANSWER -> AnswerRoute.HIGH_RISK_RULE;
            case EVIDENCE_UNAVAILABLE, SESSION_EXPIRED, SESSION_CANCELLED, SESSION_INVALID ->
                    AnswerRoute.INSUFFICIENT_EVIDENCE;
            case OUT_OF_SCOPE -> throw new IllegalStateException("越范围回复不应进入路由结论");
        };
    }

    /**
     * 规则/通知/澄清三级的统一封装。
     *
     * @param evidenceAvailable 该级是否取到了可用证据。规则/通知级没有"生成降级"这一说：
     *                          有证据就是正常作答（或正常追问），无证据就是资料不足。
     */
    private static RoutedReply routed(
            AnswerRoute route, ElectiveRetakeReply reply, boolean evidenceAvailable) {
        boolean degraded = route == AnswerRoute.INSUFFICIENT_EVIDENCE
                && reply.type() != ElectiveRetakeReplyType.SESSION_EXPIRED
                && reply.type() != ElectiveRetakeReplyType.SESSION_CANCELLED
                && reply.type() != ElectiveRetakeReplyType.SESSION_INVALID;
        return new RoutedReply(
                reply.type(),
                reply.message(),
                reply.answer(),
                route,
                isHandoff(route, reply.type()),
                List.of(),
                List.of(),
                evidenceAvailable,
                degraded
        );
    }

    private static boolean isHandoff(AnswerRoute route, ElectiveRetakeReplyType type) {
        return route == AnswerRoute.INSUFFICIENT_EVIDENCE
                || type == ElectiveRetakeReplyType.SESSION_EXPIRED
                || type == ElectiveRetakeReplyType.SESSION_CANCELLED
                || type == ElectiveRetakeReplyType.SESSION_INVALID;
    }

    /**
     * 一条已定路由的回答（HTTP 门面据此渲染响应，只追加字段）。
     *
     * <p>{@code retrievalEvidenceAvailable} / {@code generationDegraded} 是阶段 10 追加的
     * **归因事实**：只供遥测归因使用，**不进入任何用户可见契约**（HTTP 响应体仍只有
     * type/message/answer/route/handoffRequired/citationIds/citations）。</p>
     */
    public record RoutedReply(
            ElectiveRetakeReplyType type,
            String message,
            com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer answer,
            AnswerRoute route,
            boolean handoffRequired,
            List<String> citationIds,
            List<CitationView> citations,
            boolean retrievalEvidenceAvailable,
            boolean generationDegraded
    ) {

        public RoutedReply {
            Objects.requireNonNull(type, "回复类型不能为空");
            Objects.requireNonNull(route, "路由结论不能为空");
            citationIds = List.copyOf(citationIds);
            citations = List.copyOf(citations);
        }

        /**
         * 兼容构造器（只追加原则）：未提供归因事实的调用方按旧签名构造，
         * 两个事实取中性默认值（无证据 / 未降级），行为与追加前一致。
         */
        public RoutedReply(
                ElectiveRetakeReplyType type,
                String message,
                com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer answer,
                AnswerRoute route,
                boolean handoffRequired,
                List<String> citationIds,
                List<CitationView> citations
        ) {
            this(type, message, answer, route, handoffRequired, citationIds, citations,
                    false, false);
        }
    }

    /**
     * 引用明细视图（HTTP 展示用；不含片段正文，避免把整段资料塞进响应）。
     */
    public record CitationView(
            String citationId,
            String sourceTitle,
            String sourceLocator,
            Integer pageNumber
    ) {

        static CitationView from(ComposedAnswerCitation citation) {
            return new CitationView(
                    citation.citationId(),
                    citation.sourceTitle(),
                    citation.sourceLocator(),
                    citation.pageNumber()
            );
        }
    }
}
