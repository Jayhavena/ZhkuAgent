package com.zhku.knowledge.serviceguide;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 清单解析器：只放行"有当前有效证据"的清单，其余一律转人工。
 *
 * <p><b>什么算"当前有效证据"</b>：① 仓储能给出已发布且学生可见的版本；② 仍在有效期内
 * （`validUntil` 为排他上界，与既有资格过滤同语义，避免过期清单在截止当天仍被下发）；
 * ③ 至少有一条带来源定位的官方渠道——没有渠道的清单给不出"去哪办"，只能转人工。</p>
 */
public final class ServiceGuideResolver {

    private final ServiceGuideRepository repository;
    private final Clock clock;

    public ServiceGuideResolver(ServiceGuideRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "清单仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public GuideResolution resolve(String guideIdentifier, String guideVersion) {
        Objects.requireNonNull(guideIdentifier, "清单标识不能为空");
        Objects.requireNonNull(guideVersion, "清单版本不能为空");

        // 查不到＝规则缺失（未发布/内部参考/资格待确认/已被替代在仓储层都收敛为空）。
        return repository.findPublished(guideIdentifier, guideVersion)
                .map(this::resolveCurrent)
                .orElseGet(() -> GuideResolution.ofHandoff(
                        HumanHandoffReason.RULE_MISSING,
                        "当前没有可用于该事项的已发布办事清单，请联系人工渠道核实，"
                                + "不要依据过期或未公开的资料办理。"));
    }

    private GuideResolution resolveCurrent(ServiceGuide guide) {
        Instant now = clock.instant();
        if (guide.validUntil() != null && !now.isBefore(guide.validUntil())) {
            return GuideResolution.ofHandoff(
                    HumanHandoffReason.RULE_MISSING,
                    "该办事清单的有效期已结束，请联系人工渠道确认最新办理方式。");
        }
        if (guide.channels().isEmpty()) {
            return GuideResolution.ofHandoff(
                    HumanHandoffReason.RULE_MISSING,
                    "该办事清单尚未登记任何已审核的官方渠道，请联系人工渠道核实。");
        }
        return GuideResolution.ofGuide(guide);
    }
}
