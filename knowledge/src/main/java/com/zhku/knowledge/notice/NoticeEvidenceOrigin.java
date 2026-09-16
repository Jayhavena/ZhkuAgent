package com.zhku.knowledge.notice;

import java.util.Objects;

/** 说明官方通知为何可信，避免将“校内正式”伪造成不存在的公网链接。 */
public record NoticeEvidenceOrigin(
        NoticeEvidenceChannel channel,
        String publicUrl,
        String issuingUnit,
        String deliveryChannel,
        String materialReference
) {
    public NoticeEvidenceOrigin {
        channel = Objects.requireNonNull(channel, "通知来源路径不能为空");
        if (channel == NoticeEvidenceChannel.PUBLIC_WEB) {
            publicUrl = OfficialWebSnapshot.requireHttpsUrl(publicUrl, "官方链接必须是 HTTPS 地址");
            issuingUnit = optional(issuingUnit);
            deliveryChannel = optional(deliveryChannel);
            materialReference = optional(materialReference);
        } else {
            if (publicUrl != null && !publicUrl.isBlank()) {
                throw new IllegalArgumentException("校内正式材料不能伪造公网链接");
            }
            publicUrl = null;
            issuingUnit = required(issuingUnit, "校内正式材料必须记录签发单位");
            deliveryChannel = required(deliveryChannel, "校内正式材料必须记录接收渠道");
            materialReference = required(materialReference, "校内正式材料必须记录材料标识");
        }
    }

    public static NoticeEvidenceOrigin publicWeb(String url) {
        return new NoticeEvidenceOrigin(NoticeEvidenceChannel.PUBLIC_WEB, url, null, null, null);
    }

    public static NoticeEvidenceOrigin internalOfficial(
            String issuingUnit, String deliveryChannel, String materialReference
    ) {
        return new NoticeEvidenceOrigin(NoticeEvidenceChannel.INTERNAL_OFFICIAL, null,
                issuingUnit, deliveryChannel, materialReference);
    }

    public String studentCitationLabel() {
        return channel == NoticeEvidenceChannel.PUBLIC_WEB
                ? "学校官网通知：" + publicUrl
                : "已审核校内正式通知：签发单位=" + issuingUnit
                + "；接收渠道=" + deliveryChannel + "；材料标识=" + materialReference;
    }

    private static String required(String value, String message) {
        String normalized = optional(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }
    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
