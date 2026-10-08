package com.zhku.knowledge.rag;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 回答证据守卫：模型输出下发前的最后一道校验。
 *
 * <p>检查正文、分值、非空引用及本轮引用白名单，并保留转人工和特定办理短语的降级规则。
 * 短语检查仅覆盖列举的表达，不验证一般事实、数字、条件或例外；合法引用不能证明结论正确。</p>
 */
public final class AnswerEvidenceGuard {

    /** 沿用既有自报分值下限；不是经校准的正确概率。阈值调整须登记证据账本。 */
    static final double MIN_UNCERTAINTY = 0.5;

    /** 含这些特征的文本不能支持下列办理短语；不构成完整注入防护或资料入库资格检查。 */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "忽略之前的指令", "忽略以上指令", "ignore previous instructions", "ignore all instructions"
    );

    /** 既有办理断言短语表；同文命中仅是窄范围检查，不证明个人资格或办理已完成。 */
    private static final List<String> OUTCOME_ASSERTIONS = List.of(
            "登记成功", "办理成功", "已为你", "可以免修", "已批准", "已通过审核"
    );

    /**
     * 校验模型输出。
     *
     * @param response 模型原始输出
     * @param approved 本轮获准证据（白名单）
     * @return 拒绝原因；通过时为空
     */
    public Optional<String> verify(ModelResponse response, List<ApprovedEvidence> approved) {
        Objects.requireNonNull(response, "模型输出不能为空");
        Objects.requireNonNull(approved, "获准证据列表不能为空");

        // 调用方可以直接构造record，不能把解析器作为唯一边界，否则NaN和空引用仍可绕过。
        if (response.answer().isBlank()) {
            return Optional.of("模型回答不能为空白");
        }
        double score = response.uncertainty();
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            return Optional.of("模型自报分值必须是0至1的有限数");
        }
        if (response.citationIds().isEmpty()
                || response.citationIds().stream().anyMatch(String::isBlank)) {
            return Optional.of("模型回答必须包含非空白的引用标识");
        }

        Set<String> whitelist = new HashSet<>();
        for (ApprovedEvidence evidence : approved) {
            whitelist.add(evidence.citationId());
        }
        for (String citationId : response.citationIds()) {
            if (!whitelist.contains(citationId)) {
                return Optional.of("模型输出了不属于本轮获准证据的引用标识");
            }
        }

        if (response.handoffRequired()) {
            return Optional.of("模型自身要求转人工");
        }
        if (response.uncertainty() < MIN_UNCERTAINTY) {
            return Optional.of("模型置信度低于下限");
        }

        Set<String> citedIds = new HashSet<>(response.citationIds());
        List<String> supportTexts = approved.stream()
                .filter(e -> citedIds.contains(e.citationId()))
                .map(ApprovedEvidence::text)
                .filter(text -> !containsInjection(text))
                .toList();
        for (String assertion : OUTCOME_ASSERTIONS) {
            if (response.answer().contains(assertion)
                    && supportTexts.stream().noneMatch(text -> text.contains(assertion))) {
                return Optional.of("回答包含超出证据支持范围的办理结果断言");
            }
        }
        return Optional.empty();
    }

    private static boolean containsInjection(String text) {
        String lowered = text.toLowerCase(java.util.Locale.ROOT);
        return INJECTION_PATTERNS.stream().anyMatch(p -> lowered.contains(p.toLowerCase(java.util.Locale.ROOT)));
    }
}
