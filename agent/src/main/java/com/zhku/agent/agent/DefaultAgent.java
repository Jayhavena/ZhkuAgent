package com.zhku.agent.agent;

import com.zhku.agent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.concurrent.Executor;

/**
 * 默认任务 Agent。
 */
public class DefaultAgent extends ToolCallAgent {

    public DefaultAgent(
            ToolCallback[] allTools,
            ChatModel dashscopeChatModel,
            Executor streamExecutor
    ) {
        super(allTools, streamExecutor);
        this.setName("defaultAgent");
        String SYSTEM_PROMPT = """
                You are DefaultAgent, a task assistant that helps users complete requests with available tools.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
