package com.zhku.knowledge.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.Objects;

/** 工具 JSON 的统一解析与失败包装，避免每个工具各自泄漏解析异常。 */
final class ToolJsonSupport {

    static final ObjectMapper MAPPER = new ObjectMapper(
            com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build()
    );

    private ToolJsonSupport() {
    }

    static JsonNode parseObject(String argumentsJson) throws JsonProcessingException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            throw new ToolInputException("工具参数不能为空");
        }
        JsonNode root = MAPPER.readTree(argumentsJson);
        if (root == null || !root.isObject()) {
            throw new ToolInputException("工具参数必须是 JSON 对象");
        }
        return root;
    }

    static JsonNode requiredArray(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isArray()) {
            throw new ToolInputException("缺少数组字段：" + fieldName);
        }
        return value;
    }

    static JsonNode requiredObject(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new ToolInputException("缺少对象字段：" + fieldName);
        }
        return value;
    }

    static String requiredText(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new ToolInputException("缺少文本字段：" + fieldName);
        }
        return value.asText().strip();
    }

    static BigDecimal requiredNonNegativeDecimal(JsonNode object, String fieldName) {
        JsonNode value = object.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new ToolInputException("缺少数值字段：" + fieldName);
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() < 0) {
            throw new ToolInputException("学分不能为负数：" + fieldName);
        }
        return number;
    }

    static CampusToolResult invalidArguments(String toolName, String message) {
        return CampusToolResult.failure(
                toolName,
                CampusToolStatus.INVALID_ARGUMENT,
                errorJson(message),
                "只按传入参数计算，不能确认个人资格、学籍事实或校务系统状态。"
        );
    }

    static String errorJson(String message) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("error", Objects.requireNonNullElse(message, "参数不符合工具契约"));
        return root.toString();
    }

    static final class ToolInputException extends RuntimeException {

        ToolInputException(String message) {
            super(message);
        }
    }
}
