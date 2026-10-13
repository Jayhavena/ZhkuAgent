package com.zhku.knowledge.rulecheck;

import java.math.BigDecimal;
import java.util.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTaskPlan.Action.*;

/** 条款级运算而非问句特判；派生解释必须同时匹配声明ID与已核验原文条件。 */
public final class RuleCheckClauseOperations {
    private RuleCheckClauseOperations() {}
    public static RuleCheckTaskPlan.Option option(RuleCheckApplicationService.CatalogEntry entry) {
        var actions=new ArrayList<>(List.of(EXPLAIN_RULE,UNKNOWN_CURRENT));var operands=new ArrayList<String>();
        String id=entry.template().ruleId(), text=entry.template().facts().stream().map(RuleCheckRuleTemplate.Fact::text).reduce("",String::concat).replaceAll("\\s+","");
        if(id.equals("A14/GENERAL")&&text.contains("未正式办理退选")) {actions.add(EXCLUDE_CONDITION);operands.add("FORMAL_WITHDRAWAL_COMPLETED");}
        if(id.equals("A15/GENERAL")&&text.contains("超过")&&text.contains("30%")) {actions.add(COMPARE_THRESHOLD);operands.add("ABSENCE_PERCENT");}
        if(id.equals("A16/GENERAL")&&text.contains("平时成绩")&&text.contains("期末成绩")) {actions.add(MISSING_DETAIL);operands.add("GRADE_WEIGHTS");}
        if(id.equals("A17/GENERAL")&&text.contains("规定时间")) {actions.add(MISSING_DETAIL);operands.add("CURRENT_DEADLINE");}
        if(id.equals("A20/GENERAL")&&text.contains("公布之日起")) {actions.add(MISSING_DETAIL);operands.add("PUBLICATION_DATE");}
        return new RuleCheckTaskPlan.Option(id,entry.questionScope(),entry.template().facts().stream().map(RuleCheckRuleTemplate.Fact::text).toList(),actions,operands);
    }
    public static boolean accepts(RuleCheckTaskPlan.Task task,RuleCheckTaskPlan.Option option) {
        if(!option.ruleId().equals(task.ruleId())||!option.actions().contains(task.action()))return false;
        if(task.action()==EXPLAIN_RULE||task.action()==UNKNOWN_CURRENT)return task.operand().isEmpty()&&task.value().isEmpty();
        if(!option.operands().contains(task.operand()))return false;
        if(task.action()!=COMPARE_THRESHOLD)return task.value().isEmpty();
        try {var value=new BigDecimal(task.value());return task.value().matches("[0-9]{1,3}(?:\\.[0-9]{1,6})?")&&value.signum()>=0&&value.compareTo(new BigDecimal("100"))<=0;}
        catch(NumberFormatException invalid){return false;}
    }
    public static String missingDetail(String operand) {
        return switch(operand) {
            case "GRADE_WEIGHTS" -> "第16条只说明平时成绩和期末成绩综合评定，没有给出各自百分比；需要对应课程的已审核考核方案才能确定比例。";
            case "CURRENT_DEADLINE" -> "第17条要求任课教师在规定时间内按要求提交成绩，但没有提供本学期具体截止日期；需要当期已审核通知。";
            case "PUBLICATION_DATE" -> "第20条规定自公布之日起执行，但所引历史文本没有给出实际公布日期，手册快照日期不能代替公布日期。";
            default -> throw new IllegalArgumentException("未知条款缺口");
        };
    }
    /** 数值只能来自本轮问题的显式百分比；格式合法不能授权模型猜一个学生条件。 */
    public static boolean hasPercentageExpression(String question) {
        return question.contains("%")||question.contains("％")||question.contains("百分之");
    }
    public static boolean valueComesFromQuestion(RuleCheckTaskPlan.Task task,String question) {
        if(task.action()!=COMPARE_THRESHOLD)return true;
        var percentages=java.util.regex.Pattern.compile("(?<![0-9.A-Za-z+\\-−＋－])([0-9]{1,3}(?:\\.[0-9]{1,6})?)\\s*[%％]").matcher(question);
        while(percentages.find())if(new BigDecimal(percentages.group(1)).compareTo(new BigDecimal(task.value()))==0)return true;
        return false;
    }
    /** 问句可能同时引用阈值和实际比例；明确的“超过/阈值”数字不能充当学生操作数。 */
    public static Optional<String> comparisonValue(String question) {
        var percentages=java.util.regex.Pattern.compile("(?<![0-9.A-Za-z+\\-−＋－])([0-9]{1,3}(?:\\.[0-9]{1,6})?)\\s*[%％]").matcher(question);
        var actual=new LinkedHashSet<String>();
        while(percentages.find()) {
            String before=question.substring(Math.max(0,percentages.start()-16),percentages.start()).replaceAll("\\s+","");
            if(before.matches("(?s).*(?:超过|高于|大于|阈值(?:为|是)?|上限(?:为|是)?)$"))continue;
            actual.add(new BigDecimal(percentages.group(1)).stripTrailingZeros().toPlainString());
        }
        return actual.size()==1?Optional.of(actual.iterator().next()):Optional.empty();
    }
    public static String explanation(RuleCheckTaskPlan.Task task) {
        if(task.action()==EXCLUDE_CONDITION)
            return "已正式办理退选者不满足本条“未正式办理退选、改选手续”的前提，不能直接套用本条这项0分规定；这不保证成绩删除，也不排除其他规定的后果。";
        if(task.action()==COMPARE_THRESHOLD) {
            var value=new BigDecimal(task.value());String ratio=value.stripTrailingZeros().toPlainString();
            String relation=value.compareTo(new BigDecimal("30"))>0?"超过30%，满足这项比例条件":value.compareTo(new BigDecimal("30"))==0?
                "正好30%，不满足严格超过30%的条件":"低于30%，不满足严格超过30%的条件";
            return "本条要求累计缺课严格超过该门课程学期总学时数的30%，不包括正好30%；所问"+ratio+"%"+relation+"。这只解释该条条件，不能据此认定个人最终考试资格。";
        }
        throw new IllegalArgumentException("该动作没有派生解释");
    }
}
