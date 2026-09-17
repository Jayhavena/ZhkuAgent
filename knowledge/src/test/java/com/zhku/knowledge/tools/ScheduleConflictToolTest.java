package com.zhku.knowledge.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 选课冲突必须同时满足星期、节次和周次三项重叠。 */
class ScheduleConflictToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldReportOnlyCrossCourseMeetingsWithDayPeriodAndWeekOverlap()
            throws Exception {
        CampusToolResult result = new ScheduleConflictTool().execute("""
                {"courses":[
                  {"courseId":"CS101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[1,2]}]},
                  {"courseId":"MA101","meetings":[{"weekday":"MONDAY","startPeriod":2,"endPeriod":3,"weeks":[2,3]}]},
                  {"courseId":"EN101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[4]}]},
                  {"courseId":"CS101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[2]}]}]}
                """);

        JsonNode conflicts = MAPPER.readTree(result.resultJson()).path("conflicts");

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).path("firstCourseId").asText())
                .isEqualTo("CS101");
        assertThat(conflicts.get(0).path("secondCourseId").asText())
                .isEqualTo("MA101");
        assertThat(conflicts.get(0).path("startPeriod").asInt()).isEqualTo(2);
        assertThat(conflicts.get(0).path("endPeriod").asInt()).isEqualTo(2);
        assertThat(conflicts.get(0).path("overlappingWeeks").get(0).asInt())
                .isEqualTo(2);
    }

    @Test
    void shouldReturnSuccessWithNoConflictsWhenWeeksDoNotOverlap() throws Exception {
        CampusToolResult result = new ScheduleConflictTool().execute("""
                {"courses":[
                  {"courseId":"CS101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[1]}]},
                  {"courseId":"MA101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[2]}]}]}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(MAPPER.readTree(result.resultJson()).path("conflicts")).isEmpty();
    }

    @Test
    void shouldIgnoreMeetingsOfSameCourseAndDifferentWeekdays() throws Exception {
        CampusToolResult result = new ScheduleConflictTool().execute("""
                {"courses":[
                  {"courseId":"CS101","meetings":[
                    {"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[1]},
                    {"weekday":"MONDAY","startPeriod":2,"endPeriod":3,"weeks":[1]}]},
                  {"courseId":"MA101","meetings":[
                    {"weekday":"TUESDAY","startPeriod":1,"endPeriod":2,"weeks":[1]}]}]}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
        assertThat(MAPPER.readTree(result.resultJson()).path("conflicts")).isEmpty();
    }

    @Test
    void shouldRejectReversedPeriodsEmptyWeeksAndZeroWeek() {
        CampusToolResult result = new ScheduleConflictTool().execute("""
                {"courses":[{"courseId":"CS101","meetings":[
                  {"weekday":"MONDAY","startPeriod":3,"endPeriod":2,"weeks":[]}]}]}
                """);

        CampusToolResult zeroWeek = new ScheduleConflictTool().execute("""
                {"courses":[{"courseId":"CS101","meetings":[
                  {"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[0]}]}]}
                """);

        assertThat(result.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
        assertThat(zeroWeek.status()).isEqualTo(CampusToolStatus.INVALID_ARGUMENT);
    }
}
