package com.yupi.yuaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        TerminateTool terminateTool = new TerminateTool();

        // 模型默认只能使用低风险能力。文件、下载、网页抓取、终端和 PDF
        // 会触达本机文件系统、任意网络地址或 shell；在引入统一策略网关和
        // 隔离执行环境前，不能通过共享回调数组暴露给任何模型调用链。
        return ToolCallbacks.from(
                webSearchTool,
                terminateTool
        );
    }
}
