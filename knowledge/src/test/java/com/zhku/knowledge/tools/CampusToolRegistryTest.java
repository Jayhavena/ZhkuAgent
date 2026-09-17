package com.zhku.knowledge.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 校园工具注册表的稳定名称、只读描述和安全失败契约。 */
class CampusToolRegistryTest {

    @Test
    void shouldExposeDescriptorsInStableNameOrderAndDelegateKnownTool() {
        StubTool creditTool = new StubTool("summarize_course_credits");
        StubTool conflictTool = new StubTool("detect_schedule_conflicts");
        CampusToolRegistry registry = new CampusToolRegistry(
                List.of(creditTool, conflictTool)
        );

        assertThat(registry.descriptors())
                .extracting(CampusToolDescriptor::name)
                .containsExactly(
                        "detect_schedule_conflicts",
                        "summarize_course_credits"
                );
        assertThat(registry.findDescriptor("summarize_course_credits"))
                .contains(creditTool.descriptor());
        assertThatThrownBy(() -> registry.descriptors().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        CampusToolResult result = registry.execute(new CampusToolCall(
                "summarize_course_credits",
                "{}"
        ));

        assertThat(result).isEqualTo(creditTool.result());
        assertThat(creditTool.executionCount()).isEqualTo(1);
        assertThat(conflictTool.executionCount()).isZero();
    }

    @Test
    void shouldReturnStructuredUnknownToolResultWithoutExecutingRegisteredTool() {
        StubTool tool = new StubTool("summarize_course_credits");
        CampusToolRegistry registry = new CampusToolRegistry(List.of(tool));

        CampusToolResult result = registry.execute(new CampusToolCall(
                "not_registered",
                "{}"
        ));

        assertThat(result)
                .extracting(CampusToolResult::toolName, CampusToolResult::status)
                .containsExactly("not_registered", CampusToolStatus.UNKNOWN_TOOL);
        assertThat(result.boundaryMessage()).contains("不能执行或猜测结果");
        assertThat(tool.executionCount()).isZero();
    }

    @Test
    void shouldRejectDuplicateToolNamesInsteadOfOverwritingARegistration() {
        assertThatThrownBy(() -> new CampusToolRegistry(List.of(
                new StubTool("summarize_course_credits"),
                new StubTool("summarize_course_credits")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summarize_course_credits");
    }

    @Test
    void shouldRejectBlankToolCallFieldsAtTheContractBoundary() {
        assertThatThrownBy(() -> new CampusToolCall(" ", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具名称不能为空");
        assertThatThrownBy(() -> new CampusToolCall(
                "summarize_course_credits",
                " "
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具参数不能为空");
    }

    private static final class StubTool implements CampusTool {

        private final CampusToolDescriptor descriptor;
        private int executionCount;

        private StubTool(String name) {
            this.descriptor = new CampusToolDescriptor(
                    name,
                    "仅用于注册表契约测试",
                    "{\"type\":\"object\"}",
                    CampusToolAccessMode.PURE_CALCULATION
            );
        }

        @Override
        public CampusToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public CampusToolResult execute(String argumentsJson) {
            executionCount++;
            return result();
        }

        private CampusToolResult result() {
            return CampusToolResult.success(
                    descriptor.name(),
                    "{\"source\":\"TEST\"}",
                    "只用于测试；不能确认任何真实校园事实。"
            );
        }

        private int executionCount() {
            return executionCount;
        }
    }
}
