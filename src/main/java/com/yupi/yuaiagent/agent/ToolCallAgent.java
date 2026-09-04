package com.yupi.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.agent.model.ToolCallDecision;
import com.yupi.yuaiagent.agent.runtime.AgentRunContext;
import com.yupi.yuaiagent.agent.runtime.AgentRunState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实现模型思考和工具执行的 Agent 基类。
 *
 * <p>该类只保存可复用工具配置。ChatResponse 通过 ToolCallDecision 在当前步骤
 * 内传递，不再保存在 Agent 实例字段中。</p>
 */
@Slf4j
public class ToolCallAgent extends ReActAgent<ToolCallDecision> {

    /** 可复用的工具定义。 */
    private final ToolCallback[] availableTools;

    /** 工具调用管理器，本身不表示某一次运行。 */
    private final ToolCallingManager toolCallingManager;

    /** 禁用框架内部自动工具执行后的可复用选项。 */
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 调用模型，生成当前步骤的工具决策。
     */
    @Override
    protected ToolCallDecision think(AgentRunContext context) {
        // 下一步提示词只追加到本次 context，不影响其他运行。
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            context.appendMessage(new UserMessage(getNextStepPrompt()));
        }

        // messageSnapshot 返回不可修改副本，Prompt 无法反向修改内部消息集合。
        List<Message> messages = context.messageSnapshot();
        Prompt prompt = new Prompt(messages, chatOptions);

        // 模型异常不在这里转换成普通返回值，让 BaseAgent 边界统一处理 FAILED。
        ChatResponse response = getChatClient()
                .prompt(prompt)
                .system(getSystemPrompt())
                .tools(availableTools)
                .call()
                .chatResponse();

        AssistantMessage assistantMessage =
                response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls =
                assistantMessage.getToolCalls();

        String text = assistantMessage.getText();
        log.info("{} 的思考：{}", getName(), text);
        log.info("{} 选择了 {} 个工具", getName(), toolCalls.size());

        String toolCallInfo = toolCalls.stream()
                .map(toolCall -> String.format(
                        "工具名称：%s，参数：%s",
                        toolCall.name(),
                        toolCall.arguments()
                ))
                .collect(Collectors.joining("\n"));
        log.info(toolCallInfo);

        boolean shouldAct = !toolCalls.isEmpty();

        if (!shouldAct) {
            // 不调用工具时，框架不会自动补充助手消息，因此手动写入当前 context。
            context.appendMessage(assistantMessage);
        }

        // response 与 shouldAct 一起返回，生命周期只覆盖当前 step。
        return new ToolCallDecision(response, shouldAct);
    }

    /**
     * 执行当前 think 阶段选择的工具。
     */
    @Override
    protected String act(
            AgentRunContext context,
            ToolCallDecision decision
    ) {
        ChatResponse response = decision.response();

        if (!response.hasToolCalls()) {
            return "没有工具需要调用";
        }

        Prompt prompt = new Prompt(
                context.messageSnapshot(),
                chatOptions
        );
        ToolExecutionResult executionResult =
                toolCallingManager.executeToolCalls(prompt, response);

        // conversationHistory 已包含助手工具请求和工具返回消息。
        // 使用受控方法整体替换当前运行的消息历史。
        context.replaceMessages(executionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage =
                (ToolResponseMessage) CollUtil.getLast(
                        executionResult.conversationHistory()
                );

        boolean terminateToolCalled = toolResponseMessage.getResponses()
                .stream()
                .anyMatch(toolResponse ->
                        toolResponse.name().equals("doTerminate")
                );

        if (terminateToolCalled) {
            // 终止工具只结束当前 context，不修改可复用 Agent 实例。
            context.finish(AgentRunState.SUCCEEDED);
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(toolResponse -> String.format(
                        "工具 %s 返回的结果：%s",
                        toolResponse.name(),
                        toolResponse.responseData()
                ))
                .collect(Collectors.joining("\n"));
        log.info(results);

        return results;
    }
}