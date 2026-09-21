package com.zhku.knowledge.serviceguide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 决策点 → 办事清单的绑定表（显式、可审核）。
 *
 * <p><b>为什么绑定不放进数据库</b>：绑定本质是"路由决策"（哪类问题该附哪份清单），不是清单自身的
 * 属性。首批只有一个决策点，为它建表还要额外治理"谁写、怎么审、何时过期"；放进版本库后，
 * 绑定变更与代码变更一起被评审，且失败方向是 fail-safe——解析不到只会"多转人工"，
 * 不会静默给出错误卡片。</p>
 *
 * <p><b>为什么版本必须显式写出</b>：清单换版要经过审核与发布；若绑定"自动取最新"，
 * 未审核的新版本会绕过评审直接进入学生回答。</p>
 */
public final class ServiceGuideBindings {

    /** 首批唯一决策点：选课调整办理。 */
    public static final String ELECTIVE_ADJUST = "elective-adjust";

    private static final String FIRST_BATCH_GUIDE_VERSION = "2025版";

    /** 一条绑定：决策点键 → 清单标识 + 版本。 */
    public record Binding(String bindingKey, String guideIdentifier, String guideVersion) {

        public Binding {
            bindingKey = requireText(bindingKey, "绑定键不能为空");
            guideIdentifier = requireText(guideIdentifier, "清单标识不能为空");
            guideVersion = requireText(guideVersion, "清单版本不能为空");
        }
    }

    private final Map<String, Binding> bindings;

    public ServiceGuideBindings(List<Binding> bindings) {
        Objects.requireNonNull(bindings, "绑定列表不能为空");
        Map<String, Binding> collected = new LinkedHashMap<>();
        for (Binding binding : bindings) {
            Objects.requireNonNull(binding, "绑定不能为空");
            if (collected.putIfAbsent(binding.bindingKey(), binding) != null) {
                throw new IllegalArgumentException("绑定键重复：" + binding.bindingKey());
            }
        }
        this.bindings = Map.copyOf(collected);
    }

    /** 首批绑定：只有一条，显式写出版本号。 */
    public static ServiceGuideBindings firstBatch() {
        return new ServiceGuideBindings(List.of(new Binding(
                ELECTIVE_ADJUST, ELECTIVE_ADJUST, FIRST_BATCH_GUIDE_VERSION)));
    }

    /** 未绑定或键为空一律返回空——不猜测、不回落"任取一份清单"。 */
    public Optional<Binding> find(String bindingKey) {
        if (bindingKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bindings.get(bindingKey.strip()));
    }

    /** 全部绑定（供账本/状态板核对与测试断言）。 */
    public List<Binding> all() {
        return List.copyOf(bindings.values());
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }
}
