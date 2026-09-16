package com.zhku.knowledge.conversation.elective;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStatus;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.PendingField;
import com.zhku.knowledge.conversation.PendingIntent;
import com.zhku.knowledge.conversation.StoreResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * “选修课不及格能否重修”的首条两轮制度问答编排。
 *
 * <p>它只处理一个意图和一个待补字段。课程类型或证据无法可靠确认时宁可追问或拒答，
 * 避免把公共选修课的例外规则错误套用到专业选修课。</p>
 */
public final class ElectiveRetakeConversationService {

    private static final String CLARIFICATION = "你说的是公共选修课，还是专业选修课？";
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ConversationStore store;
    private final ElectiveRetakeEvidenceFinder evidenceFinder;
    private final Clock clock;
    private final Duration sessionTtl;

    public ElectiveRetakeConversationService(
            ConversationStore store,
            ElectiveRetakeEvidenceFinder evidenceFinder,
            Clock clock,
            Duration sessionTtl
    ) {
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.evidenceFinder = Objects.requireNonNull(evidenceFinder, "证据检索器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "会话有效期不能为空");
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("会话有效期必须为正数");
        }
    }

    public ElectiveRetakeReply handle(UUID sessionId, String message) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        String normalizedMessage = normalizeMessage(message);
        StoreResult result = store.find(sessionId, clock);
        return switch (result.outcome()) {
            case NOT_FOUND -> handleNewMessage(sessionId, normalizedMessage);
            case FOUND -> handleExistingMessage(sessionId, normalizedMessage, result.state());
            case EXPIRED -> reply(ElectiveRetakeReplyType.SESSION_EXPIRED, "当前会话已超时，请重新提问。", null);
            case CANCELLED -> reply(ElectiveRetakeReplyType.SESSION_CANCELLED, "当前会话已取消，请重新发起问题。", null);
            default -> reply(ElectiveRetakeReplyType.SESSION_INVALID, "当前会话状态已变化，请重新提问。", null);
        };
    }

    private ElectiveRetakeReply handleNewMessage(UUID sessionId, String message) {
        if (!isElectiveRetakeQuestion(message)) {
            return reply(ElectiveRetakeReplyType.OUT_OF_SCOPE, "当前问题不属于已支持的选修课重修咨询范围。", null);
        }
        Optional<CourseType> courseType = resolveCourseType(message);
        if (courseType.isPresent()) {
            return answerFor(courseType.get());
        }

        Instant expiresAt = nextExpiresAt();
        ConversationState initial = ConversationState.active(sessionId, expiresAt);
        StoreResult created = store.create(initial);
        if (created.outcome() != StoreResult.Outcome.CREATED) {
            return reply(ElectiveRetakeReplyType.SESSION_INVALID, "当前会话状态已变化，请重新提问。", null);
        }
        return saveCourseTypePending(sessionId, initial.revision(), expiresAt);
    }

    private ElectiveRetakeReply handleActiveMessageWithoutPending(
            UUID sessionId,
            String message,
            ConversationState state
    ) {
        if (!isElectiveRetakeQuestion(message)) {
            return reply(ElectiveRetakeReplyType.OUT_OF_SCOPE, "当前问题不属于已支持的选修课重修咨询范围。", null);
        }
        Optional<CourseType> courseType = resolveCourseType(message);
        if (courseType.isPresent()) {
            return answerFor(courseType.get());
        }
        return saveCourseTypePending(sessionId, state.revision(), nextExpiresAt());
    }

    private ElectiveRetakeReply saveCourseTypePending(
            UUID sessionId,
            long expectedRevision,
            Instant expiresAt
    ) {
        StoreResult pending = store.savePending(
                sessionId,
                expectedRevision,
                PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE,
                PendingField.COURSE_TYPE,
                expiresAt,
                clock
        );
        if (pending.outcome() == StoreResult.Outcome.UPDATED) {
            return reply(ElectiveRetakeReplyType.NEED_CLARIFICATION, CLARIFICATION, null);
        }
        return replyForWriteFailure(pending.outcome());
    }

    private ElectiveRetakeReply handleExistingMessage(
            UUID sessionId,
            String message,
            ConversationState state
    ) {
        if (state.status() != ConversationStatus.ACTIVE) {
            return reply(ElectiveRetakeReplyType.SESSION_CANCELLED, "当前会话已取消，请重新发起问题。", null);
        }
        if (state.pendingIntent() == null) {
            return handleActiveMessageWithoutPending(sessionId, message, state);
        }
        Optional<CourseType> courseType = resolveCourseType(message);
        if (courseType.isPresent()
                && (isStandaloneCourseTypeReply(message, courseType.get())
                || isElectiveRetakeQuestion(message))) {
            ElectiveRetakeReply answer = answerFor(courseType.get());
            if (answer.type() != ElectiveRetakeReplyType.ANSWER) {
                return answer;
            }
            StoreResult cleared = store.clearPending(
                    sessionId,
                    state.revision(),
                    nextExpiresAt(),
                    clock
            );
            if (cleared.outcome() == StoreResult.Outcome.UPDATED) {
                return answer;
            }
            return replyForWriteFailure(cleared.outcome());
        }
        if (message.contains("选修") || isElectiveRetakeQuestion(message)) {
            return reply(ElectiveRetakeReplyType.NEED_CLARIFICATION, CLARIFICATION, null);
        }
        return reply(ElectiveRetakeReplyType.OUT_OF_SCOPE, "请先回答当前澄清问题，或重新发起其他问题。", null);
    }

    private ElectiveRetakeReply answerFor(CourseType courseType) {
        Optional<ElectiveRetakeEvidence> evidence = evidenceFinder.find(courseType)
                .filter(item -> supportsCourseType(item, courseType));
        if (evidence.isEmpty()) {
            return reply(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE, "当前没有可确认的有效依据，无法给出确定性结论。", null);
        }
        ElectiveRetakeEvidence source = evidence.get();
        ElectiveRetakeAnswer answer = switch (courseType) {
            case PUBLIC_ELECTIVE -> new ElectiveRetakeAnswer(
                    "公共选修课成绩不合格，不安排重修。",
                    "适用于公共选修课成绩不合格的情形；本回答只说明重修安排。",
                    source.sourceId(), source.sourceTitle(), source.sourceVersion(),
                    source.sourceLocator(), source.applicableScope()
            );
            case PROFESSIONAL_ELECTIVE -> new ElectiveRetakeAnswer(
                    "专业选修课成绩不合格，可申请重修学习。",
                    "“可申请”不表示一定获批、一定开课或最终一定取得合格成绩。",
                    source.sourceId(), source.sourceTitle(), source.sourceVersion(),
                    source.sourceLocator(), source.applicableScope()
            );
        };
        return reply(ElectiveRetakeReplyType.ANSWER, answer.conclusion(), answer);
    }

    private static boolean supportsCourseType(ElectiveRetakeEvidence evidence, CourseType courseType) {
        return switch (courseType) {
            case PUBLIC_ELECTIVE -> evidence.content().contains("公共选修课不办理缓考")
                    && evidence.content().contains("成绩不合格者不安排重修");
            case PROFESSIONAL_ELECTIVE -> evidence.content().contains("专业选修课成绩不合格者可申请重修学习");
        };
    }

    private ElectiveRetakeReply replyForWriteFailure(StoreResult.Outcome outcome) {
        return switch (outcome) {
            case EXPIRED -> reply(ElectiveRetakeReplyType.SESSION_EXPIRED, "当前会话已超时，请重新提问。", null);
            case CANCELLED -> reply(ElectiveRetakeReplyType.SESSION_CANCELLED, "当前会话已取消，请重新发起问题。", null);
            default -> reply(ElectiveRetakeReplyType.SESSION_INVALID, "当前会话状态已变化，请重新提问。", null);
        };
    }

    private Instant nextExpiresAt() {
        return clock.instant().plus(sessionTtl);
    }

    private static Optional<CourseType> resolveCourseType(String message) {
        boolean publicElective = message.contains("公共选修") || message.contains("公选课") || message.contains("公选");
        boolean professionalElective = message.contains("专业选修") || message.contains("专选课") || message.contains("专选");
        if (publicElective == professionalElective) {
            return Optional.empty();
        }
        return Optional.of(publicElective ? CourseType.PUBLIC_ELECTIVE : CourseType.PROFESSIONAL_ELECTIVE);
    }

    /**
     * 待澄清时只接受课程类别本身，或保留完整“挂科后重修”语义的新问法。
     *
     * <p>不能因为一条新问题含“公选课”就视为补齐字段，否则“公选课怎么退课”会被错误回答成
     * 重修规则。首期交互约定的简短回复仅为两种课程类别名称。</p>
     */
    private static boolean isStandaloneCourseTypeReply(String message, CourseType courseType) {
        return switch (courseType) {
            case PUBLIC_ELECTIVE -> "公选课".equals(message) || "公共选修课".equals(message);
            case PROFESSIONAL_ELECTIVE -> "专业选修课".equals(message) || "专选课".equals(message);
        };
    }

    private static boolean isElectiveRetakeQuestion(String message) {
        boolean elective = message.contains("选修");
        boolean failure = message.contains("不及格") || message.contains("挂科") || message.contains("没及格");
        return elective && failure && message.contains("重修");
    }

    private static String normalizeMessage(String message) {
        Objects.requireNonNull(message, "消息不能为空");
        String normalized = message.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("消息不能超过 500 个字符");
        }
        return normalized;
    }

    private static ElectiveRetakeReply reply(
            ElectiveRetakeReplyType type,
            String message,
            ElectiveRetakeAnswer answer
    ) {
        return new ElectiveRetakeReply(type, message, answer);
    }
}
