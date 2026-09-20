package com.zhku.knowledge.serviceguide;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 办事清单（不可变）：步骤、材料、官方渠道与风险提示。
 *
 * <p><b>为什么整份清单不可变、且风险提示强制至少一条</b>：清单会被直接渲染进回答卡，
 * 构造后再被改动会造成"展示与审核不一致"；而没有风险提示的清单会让学生误以为
 * 可以照单全收——这与阶段 9"不代办、不认定资格"的边界冲突。</p>
 *
 * <p>关联的规则/通知一律用<b>软引用</b>（标识 + 版本号字符串），不建跨迁移外键：
 * 各线迁移文件互相不可见，建外键会让后合并的一侧回归直接变红。</p>
 */
public record ServiceGuide(
        String guideIdentifier,
        String guideVersion,
        String title,
        String applicableScope,
        List<GuideStep> steps,
        List<Material> materials,
        List<OfficialChannel> channels,
        List<RiskNotice> riskNotices,
        String sourceRuleReference,
        String sourceNoticeReference,
        String reviewNotice,
        boolean studentVisible,
        Instant publishedAt,
        Instant validUntil
) {

    /** 办理所需材料。 */
    public record Material(int materialOrder, String materialName, String note, boolean required) {
        public Material {
            if (materialOrder < 1) {
                throw new IllegalArgumentException("材料序号必须从 1 开始：" + materialOrder);
            }
            materialName = Objects.requireNonNull(materialName, "材料不能为空").strip();
            if (materialName.isEmpty()) {
                throw new IllegalArgumentException("材料名称不能为空");
            }
        }
    }

    /** 风险提示：告诉学生"清单不能替代什么"。 */
    public record RiskNotice(int noticeOrder, String noticeText) {
        public RiskNotice {
            if (noticeOrder < 1) {
                throw new IllegalArgumentException("风险提示序号必须从 1 开始：" + noticeOrder);
            }
            noticeText = Objects.requireNonNull(noticeText, "风险提示不能为空").strip();
            if (noticeText.isEmpty()) {
                throw new IllegalArgumentException("风险提示不能为空");
            }
        }
    }

    public ServiceGuide {
        guideIdentifier = Objects.requireNonNull(guideIdentifier, "清单标识不能为空").strip();
        if (guideIdentifier.isEmpty()) {
            throw new IllegalArgumentException("清单标识不能为空");
        }
        guideVersion = Objects.requireNonNull(guideVersion, "清单版本不能为空").strip();
        title = Objects.requireNonNull(title, "清单标题不能为空").strip();
        steps = steps == null ? List.of() : List.copyOf(steps);
        materials = materials == null ? List.of() : List.copyOf(materials);
        channels = channels == null ? List.of() : List.copyOf(channels);
        riskNotices = riskNotices == null ? List.of() : List.copyOf(riskNotices);

        // 步骤从 1 开始且连续：回答卡按序号展示，断号或跳号会被学生读成"漏了一步"。
        for (int index = 0; index < steps.size(); index++) {
            GuideStep step = steps.get(index);
            if (step.stepOrder() != index + 1) {
                if (index == 0) {
                    throw new IllegalArgumentException("步骤必须从 1 开始（当前从 "
                            + step.stepOrder() + " 开始）");
                }
                throw new IllegalArgumentException("步骤编号必须连续（在序号 "
                        + step.stepOrder() + " 处断号）");
            }
        }
        if (riskNotices.isEmpty()) {
            throw new IllegalArgumentException("风险提示不能为空：清单必须声明它不能替代什么");
        }
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
    }
}
