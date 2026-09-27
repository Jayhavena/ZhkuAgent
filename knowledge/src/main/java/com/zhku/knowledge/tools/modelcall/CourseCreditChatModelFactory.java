package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.Objects;

/** CLI 与本机协议合同共用的模型构建路径，避免测试到另一份端点拼接逻辑。 */
final class CourseCreditChatModelFactory {

    private CourseCreditChatModelFactory() {
    }

    static OpenAiChatModel create(RagProviderProfile profile, ApiKeyResolver keyResolver) {
        Objects.requireNonNull(profile, "模型 profile 不能为空");
        Objects.requireNonNull(keyResolver, "密钥解析器不能为空");
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(profile.baseUrl())
                .apiKey(keyResolver.requireApiKey(profile));
        if (profile.baseUrl().endsWith("/v1")) {
            // 配置中的兼容端点已含版本段时，默认路径会重复拼接 /v1。
            apiBuilder.completionsPath("/chat/completions");
        }
        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(OpenAiChatOptions.builder().model(profile.chatModel()).build())
                .build();
    }
}
