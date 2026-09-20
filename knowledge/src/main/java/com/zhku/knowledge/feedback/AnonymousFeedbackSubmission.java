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

    private static final Pattern GUIDE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    public AnonymousFeedbackSubmission {
        guideIdentifier = requireGuideValue(guideIdentifier, "清单标识格式不合法");
        guideVersion = requireGuideValue(guideVersion, "清单版本格式不合法");
        reason = Objects.requireNonNull(reason, "反馈原因不能为空");
        if (anonymousSessionHash == null || !SHA_256_HEX.matcher(anonymousSessionHash).matches()) {
            throw new IllegalArgumentException("匿名会话散列必须是 64 位十六进制值");
        }
        anonymousSessionHash = anonymousSessionHash.toLowerCase(Locale.ROOT);
    }

    private static String requireGuideValue(String value, String message) {
        if (value == null || !GUIDE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
