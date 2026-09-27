package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 对调用方传入的课程学分做确定性汇总的纯计算工具。 */
public final class CourseCreditSummaryTool implements CampusTool {

    static final String NAME = "summarize_course_credits";
    private static final CampusToolDescriptor DESCRIPTOR = new CampusToolDescriptor(
            NAME,
            "汇总传入课程的总学分与类别学分；不判断课程资格或培养方案归属。",
            """
            {
              "type":"object",
              "required":["courses"],
              "properties":{
                "courses":{
                  "type":"array",
                  "items":{
                    "type":"object",
                    "required":["courseId","category","credits"],
                    "properties":{
                      "courseId":{"type":"string","minLength":1},
                      "category":{"type":"string","minLength":1},
                      "credits":{"type":"number","minimum":0}
                    }
                  }
                }
              }
            }
            """,
            CampusToolAccessMode.PURE_CALCULATION
    );

    @Override
    public CampusToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CampusToolResult execute(String argumentsJson) {
        try {
            List<Course> courses = parseCourses(argumentsJson);
            Map<String, BigDecimal> totalsByCategory = new TreeMap<>();
            BigDecimal totalCredits = BigDecimal.ZERO;
            for (Course course : courses) {
                totalsByCategory.merge(course.category(), course.credits(), BigDecimal::add);
                totalCredits = totalCredits.add(course.credits());
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

    /** 适配层在进入注册表执行前使用同一解析契约，避免 schema 合法性判断与计算器漂移。 */
    public static boolean acceptsArguments(String argumentsJson) {
        try {
            parseCourses(argumentsJson);
            return true;
        } catch (JsonProcessingException | ToolJsonSupport.ToolInputException exception) {
            return false;
        }
    }

    private static List<Course> parseCourses(String argumentsJson) throws JsonProcessingException {
        JsonNode courses = ToolJsonSupport.requiredArray(ToolJsonSupport.parseObject(argumentsJson), "courses");
        List<Course> parsed = new ArrayList<>();
        for (JsonNode course : courses) {
            if (!course.isObject()) {
                throw new ToolJsonSupport.ToolInputException("courses 中每项必须是对象");
            }
            String courseId = ToolJsonSupport.requiredText(course, "courseId");
            String category = ToolJsonSupport.requiredText(course, "category");
            BigDecimal credits = ToolJsonSupport.requiredNonNegativeDecimal(course, "credits");
            parsed.add(new Course(courseId, category, credits));
        }
        return parsed;
    }

    private record Course(String courseId, String category, BigDecimal credits) {
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
