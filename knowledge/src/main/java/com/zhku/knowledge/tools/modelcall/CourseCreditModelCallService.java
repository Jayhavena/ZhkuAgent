package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.tools.CampusToolCall;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CampusToolStatus;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 一轮原生模型工具调用与服务端受控执行，仅供本机命令行演示。 */
public final class CourseCreditModelCallService {

    private static final String TOOL_NAME = "summarize_course_credits";
    private static final String BOUNDARY = "仅按输入汇总，不能确认课程资格、选课资格或毕业资格。";
    private final ChatModel model;
    private final CampusToolRegistry registry;
    private final Duration timeout;
    private final ToolCallback advertisedTool;

    public CourseCreditModelCallService(ChatModel model, CampusToolRegistry registry, Duration timeout) {
        this.model = Objects.requireNonNull(model, "模型不能为空");
        this.registry = Objects.requireNonNull(registry, "工具注册表不能为空");
        this.timeout = Objects.requireNonNull(timeout, "超时不能为空");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("超时必须为正数");
        }
        // 只从单工具注册表取得定义，避免模型看到的 schema 与真正执行的工具脱节。
        if (registry.descriptors().size() != 1 || !TOOL_NAME.equals(registry.descriptors().get(0).name())) {
            throw new IllegalArgumentException("演示仅允许学分汇总工具");
        }
        CampusToolDescriptor descriptor = registry.descriptors().get(0);
        ToolDefinition definition = ToolDefinition.builder()
                .name(descriptor.name())
                .description(descriptor.description())
                .inputSchema(descriptor.inputSchemaJson())
                .build();
        this.advertisedTool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String arguments) {
                // 即使供应商适配器错误地尝试自动执行，也不能绕过注册表与次数守卫。
                throw new IllegalStateException("工具必须由受控执行器调用");
            }
        };
    }

    public ModelCallResult run(String question) {
        if (question == null || question.isBlank()) {
            return failure(ModelCallStatus.INVALID_ARGUMENT, null);
        }
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(advertisedTool)
                .internalToolExecutionEnabled(false)
                .build();
        // 供应商调用放在有界等待中；虚拟线程超时后不阻塞命令行，且不记录异常原文。
        ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        try {
            Future<ChatResponse> future = executor.submit(() -> model.call(new Prompt(question, options)));
            ChatResponse response;
            try {
                response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                return failure(ModelCallStatus.MODEL_TIMEOUT, null);
            }
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return failure(ModelCallStatus.MODEL_FAILURE, null);
            }
            List<AssistantMessage.ToolCall> calls = new ArrayList<>();
            for (Generation generation : response.getResults()) {
                if (generation == null || generation.getOutput() == null) {
                    return failure(ModelCallStatus.MODEL_FAILURE, null);
                }
                if (generation.getOutput().getToolCalls() != null) {
                    calls.addAll(generation.getOutput().getToolCalls());
                }
            }
            if (calls.isEmpty()) {
                return failure(ModelCallStatus.NO_TOOL_CALL, null);
            }
            // 一次只执行一个；多调用整体拒绝，防止先执行第一个再发现越界。
            if (calls.size() != 1) {
                return failure(ModelCallStatus.TOOL_CALL_LIMIT, null);
            }
            AssistantMessage.ToolCall call = calls.get(0);
            if (!TOOL_NAME.equals(call.name())) {
                // 未知名称由模型提供，不能把任意文本当作安全工具名回显。
                return failure(ModelCallStatus.UNKNOWN_TOOL, null);
            }
            if (!CourseCreditSummaryTool.acceptsArguments(call.arguments())) {
                return failure(ModelCallStatus.INVALID_ARGUMENT, call.name());
            }
            CampusToolResult result = registry.execute(new CampusToolCall(call.name(), call.arguments()));
            if (result.status() == CampusToolStatus.SUCCESS) {
                return new ModelCallResult(ModelCallStatus.SUCCESS, result.toolName(),
                        result.resultJson(), result.boundaryMessage());
            }
            return failure(ModelCallStatus.INVALID_ARGUMENT, call.name());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(ModelCallStatus.MODEL_FAILURE, null);
        } catch (ExecutionException | RuntimeException exception) {
            return failure(ModelCallStatus.MODEL_FAILURE, null);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ModelCallResult failure(ModelCallStatus status, String toolName) {
        return new ModelCallResult(status, toolName, null, BOUNDARY);
    }
}
