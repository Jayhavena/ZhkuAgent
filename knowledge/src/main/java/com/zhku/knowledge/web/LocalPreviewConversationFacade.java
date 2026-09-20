package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.StoreResult;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.serviceguide.ServiceGuideCard;

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

    public LocalPreviewConversationFacade(
            ConversationStore store,
            LocalPreviewAnswerChain answerChain,
            Clock clock,
            Duration sessionTtl
    ) {
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.answerChain = Objects.requireNonNull(answerChain, "回答链路不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "会话有效期不能为空");
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("会话有效期必须为正数");
        }
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
        return ConversationReply.from(answerChain.reply(sessionId, message));
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
                AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of());
    }

    private static ConversationReply reply(ElectiveRetakeReplyType type, String message) {
        return new ConversationReply(
                type, message, null, AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of());
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
            List<LocalPreviewAnswerChain.CitationView> citations
    ) {

        public ConversationReply {
            citationIds = List.copyOf(citationIds);
            citations = List.copyOf(citations);
        }

        private static ConversationReply from(LocalPreviewAnswerChain.RoutedReply routed) {
            ElectiveRetakeAnswer sourceAnswer = routed.answer();
            ConversationAnswer answer = sourceAnswer == null ? null : ConversationAnswer.from(sourceAnswer);
            return new ConversationReply(
                    routed.type(),
                    routed.message(),
                    answer,
                    routed.route(),
                    routed.handoffRequired(),
                    routed.citationIds(),
                    routed.citations()
            );
        }
    }

    /**
     * 统一回答卡（HTTP 响应体 `answer` 对象）。
     *
     * <p>旧字段 `{conclusion, basis, sourceId, sourceTitle, sourceVersion, sourceLocator,
     * applicableScope}` 语义不变（只追加原则）；后三个数组为阶段 9 **只追加**字段，
     * 来自已审核办事清单：`guideNextSteps`＝下一步（含是否需个案核对）、
     * `guideConfirmations`＝需确认事项（必须以当期官方信息核对的内容）、
     * `guideOfficialChannels`＝官方人工渠道（类型/名称/值，只来自审核资料）。
     * 尚未接清单链路的层级为 `null`；高风险或证据不足时由服务端只填人工渠道与
     * 需确认事项、`guideNextSteps` 恒为空数组。</p>
     */
    public record ConversationAnswer(
            String conclusion,
            String basis,
            UUID sourceId,
            String sourceTitle,
            String sourceVersion,
            String sourceLocator,
            String applicableScope,
            List<ServiceGuideCard.NextStep> guideNextSteps,
            List<String> guideConfirmations,
            List<ServiceGuideCard.Channel> guideOfficialChannels
    ) {

        private static ConversationAnswer from(ElectiveRetakeAnswer answer) {
            return new ConversationAnswer(
                    answer.conclusion(),
                    answer.basis(),
                    answer.sourceId(),
                    answer.sourceTitle(),
                    answer.sourceVersion(),
                    answer.sourceLocator(),
                    answer.applicableScope(),
                    null,
                    null,
                    null);
        }

        /**
         * 清单增强卡：旧字段取自既有回答，新字段取自已审核清单视图。
         * 清单视图为空时返回原卡（新字段为 null），保证未接清单的层级行为完全不变。
         */
        private static ConversationAnswer withServiceGuide(
                ElectiveRetakeAnswer answer, ServiceGuideCard card) {
            ConversationAnswer base = from(answer);
            if (card == null) {
                return base;
            }
            return new ConversationAnswer(
                    base.conclusion(),
                    base.basis(),
                    base.sourceId(),
                    base.sourceTitle(),
                    base.sourceVersion(),
                    base.sourceLocator(),
                    base.applicableScope(),
                    card.nextSteps(),
                    card.confirmations(),
                    card.officialChannels());
        }
    }
}
