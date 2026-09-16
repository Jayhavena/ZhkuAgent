package com.zhku.knowledge.notice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 已进入通知治理域的可追溯通知快照。
 *
 * <p>当前性同时依赖发布状态、学生可见权利、替代状态和时钟窗口；不能只根据最新标题判断。</p>
 */
public record PublishedNotice(
        UUID noticeId,
        UUID sourceId,
        String sourceTitle,
        String sourceVersion,
        String applicableScope,
        AcademicTerm academicTerm,
        NoticeType type,
        NoticeLifecycleStatus status,
        Instant validFrom,
        Instant validTo,
        OfficialWebSnapshot officialSnapshot,
        boolean studentVisible,
        boolean sharingRightsConfirmed,
        List<NoticeFact> facts
) {

    public PublishedNotice {
        noticeId = Objects.requireNonNull(noticeId, "通知编号不能为空");
        sourceId = Objects.requireNonNull(sourceId, "来源编号不能为空");
        sourceTitle = requireText(sourceTitle, "通知来源标题不能为空");
        sourceVersion = requireText(sourceVersion, "通知来源版本不能为空");
        applicableScope = requireText(applicableScope, "通知适用范围不能为空");
        academicTerm = Objects.requireNonNull(academicTerm, "通知学期不能为空");
        type = Objects.requireNonNull(type, "通知类型不能为空");
        status = Objects.requireNonNull(status, "通知生命周期不能为空");
        validFrom = Objects.requireNonNull(validFrom, "通知生效开始时间不能为空");
        validTo = Objects.requireNonNull(validTo, "通知生效结束时间不能为空");
        if (!validFrom.isBefore(validTo)) {
            throw new IllegalArgumentException("通知生效开始时间必须早于结束时间");
        }
        facts = List.copyOf(Objects.requireNonNull(facts, "通知事实不能为空"));
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("已发布通知至少需要一项审核事实");
        }
        if (studentVisible) {
            if (!sharingRightsConfirmed) {
                throw new IllegalArgumentException("学生可见通知必须确认分享权利");
            }
            if (officialSnapshot == null) {
                throw new IllegalArgumentException("学生可见通知必须保留官方网页快照");
            }
        }
    }

    /** 供不需要展示来源标题的纯领域测试保留的简化构造方式。 */
    public PublishedNotice(
            UUID noticeId,
            UUID sourceId,
            AcademicTerm academicTerm,
            NoticeType type,
            NoticeLifecycleStatus status,
            Instant validFrom,
            Instant validTo,
            OfficialWebSnapshot officialSnapshot,
            boolean studentVisible,
            boolean sharingRightsConfirmed,
            List<NoticeFact> facts
    ) {
        this(
                noticeId,
                sourceId,
                "已审核官方通知",
                academicTerm.value(),
                academicTerm.value() + " 学期适用",
                academicTerm,
                type,
                status,
                validFrom,
                validTo,
                officialSnapshot,
                studentVisible,
                sharingRightsConfirmed,
                facts
        );
    }

    public boolean isCurrent(Clock clock) {
        Objects.requireNonNull(clock, "时钟不能为空");
        Instant now = clock.instant();
        return status == NoticeLifecycleStatus.PUBLISHED
                && studentVisible
                && sharingRightsConfirmed
                && officialSnapshot != null
                && !now.isBefore(validFrom)
                && now.isBefore(validTo);
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
