package com.zhku.knowledge.answer;

/** 学生回答中可展示的最小出处，不包含本地原始文件路径。 */
public record GroundedCitation(
        String sourceTitle,
        String sourceLocator,
        int pageNumber
) {
}
