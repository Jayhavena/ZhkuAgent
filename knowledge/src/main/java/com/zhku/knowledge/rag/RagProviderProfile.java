package com.zhku.knowledge.rag;

import java.time.Duration;
import java.util.Objects;

/**
 * 一个模型供应商 profile 的 provider-neutral 配置。
 *
 * <p>刻意只保存"API key 的环境变量名"而不是 key 本身：密钥只从环境变量读取，
 * 既不进配置文件、也不进日志与异常。{@link #describe()} 是唯一允许对外输出的
 * 描述形式，其内容不含任何密钥值。</p>
 *
 * @param name profile 名称（如 deepseek / kimi / mock）
 * @param baseUrl OpenAI-compatible 端点根地址（必须 HTTPS）
 * @param apiKeyEnvVar 读取密钥的环境变量名
 * @param chatModel 对话模型名
 * @param embeddingModel embedding 模型名
 * @param embeddingDimensions embedding 维度（与索引 profile 声明一致）
 * @param timeout 单次调用超时
 * @param indexProfile 该 profile 对应的索引 profile 标识
 */
public record RagProviderProfile(
        String name,
        String baseUrl,
        String apiKeyEnvVar,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        Duration timeout,
        String indexProfile
) {

    public RagProviderProfile {
        name = requireText(name, "profile 名称不能为空");
        baseUrl = requireText(baseUrl, "baseUrl 不能为空");
        if (!baseUrl.startsWith("https://") && !isLoopbackStub(baseUrl)) {
            // 明文 HTTP 会泄露 API key 与问题内容；仅允许本机回环地址用于合同测试桩。
            throw new IllegalArgumentException("模型端点必须使用 HTTPS：" + baseUrl);
        }
        apiKeyEnvVar = requireText(apiKeyEnvVar, "API key 环境变量名不能为空");
        chatModel = requireText(chatModel, "对话模型名不能为空");
        embeddingModel = requireText(embeddingModel, "embedding 模型名不能为空");
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException("embedding 维度必须为正数");
        }
        Objects.requireNonNull(timeout, "超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("超时必须为正数");
        }
        indexProfile = requireText(indexProfile, "索引 profile 不能为空");
    }

    /** 可对外输出的描述：只含结构信息与密钥来源变量名，绝不含密钥值。 */
    public String describe() {
        return "profile=" + name + ", baseUrl=" + baseUrl + ", chatModel=" + chatModel
                + ", embeddingModel=" + embeddingModel + ", dimensions=" + embeddingDimensions
                + ", timeout=" + timeout + ", indexProfile=" + indexProfile
                + ", apiKeyFrom=" + apiKeyEnvVar;
    }

    private static boolean isLoopbackStub(String baseUrl) {
        return baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost");
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
