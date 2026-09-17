package com.zhku.knowledge.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 样例校历只能返回固定样例，缺失记录时不得伪造真实校务结论。 */
class SampleAcademicScheduleToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldReturnOnlySampleEntriesMatchingTermAndDate() throws Exception {
        CampusToolResult result = new SampleAcademicScheduleTool().execute("""
                {"term":"SAMPLE-2026-1","date":"2026-03-02"}
                """);

        JsonNode json = MAPPER.readTree(result.resultJson());

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(json.path("source").asText()).isEqualTo("SAMPLE_DATA");
        assertThat(json.path("entries")).hasSize(2);
        assertThat(result.boundaryMessage()).contains("样例数据");
    }

    @Test
    void shouldFilterSampleEntriesByKeywordCaseInsensitively() throws Exception {
        CampusToolResult result = new SampleAcademicScheduleTool().execute("""
                {"term":"SAMPLE-2026-1","courseKeyword":"demo"}
                """);

        JsonNode entries = MAPPER.readTree(result.resultJson()).path("entries");

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).path("courseId").asText()).isEqualTo("DEMO101");
        assertThat(entries.get(0).has("classroom")).isFalse();
    }

    @Test
    void shouldRefuseToInventAnUnrepresentedSchedule() {
        CampusToolResult result = new SampleAcademicScheduleTool().execute(
                "{\"term\":\"SAMPLE-2099-1\"}"
        );

        assertThat(result.status()).isEqualTo(CampusToolStatus.DATA_UNAVAILABLE);
    }

    @Test
    void shouldRejectBlankTermAndMalformedDate() {
        CampusToolResult blankTerm = new SampleAcademicScheduleTool().execute(
                "{\"term\":\"  \"}"
        );
        CampusToolResult malformedDate = new SampleAcademicScheduleTool().execute(
                "{\"term\":\"SAMPLE-2026-1\",\"date\":\"2026-02-30\"}"
        );

        assertThat(blankTerm.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(malformedDate.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
    }
}
