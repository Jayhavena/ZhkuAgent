package com.zhku.agent.agent.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享运行时契约测试。
 *
 * <p>这些契约会被 {@code agent} 和 {@code knowledge} 两个模块共同使用，
 * 因此测试要防止它们反向依赖具体业务模块或模型框架类型。</p>
 */
class SharedRuntimeContractTest {

    private static final String CONTRACT_PACKAGE =
            "com.zhku.agent.agent.runtime.contract.";

    private static final String[] CONTRACT_NAMES = {
            "AgentRunSnapshot",
            "AgentToolCall",
            "AgentRunEvent",
            "AgentRuntimeError"
    };

    @Test
    void fourContractsAreImmutableRecordsWithoutModelFrameworkFields()
            throws ClassNotFoundException {
        for (String contractName : CONTRACT_NAMES) {
            String qualifiedName = CONTRACT_PACKAGE + contractName;
            Class<?> contractType = Class.forName(qualifiedName);

            // 运行时契约必须保持为不可变 record，避免业务模块直接修改共享状态。
            assertThat(contractType.isRecord())
                    .as("契约类型：%s", qualifiedName)
                    .isTrue();

            // 共享层不能携带 Spring 或校园业务类型，否则会破坏模块解耦。
            assertThat(Arrays.stream(contractType.getRecordComponents())
                    .map(component -> component.getType().getName()))
                    .noneMatch(typeName -> typeName.startsWith("org.springframework")
                            || typeName.startsWith("com.zhku.knowledge"));
        }
    }

    @Test
    void contextProvidesConsistentSnapshotAndPublicationBoundary() {
        assertThat(Arrays.stream(AgentRunContext.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                // 这些方法构成调用方读取状态、发布终止事实和记录工具调用的最小边界。
                .contains(
                        "snapshot",
                        "publishTermination",
                        "proposeToolCall",
                        "recordToolResult"
                );
    }
}
