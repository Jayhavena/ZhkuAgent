package com.zhku.knowledge.retrieval;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 检索资格过滤器：只有"已发布、学生可见、分享权利已确认、未被替代、
 * 处于有效时态内且范围匹配"的资料，才允许被索引与召回。
 *
 * <p>该过滤器同时服务于索引任务与召回路径——两侧共用同一份判定逻辑，
 * 防止"索引时合格、召回时放松"或反之的口径漂移。</p>
 */
public final class RetrievalEligibilityFilter {

    private final Clock clock;

    public RetrievalEligibilityFilter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 评估资料是否具备检索资格。
     *
     * @param snapshot 资料状态快照
     * @param requiredScope 本次检索限定的知识范围；null 表示不限定
     */
    public EligibilityDecision evaluate(SourceEligibilitySnapshot snapshot, String requiredScope) {
        Objects.requireNonNull(snapshot, "资料快照不能为空");

        if (!"PUBLISHED".equals(snapshot.publicationStatus())) {
            return EligibilityDecision.rejected("资料未发布（当前状态：" + snapshot.publicationStatus() + "）");
        }
        if (!"STUDENT_VISIBLE".equals(snapshot.visibilityScope())) {
            return EligibilityDecision.rejected("资料仅限内部使用，不属于学生可见范围");
        }
        if (!snapshot.sharingRightsConfirmed()) {
            return EligibilityDecision.rejected("资料分享权利未确认");
        }
        if (snapshot.superseded()) {
            return EligibilityDecision.rejected("资料已被新版本替代");
        }
        Instant now = clock.instant();
        if (snapshot.validFrom() != null && now.isBefore(snapshot.validFrom())) {
            return EligibilityDecision.rejected("资料尚未生效（生效时间：" + snapshot.validFrom() + "）");
        }
        // validUntil 为排他上界：到期时刻即失效，避免过期通知在截止当天仍被当作办理依据。
        if (snapshot.validUntil() != null && !now.isBefore(snapshot.validUntil())) {
            return EligibilityDecision.rejected("资料已过期（失效时间：" + snapshot.validUntil() + "）");
        }
        if (requiredScope != null && !requiredScope.equals(snapshot.knowledgeScope())) {
            return EligibilityDecision.rejected(
                    "资料范围不符（检索范围：" + requiredScope + "，资料范围：" + snapshot.knowledgeScope() + "）");
        }
        return EligibilityDecision.accepted();
    }
}
