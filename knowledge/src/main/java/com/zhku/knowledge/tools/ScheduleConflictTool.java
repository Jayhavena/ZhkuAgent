package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 仅根据调用方提供的课表时段检测重叠，不连接或推断校务系统中的真实选课数据。
 */
public final class ScheduleConflictTool implements CampusTool {

    public static final String NAME = "detect_schedule_conflicts";

    private static final CampusToolDescriptor DESCRIPTOR = new CampusToolDescriptor(
            NAME,
            "检测传入课表中不同课程在同一星期、节次和教学周的重叠。",
            "{\"type\":\"object\",\"required\":[\"courses\"],\"properties\":{\"courses\":{\"type\":\"array\"}}}",
            CampusToolAccessMode.PURE_CALCULATION
    );

    @Override
    public CampusToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CampusToolResult execute(String argumentsJson) {
        try {
            JsonNode input = ToolJsonSupport.parseObject(argumentsJson);
            List<Meeting> meetings = parseMeetings(ToolJsonSupport.requiredArray(input, "courses"));
            List<Conflict> conflicts = detectConflicts(meetings);

            ObjectNode payload = ToolJsonSupport.MAPPER.createObjectNode();
            ArrayNode items = payload.putArray("conflicts");
            for (Conflict conflict : conflicts) {
                ObjectNode item = items.addObject();
                item.put("firstCourseId", conflict.firstCourseId());
                item.put("secondCourseId", conflict.secondCourseId());
                item.put("weekday", conflict.weekday().name());
                item.put("startPeriod", conflict.startPeriod());
                item.put("endPeriod", conflict.endPeriod());
                ArrayNode weeks = item.putArray("overlappingWeeks");
                conflict.overlappingWeeks().forEach(weeks::add);
            }
            payload.put("conflictCount", conflicts.size());
            return CampusToolResult.success(
                    NAME,
                    ToolJsonSupport.MAPPER.writeValueAsString(payload),
                    "只按传入课表时段检测冲突，不能确认真实课表、教室或选课资格。"
            );
        } catch (ToolJsonSupport.ToolInputException | JsonProcessingException exception) {
            return ToolJsonSupport.invalidArguments(NAME, exception.getMessage());
        }
    }

    private List<Meeting> parseMeetings(JsonNode courses) {
        List<Meeting> meetings = new ArrayList<>();
        for (JsonNode courseNode : courses) {
            if (!courseNode.isObject()) {
                throw new ToolJsonSupport.ToolInputException("courses 中每项必须是对象");
            }
            JsonNode course = courseNode;
            String courseId = ToolJsonSupport.requiredText(course, "courseId");
            JsonNode courseMeetings = ToolJsonSupport.requiredArray(course, "meetings");
            for (JsonNode meetingNode : courseMeetings) {
                if (!meetingNode.isObject()) {
                    throw new ToolJsonSupport.ToolInputException("meetings 中每项必须是对象");
                }
                JsonNode meeting = meetingNode;
                DayOfWeek weekday = parseWeekday(ToolJsonSupport.requiredText(meeting, "weekday"));
                int startPeriod = requiredPositiveInt(meeting, "startPeriod");
                int endPeriod = requiredPositiveInt(meeting, "endPeriod");
                if (startPeriod > endPeriod) {
                    throw new ToolJsonSupport.ToolInputException("startPeriod 不能大于 endPeriod");
                }
                meetings.add(new Meeting(courseId, weekday, startPeriod, endPeriod,
                        parseWeeks(ToolJsonSupport.requiredArray(meeting, "weeks"))));
            }
        }
        return meetings;
    }

    private DayOfWeek parseWeekday(String value) {
        try {
            return DayOfWeek.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ToolJsonSupport.ToolInputException("weekday 必须为 MONDAY 至 SUNDAY");
        }
    }

    private int requiredPositiveInt(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isIntegralNumber() || value.intValue() <= 0) {
            throw new ToolJsonSupport.ToolInputException(fieldName + " 必须是正整数");
        }
        return value.intValue();
    }

    private Set<Integer> parseWeeks(JsonNode weekNodes) {
        if (weekNodes.isEmpty()) {
            throw new ToolJsonSupport.ToolInputException("weeks 不能为空");
        }
        Set<Integer> weeks = new HashSet<>();
        for (JsonNode weekNode : weekNodes) {
            if (!weekNode.canConvertToInt() || weekNode.intValue() <= 0) {
                throw new ToolJsonSupport.ToolInputException("weeks 必须包含正整数");
            }
            if (!weeks.add(weekNode.intValue())) {
                throw new ToolJsonSupport.ToolInputException("weeks 不能包含重复教学周");
            }
        }
        return weeks;
    }

    private List<Conflict> detectConflicts(List<Meeting> meetings) {
        List<Conflict> conflicts = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < meetings.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < meetings.size(); secondIndex++) {
                Meeting first = meetings.get(firstIndex);
                Meeting second = meetings.get(secondIndex);
                if (first.courseId().equals(second.courseId())
                        || first.weekday() != second.weekday()
                        || first.endPeriod() < second.startPeriod()
                        || second.endPeriod() < first.startPeriod()) {
                    continue;
                }
                List<Integer> overlappingWeeks = first.weeks().stream()
                        .filter(second.weeks()::contains)
                        .sorted()
                        .toList();
                if (!overlappingWeeks.isEmpty()) {
                    String firstCourseId = first.courseId().compareTo(second.courseId()) <= 0
                            ? first.courseId() : second.courseId();
                    String secondCourseId = firstCourseId.equals(first.courseId())
                            ? second.courseId() : first.courseId();
                    conflicts.add(new Conflict(
                            firstCourseId,
                            secondCourseId,
                            first.weekday(),
                            Math.max(first.startPeriod(), second.startPeriod()),
                            Math.min(first.endPeriod(), second.endPeriod()),
                            overlappingWeeks
                    ));
                }
            }
        }
        return conflicts.stream()
                // 同一课程的重复时段记录不应把同一风险放大成多条提示。
                .distinct()
                .sorted(Comparator.comparing(Conflict::firstCourseId)
                        .thenComparing(Conflict::secondCourseId)
                        .thenComparing(Conflict::weekday)
                        .thenComparingInt(Conflict::startPeriod)
                        .thenComparingInt(Conflict::endPeriod))
                .toList();
    }

    private record Meeting(String courseId, DayOfWeek weekday, int startPeriod, int endPeriod, Set<Integer> weeks) {
    }

    private record Conflict(
            String firstCourseId,
            String secondCourseId,
            DayOfWeek weekday,
            int startPeriod,
            int endPeriod,
            List<Integer> overlappingWeeks
    ) {
    }
}
