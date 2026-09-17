package com.zhku.knowledge.retrieval;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一份资料在检索资格判定时刻的状态快照。
 *
 * <p>刻意做成与表结构解耦的扁平快照：判定所需的全部事实由仓储层一次性取齐，
 * 过滤器本身不再触库，保证索引侧与召回侧对同一份资料得到一致的结论。</p>
 *
 * @param superseded 是否已被新版本替代（含已归档）；被替代资料即使曾对学生可见也不可召回
 * @param validFrom 时态下界（含），null 表示不设下界
 * @param validUntil 时态上界（不含），null 表示不设上界
 */
public record SourceEligibilitySnapshot(
        UUID sourceId,
        String publicationStatus,
        String visibilityScope,
        boolean sharingRightsConfirmed,
        boolean superseded,
        String knowledgeScope,
        Instant validFrom,
        Instant validUntil
) {

    public SourceEligibilitySnapshot {
        Objects.requireNonNull(sourceId, "资料标识不能为空");
        Objects.requireNonNull(publicationStatus, "发布状态不能为空");
        Objects.requireNonNull(visibilityScope, "可见范围不能为空");
        Objects.requireNonNull(knowledgeScope, "知识范围不能为空");
    }
}
