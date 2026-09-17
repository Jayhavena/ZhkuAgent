package com.zhku.knowledge.rag;

import java.util.Map;
import java.util.Objects;

/**
 * API key 解析器：按环境变量名取值，取不到就明确失败。
 *
 * <p>三条纪律：不在任何返回值/异常信息中回显密钥；不做"缺密钥就用空串继续"的
 * 兜底（那会把配置错误伪装成模型故障）；不缓存到字段里供其他地方顺手读取。</p>
 */
public final class ApiKeyResolver {

    private final Map<String, String> environment;

    /** 生产装配使用进程环境；测试可注入受控环境。 */
    public ApiKeyResolver(Map<String, String> environment) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "环境变量视图不能为空"));
    }

    public static ApiKeyResolver systemEnvironment() {
        return new ApiKeyResolver(System.getenv());
    }

    /**
     * 读取指定 profile 的密钥。
     *
     * @throws IllegalStateException 未配置对应环境变量
     */
    public String requireApiKey(RagProviderProfile profile) {
        Objects.requireNonNull(profile, "profile 不能为空");
        String value = environment.get(profile.apiKeyEnvVar());
        if (value == null || value.isBlank()) {
            // 只报变量名，不报值——异常信息可能进入日志。
            throw new IllegalStateException(
                    "未配置模型密钥环境变量 " + profile.apiKeyEnvVar() + "（profile=" + profile.name() + "）");
        }
        return value;
    }
}
