package com.zhku.knowledge.feedback;

import java.time.LocalDate;
import java.util.Objects;

/** 管理员可见的不可识别统计行；刻意不包含任何逐条反馈资料。 */
public record FeedbackAggregate(
        String guideIdentifier,
        String guideVersion,
        FeedbackReason reason,
        LocalDate feedbackDate,
        long count
) {

    public FeedbackAggregate {
        Objects.requireNonNull(guideIdentifier, "清单标识不能为空");
        Objects.requireNonNull(guideVersion, "清单版本不能为空");
        Objects.requireNonNull(reason, "反馈原因不能为空");
        Objects.requireNonNull(feedbackDate, "反馈日期不能为空");
        if (count <= 0) {
            throw new IllegalArgumentException("反馈聚合计数必须为正数");
        }
    }
}
