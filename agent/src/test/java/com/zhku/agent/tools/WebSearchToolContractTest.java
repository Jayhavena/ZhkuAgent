package com.zhku.agent.tools;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebSearchToolContractTest {
    @Test
    void zeroToFourResultsAreValidAndNotErrors() {
        for (int size = 0; size < 5; size++) {
            String entries = java.util.stream.IntStream.range(0, size)
                    .mapToObj(i -> "{\"title\":\"r" + i + "\"}")
                    .collect(java.util.stream.Collectors.joining(","));
            var tool = new WebSearchTool("test-key", (url, args) -> "{\"organic_results\":[" + entries + "]}");
            String result = tool.searchWeb("test");
            assertThat(result).doesNotContain("Error");
            assertThat(result).isEqualTo(entries);
        }
    }

    @Test
    void atMostFiveResultsAreReturned() {
        var tool = new WebSearchTool("test-key", (url, args) -> """
                {"organic_results":[{"title":"r0"},{"title":"r1"},{"title":"r2"},
                {"title":"r3"},{"title":"r4"},{"title":"r5"}]}
                """);
        assertThat(tool.searchWeb("test")).contains("r4").doesNotContain("r5");
    }

    @Test
    void invalidEnvelopesFailWithFixedCategory() {
        for (String body : new String[] { "", "not-json", "{}", "{\"organic_results\":{}}",
                "{\"organic_results\":[null]}" }) {
            var tool = new WebSearchTool("test-key", (url, args) -> body);
            assertThatThrownBy(() -> tool.searchWeb("test")).hasMessage("TOOL_FAILURE").hasNoCause();
        }
    }

    @Test
    void transportFailureDoesNotExposeRawException() {
        var tool = new WebSearchTool("test-key", (url, args) -> {
            throw new IllegalStateException("SECRET-TRANSPORT");
        });
        assertThatThrownBy(() -> tool.searchWeb("test")).hasMessage("TOOL_FAILURE").hasNoCause();
    }
}
