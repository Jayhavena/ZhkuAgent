package com.yupi.yuaiagent.zhku.knowledge.governance;

import java.util.Objects;

/**
 * 拆分后两段待审核正文；原始页文本仍保留，用于追溯。
 */
public record CandidateSplitCommand(
        String firstEditedText,
        String secondEditedText
) {

    public CandidateSplitCommand {
        firstEditedText = requireText(firstEditedText, "第一段编辑后文本不能为空");
        secondEditedText = requireText(secondEditedText, "第二段编辑后文本不能为空");
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
