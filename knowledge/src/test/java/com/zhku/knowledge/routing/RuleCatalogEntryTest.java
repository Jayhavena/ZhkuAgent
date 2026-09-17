package com.zhku.knowledge.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 规则目录项的声明完整性约束：缺任一声明都不允许入目录。 */
class RuleCatalogEntryTest {

    @Test
    void shouldCreateValidEntry() {
        RuleCatalogEntry entry = new RuleCatalogEntry(
                "retake-after-failure",
                "选修课不及格重修",
                List.of("是否允许缓考", "重修报名方式"),
                "已发布学生手册相应条款",
                RuleRisk.HIGH,
                "涉及个人学籍状态变更时转人工"
        );

        assertThat(entry.ruleId()).isEqualTo("retake-after-failure");
        assertThat(entry.requiredFacts()).containsExactly("是否允许缓考", "重修报名方式");
        assertThat(entry.risk()).isEqualTo(RuleRisk.HIGH);
    }

    @Test
    void shouldRejectEntryWithoutRequiredFacts() {
        // 未声明所需事实的规则无法判断证据是否足够，禁止入目录。
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleCatalogEntry(
                        "r1", "主题", List.of(), "范围", RuleRisk.MEDIUM, "转人工条件"));
    }

    @Test
    void shouldRejectBlankDeclarations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleCatalogEntry(
                        " ", "主题", List.of("事实"), "范围", RuleRisk.LOW, "转人工条件"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleCatalogEntry(
                        "r1", "主题", List.of("事实"), " ", RuleRisk.LOW, "转人工条件"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RuleCatalogEntry(
                        "r1", "主题", List.of("事实"), "范围", RuleRisk.LOW, " "));
    }

    @Test
    void shouldRejectNullRisk() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RuleCatalogEntry(
                        "r1", "主题", List.of("事实"), "范围", null, "转人工条件"));
    }

    @Test
    void shouldDefensivelyCopyRequiredFacts() {
        RuleCatalogEntry entry = new RuleCatalogEntry(
                "r1", "主题", List.of("事实"), "范围", RuleRisk.LOW, "转人工条件");

        assertThatThrownBy(() -> entry.requiredFacts().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
