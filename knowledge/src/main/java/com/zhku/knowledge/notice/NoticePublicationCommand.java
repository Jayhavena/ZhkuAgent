package com.zhku.knowledge.notice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 本机管理员在发布已审核官方通知时补充的时态事实。 */
public record NoticePublicationCommand(
        AcademicTerm academicTerm,
        Instant validFrom,
        Instant validTo,
        String officialUrl,
        List<NoticeFact> facts,
        NoticeEvidenceOrigin origin
) {

    public NoticePublicationCommand(AcademicTerm academicTerm, Instant validFrom,
                                    Instant validTo, String officialUrl, List<NoticeFact> facts) {
        this(academicTerm, validFrom, validTo, officialUrl, facts,
                NoticeEvidenceOrigin.publicWeb(officialUrl));
    }

    public NoticePublicationCommand {
        academicTerm = Objects.requireNonNull(academicTerm, "通知学期不能为空");
        validFrom = Objects.requireNonNull(validFrom, "通知生效开始时间不能为空");
        validTo = Objects.requireNonNull(validTo, "通知生效结束时间不能为空");
        if (!validFrom.isBefore(validTo)) {
            throw new IllegalArgumentException("通知生效开始时间必须早于结束时间");
        }
        origin = Objects.requireNonNull(origin, "通知来源依据不能为空");
        if (origin.channel() == NoticeEvidenceChannel.PUBLIC_WEB) {
            officialUrl = requireHttpsUrl(officialUrl, "官方链接必须是 HTTPS 地址");
            if (!officialUrl.equals(origin.publicUrl())) throw new IllegalArgumentException("通知链接必须与来源链接一致");
        } else if (officialUrl != null && !officialUrl.isBlank()) {
            throw new IllegalArgumentException("校内正式材料不能伪造公网链接");
        } else officialUrl = null;
        facts = List.copyOf(Objects.requireNonNull(facts, "通知事实不能为空"));
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("通知至少需要一项已确认办理事实");
        }
    }

    private static String requireHttpsUrl(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).strip();
        if (!normalized.startsWith("https://")) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
