package com.zhku.knowledge.serviceguide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 绑定表：显式、可审核的"决策点 → 清单"映射。
 *
 * <p>为什么绑定不放数据库：绑定本质是路由决策，不是清单属性；首批只有一个决策点，
 * 为它建表还要额外治理"谁写、怎么审"。放进版本库后，绑定变更与代码变更一起被评审，
 * 且解析失败的方向是 fail-safe（多转人工，不会静默出错卡）。</p>
 */
class ServiceGuideBindingsTest {

    @Test
    void shouldExposeFirstBatchBindingForElectiveAdjustment() {
        ServiceGuideBindings bindings = ServiceGuideBindings.firstBatch();

        assertThat(bindings.all()).hasSize(1);
        var binding = bindings.find(ServiceGuideBindings.ELECTIVE_ADJUST);
        assertThat(binding).isPresent();
        assertThat(binding.get().guideIdentifier()).isEqualTo("elective-adjust");
        // 版本显式写出：换版本＝改代码＝走评审，不允许"自动取最新"这种隐式行为。
        assertThat(binding.get().guideVersion()).isNotBlank();
    }

    @Test
    void shouldReturnEmptyForUnknownBindingKey() {
        assertThat(ServiceGuideBindings.firstBatch().find("not-bound")).isEmpty();
        assertThat(ServiceGuideBindings.firstBatch().find(null)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateOrBlankBindings() {
        var duplicate = List.of(
                new ServiceGuideBindings.Binding("k", "g1", "v1"),
                new ServiceGuideBindings.Binding("k", "g2", "v1"));
        assertThatThrownBy(() -> new ServiceGuideBindings(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        assertThatThrownBy(() -> new ServiceGuideBindings.Binding(" ", "g", "v"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绑定键");
    }
}
