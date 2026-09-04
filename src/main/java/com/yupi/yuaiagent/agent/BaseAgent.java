package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.runtime.AgentRunContext;
import com.yupi.yuaiagent.agent.runtime.AgentRunState;
import com.yupi.yuaiagent.agent.runtime.AgentStopReason;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 抽象基础 Agent。
 *
 * <p>该对象只保存可以跨运行复用的 Agent 配置。每次 run 或 runStream 都创建
 * 独立的 AgentRunContext，由 context 保存状态、步骤、消息、停止原因和终止
 * 信号，避免多次运行共享可变数据。</p>
 */
@Data
@Slf4j
public abstract class BaseAgent {

    /** Agent 名称，属于可复用配置。 */
    private String name;

    /** 系统提示词，属于可复用配置。 */
    private String systemPrompt;

    /** 每一步追加的提示词，属于可复用配置。 */
    private String nextStepPrompt;

    /** 单次运行允许执行的最大步骤数。 */
    private int maxSteps = 10;

    /** 大模型客户端，属于可复用依赖。 */
    private ChatClient chatClient;

    /**
     * 同步运行 Agent。
     *
     * @param userPrompt 本次运行的用户提示词
     * @return 每一步结果拼接后的文本
     */
    public String run(String userPrompt) {
        // 输入必须在创建上下文和启动业务逻辑前完成校验。
        validateUserPrompt(userPrompt);

        // 每次调用都创建全新的 context，不读取任何上一次运行的数据。
        AgentRunContext context = createRunningContext(userPrompt);
        List<String> results = new ArrayList<>();

        try {
            // 同步结果写入本次方法的局部列表。
            executeSteps(context, results::add);
            return String.join("\n", results);
        } catch (RuntimeException exception) {
            // step/think/act 的异常统一到运行边界，在这里标记 FAILED。
            finishAsFailure(context);
            log.error("Agent 同步执行失败，runId={}", context.runId(), exception);

            // 保留原异常对象及因果链，避免把失败伪装成普通步骤字符串。
            throw exception;
        } finally {
            // cleanup 和 termination 都属于本次 context，且顺序不能颠倒。
            finalizeRun(context);
        }
    }

    /**
     * 流式运行 Agent。
     *
     * <p>本阶段只把运行状态迁移到 context，并让回调只请求停止。
     * 专用有界线程池、提交前绑定 FutureTask 和完整 SSE 通道将在下一阶段实现。</p>
     *
     * @param userPrompt 本次运行的用户提示词
     * @return 当前请求专属的 SSE emitter
     */
    public SseEmitter runStream(String userPrompt) {
        validateUserPrompt(userPrompt);

        AgentRunContext context = createRunningContext(userPrompt);
        SseEmitter emitter = new SseEmitter(300000L);

        // 回调必须在提交后台任务前注册，减少任务快速结束造成的注册时序窗口。
        registerEmitterCallbacks(context, emitter);

        // 仍然使用公共异步池只是本阶段的编译过渡，后续会替换为专用执行器。
        CompletableFuture.runAsync(() -> {
            try {
                executeSteps(
                        context,
                        result -> sendStreamResult(context, emitter, result)
                );

                // executeSteps 已先设置最终状态；完成回调不会把成功运行改成取消。
                emitter.complete();
            } catch (UncheckedIOException exception) {
                // SSE 发送失败属于运行失败，同时保留首个停止原因 SEND_FAILURE。
                finishAsFailure(context);
                log.error(
                        "Agent SSE 发送失败，runId={}",
                        context.runId(),
                        exception
                );
                emitter.completeWithError(exception.getCause());
            } catch (RuntimeException exception) {
                // 如果之前已收到 timeout/disconnect，则最终结果为取消；
                // 否则这是普通执行异常，最终结果为失败。
                finishAfterStreamException(context);
                log.error(
                        "Agent 流式执行失败，runId={}",
                        context.runId(),
                        exception
                );
                emitter.completeWithError(exception);
            } finally {
                // 只有工作线程负责最终清理和真实终止信号。
                finalizeRun(context);
            }
        });

        return emitter;
    }

    /**
     * 执行当前 context 的步骤循环。
     *
     * @param context        本次运行上下文
     * @param resultConsumer 当前输出方式；同步调用写入列表，流式调用发送 SSE
     */
    private void executeSteps(
            AgentRunContext context,
            Consumer<String> resultConsumer
    ) {
        for (int index = 0; index < maxSteps; index++) {
            // STOP_REQUESTED 或最终状态出现后，不允许开始新的模型/工具步骤。
            if (context.isStopRequested() || context.state().isTerminal()) {
                break;
            }

            // 步骤编号由本次 context 原子递增，不再写入 Agent 实例。
            int stepNumber = context.advanceStep();
            log.info(
                    "Agent {} 执行步骤 {}/{}, runId={}",
                    name,
                    stepNumber,
                    maxSteps,
                    context.runId()
            );

            // context 通过参数显式传入，子类只能操作当前运行的数据。
            String stepResult = step(context);
            resultConsumer.accept(
                    "Step " + stepNumber + ": " + stepResult
            );
        }

        finishAfterLoop(context, resultConsumer);
    }

    /**
     * 根据循环结束原因设置最终状态。
     */
    private void finishAfterLoop(
            AgentRunContext context,
            Consumer<String> resultConsumer
    ) {
        if (context.isStopRequested()) {
            context.finish(AgentRunState.CANCELLED);
            return;
        }

        // 终止工具可能已经把当前 context 标记为 SUCCEEDED。
        if (context.state().isTerminal()) {
            return;
        }

        if (context.currentStep() >= maxSteps) {
            resultConsumer.accept(
                    "Terminated: Reached max steps (" + maxSteps + ")"
            );
        }

        context.finish(AgentRunState.SUCCEEDED);
    }

    /**
     * 注册 SSE 生命周期回调。
     *
     * <p>Spring 的 onCompletion 会在异步请求因任何原因完成时执行，所以回调
     * 不能直接 cleanup。它只能在运行尚未终态时请求停止当前 context。</p>
     */
    private void registerEmitterCallbacks(
            AgentRunContext context,
            SseEmitter emitter
    ) {
        emitter.onTimeout(() -> {
            context.requestStop(AgentStopReason.SSE_TIMEOUT);
            log.warn("SSE 连接超时，runId={}", context.runId());
        });

        emitter.onError(error -> {
            context.requestStop(AgentStopReason.SEND_FAILURE);
            log.warn("SSE 连接发生错误，runId={}", context.runId(), error);
        });

        emitter.onCompletion(() -> {
            if (!context.state().isTerminal()) {
                context.requestStop(AgentStopReason.CLIENT_DISCONNECTED);
            }
            log.info("SSE 连接完成，runId={}", context.runId());
        });
    }

    /**
     * 向当前请求的 SSE emitter 发送一步结果。
     */
    private void sendStreamResult(
            AgentRunContext context,
            SseEmitter emitter,
            String result
    ) {
        try {
            emitter.send(result);
        } catch (IOException exception) {
            // 第一个停止原因会被 RunContext 保留，重复回调不能覆盖它。
            context.requestStop(AgentStopReason.SEND_FAILURE);
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 创建并启动一次全新的运行上下文。
     */
    private AgentRunContext createRunningContext(String userPrompt) {
        AgentRunContext context = AgentRunContext.create(userPrompt);

        if (!context.start()) {
            // 新创建的 context 理论上必须能从 CREATED 进入 RUNNING。
            throw new IllegalStateException("新建运行上下文无法启动");
        }

        return context;
    }

    /**
     * 把普通执行异常映射为 FAILED。
     */
    private void finishAsFailure(AgentRunContext context) {
        if (!context.state().isTerminal()) {
            context.finish(AgentRunState.FAILED);
        }
    }

    /**
     * 根据流式异常发生前是否已收到停止请求，选择 CANCELLED 或 FAILED。
     */
    private void finishAfterStreamException(AgentRunContext context) {
        if (context.state().isTerminal()) {
            return;
        }

        if (context.isStopRequested()
                && context.stopReason() != AgentStopReason.SEND_FAILURE) {
            context.finish(AgentRunState.CANCELLED);
            return;
        }

        context.finish(AgentRunState.FAILED);
    }

    /**
     * 执行本次运行的最终清理并发送真实终止信号。
     */
    private void finalizeRun(AgentRunContext context) {
        // 防御性处理：如果异常绕过了正常边界，仍要得到一个最终状态。
        if (!context.state().isTerminal()) {
            if (context.isStopRequested()) {
                context.finish(AgentRunState.CANCELLED);
            } else {
                context.finish(AgentRunState.FAILED);
            }
        }

        context.cleanupOnce(() -> cleanup(context));
        context.signalTermination();
    }

    /**
     * 校验用户输入。
     */
    private static void validateUserPrompt(String userPrompt) {
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException(
                    "Cannot run agent with empty user prompt"
            );
        }
    }

    /**
     * 执行当前运行的一步。
     *
     * @param context 本次运行独享的上下文
     * @return 当前步骤的文本结果
     */
    protected abstract String step(AgentRunContext context);

    /**
     * 清理当前运行持有的资源。
     *
     * <p>默认不执行动作。子类如需清理，只能清理参数 context 对应的资源。</p>
     */
    protected void cleanup(AgentRunContext context) {
        // 子类可以按需重写，但不能清理其他运行。
    }
}

/*
    笔记：
    `BaseAgent` 管理运行状态、执行循环、对话记忆和输出方式；
        什么是运行状态？什么是执行循环？什么是对话记忆？什么是输出方式？
        运行状态：IDLE、RUNNING、FINISHED、ERROR
        执行循环：每次执行一个 step()，直到达到 maxSteps 或者状态变为 FINISHED 或 ERROR
        对话记忆：messageList，记录用户输入和模型输出
            什么是 messageList？为什么要记录用户输入和模型输出？
            messageList 是一个 List<Message>，用于存储用户输入的 UserMessage 和模型输出的 Message。记录这些信息可以帮助模型在后续步骤中参考之前的对话内容，从而生成更相关的响应。
    `ReActAgent` 把一次 `step()` 拆成“思考 + 行动”；
    `ToolCallAgent` 负责真正调用大模型和工具。


    BaseAgent 管循环，ReActAgent 定义步骤结构，ToolCallAgent 填充实际行为。

    step → think → act → 工具结果写入 messageList → 下一次 step
    这就是 ReAct 的“思考—行动—观察—再思考”循环。
*/





/*
    问题：当前 BaseAgent 同时承担了“可复用的 Agent 定义”和“一次执行的运行上下文”，所以复用、流式和并发都会变复杂。



*/