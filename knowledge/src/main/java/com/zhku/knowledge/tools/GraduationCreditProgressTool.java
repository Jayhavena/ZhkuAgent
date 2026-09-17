package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** 仅按调用方给出的类别目标核算毕业学分缺口的纯计算工具。 */
public final class GraduationCreditProgressTool implements CampusTool {

    static final String NAME = "assess_graduation_credit_progress";
    private static final CampusToolDescriptor DESCRIPTOR = new CampusToolDescriptor(
            NAME,
            "核算传入培养方案类别目标的学分缺口；不能确认毕业资格。",
            "{\"type\":\"object\",\"required\":[\"requirements\",\"completedCreditsByCategory\"]}",
            CampusToolAccessMode.PURE_CALCULATION
    );

    @Override
    public CampusToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CampusToolResult execute(String argumentsJson) {
        try {
            JsonNode root = ToolJsonSupport.parseObject(argumentsJson);
            Map<String, BigDecimal> requirements = readRequirements(root);
            Map<String, BigDecimal> completed = readCompleted(root, requirements);
            return CampusToolResult.success(
                    NAME,
                    resultJson(requirements, completed),
                    "只按传入学分目标核算缺口；课程性质、学制、论文、处分等条件仍需人工核对，不能确认毕业资格。"
            );
        } catch (JsonProcessingException | ToolJsonSupport.ToolInputException exception) {
            return ToolJsonSupport.invalidArguments(NAME, exception.getMessage());
        }
    }

    private static Map<String, BigDecimal> readRequirements(JsonNode root) {
        Map<String, BigDecimal> requirements = new TreeMap<>();
        for (JsonNode requirement : ToolJsonSupport.requiredArray(root, "requirements")) {
            if (!requirement.isObject()) {
                throw new ToolJsonSupport.ToolInputException("requirements 中每项必须是对象");
            }
            String category = ToolJsonSupport.requiredText(requirement, "category");
            BigDecimal credits = ToolJsonSupport.requiredNonNegativeDecimal(
                    requirement,
                    "requiredCredits"
            );
            if (requirements.putIfAbsent(category, credits) != null) {
                throw new ToolJsonSupport.ToolInputException("重复的培养方案类别：" + category);
            }
        }
        if (requirements.isEmpty()) {
            throw new ToolJsonSupport.ToolInputException("requirements 不能为空");
        }
        return requirements;
    }

    private static Map<String, BigDecimal> readCompleted(
            JsonNode root,
            Map<String, BigDecimal> requirements
    ) {
        JsonNode completedNode = ToolJsonSupport.requiredObject(root, "completedCreditsByCategory");
        Map<String, BigDecimal> completed = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = completedNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String category = field.getKey().strip();
            if (category.isEmpty() || !requirements.containsKey(category)) {
                throw new ToolJsonSupport.ToolInputException("已修学分类别不在培养方案目标中：" + field.getKey());
            }
            JsonNode value = field.getValue();
            if (!value.isNumber() || value.decimalValue().signum() < 0) {
                throw new ToolJsonSupport.ToolInputException("已修学分必须是非负数：" + category);
            }
            completed.put(category, value.decimalValue());
        }
        if (!completed.keySet().equals(requirements.keySet())) {
            throw new ToolJsonSupport.ToolInputException("已修学分必须覆盖每个培养方案类别");
        }
        return completed;
    }

    private static String resultJson(
            Map<String, BigDecimal> requirements,
            Map<String, BigDecimal> completed
    ) {
        ObjectNode root = ToolJsonSupport.MAPPER.createObjectNode();
        ArrayNode progress = root.putArray("progress");
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> requirement : requirements.entrySet()) {
            BigDecimal completedCredits = completed.get(requirement.getKey());
            BigDecimal remaining = requirement.getValue()
                    .subtract(completedCredits)
                    .max(BigDecimal.ZERO);
            ObjectNode category = progress.addObject();
            category.put("category", requirement.getKey());
            category.put("requiredCredits", requirement.getValue());
            category.put("completedCredits", completedCredits);
            category.put("remainingCredits", remaining);
            totalRemaining = totalRemaining.add(remaining);
        }
        root.put("totalRemainingCredits", totalRemaining);
        return root.toString();
    }
}
