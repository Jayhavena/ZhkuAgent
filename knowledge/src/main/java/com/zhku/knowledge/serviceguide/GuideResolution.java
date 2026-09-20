package com.zhku.knowledge.serviceguide;

import java.util.List;
import java.util.Objects;

/**
 * 清单解析结果：要么给出完整清单，要么明确转人工。
 *
 * <p><b>为什么是"清单或转人工"二选一，而不是"清单+可空渠道"</b>：清单的价值在"下一步怎么做、
 * 去哪办"；拿不到带已审核渠道的当前有效清单时，任何"半个清单"都会被误读成可办结方案。
 * 二选一把这种歧义在类型上排除掉。</p>
 */
public record GuideResolution(
        ServiceGuide guide,
        boolean handoffRequired,
        HumanHandoffReason handoffReason,
        String guideResolutionMessage
) {

    public static GuideResolution ofGuide(ServiceGuide guide) {
        Objects.requireNonNull(guide, "清单不能为空");
        return new GuideResolution(guide, false, null,
                "已按已审核清单给出办理指引，请以当学期官方通知为准。");
    }

    public static GuideResolution ofHandoff(HumanHandoffReason reason, String message) {
        Objects.requireNonNull(reason, "转人工原因不能为空");
        Objects.requireNonNull(message, "转人工说明不能为空");
        return new GuideResolution(null, true, reason, message);
    }
}
