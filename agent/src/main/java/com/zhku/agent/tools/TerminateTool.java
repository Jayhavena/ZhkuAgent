package com.zhku.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.Map;

/**
 * 终止工具（作用是让自主规划智能体能够合理地中断）
 */
public class TerminateTool {

    @Tool(description = """
            End this run with outcome COMPLETED only when the request is completed, or BLOCKED when unable to proceed.
            """)
    public Map<String, String> doTerminate(@ToolParam(description = "Required: COMPLETED or BLOCKED") String outcome) {
        if (!"COMPLETED".equals(outcome) && !"BLOCKED".equals(outcome)) {
            throw new IllegalArgumentException("终止结果必须为 COMPLETED 或 BLOCKED");
        }
        // 返回结构化对象，避免框架把 JSON 字符串再次转义为带引号的文本。
        return Map.of("outcome", outcome);
    }
}
