package com.zhku.knowledge.serviceguide;

import java.util.Objects;

/**
 * 办事清单的一个步骤。
 *
 * @param stepOrder             步骤序号，从 1 开始且连续（由 {@link ServiceGuide} 统一校验）
 * @param instruction           步骤说明（来自审核资料，不得由模型生成）
 * @param requiresIdentityCheck 该步骤是否需要身份/资格个案核对；为 true 时回答卡必须同时提示转人工
 */
public record GuideStep(int stepOrder, String instruction, boolean requiresIdentityCheck) {

    public GuideStep {
        if (stepOrder < 1) {
            throw new IllegalArgumentException("步骤序号必须从 1 开始：" + stepOrder);
        }
        instruction = Objects.requireNonNull(instruction, "步骤说明不能为空").strip();
        if (instruction.isEmpty()) {
            throw new IllegalArgumentException("步骤说明不能为空");
        }
    }
}
