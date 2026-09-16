package com.zhku.knowledge.notice;

import java.time.Instant;
import java.util.Objects;

/**
 * 一项学生可见的动态办理时间。
 *
 * <p>不允许半确认时间：少一个边界就不能安全给出“当前可办”的结论。</p>
 */
public record NoticeFact(
        NoticeFactType type,
        Instant startsAt,
        Instant endsAt,
        String officialEntryUrl,
        boolean startsAtConfirmed,
        boolean endsAtConfirmed
) {

    public NoticeFact {
        type = Objects.requireNonNull(type, "通知事实类型不能为空");
        if (startsAt == null || endsAt == null
                || !startsAtConfirmed || !endsAtConfirmed) {
            throw new IllegalArgumentException("通知事实必须有已确认的开始和结束时间");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("通知事实开始时间必须早于结束时间");
        }
        officialEntryUrl = OfficialWebSnapshot.requireHttpsUrl(
                officialEntryUrl,
                "通知事实必须提供 HTTPS 官方入口"
        );
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "当前时间不能为空");
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
