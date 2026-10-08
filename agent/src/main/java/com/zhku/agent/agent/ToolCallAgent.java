package com.zhku.agent.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.model.AgentExecutionFailure;
import com.zhku.agent.agent.model.ToolCallDecision;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** 模型与工具结果只在当前步骤传递，不保存跨运行可变决策。 */
public class ToolCallAgent extends ReActAgent<ToolCallDecision> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolCallback[] availableTools;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools, Executor streamExecutor) {
        super(streamExecutor);
        this.availableTools = availableTools.clone();
        // 框架默认会把工具异常转换成普通文本；本路径必须抛出，才能阻止同批终止声明误报成功。
        this.toolCallingManager = ToolCallingManager.builder().toolExecutionExceptionProcessor(exception -> {
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.TOOL_FAILURE);
        }).build();
        // 执行阶段也必须携带同一组回调；只在模型提示中声明工具不足以让执行管理器解析工具。
        this.chatOptions = ToolCallingChatOptions.builder().toolCallbacks(this.availableTools)
                // 工具由当前步骤显式执行，避免模型调用阶段自动执行后再被 act 重复执行。
                .internalToolExecutionEnabled(false).build();
    }

    @Override
    protected ToolCallDecision think(AgentRunContext context) {
        if (StrUtil.isNotBlank(getNextStepPrompt()))
            context.appendMessage(new UserMessage(getNextStepPrompt()));
        try {
            ChatResponse response = getChatClient().prompt(new Prompt(context.messageSnapshot(), chatOptions))
                    .system(getSystemPrompt()).toolCallbacks(availableTools).call().chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
                throw new IllegalStateException("模型无有效输出");
            AssistantMessage message = response.getResult().getOutput();
            List<AssistantMessage.ToolCall> calls = message.getToolCalls();
            if (calls == null)
                throw new IllegalStateException("模型工具结果无效");
            if (!context.isStopRequested() && calls.isEmpty())
                context.appendMessage(message);
            return new ToolCallDecision(response, !calls.isEmpty());
        } catch (RuntimeException exception) {
            // 不保留供应商原始因果链，避免凭据随框架异常进入日志或 HTTP/SSE。
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.MODEL_FAILURE);
        }
    }

    @Override
    protected String act(AgentRunContext context, ToolCallDecision decision) {
        if (context.isStopRequested())
            return "运行已请求停止";
        try {
            if (!decision.response().hasToolCalls())
                return "没有工具需要调用";
            List<AgentToolCall> proposed = decision.response().getResult().getOutput().getToolCalls().stream()
                    .map(call -> context.proposeToolCall(call.id(), call.name(), call.arguments())).toList();
            var result = toolCallingManager.executeToolCalls(
                    new Prompt(context.messageSnapshot(), chatOptions), decision.response());
            var history = result.conversationHistory();
            if (history.isEmpty() || !(history.get(history.size() - 1) instanceof ToolResponseMessage message))
                throw new IllegalStateException("工具返回格式无效");
            AgentRunState declaredState = null;
            // 全部工具执行与返回校验通过以后，才接受终止声明，避免同批失败被成功掩盖。
            for (var response : message.getResponses()) {
                if ("doTerminate".equals(response.name())) {
                    var payload = JSON.readTree(response.responseData());
                    if (!payload.isObject() || payload.size() != 1 || !payload.path("outcome").isTextual())
                        throw new IllegalStateException("终止结果无效");
                    var outcome = payload.path("outcome").asText();
                    AgentRunState state = switch (outcome) {
                        case "COMPLETED" -> AgentRunState.SUCCEEDED;
                        case "BLOCKED" -> AgentRunState.INCOMPLETE;
                        default -> throw new IllegalStateException("终止结果无效");
                    };
                    if (declaredState != null && declaredState != state)
                        throw new IllegalStateException("终止声明冲突");
                    declaredState = state;
                }
            }
            // 只关联唯一、已观测的响应；批次抛错或供应商 ID 歧义时不猜测逐工具结果。
            for (var response : message.getResponses()) {
                var matching = proposed.stream()
                        .filter(call -> java.util.Objects.equals(call.providerCallId(), response.id())
                                && java.util.Objects.equals(call.toolName(), response.name()))
                        .toList();
                long responses = message.getResponses().stream()
                        .filter(other -> java.util.Objects.equals(other.id(), response.id())
                                && other.name().equals(response.name()))
                        .count();
                if (matching.size() == 1 && responses == 1) {
                    context.recordToolResult(matching.get(0), new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                            response.responseData(), AgentToolCall.PayloadFormat.TEXT, null));
                }
            }
            if (context.isStopRequested())
                return "运行已请求停止";
            context.replaceMessages(history);
            if (declaredState != null)
                context.finish(declaredState,
                        declaredState == AgentRunState.SUCCEEDED ? EndReason.COMPLETED : EndReason.BLOCKED, null);
            return message.getResponses().stream()
                    .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                    .collect(Collectors.joining("\n"));
        } catch (Exception exception) {
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.TOOL_FAILURE);
        }
    }
}
