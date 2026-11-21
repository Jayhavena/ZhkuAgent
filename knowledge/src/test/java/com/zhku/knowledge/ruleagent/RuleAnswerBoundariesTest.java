package com.zhku.knowledge.ruleagent;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuleAnswerBoundariesTest {
    private final List<String> hours = List.of("公共选修课授课时间原则上为16学时。", "公共选修课最多不超过32学时。");

    @Test void declaredHoursWithinLimitStillShowsPrincipleAndRefusesVerdict() {
        var text = RuleAnswerBoundaries.hoursComparison("有门公选课安排24学时，这能直接判定违规吗？", hours).orElseThrow();
        assertThat(text).contains("所问24学时未超过上限32学时", "原文原则值为16学时", "所问数值高于原则值",
                "不能判定该课程整体合规或违规", "仍需核实该课程实际安排及适用例外");
    }

    @Test void declaredHoursAboveLimitIsStatedWithoutCallingItAViolation() {
        var text = RuleAnswerBoundaries.hoursComparison("某课程安排36学时，违规吗？", hours).orElseThrow();
        assertThat(text).contains("所问36学时超过上限32学时", "不能判定该课程整体合规或违规");
        assertThat(text).doesNotContain("必然违规", "一定违规");
    }

    @Test void equalLimitAndMissingFactsDoNotProduceComparison() {
        assertThat(RuleAnswerBoundaries.hoursComparison("安排32学时", hours).orElseThrow()).contains("正好等于上限32学时");
        assertThat(RuleAnswerBoundaries.hoursComparison("安排24学时，不是超过32学时上限吗？", hours).orElseThrow())
                .contains("所问24学时未超过上限32学时");
        assertThat(RuleAnswerBoundaries.hoursComparison("安排24学时", List.of(hours.get(1)))).isEmpty();
        assertThat(RuleAnswerBoundaries.hoursComparison("安排24学时和36学时", hours)).isEmpty();
        assertThat(RuleAnswerBoundaries.hoursComparison("学时安排很紧", hours)).isEmpty();
    }

    @Test void applicationBoundaryOnlyRepeatsTheLimitsOfTheSourceText() {
        var boundary = RuleAnswerBoundaries.applicationBoundary("专业选修课成绩不合格者可申请重修学习。").orElseThrow();
        assertThat(boundary).contains("原文只说明可以申请", "不等于已获批准", "需另行确认");
        assertThat(RuleAnswerBoundaries.applicationBoundary("公共选修课成绩不合格者不安排重修。")).isEmpty();
    }

    @Test void planRelativeRuleKeepsThePersonalCalculationBoundary() {
        var boundary = RuleAnswerBoundaries.planRelativeBoundary("选修网络公选课一般不能超过人才培养方案要求选修课的一半学分。").orElseThrow();
        assertThat(boundary).contains("培养方案的选修课学分和已修记录");
        assertThat(RuleAnswerBoundaries.planRelativeBoundary("学生应按人才培养方案要求取得规定学分。")).isEmpty();
    }
}
