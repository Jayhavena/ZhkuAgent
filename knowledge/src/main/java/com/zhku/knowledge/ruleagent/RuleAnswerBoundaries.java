package com.zhku.knowledge.ruleagent;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 只生成可由原文数值或原文限定词直接核对的边界说明。
 * 这些说明不是新的规则事实：正文仍绑定原事实与原文，宿主只补充“按原文比较”和“申请不等于批准”的适用范围。
 */
final class RuleAnswerBoundaries {
    private static final Pattern HOURS_LIMIT =
            Pattern.compile("最多不超过\\s*([0-9]{1,9}(?:\\.[0-9]{1,6})?)\\s*学时");
    private static final Pattern HOURS_PRINCIPLE =
            Pattern.compile("原则上为\\s*([0-9]{1,9}(?:\\.[0-9]{1,6})?)\\s*学时");
    private static final Pattern QUESTION_HOURS =
            Pattern.compile("([0-9]{1,9}(?:\\.[0-9]{1,6})?)\\s*学时");
    private static final Pattern APPLICATION = Pattern.compile("可申请|可以申请");
    private static final Pattern PLAN_RELATIVE = Pattern.compile("培养方案.*(?:一半|比例)");

    private RuleAnswerBoundaries() {}

    static boolean isHoursLimitFact(String factText) {
        return factText != null && HOURS_LIMIT.matcher(factText).find();
    }

    /**
     * 仅当问题显式给出一个学时数值，且已发布事实同时包含原则值与上限时，才给出比较说明。
     * 比较只陈述数值关系，不把“超过原则值”直接改写成违规结论。
     */
    static Optional<String> hoursComparison(String question, List<String> publishedFactTexts) {
        var declared = declaredHours(question);
        if (declared.size() != 1) return Optional.empty();
        var limit = firstNumber(HOURS_LIMIT, publishedFactTexts);
        var principle = firstNumber(HOURS_PRINCIPLE, publishedFactTexts);
        if (limit.isEmpty() || principle.isEmpty()) return Optional.empty();
        var value = declared.getFirst();
        return Optional.of("按原文数值比较：所问" + display(value) + "学时" + limitRelation(value, limit.get())
                + "上限" + display(limit.get()) + "学时；原文原则值为" + display(principle.get())
                + "学时，所问数值" + principleRelation(value, principle.get())
                + "原则值。仅凭该条不能判定该课程整体合规或违规，仍需核实该课程实际安排及适用例外。");
    }

    /** “可申请”是结果边界：宿主只重复原文，不推定批准。 */
    static Optional<String> applicationBoundary(String factText) {
        if (factText == null || !APPLICATION.matcher(factText).find()) return Optional.empty();
        return Optional.of("原文只说明可以申请，不等于已获批准；是否符合个人办理条件及最终结果需另行确认。");
    }

    /** 与培养方案挂钩的比例规则仍缺个人分母；只说明计算边界，不代替个人计算。 */
    static Optional<String> planRelativeBoundary(String factText) {
        if (factText == null || !PLAN_RELATIVE.matcher(factText).find()) return Optional.empty();
        return Optional.of("具体个人是否超过该比例，需要按对应培养方案的选修课学分和已修记录计算。");
    }

    static String append(String text, Optional<String> boundary) {
        return boundary.map(value -> text + value).orElse(text);
    }

    private static String limitRelation(BigDecimal value, BigDecimal limit) {
        int relation = value.compareTo(limit);
        return relation > 0 ? "超过" : relation == 0 ? "正好等于" : "未超过";
    }

    private static String principleRelation(BigDecimal value, BigDecimal principle) {
        int relation = value.compareTo(principle);
        return relation > 0 ? "高于" : relation == 0 ? "等于" : "低于";
    }

    private static Optional<BigDecimal> firstNumber(Pattern pattern, List<String> texts) {
        for (String text : texts) {
            var matcher = pattern.matcher(text == null ? "" : text);
            if (matcher.find()) return Optional.of(new BigDecimal(matcher.group(1)));
        }
        return Optional.empty();
    }

    /** 去掉“上限32学时/超过32学时/原则上16学时”这类条文常量，只留用户实际给出的课时数。 */
    private static List<BigDecimal> declaredHours(String question) {
        var values = new LinkedHashSet<BigDecimal>();
        var matcher = QUESTION_HOURS.matcher(question == null ? "" : question);
        while (matcher.find()) {
            String before = (question == null ? "" : question)
                    .substring(Math.max(0, matcher.start() - 10), matcher.start()).replaceAll("\\s+", "");
            if (before.matches("(?s).*(?:上限(?:为|是)?|最多(?:不超过)?|不超过|原则上(?:为|是)?|超过|高于|大于)$")) continue;
            values.add(new BigDecimal(matcher.group(1)));
        }
        return List.copyOf(values);
    }

    private static String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
