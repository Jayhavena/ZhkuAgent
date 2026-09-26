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
import com.zhku.knowledge.telemetry.TelemetryRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** 可为 null：未装配遥测时不做任何记录（默认关闭）。**旁路**——写入失败不影响作答。 */
    private final TelemetryRecorder telemetry;

    private static final Logger LOG = LoggerFactory.getLogger(LocalPreviewConversationFacade.class);

    public LocalPreviewConversationFacade(
            ConversationStore store,
            LocalPreviewAnswerChain answerChain,
            Clock clock,
            Duration sessionTtl
    ) {
        this(store, answerChain, clock, sessionTtl, null, null, null);
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
        this(store, answerChain, clock, sessionTtl, guideCards, guideBindingKey, null);
    }

    /**
     * 完整装配（阶段 10 追加匿名遥测）。
     *
     * <p>{@code telemetry} 允许为 null：不装配即完全不记录。装配时它只是**旁路**——
     * 记录失败只会被吞掉并打一条 warn，作答一律照常返回（有测试锁定该约束）。</p>
     */
    public LocalPreviewConversationFacade(
            ConversationStore store,
            LocalPreviewAnswerChain answerChain,
            Clock clock,
            Duration sessionTtl,
            ServiceGuideCardProvider guideCards,
            String guideBindingKey,
            TelemetryRecorder telemetry
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
        this.telemetry = telemetry;
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
        long startedNanos = System.nanoTime();
        LocalPreviewAnswerChain.RoutedReply routed = answerChain.reply(sessionId, message);
        long elapsedNanos = System.nanoTime() - startedNanos;
        recordTelemetry(routed, elapsedNanos);
        return ConversationReply.from(routed, guideCardFor(routed));
    }

    /**
     * 记录一次匿名遥测（阶段 10）。三条边界：
     * ① **只落白名单维度**——字段集与 `PrivacyBoundary.anonymousTelemetry` 一致，实现侧还会再校验一次；
     * ② **旁路**——任何异常都被吞掉并降级为"本次不记录"，作答不受影响；
     * ③ **不记录会话异常**——未知/过期/取消的会话不是知识事件，记录它们只会污染链路统计。
     */
    private void recordTelemetry(LocalPreviewAnswerChain.RoutedReply routed, long elapsedNanos) {
        if (telemetry == null || isSessionIssue(routed.type())) {
            return;
        }
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("route", routed.route().name());
            fields.put("handoffRequired", routed.handoffRequired());
            fields.put("failureCategory", failureCategoryOf(routed));
            fields.put("latencyBucket", latencyBucketOf(elapsedNanos));
            fields.put("eventDate", LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString());
            // retrievalProfile / generatorProfile 留空：门面这层拿不到模型栈标识，
            // 而白名单允许缺省——宁可少两个维度，也不在这里拼一个猜出来的标识。
            telemetry.record(fields);
        } catch (RuntimeException exception) {
            LOG.warn("匿名遥测写入失败，已跳过本次记录（作答不受影响）：{}", exception.getMessage());
        }
    }

    private static boolean isSessionIssue(ElectiveRetakeReplyType type) {
        return type == ElectiveRetakeReplyType.SESSION_EXPIRED
                || type == ElectiveRetakeReplyType.SESSION_CANCELLED
                || type == ElectiveRetakeReplyType.SESSION_INVALID;
    }

    /**
     * 归因：**依据链路给出的两个显式事实**（是否有可用证据 / 生成是否降级），不用 route 反推。
     *
     * <p>为什么不能用 route 反推：生成侧降级与检索侧未召回是**两种不同的失败**，却都会落到同一个
     * 拒答路由上——按 route 判会把"有证据但模型没答成"记成"根本没召回"，归因表随之失去意义
     * （阶段 10 第 5 项要的正是"按层定位：未召回 / 候选或融合错误 / 证据对但表达错"）。</p>
     */
    static String failureCategoryOf(LocalPreviewAnswerChain.RoutedReply routed) {
        if (!routed.generationDegraded()) {
            return "NONE";
        }
        // 有证据仍降级＝生成侧问题；无可用证据＝检索侧没给出可用依据。
        return routed.retrievalEvidenceAvailable() ? "MODEL_DEGRADED" : "RETRIEVAL_MISS";
    }

    /** 耗时只落桶：精确毫秒与时间戳结合可参与指纹化，桶化后仅保留运维所需的分布。 */
    private static String latencyBucketOf(long elapsedNanos) {
        long millis = elapsedNanos / 1_000_000L;
        if (millis < 1_000L) {
            return "LT_1S";
        }
        if (millis < 3_000L) {
            return "S_1_3";
        }
        if (millis < 10_000L) {
            return "S_3_10";
        }
        return "GE_10S";
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
