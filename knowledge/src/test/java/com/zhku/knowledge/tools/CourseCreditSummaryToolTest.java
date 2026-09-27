package com.zhku.knowledge.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 课程学分汇总只能计算调用方给出的课程，不推断课程资格或培养方案归属。 */
class CourseCreditSummaryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldSumCreditsAndSortCategoryTotalsDeterministically() throws Exception {
        CampusToolResult result = new CourseCreditSummaryTool().execute("""
                {"courses":[
                  {"courseId":"MA101","category":"MAJOR","credits":3.0},
                  {"courseId":"GE001","category":"GENERAL","credits":2.0},
                  {"courseId":"GE002","category":"GENERAL","credits":1.5}]}
                """);

        JsonNode json = MAPPER.readTree(result.resultJson());

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(json.path("totalCredits").decimalValue())
                .isEqualByComparingTo("6.5");
        assertThat(json.path("creditsByCategory").path("GENERAL").decimalValue())
                .isEqualByComparingTo("3.5");
        List<String> categoryNames = new ArrayList<>();
        json.path("creditsByCategory").fieldNames().forEachRemaining(
                categoryNames::add
        );
        assertThat(categoryNames)
                .containsExactly("GENERAL", "MAJOR");
        assertThat(result.boundaryMessage()).contains("不能确认选课资格");
    }

    @Test
    void shouldReturnInvalidArgumentForNegativeCreditWithoutThrowing() {
        CampusToolResult result = new CourseCreditSummaryTool().execute("""
                {"courses":[
                  {"courseId":"GE001","category":"GENERAL","credits":-0.5}]}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).contains("学分不能为负数");
    }

    @Test
    void shouldReturnInvalidArgumentForMissingCourseList() {
        CampusToolResult result = new CourseCreditSummaryTool().execute("{}");

        assertThat(result.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).contains("courses");
    }

    @Test
    void modelSchemaRequiresEveryFieldUsedByExecutor() throws Exception {
        JsonNode schema = MAPPER.readTree(new CourseCreditSummaryTool().descriptor().inputSchemaJson());
        JsonNode item = schema.path("properties").path("courses").path("items");

        assertThat(schema.path("required").toString()).isEqualTo("[\"courses\"]");
        assertThat(item.path("required").toString())
                .isEqualTo("[\"courseId\",\"category\",\"credits\"]");
        assertThat(item.path("properties").path("credits").path("minimum").intValue()).isZero();
    }
}
