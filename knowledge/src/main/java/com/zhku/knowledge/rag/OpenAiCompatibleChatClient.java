package com.zhku.knowledge.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI-compatible 协议的对话客户端（DeepSeek/Kimi/GLM/Qwen/混元 等同协议端点）。
 *
 * <p>只实现 `/chat/completions` 的最小契约：把当轮获准证据与不可变系统约束
 * 组装成消息，要求模型以 JSON 返回 answer/uncertainty/citationIds/handoffRequired。
 * 密钥按调用时从环境变量解析，不驻留在字段中；日志与异常信息均不含密钥。</p>
 *
 * <p>注意：本类通过合同测试不代表供应商已"支持"——见状态板与证据账本中
 * "未通过合同测试的端点不得写入已支持"的约定。</p>
 */
public final class OpenAiCompatibleChatClient implements ModelChatClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RagProviderProfile profile;
    private final ApiKeyResolver apiKeyResolver;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatClient(RagProviderProfile profile, ApiKeyResolver apiKeyResolver) {
        this(profile, apiKeyResolver, HttpClient.newBuilder()
                .connectTimeout(profile.timeout())
                .build());
    }

    OpenAiCompatibleChatClient(
            RagProviderProfile profile,
            ApiKeyResolver apiKeyResolver,
            HttpClient httpClient
    ) {
        this.profile = Objects.requireNonNull(profile, "profile 不能为空");
        this.apiKeyResolver = Objects.requireNonNull(apiKeyResolver, "密钥解析器不能为空");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP 客户端不能为空");
    }

    @Override
    public ModelResponse call(ModelRequest request) {
        Objects.requireNonNull(request, "模型请求不能为空");
        String apiKey = apiKeyResolver.requireApiKey(profile);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(profile.baseUrl()) + "/chat/completions"))
                .timeout(profile.timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(request)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            throw new ModelCallTimeoutException("模型调用超时（profile=" + profile.name() + "）", exception);
        } catch (IOException exception) {
            // 异常信息只带 profile 名前缀，避免任何凭据内容通过 cause 泄漏到日志。
            throw new ModelCallFailedException("模型连接失败（profile=" + profile.name() + "）", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelCallFailedException("模型调用被中断（profile=" + profile.name() + "）", exception);
        }

        if (response.statusCode() == 429) {
            throw new ModelCallRateLimitException("模型限流（profile=" + profile.name() + "）");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ModelCallFailedException(
                    "模型返回非成功状态码 " + response.statusCode() + "（profile=" + profile.name() + "）");
        }
        return parse(response.body());
    }

    private String buildPayload(ModelRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", profile.chatModel());
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemConstraints());
        ObjectNode user = messages.addObject().put("role", "user");
        StringBuilder prompt = new StringBuilder(request.question()).append("\n\n获准证据：\n");
        for (ApprovedEvidence evidence : request.evidence()) {
            prompt.append('[').append(evidence.citationId()).append("] ")
                    .append(evidence.text()).append('\n');
        }
        user.put("content", prompt.toString());
        // 要求 JSON 输出，降低解析失败率；结构非法仍由解析层降级，不在此处重试。
        root.putObject("response_format").put("type", "json_object");
        return root.toString();
    }

    private ModelResponse parse(String body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException exception) {
            throw new ModelOutputFormatException("模型响应不是合法 JSON", exception);
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new ModelOutputFormatException("模型响应缺少 choices[0].message.content");
        }
        // 内层 JSON 的解析规则与 Spring AI 适配器共用，避免两条链路降级判定漂移。
        return ModelOutputParser.parse(content.asText());
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 供治理台/日志使用的安全描述：不含密钥。 */
    public String describe() {
        return profile.describe();
    }

    /** 超时配置只读暴露，供装配层校验（不暴露密钥）。 */
    public Duration timeout() {
        return profile.timeout();
    }
}
