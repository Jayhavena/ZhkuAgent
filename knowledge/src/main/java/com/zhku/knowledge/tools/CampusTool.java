package com.zhku.knowledge.tools;

/** 单个低风险校园工具的框架无关契约。 */
public interface CampusTool {

    CampusToolDescriptor descriptor();

    CampusToolResult execute(String argumentsJson);
}
