package com.zhku.knowledge.feedback;

import java.time.Instant;
import java.util.Objects;

/** 仅供仓储使用的短期匿名事件；它不属于任何面向客户端或管理员的响应模型。 */
public record AnonymousFeedbackEvent(
        String guideIdentifier,
        String guideVersion,
        FeedbackReason reason,
        String anonymousSessionHash,
        String dedupeKey,
        Instant createdAt,
        Instant expiresAt
) {

    public AnonymousFeedbackEvent {
        Objects.requireNonNull(guideIdentifier, "清单标识不能为空");
        Objects.requireNonNull(guideVersion, "清单版本不能为空");
        Objects.requireNonNull(reason, "反馈原因不能为空");
        Objects.requireNonNull(anonymousSessionHash, "匿名会话散列不能为空");
        Objects.requireNonNull(dedupeKey, "去重键不能为空");
        Objects.requireNonNull(createdAt, "创建时刻不能为空");
        Objects.requireNonNull(expiresAt, "到期时刻不能为空");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("反馈事件到期时刻必须晚于创建时刻");
        }
    }
}
