package com.zhku.knowledge.notice;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * 审核时保存的官方网页或 PDF 快照描述。
 *
 * <p>官方链接会变化，学生答案引用审核快照的哈希和抓取时间，才能复现当时依据。</p>
 */
public record OfficialWebSnapshot(
        String officialUrl,
        String storageKey,
        String sha256,
        Instant capturedAt,
        String mediaType,
        NoticeEvidenceOrigin origin
) {

    public OfficialWebSnapshot {
        origin = Objects.requireNonNull(origin, "通知来源依据不能为空");
        if (origin.channel() == NoticeEvidenceChannel.PUBLIC_WEB) {
            officialUrl = requireHttpsUrl(officialUrl, "官方链接必须是 HTTPS 地址");
            if (!officialUrl.equals(origin.publicUrl())) throw new IllegalArgumentException("快照链接必须与来源链接一致");
        } else if (officialUrl != null && !officialUrl.isBlank()) {
            throw new IllegalArgumentException("校内正式材料不能伪造公网链接");
        } else officialUrl = null;
        storageKey = requireStorageKey(storageKey);
        sha256 = requireChecksum(sha256);
        capturedAt = Objects.requireNonNull(capturedAt, "快照抓取时间不能为空");
        mediaType = requireText(mediaType, "快照媒体类型不能为空");
        if (!"text/html".equals(mediaType)
                && !"application/pdf".equals(mediaType)) {
            throw new IllegalArgumentException("快照媒体类型只能是 HTML 或 PDF");
        }
    }

    public OfficialWebSnapshot(String officialUrl, String storageKey, String sha256,
                               Instant capturedAt, String mediaType) {
        this(officialUrl, storageKey, sha256, capturedAt, mediaType,
                NoticeEvidenceOrigin.publicWeb(officialUrl));
    }

    static String requireHttpsUrl(String value, String message) {
        String normalized = requireText(value, message);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message, exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String requireStorageKey(String value) {
        String normalized = requireText(value, "快照存储标识不能为空");
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("快照存储标识不能包含路径分隔符");
        }
        return normalized;
    }

    private static String requireChecksum(String value) {
        String normalized = requireText(value, "快照摘要不能为空");
        if (!normalized.matches("^[A-F0-9]{64}$")) {
            throw new IllegalArgumentException("快照摘要必须是 64 位大写十六进制");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
