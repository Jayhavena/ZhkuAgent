package com.zhku.knowledge.rag;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 回答证据守卫：模型输出下发前的最后一道校验。
 *
 * <p>三条确定性校验，任一不过即整答降级：
 * ① 引用白名单——citationIds 必须全部属于本轮获准证据；
 * ② 置信下限——低置信结论不展示给学生；
 * ③ 原子结论支持范围——回答中的办理结果断言必须能在所引证据（剔除注入嫌疑文本后）
 * 中找到同文依据，防止越界资格结论与提示注入驱动回答。</p>
 */
public final class AnswerEvidenceGuard {

    /** 置信下限：低于该值的结论按"不确定"处理。阈值调整属基线变更，须登记证据账本。 */
    static final double MIN_UNCERTAINTY = 0.5;

    /** 提示注入特征：含此类文本的证据失去支持力（不等于资料入库资格，那是过滤器的职责）。 */
    private static final List<String> INJECTION_PATTERNS = List.of(
            "忽略之前的指令", "忽略以上指令", "ignore previous instructions", "ignore all instructions"
    );

    /** 办理结果断言特征：此类结论直接改变学生状态，证据必须同文支持，不得由模型推断。 */
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
