package com.zhku.knowledge.tools;

import java.util.Objects;

/** 可供 Function Calling 适配器读取的稳定校园工具描述。 */
public record CampusToolDescriptor(
        String name,
        String description,
        String inputSchemaJson,
        CampusToolAccessMode accessMode
) {

    public CampusToolDescriptor {
        name = requireText(name, "工具名称不能为空");
        description = requireText(description, "工具说明不能为空");
        inputSchemaJson = requireText(inputSchemaJson, "工具参数结构不能为空");
        accessMode = Objects.requireNonNull(accessMode, "工具访问模式不能为空");
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
