package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.StoreResult;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.serviceguide.HumanHandoffReason;
import com.zhku.knowledge.serviceguide.ServiceGuideCard;
import com.zhku.knowledge.serviceguide.ServiceGuideCardProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 本机预览的会话 HTTP 适配门面。
 *
 * <p>Controller 不得直接创建或更新领域状态，否则浏览器请求会绕过 revision 防护。该门面先验证
 * session 是否存在，再交给规则路由器，确保未知 ID 不会被一条 HTTP 消息隐式创建。</p>
 */
public final class LocalPreviewConversationFacade {

    private final ConversationStore store;
    private final LocalPreviewAnswerChain answerChain;
    private final Clock clock;
    private final Duration sessionTtl;
    /** 可为 null：未装配清单链路时，回答卡上的清单字段恒为 null，既有行为完全不变。 */
    private final ServiceGuideCardProvider guideCards;
    private final String guideBindingKey;

    public LocalPreviewConversationFacade(
            ConversationStore store,
            LocalPreviewAnswerChain answerChain,
            Clock clock,
            Duration sessionTtl
    ) {
        this(store, answerChain, clock, sessionTtl, null, null);
    }

    /**
     * 装配清单卡片的构造入口（只追加参数，旧构造签名保留并委托到这里）。
     *
     * <p>绑定键由装配侧显式给出（见 {@code ZhkuLocalPreviewConfiguration}），门面不做任何
     * "按问题猜清单"的推断——猜错会把不相干的清单当成依据发给学生。</p>
     */
    public LocalPreviewConversationFacade(
            ConversationStore store,
            LocalPreviewAnswerChain answerChain,
            Clock clock,
            Duration sessionTtl,
            ServiceGuideCardProvider guideCards,
            String guideBindingKey
    ) {
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.answerChain = Objects.requireNonNull(answerChain, "回答链路不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "会话有效期不能为空");
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("会话有效期必须为正数");
        }
        this.guideCards = guideCards;
        this.guideBindingKey = guideBindingKey == null || guideBindingKey.isBlank()
                ? null
                : guideBindingKey.strip();
    }

    public ConversationCreated createConversation() {
        Instant expiresAt = clock.instant().plus(sessionTtl);
        while (true) {
            UUID sessionId = UUID.randomUUID();
            ConversationState initial = ConversationState.active(sessionId, expiresAt);
            if (store.create(initial).outcome() == StoreResult.Outcome.CREATED) {
                return new ConversationCreated(sessionId, expiresAt);
            }
        }
    }

    public ConversationReply sendMessage(UUID sessionId, String message) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        StoreResult visible = store.find(sessionId, clock);
        if (visible.outcome() == StoreResult.Outcome.NOT_FOUND) {
            return sessionInvalid();
        }
        // 会话存在性由门面保证（未知 ID 不得被一条消息隐式创建），作答顺序交给回答链路。
        LocalPreviewAnswerChain.RoutedReply routed = answerChain.reply(sessionId, message);
        return ConversationReply.from(routed, guideCardFor(routed));
    }

    public ConversationReply cancelConversation(UUID sessionId) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        while (true) {
            StoreResult visible = store.find(sessionId, clock);
            switch (visible.outcome()) {
                case NOT_FOUND:
                    return sessionInvalid();
                case EXPIRED:
                    return reply(ElectiveRetakeReplyType.SESSION_EXPIRED, "当前会话已超时，请重新提问。");
                case CANCELLED:
                    return reply(ElectiveRetakeReplyType.SESSION_CANCELLED, "当前会话已取消，请重新发起问题。");
                case FOUND:
                    StoreResult cancelled = store.cancel(
                            sessionId,
                            visible.state().revision(),
                            clock
                    );
                    if (cancelled.outcome() == StoreResult.Outcome.STALE_WRITE) {
                        // 取消与消息并发时必须读取新版本后再取消，不能把陈旧 revision 当作成功。
                        continue;
                    }
                    if (cancelled.outcome() == StoreResult.Outcome.CANCELLED) {
                        return reply(ElectiveRetakeReplyType.SESSION_CANCELLED, "当前会话已取消，请重新发起问题。");
                    }
                    if (cancelled.outcome() == StoreResult.Outcome.EXPIRED) {
                        return reply(ElectiveRetakeReplyType.SESSION_EXPIRED, "当前会话已超时，请重新提问。");
                    }
                    if (cancelled.outcome() == StoreResult.Outcome.NOT_FOUND) {
                        return sessionInvalid();
                    }
                    return reply(ElectiveRetakeReplyType.SESSION_INVALID, "当前会话状态已变化，请重新提问。");
                default:
                    return reply(ElectiveRetakeReplyType.SESSION_INVALID, "当前会话状态已变化，请重新提问。");
            }
        }
    }

    private static ConversationReply sessionInvalid() {
        return new ConversationReply(
                ElectiveRetakeReplyType.SESSION_INVALID, "当前会话不存在，请新建对话。", null,
                AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of(), null);
    }

    private static ConversationReply reply(ElectiveRetakeReplyType type, String message) {
        return new ConversationReply(
                type, message, null, AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of(),
                null);
    }

    /**
     * 回答卡上的清单卡挂载（阶段 9，只追加）。三条例外都是刻意的：
     * ① 未装配清单链路（`guideCards`/绑定键为空）→ null，既有层级行为完全不变；
     * ② 会话类问题（过期/取消/无效）→ 不附卡，它与"办事依据"无关，附卡会被误读成办理指引；
     * ③ 规则级以外的无卡层级且未要求转人工 → 不附卡（检索增强级正常作答时不需要下一步指引）。
     */
    private ServiceGuideCard guideCardFor(LocalPreviewAnswerChain.RoutedReply routed) {
        if (guideCards == null || guideBindingKey == null) {
            return null;
        }
        boolean sessionIssue = switch (routed.type()) {
            case SESSION_EXPIRED, SESSION_CANCELLED, SESSION_INVALID -> true;
            default -> false;
        };
        if (sessionIssue) {
            return null;
        }
        if (routed.handoffRequired()) {
            // 检索增强级仍要转人工＝生成侧拒答/降级；其余转人工都归"规则缺失/证据不足"。
            HumanHandoffReason reason = routed.route() == AnswerRoute.RETRIEVAL_AUGMENTED
                    ? HumanHandoffReason.MODEL_REFUSAL
                    : HumanHandoffReason.RULE_MISSING;
            return guideCards.handoffCard(guideBindingKey, reason).orElse(null);
        }
        return routed.answer() == null
                ? null
                : guideCards.fullCard(guideBindingKey).orElse(null);
    }

    public record ConversationCreated(UUID sessionId, Instant expiresAt) {

        public ConversationCreated {
            sessionId = Objects.requireNonNull(sessionId, "会话编号不能为空");
            expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        }
    }

    /**
     * 会话回复（HTTP 响应体）。
     *
     * <p>字段语义：{@code type/message/answer} 沿用既有语义（只追加原则）；
     * {@code route} 为五级路由结论、{@code handoffRequired} 表示需转人工渠道、
     * {@code citationIds} 为服务端为本轮获准证据分配的不透明引用标识、
     * {@code citations} 为同一批标识的来源明细（标题/定位/页码），供前端渲染引用卡。
     * 后四者为本阶段新增的**只追加**字段；{@code citations} 与 {@code citationIds}
     * 长度恒等且顺序一致。</p>
     */
    public record ConversationReply(
            ElectiveRetakeReplyType type,
            String message,
            ConversationAnswer answer,
            AnswerRoute route,
            boolean handoffRequired,
            List<String> citationIds,
            List<LocalPreviewAnswerChain.CitationView> citations,
            ServiceGuideCard guideCard
    ) {

        public ConversationReply {
            citationIds = List.copyOf(citationIds);
            citations = List.copyOf(citations);
        }

        /**
         * 兼容构造器（只追加原则的落点）：历史调用方按原 7 参构造（如 SSE 流式处理），
         * 语义完全不变，清单卡为 null。**不要求他线改代码是本字段"只追加"的应有之义。**
         */
        public ConversationReply(
                ElectiveRetakeReplyType type,
                String message,
                ConversationAnswer answer,
                AnswerRoute route,
                boolean handoffRequired,
                List<String> citationIds,
                List<LocalPreviewAnswerChain.CitationView> citations
        ) {
            this(type, message, answer, route, handoffRequired, citationIds, citations, null);
        }

        private static ConversationReply from(
                LocalPreviewAnswerChain.RoutedReply routed, ServiceGuideCard guideCard) {
            ElectiveRetakeAnswer sourceAnswer = routed.answer();
            ConversationAnswer answer = sourceAnswer == null ? null : ConversationAnswer.from(sourceAnswer);
            return new ConversationReply(
                    routed.type(),
                    routed.message(),
                    answer,
                    routed.route(),
                    routed.handoffRequired(),
                    routed.citationIds(),
                    routed.citations(),
                    guideCard
            );
        }
    }

    /** 统一回答卡（HTTP 响应体 `answer` 对象）：字段语义不变（只追加原则），清单指引另见 `ConversationReply.guideCard`。 */
    public record ConversationAnswer(
            String conclusion,
            String basis,
            UUID sourceId,
            String sourceTitle,
            String sourceVersion,
            String sourceLocator,
            String applicableScope
    ) {

        private static ConversationAnswer from(ElectiveRetakeAnswer answer) {
            return new ConversationAnswer(
                    answer.conclusion(),
                    answer.basis(),
                    answer.sourceId(),
                    answer.sourceTitle(),
                    answer.sourceVersion(),
                    answer.sourceLocator(),
                    answer.applicableScope()
            );
        }
    }
}
