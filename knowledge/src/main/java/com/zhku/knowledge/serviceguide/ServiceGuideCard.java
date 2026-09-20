package com.zhku.knowledge.serviceguide;

import java.util.List;
import java.util.Objects;

/**
 * 回答卡上的清单视图（"下一步 / 需确认事项 / 官方人工渠道"）。
 *
 * <p><b>为什么是独立视图而不是直接下发 {@link ServiceGuide}</b>：回答卡只需要"怎么做、
 * 要确认什么、去哪办"；把审核说明、软引用、发布时间等内部字段一起下发，既扩大了攻击面，
 * 也让前端误以为可以解析清单全文。视图只保留学生决策需要的最小集合。</p>
 *
 * <p><b>handoffOnly 的语义</b>：高风险或证据不足时，清单不可下发，卡片只保留人工渠道与
 * 需确认事项（`nextSteps` 恒为空）——绝不伪装成"可按步骤办结"的方案。</p>
 */
public record ServiceGuideCard(
        String guideIdentifier,
        String guideVersion,
        String title,
        String applicableScope,
        List<NextStep> nextSteps,
        List<String> confirmations,
        List<Channel> officialChannels,
        boolean handoffOnly
) {

    /** 下一步：一条步骤 + 是否需要身份/资格个案核对（需要时前端必须同时提示转人工）。 */
    public record NextStep(int order, String instruction, boolean requiresIdentityCheck) {
    }

    /** 官方人工渠道：类型 + 名称 + 值；不带审核定位（审计信息留在库与引用明细中）。 */
    public record Channel(String channelType, String name, String value) {
    }

    public ServiceGuideCard {
        guideIdentifier = Objects.requireNonNull(guideIdentifier, "清单标识不能为空").strip();
        guideVersion = Objects.requireNonNull(guideVersion, "清单版本不能为空").strip();
        title = Objects.requireNonNull(title, "清单标题不能为空").strip();
        applicableScope = applicableScope == null ? null : applicableScope.strip();
        nextSteps = List.copyOf(nextSteps == null ? List.of() : nextSteps);
        confirmations = List.copyOf(confirmations == null ? List.of() : confirmations);
        officialChannels = List.copyOf(officialChannels == null ? List.of() : officialChannels);
        if (handoffOnly && !nextSteps.isEmpty()) {
            throw new IllegalArgumentException("转人工卡片不能携带下一步步骤");
        }
    }

    /** 从当前有效清单构建完整卡片（正常路径）。 */
    public static ServiceGuideCard from(ServiceGuide guide) {
        Objects.requireNonNull(guide, "清单不能为空");
        List<NextStep> nextSteps = guide.steps().stream()
                .map(step -> new NextStep(step.stepOrder(), step.instruction(),
                        step.requiresIdentityCheck()))
                .toList();
        List<String> confirmations = guide.riskNotices().stream()
                .map(ServiceGuide.RiskNotice::noticeText)
                .toList();
        List<Channel> channels = guide.channels().stream()
                .map(channel -> new Channel(channel.channelType().name(),
                        channel.channelName(), channel.channelValue()))
                .toList();
        return new ServiceGuideCard(guide.guideIdentifier(), guide.guideVersion(), guide.title(),
                guide.applicableScope(), nextSteps, confirmations, channels, false);
    }

    /** 转人工路径的卡片：只保留人工渠道与需确认事项，不给任何步骤。 */
    public static ServiceGuideCard ofHandoff(
            HumanHandoffReason reason,
            HumanHandoffRoute.HandoffDecision decision,
            List<String> confirmations) {
        Objects.requireNonNull(reason, "转人工原因不能为空");
        Objects.requireNonNull(decision, "人工渠道决策不能为空");
        List<Channel> channels = decision.channels().stream()
                .map(channel -> new Channel(channel.channelType().name(),
                        channel.channelName(), channel.channelValue()))
                .toList();
        return new ServiceGuideCard(
                "handoff-" + reason.name().toLowerCase(),
                "n/a",
                decision.message(),
                null,
                List.of(),
                confirmations == null ? List.of() : List.copyOf(confirmations),
                channels,
                true);
    }
}
