package com.zhku.knowledge.conversation.publicelective;

import java.util.Objects;

/** “每学期公共选修课最高 4 学分”规则的受限编排。 */
public final class PublicElectiveCreditLimitService {

    private static final String CREDIT_LIMIT_PHRASE = "每学期公共选修课最高不得超过 4 学分";
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final CreditLimitEvidenceFinder evidenceFinder;

    public PublicElectiveCreditLimitService(CreditLimitEvidenceFinder evidenceFinder) {
        this.evidenceFinder = Objects.requireNonNull(evidenceFinder, "证据检索器不能为空");
    }

    public CreditLimitReply handle(String message) {
        String normalized = normalize(message);
        if (!isCreditLimitQuestion(normalized)) {
            return CreditLimitReply.outOfScope();
        }
        return evidenceFinder.find()
                .filter(this::supportsCreditLimit)
                .map(evidence -> CreditLimitReply.answer(answerFrom(evidence, normalized)))
                .orElseGet(CreditLimitReply::evidenceUnavailable);
    }

    private CreditLimitAnswer answerFrom(CreditLimitEvidence evidence, String message) {
        String basis = "本回答只说明单学期公共选修课学分上限；个人培养方案与当期通知仍需核对。";
        if (isPersonalEligibilityQuestion(message)) {
            // 不能将用户口述的已选学分当作教务系统事实，从而承诺其仍可选课。
            basis += "系统无法依据口述确认个人已选学分、培养方案或当期选课限制，不承诺个人是否仍可选课。";
        }
        return new CreditLimitAnswer(
                "每学期公共选修课最高不得超过 4 学分。",
                basis,
                evidence.sourceId(),
                evidence.sourceTitle(),
                evidence.sourceVersion(),
                evidence.sourceLocator(),
                evidence.applicableScope()
        );
    }

    private boolean supportsCreditLimit(CreditLimitEvidence evidence) {
        return evidence.content().contains(CREDIT_LIMIT_PHRASE);
    }

    private static boolean isCreditLimitQuestion(String message) {
        boolean publicElective = message.contains("公共选修课")
                || message.contains("公共选修课程")
                || message.contains("公选课")
                || message.contains("公选");
        boolean termLimit = message.contains("每学期")
                || message.contains("一学期")
                || message.contains("这学期")
                || message.contains("最多")
                || message.contains("上限");
        return publicElective && message.contains("学分") && termLimit
                && !message.contains("网络公共选修");
    }

    private static boolean isPersonalEligibilityQuestion(String message) {
        return message.contains("我") && (message.contains("还能") || message.contains("可以再"));
    }

    private static String normalize(String message) {
        Objects.requireNonNull(message, "消息不能为空");
        String normalized = message.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("消息不能超过 500 个字符");
        }
        return normalized;
    }
}
