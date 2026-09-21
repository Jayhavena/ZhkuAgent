package com.zhku.knowledge.serviceguide;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 卡片提供者：把"决策点 + 场景"折算成回答卡上的清单视图。
 *
 * <p><b>两条路径的差别是刻意的</b>：清单当前有效时给完整卡（下一步 + 需确认事项 + 官方渠道）；
 * 一旦落入转人工（个案认定、证据冲突、规则缺失、模型拒答），只给"人工渠道 + 需确认事项"，
 * `nextSteps` 恒空——避免学生把"半份方案"当成可办结步骤。</p>
 *
 * <p><b>清单解析不到时的行为</b>：仍给转人工卡，但渠道列表为空（无已审核渠道可下发），
 * 只留下方向性说明。绝不为了让卡片"看起来完整"而拼一个联系方式出来。</p>
 */
public final class ServiceGuideCardProvider {

    private final ServiceGuideBindings bindings;
    private final ServiceGuideResolver resolver;

    public ServiceGuideCardProvider(ServiceGuideBindings bindings, ServiceGuideResolver resolver) {
        this.bindings = Objects.requireNonNull(bindings, "绑定表不能为空");
        this.resolver = Objects.requireNonNull(resolver, "清单解析器不能为空");
    }

    /** 完整清单卡：仅当绑定存在且清单当前有效。未绑定/不可解析一律返回空。 */
    public Optional<ServiceGuideCard> fullCard(String bindingKey) {
        return bindings.find(bindingKey)
                .map(binding -> resolver.resolve(binding.guideIdentifier(), binding.guideVersion()))
                .filter(resolution -> !resolution.handoffRequired())
                .map(resolution -> ServiceGuideCard.from(resolution.guide()));
    }

    /** 转人工卡：绑定存在即给（即便清单不可解析），但绝不携带步骤。 */
    public Optional<ServiceGuideCard> handoffCard(String bindingKey, HumanHandoffReason reason) {
        Objects.requireNonNull(reason, "转人工原因不能为空");
        return bindings.find(bindingKey).map(binding -> {
            GuideResolution resolution = resolver.resolve(
                    binding.guideIdentifier(), binding.guideVersion());
            if (resolution.handoffRequired()) {
                return ServiceGuideCard.ofHandoff(reason,
                        HumanHandoffRoute.decide(reason, List.of()), List.of());
            }
            ServiceGuide guide = resolution.guide();
            List<String> confirmations = guide.riskNotices().stream()
                    .map(ServiceGuide.RiskNotice::noticeText)
                    .toList();
            return ServiceGuideCard.ofHandoff(reason,
                    HumanHandoffRoute.decide(reason, guide.channels()), confirmations);
        });
    }
}
