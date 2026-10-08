package com.zhku.agent.tools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import static org.assertj.core.api.Assertions.*;

class TerminateToolContractTest {
    private final org.springframework.ai.tool.ToolCallback callback = ToolCallbacks.from(new TerminateTool())[0];
    @Test void completedHasExplicitOutcome() {
        assertThat(callback.call("{\"outcome\":\"COMPLETED\"}")).isEqualTo("{\"outcome\":\"COMPLETED\"}");
    }
    @Test void blockedIsNotAnImplicitSuccess() {
        assertThat(callback.call("{\"outcome\":\"BLOCKED\"}")).isEqualTo("{\"outcome\":\"BLOCKED\"}");
    }
    @Test void missingAndUnknownOutcomeFail() {
        assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> callback.call("{\"outcome\":\"unknown\"}")).isInstanceOf(RuntimeException.class);
    }
}
