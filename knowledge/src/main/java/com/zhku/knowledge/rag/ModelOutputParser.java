package com.zhku.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型输出解析（协议无关）。
 *
 * <p>被自实现 OpenAI-compatible 客户端与 Spring AI 适配器共用：解析规则必须唯一，
 * 否则两个客户端对"结构非法"的判定会漂移，降级行为随之不一致。</p>
 */
public final class ModelOutputParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelOutputParser() {
    }

    /**
     * 解析模型返回的 JSON 文本。
     *
     * @param jsonText 模型输出的 JSON 文本（answer/uncertainty/citationIds/handoffRequired）
     * @throws ModelOutputFormatException 非对象、缺字段、类型/非空约束或分值范围非法
     */
    public static ModelResponse parse(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new ModelOutputFormatException("模型输出不能为空");
        }
        JsonNode payload;
        try {
            payload = MAPPER.readTree(jsonText);
        } catch (IOException exception) {
            throw new ModelOutputFormatException("模型输出不是合法 JSON", exception);
        }
        if (payload == null || !payload.isObject()) {
            throw new ModelOutputFormatException("模型输出根节点必须是对象");
        }
        JsonNode answer = payload.get("answer");
        JsonNode uncertainty = payload.get("uncertainty");
        JsonNode citationIds = payload.get("citationIds");
        JsonNode handoff = payload.get("handoffRequired");
        // 必须先检查类型：宽松转换会把协议错误变成看似合法的回答或默认“不转人工”。
        if (answer == null || !answer.isTextual() || answer.textValue().isBlank()
                || uncertainty == null || !uncertainty.isNumber()
                || citationIds == null || !citationIds.isArray()
                || handoff == null || !handoff.isBoolean()) {
            throw new ModelOutputFormatException("模型输出字段缺失、为空或类型非法");
        }
        double score = uncertainty.doubleValue();
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new ModelOutputFormatException("模型自报分值必须是0至1的有限数");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode id : citationIds) {
            if (!id.isTextual() || id.textValue().isBlank()) {
                throw new ModelOutputFormatException("引用标识必须是非空白字符串");
            }
            ids.add(id.textValue());
        }
        // 转人工响应可带空引用；是否能作为答案下发仍由守卫检查，不能靠解析成功放行。
        return new ModelResponse(
                answer.textValue(),
                score,
                ids,
                handoff.booleanValue()
        );
    }
}
