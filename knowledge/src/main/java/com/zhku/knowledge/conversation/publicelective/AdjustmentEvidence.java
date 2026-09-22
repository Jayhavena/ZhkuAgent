package com.zhku.knowledge.conversation.publicelective;

import java.util.UUID;

/**
 * "选课调整办理"的可回答证据：一条已发布、学生可见、带条级定位的规则片段。
 *
 * <p>回答卡必须能追溯到具体条款（{@code sourceLocator}）与资料版本（{@code sourceVersion}），
 * 因此证据里必须携带这两项，不能只给片段正文——否则学生无法核对我们答的是哪一条。</p>
 */
public record AdjustmentEvidence(
        UUID sourceId,
        String sourceTitle,
        String sourceVersion,
        String sourceLocator,
        String applicableScope,
        String content
) {
}
