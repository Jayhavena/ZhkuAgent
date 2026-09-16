package com.zhku.knowledge.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 不可变的会话状态快照。
 *
 * <p>{@code pendingIntent} 与 {@code pendingField} 必须成对出现，否则调用方无法判断
 * 系统正在完成什么意图、还缺少什么信息。取消状态始终清空这对字段，防止旧请求借由
 * 普通更新路径重新激活已取消会话。</p>
 */
public record ConversationState(
        UUID sessionId,
        ConversationStatus status,
        PendingIntent pendingIntent,
        PendingField pendingField,
        Instant expiresAt,
        long revision
) {

    public ConversationState {
        sessionId = Objects.requireNonNull(sessionId, "会话编号不能为空");
        status = Objects.requireNonNull(status, "会话状态不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        if (revision < 0) {
            throw new IllegalArgumentException("会话版本不能为负数");
        }
        if ((pendingIntent == null) != (pendingField == null)) {
            throw new IllegalArgumentException("待补意图和待补字段必须同时存在或同时为空");
        }
        if (status == ConversationStatus.CANCELLED && pendingIntent != null) {
            throw new IllegalArgumentException("已取消会话不能保留待补信息");
        }
    }

    /** 创建尚未进入追问流程的有效会话。 */
    public static ConversationState active(UUID sessionId, Instant expiresAt) {
        return new ConversationState(
                sessionId,
                ConversationStatus.ACTIVE,
                null,
                null,
                expiresAt,
                0
        );
    }

    /**
     * 写入一次完整的待补信息并形成新版本。
     *
     * <p>已取消会话不允许走此路径，具体存储层再用 revision 拦截晚到请求。</p>
     */
    public ConversationState withPending(
            PendingIntent nextPendingIntent,
            PendingField nextPendingField,
            Instant nextExpiresAt
    ) {
        requireActive();
        return new ConversationState(
                sessionId,
                status,
                Objects.requireNonNull(nextPendingIntent, "待补意图不能为空"),
                Objects.requireNonNull(nextPendingField, "待补字段不能为空"),
                Objects.requireNonNull(nextExpiresAt, "过期时间不能为空"),
                nextRevision()
        );
    }

    /**
     * 清理已成功补齐的待补信息并形成新版本。
     *
     * <p>没有待补信息时禁止刷新过期时间，确保阶段 2 的滑动过期只发生在真实成功写入上。</p>
     */
    public ConversationState clearPending(Instant nextExpiresAt) {
        requireActive();
        if (pendingIntent == null) {
            throw new IllegalStateException("当前会话没有待补信息");
        }
        return new ConversationState(
                sessionId,
                status,
                null,
                null,
                Objects.requireNonNull(nextExpiresAt, "过期时间不能为空"),
                nextRevision()
        );
    }

    /**
     * 取消会话并清除待补信息。
     *
     * <p>重复取消直接返回同一快照以保持幂等；取消不刷新有效期，过期清理仍可回收该记录。</p>
     */
    public ConversationState cancel() {
        if (status == ConversationStatus.CANCELLED) {
            return this;
        }
        return new ConversationState(
                sessionId,
                ConversationStatus.CANCELLED,
                null,
                null,
                expiresAt,
                nextRevision()
        );
    }

    /** 使用可控时钟判断到期边界，避免领域逻辑读取真实系统时间。 */
    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "时钟不能为空");
        return !clock.instant().isBefore(expiresAt);
    }

    private void requireActive() {
        if (status != ConversationStatus.ACTIVE) {
            throw new IllegalStateException("已取消会话不能更新待补信息");
        }
    }

    private long nextRevision() {
        return Math.incrementExact(revision);
    }
}
