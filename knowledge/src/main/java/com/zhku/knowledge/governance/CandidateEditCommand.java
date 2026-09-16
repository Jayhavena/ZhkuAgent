package com.zhku.knowledge.governance;

import java.util.Objects;

/**
 * 管理员修订候选片段时可更正的最小内容。
 */
public record CandidateEditCommand(
        int startPageNumber,
        int endPageNumber,
        String editedText,
        String sectionTitle,
        String sourceLocator
) {

    public CandidateEditCommand {
        if (startPageNumber < 1 || endPageNumber < startPageNumber) {
            throw new IllegalArgumentException("候选片段页码范围无效");
        }
        editedText = requireText(editedText, "编辑后文本不能为空");
        sectionTitle = requireText(sectionTitle, "片段标题不能为空");
        sourceLocator = requireText(sourceLocator, "资料定位不能为空");
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
