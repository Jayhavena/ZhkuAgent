package com.zhku.knowledge.rag;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * 按 profile 构建 Spring AI 对话客户端。
 *
 * <p>密钥在**此处、运行时**从环境变量解析（`ApiKeyResolver`），不缓存到字段、
 * 不写进配置；baseUrl 与模型名来自 profile 声明。</p>
 */
public final class SpringAiChatModelClientFactory {

    private SpringAiChatModelClientFactory() {
    }

    public static ModelChatClient create(RagProviderProfile profile, ApiKeyResolver apiKeyResolver) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(profile.baseUrl())
                .apiKey(apiKeyResolver.requireApiKey(profile))
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(profile.chatModel()).build())
                .build();
        return new SpringAiChatModelClient(chatModel, profile.name());
    }
}
