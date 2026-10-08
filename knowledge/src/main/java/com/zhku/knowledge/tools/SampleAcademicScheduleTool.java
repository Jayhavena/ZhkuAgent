package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 查询随应用发布的虚构样例校历与课表；它刻意不接入真实校务数据源。 */
public final class SampleAcademicScheduleTool implements CampusTool {

    static final String NAME = "query_sample_academic_schedule";
    private static final String RESOURCE_PATH = "/zhku/tools/sample-academic-schedule.json";
    private static final List<SampleEntry> SAMPLE_ENTRIES = loadSampleEntries();
    private static final CampusToolDescriptor DESCRIPTOR = new CampusToolDescriptor(
            NAME,
            "查询固定的虚构样例校历和课表；不代表学校官方安排。",
            "{\"type\":\"object\",\"required\":[\"term\"],\"properties\":{\"term\":{\"type\":\"string\"},\"date\":{\"type\":\"string\"},\"courseKeyword\":{\"type\":\"string\"}}}",
            CampusToolAccessMode.SAMPLE_DATA_READ_ONLY);

    @Override
    public CampusToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CampusToolResult execute(String argumentsJson) {
        try {
            JsonNode input = ToolJsonSupport.parseObject(argumentsJson);
            String term = ToolJsonSupport.requiredText(input, "term");
            LocalDate date = optionalDate(input);
            String keyword = optionalText(input, "courseKeyword");
            List<SampleEntry> matches = findMatches(term, date, keyword);
            if (matches.isEmpty()) {
                return CampusToolResult.failure(
                        NAME,
                        CampusToolStatus.DATA_UNAVAILABLE,
                        ToolJsonSupport.errorJson("没有匹配的样例校历或课表记录"),
                        "仅查询固定样例数据，未匹配时不能推断真实校历、课表或选课状态。");
            }
            return CampusToolResult.success(
                    NAME,
                    resultJson(matches),
                    "结果仅来自固定虚构样例数据，不代表学校官方校历、课表、教室或选课资格。");
        } catch (JsonProcessingException | ToolJsonSupport.ToolInputException exception) {
            return ToolJsonSupport.invalidArguments(NAME, exception.getMessage());
        }
    }

    private static LocalDate optionalDate(JsonNode input) {
        String value = optionalText(input, "date");
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new ToolJsonSupport.ToolInputException("date 必须为有效的 ISO-8601 日期");
        }
    }

    private static String optionalText(JsonNode input, String fieldName) {
        JsonNode value = input.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ToolJsonSupport.ToolInputException(fieldName + " 必须是非空文本");
        }
        return value.asText().strip();
    }

    private static List<SampleEntry> findMatches(String term, LocalDate date, String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return SAMPLE_ENTRIES.stream()
                .filter(entry -> entry.term().equals(term))
                .filter(entry -> date == null || entry.date().equals(date))
                .filter(entry -> normalizedKeyword == null || entry.matches(normalizedKeyword))
                .toList();
    }

    private static String resultJson(List<SampleEntry> matches) {
        ObjectNode root = ToolJsonSupport.MAPPER.createObjectNode();
        root.put("source", "SAMPLE_DATA");
        ArrayNode entries = root.putArray("entries");
        for (SampleEntry entry : matches) {
            ObjectNode item = entries.addObject();
            item.put("term", entry.term());
            item.put("date", entry.date().toString());
            item.put("type", entry.type());
            if (entry.courseId() != null) {
                item.put("courseId", entry.courseId());
            }
            item.put("title", entry.title());
        }
        return root.toString();
    }

    private static List<SampleEntry> loadSampleEntries() {
        try (InputStream input = SampleAcademicScheduleTool.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("缺少样例校历资源：" + RESOURCE_PATH);
            }
            JsonNode root = ToolJsonSupport.MAPPER.readTree(input);
            JsonNode entries = root.path("entries");
            if (!entries.isArray() || entries.isEmpty()) {
                throw new IllegalStateException("样例校历资源必须包含 entries 数组");
            }
            List<SampleEntry> result = new ArrayList<>();
            for (JsonNode entry : entries) {
                if (!entry.isObject()) {
                    throw new IllegalStateException("样例校历 entries 必须为对象");
                }
                result.add(new SampleEntry(
                        requiredSampleText(entry, "term"),
                        parseSampleDate(entry),
                        requiredSampleText(entry, "type"),
                        optionalSampleText(entry, "courseId"),
                        requiredSampleText(entry, "title")));
            }
            return List.copyOf(result);
        } catch (IOException | DateTimeException exception) {
            throw new IllegalStateException("无法加载样例校历资源", exception);
        }
    }

    private static String requiredSampleText(JsonNode entry, String fieldName) {
        String value = optionalSampleText(entry, fieldName);
        if (value == null) {
            throw new IllegalStateException("样例校历缺少文本字段：" + fieldName);
        }
        return value;
    }

    private static String optionalSampleText(JsonNode entry, String fieldName) {
        JsonNode value = entry.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("样例校历字段必须为非空文本：" + fieldName);
        }
        return value.asText().strip();
    }

    private static LocalDate parseSampleDate(JsonNode entry) {
        try {
            return LocalDate.parse(requiredSampleText(entry, "date"));
        } catch (DateTimeException exception) {
            throw new IllegalStateException("样例校历日期无效", exception);
        }
    }

    private record SampleEntry(
            String term,
            LocalDate date,
            String type,
            String courseId,
            String title) {
        private SampleEntry {
            Objects.requireNonNull(term);
            Objects.requireNonNull(date);
            Objects.requireNonNull(type);
            Objects.requireNonNull(title);
        }

        private boolean matches(String normalizedKeyword) {
            return (courseId != null && courseId.toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                    || title.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
        }
    }
}
