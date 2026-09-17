package com.zhku.knowledge.rag;

import org.springframework.ai.openai.api.OpenAiApi;

/**
 * OpenAI-compatible 端点的路径归一化。
 *
 * <p>Spring AI 的 {@link OpenAiApi} 默认在 baseUrl 之后追加 `/v1/chat/completions`
 * 与 `/v1/embeddings`。而国内厂商的兼容模式端点通常**已经把版本段写进 baseUrl**
 * （如百炼 `https://dashscope.aliyuncs.com/compatible-mode/v1`），此时若沿用默认路径
 * 会拼出 `/compatible-mode/v1/v1/chat/completions`（404）。本类据此显式指定路径，
 * 让"配置里写什么端点就打到什么端点"。</p>
 */
final class OpenAiCompatibleApiPaths {

    private OpenAiCompatibleApiPaths() {
    }

    /**
     * 按 baseUrl 是否已含版本段，返回配置好的 {@link OpenAiApi.Builder}。
     *
     * @param baseUrl profile 声明的端点根（可能已含 `/v1`）
     * @param apiKey 运行时从环境变量解析出的密钥（不落配置、不写日志）
     */
    static OpenAiApi.Builder builder(String baseUrl, String apiKey) {
        OpenAiApi.Builder builder = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey);
        if (baseUrl.endsWith("/v1")) {
            // 端点自带版本段：改用不含 /v1 的相对路径，避免重复。
            builder.completionsPath("/chat/completions").embeddingsPath("/embeddings");
        }
        return builder;
    }
}
