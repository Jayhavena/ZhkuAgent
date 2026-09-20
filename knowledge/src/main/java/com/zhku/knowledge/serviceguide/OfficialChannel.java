package com.zhku.knowledge.serviceguide;

import java.time.Instant;
import java.util.Objects;

/**
 * 办事清单的官方渠道。
 *
 * <p><b>为什么渠道值与来源定位都强制必填</b>：渠道是"去哪办、找谁办"的唯一出口，一旦允许空值，
 * 模型或维护者就可能塞进审核资料里没有的联系方式；而没有 `sourceLocator` 的渠道无法核对
 * "它是不是审核资料里写明的那条"。这两条约束合起来，才撑得起"渠道只能来自审核资料"的承诺。</p>
 */
public record OfficialChannel(
        int channelOrder,
        ChannelType channelType,
        String channelName,
        String channelValue,
        String sourceLocator,
        Instant verifiedAt
) {

    /** 渠道类型；类型决定人工渠道路由的优先级（如个案认定优先办公室面办）。 */
    public enum ChannelType {
        SCHOOL_OFFICE,
        PHONE,
        WEBSITE,
        EMAIL
    }

    public OfficialChannel {
        if (channelOrder < 1) {
            throw new IllegalArgumentException("渠道序号必须从 1 开始：" + channelOrder);
        }
        Objects.requireNonNull(channelType, "渠道类型不能为空");
        channelName = Objects.requireNonNull(channelName, "渠道名称不能为空").strip();
        if (channelName.isEmpty()) {
            throw new IllegalArgumentException("渠道名称不能为空");
        }
        channelValue = Objects.requireNonNull(channelValue, "渠道不能为空").strip();
        if (channelValue.isEmpty()) {
            throw new IllegalArgumentException("渠道不能为空：渠道值（电话/地点/网址）必须来自审核资料");
        }
        sourceLocator = Objects.requireNonNull(sourceLocator, "来源不能为空").strip();
        if (sourceLocator.isEmpty()) {
            throw new IllegalArgumentException("来源不能为空：渠道必须携带审核资料定位");
        }
        Objects.requireNonNull(verifiedAt, "渠道核对时间不能为空");
    }
}
