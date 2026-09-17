package com.zhku.knowledge.rag;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从配置读取 provider profile；一次只允许一个激活 profile。
 *
 * <p>配置键沿用冻结契约的 `zhku.` 前缀（已登记于状态板第五节）：
 * <pre>
 * zhku.rag.enabled                                 是否启用受控检索增强
 * zhku.rag.active-profile                          当前启用的 profile 名
 * zhku.rag.profiles.&lt;name&gt;.base-url                HTTPS 端点
 * zhku.rag.profiles.&lt;name&gt;.api-key-env-var         密钥环境变量名
 * zhku.rag.profiles.&lt;name&gt;.chat-model
 * zhku.rag.profiles.&lt;name&gt;.embedding-model
 * zhku.rag.profiles.&lt;name&gt;.embedding-dimensions
 * zhku.rag.profiles.&lt;name&gt;.timeout-seconds
 * zhku.rag.profiles.&lt;name&gt;.index-profile
 * </pre>
 */
public final class RagProviderProfileResolver {

    private static final String PREFIX = "zhku.rag.";

    private final Map<String, String> properties;

    public RagProviderProfileResolver(Map<String, String> properties) {
        this.properties = Map.copyOf(Objects.requireNonNull(properties, "配置视图不能为空"));
    }

    /** 受控检索增强是否启用；未配置视为关闭（默认不接模型）。 */
    public boolean enabled() {
        return Boolean.parseBoolean(properties.getOrDefault(PREFIX + "enabled", "false"));
    }

    /** 当前激活 profile 名；未配置或未启用时为空。 */
    public Optional<String> activeProfileName() {
        if (!enabled()) {
            return Optional.empty();
        }
        String name = properties.get(PREFIX + "active-profile");
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name.strip());
    }

    /**
     * 读取激活 profile。要求：启用 + 指定了 profile 名 + 该 profile 全部键齐全。
     */
    public Optional<RagProviderProfile> activeProfile() {
        Optional<String> name = activeProfileName();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(profile(name.get()));
    }

    /** 读取指定 profile（不要求它是激活 profile，供治理台展示与合同测试使用）。 */
    public RagProviderProfile profile(String name) {
        Objects.requireNonNull(name, "profile 名称不能为空");
        return new RagProviderProfile(
                name,
                require(name, "base-url"),
                require(name, "api-key-env-var"),
                require(name, "chat-model"),
                require(name, "embedding-model"),
                requireInt(name, "embedding-dimensions"),
                Duration.ofSeconds(requireInt(name, "timeout-seconds")),
                require(name, "index-profile")
        );
    }

    /** 已完整配置的 profile 名列表（缺键的不算"已支持"）。 */
    public List<String> configuredProfileNames() {
        List<String> names = new ArrayList<>();
        String marker = PREFIX + "profiles.";
        for (String key : properties.keySet()) {
            if (!key.startsWith(marker)) {
                continue;
            }
            String remainder = key.substring(marker.length());
            int dot = remainder.indexOf('.');
            if (dot > 0) {
                String name = remainder.substring(0, dot);
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        List<String> complete = new ArrayList<>();
        for (String name : names) {
            try {
                profile(name);
                complete.add(name);
            } catch (RuntimeException exception) {
                // 配置不完整的 profile 不计入"已支持"：不能因为键名存在就宣称可用。
                continue;
            }
        }
        return List.copyOf(complete);
    }

    private String require(String profileName, String suffix) {
        String key = PREFIX + "profiles." + profileName + "." + suffix;
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少配置项：" + key);
        }
        return value.strip();
    }

    private int requireInt(String profileName, String suffix) {
        String raw = require(profileName, suffix);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "配置项必须为整数：zhku.rag.profiles." + profileName + "." + suffix
                            + "=" + raw.toLowerCase(Locale.ROOT), exception);
        }
    }
}
