package com.zhku.knowledge.feedback;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 匿名反馈的受控输入。
 *
 * <p>清单标识和版本只是跨 V15 的软引用；这里不读取办事清单，也不把用户身份
 * 当作校验依据，防止反馈链路意外扩大为身份或资料治理入口。</p>
 */
public record AnonymousFeedbackSubmission(
        String guideIdentifier,
        String guideVersion,
        FeedbackReason reason,
        String anonymousSessionHash
) {

    private static final Pattern GUIDE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern GUIDE_VERSION = Pattern.compile("[\\p{L}\\p{N}._-]{1,128}");
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    public AnonymousFeedbackSubmission {
        guideIdentifier = requireGuideIdentifier(guideIdentifier);
        guideVersion = requireGuideVersion(guideVersion);
        reason = Objects.requireNonNull(reason, "反馈原因不能为空");
        if (anonymousSessionHash == null || !SHA_256_HEX.matcher(anonymousSessionHash).matches()) {
            throw new IllegalArgumentException("匿名会话散列必须是 64 位十六进制值");
        }
        anonymousSessionHash = anonymousSessionHash.toLowerCase(Locale.ROOT);
    }

    private static String requireGuideIdentifier(String value) {
        if (value == null || !GUIDE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("清单标识格式不合法");
        }
        return value;
    }

    private static String requireGuideVersion(String value) {
        // 版本是已发布清单的软引用，不能因 ASCII 限制拒绝合法的“2025版”等真实版本。
        if (value == null || !GUIDE_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("清单版本格式不合法");
        }
        return value;
    }
}
