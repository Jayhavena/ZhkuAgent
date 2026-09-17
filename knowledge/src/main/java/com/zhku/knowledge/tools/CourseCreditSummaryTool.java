package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** 对调用方传入的课程学分做确定性汇总的纯计算工具。 */
public final class CourseCreditSummaryTool implements CampusTool {

    static final String NAME = "summarize_course_credits";
    private static final CampusToolDescriptor DESCRIPTOR = new CampusToolDescriptor(
            NAME,
            "汇总传入课程的总学分与类别学分；不判断课程资格或培养方案归属。",
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
            JsonNode courses = ToolJsonSupport.requiredArray(
                    ToolJsonSupport.parseObject(argumentsJson),
                    "courses"
            );
            Map<String, BigDecimal> totalsByCategory = new TreeMap<>();
            BigDecimal totalCredits = BigDecimal.ZERO;
            for (JsonNode course : courses) {
                if (!course.isObject()) {
                    throw new ToolJsonSupport.ToolInputException("courses 中每项必须是对象");
                }
                String courseId = ToolJsonSupport.requiredText(course, "courseId");
                String category = ToolJsonSupport.requiredText(course, "category");
                BigDecimal credits = ToolJsonSupport.requiredNonNegativeDecimal(course, "credits");
                if (courseId.isBlank()) {
                    throw new ToolJsonSupport.ToolInputException("课程标识不能为空");
                }
                totalsByCategory.merge(category, credits, BigDecimal::add);
                totalCredits = totalCredits.add(credits);
            }
            return CampusToolResult.success(
                    NAME,
                    resultJson(totalCredits, totalsByCategory),
                    "只按传入课程汇总学分，不能确认选课资格、课程归属或培养方案认定。"
            );
        } catch (JsonProcessingException | ToolJsonSupport.ToolInputException exception) {
            return ToolJsonSupport.invalidArguments(NAME, exception.getMessage());
        }
    }

    private static String resultJson(
            BigDecimal totalCredits,
            Map<String, BigDecimal> totalsByCategory
    ) {
        ObjectNode root = ToolJsonSupport.MAPPER.createObjectNode();
        root.put("totalCredits", totalCredits);
        ObjectNode categories = root.putObject("creditsByCategory");
        totalsByCategory.forEach(categories::put);
        return root.toString();
    }
}
