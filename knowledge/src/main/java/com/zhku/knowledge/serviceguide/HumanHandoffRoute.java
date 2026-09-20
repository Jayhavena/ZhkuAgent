package com.zhku.knowledge.serviceguide;

import java.util.List;
import java.util.Objects;

/**
 * 人工渠道路由：把"为什么要转"映射成"该找哪些渠道"。
 *
 * <p><b>为什么路由只筛选、不生成</b>：渠道的每次出现都必须能回溯到审核资料
 * （{@link OfficialChannel#sourceLocator()}）。路由若允许生成兜底联系方式，
 * "渠道只能来自审核资料"的承诺就塌了——所以选不出偏好类型时退回全部已审核渠道，
 * 一个都没有时只给一句不带任何联系方式的通用指引。</p>
 */
public final class HumanHandoffRoute {

    private HumanHandoffRoute() {
    }

    /**
     * 人工渠道决策。
     *
     * @param channels 实际应呈现的渠道（来自已审核集合，可能为空列表）
     * @param message  面向学生的转人工说明
     */
    public record HandoffDecision(HumanHandoffReason handoffReason,
                                  List<OfficialChannel> channels,
                                  String message) {
        public HandoffDecision {
            Objects.requireNonNull(handoffReason, "转人工原因不能为空");
            channels = List.copyOf(channels == null ? List.of() : channels);
            Objects.requireNonNull(message, "转人工说明不能为空");
        }
    }

    /** 个案与证据冲突类原因优先办公室面办渠道；模型拒答无偏好，按审核顺序原样呈现。 */
    public static HandoffDecision decide(HumanHandoffReason reason, List<OfficialChannel> reviewedChannels) {
        Objects.requireNonNull(reason, "转人工原因不能为空");
        List<OfficialChannel> channels = reviewedChannels == null
                ? List.of() : List.copyOf(reviewedChannels);

        if (channels.isEmpty()) {
            // 不发明联系方式：只指方向，具体渠道由学生通过学校官方入口获取。
            return new HandoffDecision(reason, List.of(),
                    "请通过学校官方渠道核实办理方式；本系统未登记可下发的联系方式。");
        }
        if (reason == HumanHandoffReason.MODEL_REFUSAL) {
            return new HandoffDecision(reason, channels,
                    "系统无法就该问题给出确定回答，请通过以下已审核渠道人工核实。");
        }
        List<OfficialChannel> offices = channels.stream()
                .filter(channel -> channel.channelType()
                        == OfficialChannel.ChannelType.SCHOOL_OFFICE)
                .toList();
        List<OfficialChannel> selected = offices.isEmpty() ? channels : offices;
        return new HandoffDecision(reason, selected,
                "该事项需要人工核对，请优先通过以下官方渠道办理或确认。");
    }
}
