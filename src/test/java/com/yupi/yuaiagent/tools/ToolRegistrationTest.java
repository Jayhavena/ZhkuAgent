package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistrationTest {

    @Test
    void shouldExposeOnlyLowRiskToolsToEveryModelCaller() {
        ToolRegistration registration = new ToolRegistration();
        ReflectionTestUtils.setField(registration, "searchApiKey", "test-key");

        ToolCallback[] callbacks = registration.allTools();
        Set<String> exposedNames = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertThat(exposedNames).containsExactlyInAnyOrder(
                "searchWeb",
                "doTerminate"
        );
    }
}
