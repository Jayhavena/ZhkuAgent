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
     * @throws ModelOutputFormatException 结构非法（非 JSON、缺字段、citationIds 非数组）
     */
    public static ModelResponse parse(String jsonText) {
        JsonNode payload;
        try {
            payload = MAPPER.readTree(jsonText);
        } catch (IOException exception) {
            throw new ModelOutputFormatException("模型输出不是合法 JSON", exception);
        }
        JsonNode answer = payload.get("answer");
        JsonNode uncertainty = payload.get("uncertainty");
        JsonNode citationIds = payload.get("citationIds");
        if (answer == null || uncertainty == null || citationIds == null || !citationIds.isArray()) {
            throw new ModelOutputFormatException("模型输出缺少 answer/uncertainty/citationIds 字段");
        }
        List<String> ids = new ArrayList<>();
        citationIds.forEach(node -> ids.add(node.asText()));
        return new ModelResponse(
                answer.asText(),
                uncertainty.asDouble(),
                ids,
                payload.path("handoffRequired").asBoolean(false)
        );
    }
}
