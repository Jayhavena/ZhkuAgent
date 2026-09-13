package com.yupi.yuaiagent.agent;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 为每次请求创建独立的 YuManus。
 *
 * <p>工厂本身可以由 Spring 单例管理，因为它只保存共享依赖；创建出的 Agent
 * 不注册为 Bean，从而避免多个请求共享同一 Agent 配置对象。</p>
 */
@Component
public class YuManusFactory {

    private final ToolCallback[] allTools;

    private final ChatModel dashscopeChatModel;

    private final Executor agentRunExecutor;

    public YuManusFactory(
            @Qualifier("allTools") ToolCallback[] allTools,
            @Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel,
            @Qualifier("agentRunExecutor") Executor agentRunExecutor
    ) {
        this.allTools = allTools;
        this.dashscopeChatModel = dashscopeChatModel;
        this.agentRunExecutor = agentRunExecutor;
    }

    /**
     * 创建只属于当前请求的新 Agent，执行器仍复用容器管理的共享资源。
     */
    public YuManus create() {
        return new YuManus(
                allTools,
                dashscopeChatModel,
                agentRunExecutor
        );
    }
}
