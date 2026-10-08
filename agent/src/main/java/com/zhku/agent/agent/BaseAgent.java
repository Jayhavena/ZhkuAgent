package com.zhku.agent.agent;

import cn.hutool.core.util.StrUtil;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunObservationDispatcher;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentRunTask;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.model.AgentExecutionFailure;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

/**
 * 抽象基础 Agent。
 *
 * <p>该对象只保存可以跨运行复用的 Agent 配置。每次 run 或 runStream 都创建
 * 独立的 AgentRunContext，由 context 保存状态、步骤、消息、停止原因和终止
 * 信号，避免多次运行共享可变数据。</p>
 */
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
     * 执行流式 Agent 任务的资源入口。
     *
     * <p>字段属于 Agent 配置，不保存任何一次运行的状态。本阶段先建立注入
     * 接缝；下一阶段再由 Spring 专用有界执行器接管其生命周期。</p>
     */
    private final Executor streamExecutor;

    /**
     * 使用调用方明确提供的执行器创建 Agent 定义。
     *
     * @param streamExecutor 执行流式任务的执行器
     */
    protected BaseAgent(Executor streamExecutor) {
        this.streamExecutor = streamExecutor;
    }

    /** 以下配置访问器只向子类开放，外部调用方不能在运行中改写 Agent 定义。 */
    protected final String getName() {
        return name;
    }

    protected final void setName(String name) {
        this.name = name;
    }

    protected final String getSystemPrompt() {
        return systemPrompt;
    }

    protected final void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    protected final String getNextStepPrompt() {
        return nextStepPrompt;
    }

    protected final void setNextStepPrompt(String nextStepPrompt) {
        this.nextStepPrompt = nextStepPrompt;
    }

    protected final int getMaxSteps() {
        return maxSteps;
    }

    protected final void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    protected final ChatClient getChatClient() {
        return chatClient;
    }

    protected final void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

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
            finishAfterStreamException(context, exception);
            log.error("Agent 同步执行失败，runId={}，category={}", context.runId(), failureCategory(exception));

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
     * <p>本阶段为每次流式运行建立可取消的 FutureTask，并在提交前绑定到
     * AgentRunContext。专用有界线程池和完整 SSE 通道将在后续阶段实现。</p>
     *
     * @param userPrompt 本次运行的用户提示词
     * @return 当前请求专属的 SSE emitter
     */
    public SseEmitter runStream(String userPrompt) {
        validateUserPrompt(userPrompt);

        AgentRunContext context = createRunningContext(userPrompt);
        SseEmitter emitter = createStreamEmitter();

        // 回调必须在提交后台任务前注册，减少任务快速结束造成的注册时序窗口。
        registerEmitterCallbacks(context, emitter);

        // 先创建并绑定任务句柄，再提交给执行器。
        // 这样停止请求始终能够找到当前运行对应的 FutureTask。
        FutureTask<Void> streamTask = AgentRunTask.create(context, () -> {
            try {
                executeSteps(
                        context,
                        result -> sendStreamResult(context, emitter, result));

                // executeSteps 已先设置最终状态；完成回调不会把成功运行改成取消。
                finalizeRun(context);
                emitter.complete();
            } catch (UncheckedIOException exception) {
                // SSE 发送失败属于运行失败，同时保留首个停止原因 SEND_FAILURE。
                finishAsFailure(context, AgentRuntimeError.of(AgentRuntimeError.Category.SEND_FAILURE));
                log.error(
                        "Agent SSE 发送失败，runId={}，category={}",
                        context.runId(),
                        failureCategory(exception));
                emitter.completeWithError(exception.getCause());
            } catch (RuntimeException exception) {
                // 如果之前已收到 timeout/disconnect，则最终结果为取消；
                // 否则这是普通执行异常，最终结果为失败。
                finishAfterStreamException(context, exception);
                log.error(
                        "Agent 流式执行失败，runId={}，category={}",
                        context.runId(),
                        failureCategory(exception));
                // 尝试发送一次固定分类；发送本身失败时不能递归重试。
                try {
                    sendStreamResult(context, emitter, "Error: " + failureCategory(exception));
                } catch (RuntimeException sendFailure) {
                    /* 通道不可用，仍需执行 finally。 */
                }
                emitter.completeWithError(new IllegalStateException(failureCategory(exception)));
            } finally {
                // 只有工作线程负责最终清理和真实终止信号。
                finalizeRun(context);
            }
        });

        try {
            // 任务句柄已经绑定，具体线程和容量策略由注入执行器决定。
            streamExecutor.execute(streamTask);
        } catch (RuntimeException exception) {
            // 如果任务提交失败，工作线程不会启动，因此由提交线程完成失败收尾。
            streamTask.cancel(false);
            finishAsFailure(context, AgentRuntimeError.of(AgentRuntimeError.Category.EXECUTOR_REJECTED));
            finalizeRun(context);
            emitter.completeWithError(exception);
            throw exception;
        }

        return emitter;
    }

    /**
     * 为一次流式请求创建独立的 SSE emitter。
     *
     * <p>把创建动作与运行逻辑分离，既允许子类按部署环境调整 emitter，也允许
     * 测试通过可控 emitter 触发真实生命周期回调。每次调用必须返回新对象，不能
     * 在不同请求之间共享连接状态。</p>
     */
    protected SseEmitter createStreamEmitter() {
        return new SseEmitter(300000L);
    }

    /**
     * 执行当前 context 的步骤循环。
     *
     * @param context        本次运行上下文
     * @param resultConsumer 当前输出方式；同步调用写入列表，流式调用发送 SSE
     */
    private void executeSteps(
            AgentRunContext context,
            Consumer<String> resultConsumer) {
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
                    context.runId());

            // context 通过参数显式传入，子类只能操作当前运行的数据。
            String stepResult = step(context);
            resultConsumer.accept(
                    "Step " + stepNumber + ": " + stepResult);
        }

        finishAfterLoop(context, resultConsumer);
    }

    /**
     * 根据循环结束原因设置最终状态。
     */
    private void finishAfterLoop(
            AgentRunContext context,
            Consumer<String> resultConsumer) {
        if (context.isStopRequested()) {
            // timeout、断连和用户停止属于取消；SSE error/send 失败属于故障。
            // 不能把所有停止请求都压成 CANCELLED，否则监控和调用方无法区分
            // “用户不再需要结果”和“传输链路执行失败”。
            AgentRunState finalState = context.stopReason() == AgentStopReason.SEND_FAILURE
                    ? AgentRunState.FAILED
                    : AgentRunState.CANCELLED;
            if (finalState == AgentRunState.FAILED)
                context.finish(finalState, EndReason.ERROR,
                        AgentRuntimeError.of(AgentRuntimeError.Category.SEND_FAILURE));
            else
                context.finish(finalState, EndReason.STOPPED, null);
            return;
        }

        // 终止工具可能已经把当前 context 标记为 SUCCEEDED。
        if (context.state().isTerminal()) {
            return;
        }

        if (context.currentStep() >= maxSteps) {
            resultConsumer.accept(
                    "Terminated: Reached max steps (" + maxSteps + ")");
        }

        // 消耗完步骤只证明本次执行结束，不能证明用户请求已完成。
        if (!context.finish(AgentRunState.INCOMPLETE, EndReason.STEP_LIMIT, null) && context.isStopRequested()) {
            context.finish(AgentRunState.CANCELLED);
        }
    }

    /**
     * 注册 SSE 生命周期回调。
     *
     * <p>Spring 的 onCompletion 会在异步请求因任何原因完成时执行，所以回调
     * 不能直接 cleanup。它只能在运行尚未终态时请求停止当前 context。</p>
     */
    private void registerEmitterCallbacks(
            AgentRunContext context,
            SseEmitter emitter) {
        emitter.onTimeout(() -> {
            context.requestStop(AgentStopReason.SSE_TIMEOUT);
            log.warn("SSE 连接超时，runId={}", context.runId());
        });

        emitter.onError(error -> {
            context.requestStop(AgentStopReason.SEND_FAILURE);
            log.warn("SSE 连接发生错误，runId={}，category=SEND_FAILURE", context.runId());
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
            String result) {
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
        AgentRunContext context = AgentRunContext.create(userPrompt, this::onRunEvent, observationDispatcher());

        if (!context.start()) {
            // 新创建的 context 理论上必须能从 CREATED 进入 RUNNING。
            throw new IllegalStateException("新建运行上下文无法启动");
        }

        return context;
    }

    /**
     * 把普通执行异常映射为 FAILED。
     */
    private void finishAsFailure(AgentRunContext context, AgentRuntimeError error) {
        if (!context.state().isTerminal()) {
            context.finish(AgentRunState.FAILED, EndReason.ERROR, error);
        }
    }

    /**
     * 根据流式异常发生前是否已收到停止请求，选择 CANCELLED 或 FAILED。
     */
    private void finishAfterStreamException(AgentRunContext context, RuntimeException exception) {
        if (context.state().isTerminal()) {
            return;
        }

        if (context.isStopRequested()
                && context.stopReason() != AgentStopReason.SEND_FAILURE) {
            context.finish(AgentRunState.CANCELLED);
            return;
        }

        AgentRuntimeError error = exception instanceof AgentExecutionFailure failure ? failure.runtimeError()
                : AgentRuntimeError.of(context.stopReason() == AgentStopReason.SEND_FAILURE
                        ? AgentRuntimeError.Category.SEND_FAILURE
                        : AgentRuntimeError.Category.EXECUTION_FAILURE);
        context.finish(AgentRunState.FAILED, EndReason.ERROR, error);
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

        try {
            context.cleanupOnce(() -> cleanup(context));
        } catch (RuntimeException exception) {
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.CLEANUP_FAILURE);
        } finally {
            // 清理失败仍须通知等待方；context 已将成功降级为失败且记录清理分类。
            context.signalTermination();
            context.publishTermination();
        }
    }

    private static String failureCategory(RuntimeException exception) {
        return exception instanceof AgentExecutionFailure failure ? failure.category().name() : "EXECUTION_FAILURE";
    }

    /**
     * 校验用户输入。
     */
    private static void validateUserPrompt(String userPrompt) {
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException(
                    "Cannot run agent with empty user prompt");
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

    /** 宿主借用资源；测试可显式拥有，不能在构造阶段调用可覆写接线方法。 */
    protected AgentRunObservationDispatcher observationDispatcher() {
        return AgentRunObservationDispatcher.processDefault();
    }

    /** 异步、尽力交付；不能通过回调顺序推断业务结果尚未发布。 */
    protected void onRunEvent(AgentRunEvent event) {
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
