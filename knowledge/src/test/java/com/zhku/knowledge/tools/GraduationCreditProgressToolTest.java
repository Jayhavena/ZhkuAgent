package com.zhku.knowledge.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 毕业进度工具只核算传入学分目标，不能越界确认毕业资格。 */
class GraduationCreditProgressToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldCalculateEachCategoryGapAndTotalWithoutClaimingGraduationEligibility()
            throws Exception {
        CampusToolResult result = new GraduationCreditProgressTool().execute("""
                {"requirements":[
                  {"category":"GENERAL","requiredCredits":8.0},
                  {"category":"MAJOR","requiredCredits":30.0}],
                 "completedCreditsByCategory":{"MAJOR":31.0,"GENERAL":6.0}}
                """);

        JsonNode json = MAPPER.readTree(result.resultJson());

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(json.path("progress").get(0).path("category").asText())
                .isEqualTo("GENERAL");
        assertThat(json.path("progress").get(0).path("remainingCredits").decimalValue())
                .isEqualByComparingTo("2.0");
        assertThat(json.path("progress").get(1).path("remainingCredits").decimalValue())
                .isEqualByComparingTo("0.0");
        assertThat(json.path("totalRemainingCredits").decimalValue())
                .isEqualByComparingTo("2.0");
        assertThat(result.boundaryMessage()).contains("不能确认毕业资格");
    }

    @Test
    void shouldRejectCompletedCategoryThatIsNotPartOfTheRequirements() {
        CampusToolResult result = new GraduationCreditProgressTool().execute("""
                {"requirements":[{"category":"GENERAL","requiredCredits":8.0}],
                 "completedCreditsByCategory":{"MAJOR":3.0}}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).contains("MAJOR");
    }

    @Test
    void shouldRejectDuplicateRequirementCategories() {
        CampusToolResult result = new GraduationCreditProgressTool().execute("""
                {"requirements":[
                  {"category":"GENERAL","requiredCredits":8.0},
                  {"category":"GENERAL","requiredCredits":4.0}],
                 "completedCreditsByCategory":{"GENERAL":6.0}}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).contains("GENERAL");
    }
}
