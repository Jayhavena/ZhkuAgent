package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/** CLI 与本机协议合同共用的模型构建路径，避免测试到另一份端点拼接逻辑。 */
public final class CourseCreditChatModelFactory {

    private CourseCreditChatModelFactory() {
    }

    public static CourseCreditModelTransport open(RagProviderProfile profile, ApiKeyResolver keyResolver) {
        Objects.requireNonNull(profile, "模型 profile 不能为空");
        Objects.requireNonNull(keyResolver, "密钥解析器不能为空");
        if (profile.timeout().toMillis() < 1) {
            throw new IllegalArgumentException("模型时限至少为1毫秒");
        }
        String apiKey = keyResolver.requireApiKey(profile);
        // 配置/凭据先验证，失败时不能留下没有关闭者的线程或 selector。
        return new CourseCreditModelTransport(profile.timeout(), (client, sender) -> {
            var requests = new JdkClientHttpRequestFactory(client, sender);
            // Spring 6.2 时限覆盖响应头和正文，包括持续发送少量字节的慢流。
            requests.setReadTimeout(profile.timeout());
            OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                    .baseUrl(profile.baseUrl())
                    .apiKey(apiKey)
                    .restClientBuilder(RestClient.builder().requestFactory(requests));
            if (profile.baseUrl().endsWith("/v1")) {
                // 配置中的兼容端点已含版本段时，默认路径会重复拼接 /v1。
                apiBuilder.completionsPath("/chat/completions");
            }
            return OpenAiChatModel.builder()
                    .openAiApi(apiBuilder.build())
                    // 校园每次决策只接受一个工具；显式串行约束避免兼容SDK默认请求让模型同时提出查政策和计算。
                    // 供应商仍可能违约，Service的多调用拒绝守卫必须保留，不能把这个选项当执行许可。
                    .defaultOptions(OpenAiChatOptions.builder().model(profile.chatModel()).parallelToolCalls(false).maxTokens(2048).build())
                    // 一次初始请求、零自动重试，避免默认长退避扩大资源占用或重放未知 POST。
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                    .build();
        });
    }
}
