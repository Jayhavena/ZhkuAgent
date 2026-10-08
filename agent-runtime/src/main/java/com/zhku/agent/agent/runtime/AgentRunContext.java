package com.zhku.agent.agent.runtime;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import com.zhku.agent.agent.runtime.contract.*;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import java.time.Instant;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 保存一次 Agent 运行所需的全部可变状态。
 *
 * <p>每次调用 Agent 时都应该创建一个新的 AgentRunContext。
 * 不同用户、不同请求和不同运行之间不能共享该对象。</p>
 *
 * <p>线程职责约定：</p>
 * <ul>
 *     <li>工作线程负责推进步骤、追加消息和设置最终结果；</li>
 *     <li>SSE 回调线程可以请求停止；</li>
 *     <li>所有线程都可以读取状态快照；</li>
 *     <li>终止信号在业务与清理收敛后发出；任务包装器随后仍需返回，不代表池线程销毁。</li>
 * </ul>
 */
public final class AgentRunContext {

    /**
     * 每次运行的唯一标识。
     *
     * <p>后续可以把它加入日志，帮助我们区分并发运行。</p>
     */
    private final UUID runId;
    private final AgentRunObservationDispatcher observationDispatcher;
    private final AgentRunObservationDispatcher.Subscription observation;
    private final Object eventLock = new Object();
    private long eventSequence;
    private volatile boolean resultPublished;

    /**
     * 消息列表的专用锁。
     *
     * <p>消息主要由工作线程修改，但状态查询和测试可能从其他线程读取。
     * 使用独立锁可以避免读取快照时与写入操作交叉。</p>
     */
    private final Object messageLock = new Object();

    /**
     * 本次运行独享的消息列表。
     *
     * <p>该集合绝不能暴露给外部直接修改。</p>
     */
    private final List<Message> messageList = new ArrayList<>();

    /**
     * 将“状态”和“停止原因”放在同一个不可变快照中。
     *
     * <p>如果使用两个彼此独立的原子变量，读取线程可能短暂看到
     * STOP_REQUESTED 状态，却仍然读到 NONE 原因。将两者一起替换，
     * 可以保证一次读取获得一致的数据。</p>
     */
    private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(
            new Lifecycle(
                    AgentRunState.CREATED,
                    AgentStopReason.NONE));

    /**
     * 当前已经开始的步骤数量。
     */
    private final AtomicInteger currentStep = new AtomicInteger();

    /**
     * 本次后台任务对应的 Future 句柄。
     *
     * <p>Future 用于发出最佳努力的中断请求，但不能作为
     * “工作线程已经退出”的判断依据。</p>
     */
    private final AtomicReference<Future<?>> taskHandle = new AtomicReference<>();

    /**
     * 标记工作线程是否已经真正进入任务包装器。
     */
    private final AtomicBoolean workerStarted = new AtomicBoolean();

    /**
     * 保证真正的清理动作最多执行一次。
     */
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final AtomicBoolean cleanupFinished = new AtomicBoolean();
    private volatile boolean cleanupFailure;

    /**
     * 只有业务结果与清理收敛后才完成该 Future。
     *
     * <p>它与 taskHandle.isDone() 的语义不同：
     * taskHandle 被取消，不代表底层阻塞调用已经停止。</p>
     */
    private final CompletableFuture<AgentRunState> termination = new CompletableFuture<>();

    /**
     * 创建一次新的运行上下文。
     *
     * @param userPrompt 本次运行的用户输入
     */
    private AgentRunContext(String userPrompt, Consumer<AgentRunEvent> observer,
            AgentRunObservationDispatcher dispatcher) {
        this(UUID.randomUUID(), userPrompt, observer, dispatcher);
    }
    private AgentRunContext(UUID runId, String userPrompt, Consumer<AgentRunEvent> observer,
            AgentRunObservationDispatcher dispatcher) {
        validatePrompt(userPrompt);
        this.runId = Objects.requireNonNull(runId, "运行编号不能为空");
        this.observationDispatcher = dispatcher;
        this.observation = dispatcher == null ? null : dispatcher.subscribe(runId, observer);
        this.messageList.add(new UserMessage(userPrompt));
    }

    private static void validatePrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank())
            throw new IllegalArgumentException("userPrompt 不能为空");
    }

    /** 无观察模式不会初始化默认资源，也不会占登记名额。 */
    public static AgentRunContext create(String userPrompt) {
        return new AgentRunContext(userPrompt, null, null);
    }
    /** 已确认受理的宿主提供原ID；该重载不承诺任务恢复，也不改变清理/发布语义。 */
    public static AgentRunContext create(UUID runId, String userPrompt) { return new AgentRunContext(runId,userPrompt,null,null); }
    public static AgentRunContext create(UUID runId, String userPrompt, Consumer<AgentRunEvent> observer) {
        validatePrompt(userPrompt); Objects.requireNonNull(runId); Objects.requireNonNull(observer);
        return new AgentRunContext(runId,userPrompt,observer,AgentRunObservationDispatcher.processDefault());
    }
    public static AgentRunContext create(UUID runId, String userPrompt, Consumer<AgentRunEvent> observer,
            AgentRunObservationDispatcher dispatcher) {
        return new AgentRunContext(runId,userPrompt,Objects.requireNonNull(observer),Objects.requireNonNull(dispatcher));
    }

    /** 借用JVM默认资源；Context及宿主均不拥有其关闭权。 */
    public static AgentRunContext create(String userPrompt, Consumer<AgentRunEvent> observer) {
        validatePrompt(userPrompt);
        Objects.requireNonNull(observer);
        return new AgentRunContext(userPrompt, observer, AgentRunObservationDispatcher.processDefault());
    }

    public static AgentRunContext create(String userPrompt, Consumer<AgentRunEvent> observer,
            AgentRunObservationDispatcher dispatcher) {
        return new AgentRunContext(userPrompt, Objects.requireNonNull(observer),
                Objects.requireNonNull(dispatcher));
    }

    /** 交付状态不是运行终态；DETACHED也不能证明观察线程已退出。 */
    public AgentRunObservationDispatcher.ObservationStatus observationStatus() {
        return observation == null ? AgentRunObservationDispatcher.disabledStatus()
                : observationDispatcher.observationStatus(observation);
    }

    /** 先读取发布标记再读生命周期，避免将收尾前的旧状态标成已终止。 */
    public AgentRunSnapshot snapshot() {
        boolean published = resultPublished;
        Lifecycle value = lifecycle.get();
        return snapshot(value, published);
    }

    private AgentRunSnapshot snapshot(Lifecycle value, boolean published) {
        return new AgentRunSnapshot(runId, value.state(), value.stopReason(), currentStep.get(),
                published, value.endReason(), value.error());
    }

    /**
     * 宿主保存完整结果后显式发布唯一终止事实。
     * termination Future 只证明清理收敛；发布标记不证明观察器已返回或线程已退出。
     */
    public boolean publishTermination() {
        synchronized (eventLock) {
            if (!termination.isDone())
                throw new IllegalStateException("清理尚未收敛");
            if (resultPublished)
                return false;
            resultPublished = true;
            deliver(AgentRunEvent.Type.RUN_TERMINATED, null, null, snapshot(), null);
            return true;
        }
    }

    public AgentToolCall proposeToolCall(String providerId, String name, String arguments) {
        AgentToolCall call = new AgentToolCall(runId, UUID.randomUUID().toString(), providerId,
                currentStep.get(), name, arguments, null);
        emit(AgentRunEvent.Type.TOOL_CALL_PROPOSED, call.step(), call, null, null);
        return call;
    }

    public void recordToolResult(AgentToolCall call, AgentToolCall.Result result) {
        if (!runId.equals(call.runId()))
            throw new IllegalArgumentException("工具调用不属于当前运行");
        emit(AgentRunEvent.Type.TOOL_CALL_RESULT, call.step(), call.withResult(result), null, null);
    }

    private void emit(AgentRunEvent.Type type, Integer step, AgentToolCall call,
            AgentRunSnapshot snapshot, AgentRuntimeError error) {
        synchronized (eventLock) {
            // 最终发布后不追加迟到事件；不缓存历史，避免长任务无限增长。
            if (!resultPublished)
                deliver(type, step, call, snapshot, error);
        }
    }

    private void deliver(AgentRunEvent.Type type, Integer step, AgentToolCall call,
            AgentRunSnapshot snapshot, AgentRuntimeError error) {
        AgentRunEvent event = new AgentRunEvent(runId, ++eventSequence, Instant.now(), type, step, call, snapshot,
                error);
        // E只向D提交有界诊断；事实序号不承诺观察回调已开始或已返回。
        if (observation != null) observationDispatcher.offer(observation, event);
    }

    /**
     * 获取本次运行的唯一标识。
     */
    public UUID runId() {
        return runId;
    }

    /**
     * 获取当前运行状态。
     */
    public AgentRunState state() {
        return lifecycle.get().state();
    }

    /**
     * 获取最先被接受的停止原因。
     */
    public AgentStopReason stopReason() {
        return lifecycle.get().stopReason();
    }

    /**
     * 获取已经开始的步骤数量。
     */
    public int currentStep() {
        return currentStep.get();
    }

    /**
     * 将上下文从 CREATED 原子地切换到 RUNNING。
     *
     * <p>并发调用时只能有一个调用者成功。</p>
     *
     * @return 本次调用是否成功启动了运行
     */
    public boolean start() {
        // 生命周期提交与对应事件共用顺序边界，防止最终发布吞掉已接受的事实。
        synchronized (eventLock) {
            Lifecycle current = lifecycle.get();

            if (current.state() != AgentRunState.CREATED) {
                return false;
            }

            Lifecycle running = new Lifecycle(
                    AgentRunState.RUNNING,
                    AgentStopReason.NONE);

            boolean started = lifecycle.compareAndSet(current, running);
            if (started)
                emit(AgentRunEvent.Type.RUN_STARTED, null, null, snapshot(running, false), null);
            return started;
        }
    }

    /**
     * 开始一个新的执行步骤，并返回新的步骤编号。
     *
     * @return 从 1 开始递增的步骤编号
     * @throws IllegalStateException 当前不处于 RUNNING 状态时抛出
     */
    public int advanceStep() {
        // 生命周期提交与对应事件共用顺序边界，防止最终发布吞掉已接受的事实。
        synchronized (eventLock) {
            ensureRunning("只有 RUNNING 状态才能开始新步骤");
            int number = currentStep.incrementAndGet();
            emit(AgentRunEvent.Type.STEP_STARTED, number, null, null, null);
            return number;
        }
    }

    /**
     * 为本次运行追加一条消息。
     *
     * @param message 需要追加的消息
     */
    public void appendMessage(Message message) {
        Objects.requireNonNull(message, "message 不能为空");
        ensureRunning("只有 RUNNING 状态才能追加消息");

        synchronized (messageLock) {
            messageList.add(message);
        }
    }

    /**
     * 使用新的消息历史替换本次运行的消息。
     *
     * <p>先复制输入集合，再替换内部内容，防止调用者之后修改原集合，
     * 间接改变 RunContext 内部状态。</p>
     *
     * @param messages 新的完整消息历史
     */
    public void replaceMessages(List<Message> messages) {
        Objects.requireNonNull(messages, "messages 不能为空");
        ensureRunning("只有 RUNNING 状态才能替换消息");

        // List.copyOf 同时完成防御性复制和 null 元素检查。
        List<Message> safeCopy = List.copyOf(messages);

        synchronized (messageLock) {
            messageList.clear();
            messageList.addAll(safeCopy);
        }
    }

    /**
     * 返回当前消息的不可修改快照。
     *
     * @return 与内部集合隔离的只读消息列表
     */
    public List<Message> messageSnapshot() {
        synchronized (messageLock) {
            return List.copyOf(messageList);
        }
    }

    /**
     * 请求停止当前运行。
     *
     * <p>只有第一个停止请求能够成功。后续 timeout、error 或 completion
     * 回调不会覆盖最初的停止原因。</p>
     *
     * <p>如果工作线程已经进入任务体，则向 Future 发出最佳努力的
     * cancel(true)；如果尚未进入，则只记录停止请求，让任务包装器
     * 获得启动并执行最终清理的机会。</p>
     *
     * @param reason 停止原因，不能为 NONE
     * @return 本次调用是否成为第一个有效停止请求
     */
    public boolean requestStop(AgentStopReason reason) {
        // 生命周期提交与对应事件共用顺序边界，防止最终发布吞掉已接受的事实。
        synchronized (eventLock) {
            Objects.requireNonNull(reason, "reason 不能为空");

            if (reason == AgentStopReason.NONE) {
                throw new IllegalArgumentException(
                        "停止原因不能是 AgentStopReason.NONE");
            }

            while (true) {
                Lifecycle current = lifecycle.get();

                if (current.state().isTerminal()
                        || current.state() == AgentRunState.STOP_REQUESTED) {
                    return false;
                }

                if (current.state() != AgentRunState.CREATED
                        && current.state() != AgentRunState.RUNNING) {
                    throw new IllegalStateException(
                            "当前状态不能请求停止：" + current.state());
                }

                Lifecycle stopping = new Lifecycle(
                        AgentRunState.STOP_REQUESTED,
                        reason);

                if (lifecycle.compareAndSet(current, stopping)) {
                    emit(AgentRunEvent.Type.STOP_REQUESTED, null, null, snapshot(stopping, false), null);
                    break;
                }

                // CAS 失败说明其他线程刚刚修改了生命周期。
                // 重新读取最新状态后再次判断。
            }
        }
        // Future取消可能执行用户回调，因此必须离开事实锁后再传播业务中断。
        cancelRunningTaskIfNecessary();
        return true;
    }

    /**
     * 判断当前是否已经收到停止请求。
     */
    public boolean isStopRequested() {
        return state() == AgentRunState.STOP_REQUESTED;
    }

    /**
     * 绑定本次运行的后台任务句柄。
     *
     * <p>该方法只供同一 runtime 包下的执行器调用。
     * 每个 RunContext 只能绑定一个任务。</p>
     *
     * @param future 本次运行的任务句柄
     */
    void bindTask(Future<?> future) {
        Objects.requireNonNull(future, "future 不能为空");

        if (!taskHandle.compareAndSet(null, future)) {
            throw new IllegalStateException(
                    "一个 AgentRunContext 只能绑定一个任务");
        }
    }

    /**
     * 标记工作线程已经进入任务包装器。
     *
     * <p>执行器必须在调用真实业务逻辑前调用该方法，
     * 然后立即检查 isStopRequested()。</p>
     *
     * @return 是否是第一次标记工作线程启动
     */
    boolean markWorkerStarted() {
        return workerStarted.compareAndSet(false, true);
    }

    /**
     * 设置本次运行的最终业务状态。
     *
     * @param terminalState SUCCEEDED、INCOMPLETE、FAILED 或 CANCELLED
     * @return 本次调用是否成功设置了最终状态
     */
    public boolean finish(AgentRunState terminalState) {
        EndReason reason = switch (terminalState) {
            case SUCCEEDED -> EndReason.COMPLETED;
            case INCOMPLETE -> EndReason.DOMAIN_DECISION;
            case CANCELLED -> EndReason.STOPPED;
            default -> EndReason.ERROR;
        };
        return finish(terminalState, reason, terminalState == AgentRunState.FAILED
                ? AgentRuntimeError.of(AgentRuntimeError.Category.EXECUTION_FAILURE)
                : null);
    }

    public boolean finish(AgentRunState terminalState, EndReason endReason, AgentRuntimeError error) {
        // 生命周期提交与对应事件共用顺序边界，防止最终发布吞掉已接受的事实。
        synchronized (eventLock) {
            Objects.requireNonNull(
                    terminalState,
                    "terminalState 不能为空");

            if (!terminalState.isTerminal()) {
                throw new IllegalArgumentException(
                        "finish 只能接收最终状态：" + terminalState);
            }

            while (true) {
                Lifecycle current = lifecycle.get();

                if (current.state().isTerminal()) {
                    return false;
                }

                // 停止先赢得 CAS 后，正常完成只能让步，不能把取消竞态变成执行异常。
                if (current.state() == AgentRunState.STOP_REQUESTED
                        && (terminalState == AgentRunState.SUCCEEDED
                                || terminalState == AgentRunState.INCOMPLETE))
                    return false;
                validateFinishTransition(current.state(), terminalState);

                Lifecycle finished = new Lifecycle(
                        terminalState,
                        current.stopReason(), endReason, error);
                snapshot(finished, false); // 同一 CAS 发布状态及结束事实，不能分开写入。

                if (lifecycle.compareAndSet(current, finished)) {
                    if (error != null)
                        emit(AgentRunEvent.Type.ERROR_RECORDED, null, null, null, error);
                    return true;
                }
            }
        }
    }

    /**
     * 最多执行一次清理动作。
     *
     * <p>即使多个线程同时调用，也只有一个线程能够执行 cleanupAction。
     * 如果清理动作自身抛出异常，本方法不会自动重复执行，避免部分清理后
     * 再次执行产生二次释放问题。</p>
     *
     * @param cleanupAction 本次运行对应的资源清理动作
     * @return 当前调用是否获得了清理执行权
     */
    public boolean cleanupOnce(Runnable cleanupAction) {
        Objects.requireNonNull(
                cleanupAction,
                "cleanupAction 不能为空");

        if (!cleanupStarted.compareAndSet(false, true)) {
            return false;
        }

        try {
            cleanupAction.run();
            return true;
        } catch (RuntimeException | Error exception) {
            // 只有收尾期间允许降级终态；标记必须先于 cleanupFinished 发布。
            cleanupFailure = true;
            AgentRuntimeError error = AgentRuntimeError.of(AgentRuntimeError.Category.CLEANUP_FAILURE);
            lifecycle.updateAndGet(
                    current -> new Lifecycle(AgentRunState.FAILED, current.stopReason(), EndReason.ERROR, error));
            emit(AgentRunEvent.Type.ERROR_RECORDED, null, null, null, error);
            throw exception;
        } finally {
            cleanupFinished.set(true);
        }
    }

    public boolean hasCleanupFailure() {
        return cleanupFailure;
    }

    /**
     * 发出“运行已经真正终止”的信号。
     *
     * <p>调用方必须先设置最终状态并结束 cleanup。清理抛错时也可终止，
     * 但状态已降级 FAILED；终止不承诺所有资源都成功释放。</p>
     *
     * @return 是否由本次调用首次完成终止信号
     */
    public boolean signalTermination() {
        // 先观察清理完成的发布，再读取终态，否则可能在清理抛错前读到旧的成功状态。
        if (!cleanupFinished.get()) {
            throw new IllegalStateException(
                    "必须完成 cleanup 后才能发送 termination 信号");
        }
        AgentRunState finalState = state();

        if (!finalState.isTerminal()) {
            throw new IllegalStateException(
                    "只有最终状态才能发送 termination 信号");
        }

        return termination.complete(finalState);
    }

    /**
     * 返回终止信号的只读副本。
     *
     * <p>调用者可以等待或观察该 Future，但对副本调用 complete()
     * 不会改变 AgentRunContext 内部持有的原始终止信号。</p>
     */
    public CompletableFuture<AgentRunState> termination() {
        return termination.copy();
    }

    /**
     * 在工作线程已经开始后，向任务句柄发送中断请求。
     */
    private void cancelRunningTaskIfNecessary() {
        if (!workerStarted.get()) {
            return;
        }

        Future<?> future = taskHandle.get();

        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * 校验当前必须处于运行状态。
     */
    private void ensureRunning(String message) {
        AgentRunState currentState = state();

        if (currentState != AgentRunState.RUNNING) {
            throw new IllegalStateException(
                    message + "，当前状态为：" + currentState);
        }
    }

    /**
     * 校验最终状态迁移是否合法。
     *
     * <p>正常运行可以成功、未完成或失败；收到停止请求后应当取消，
     * 也允许在终止过程中因为额外异常进入 FAILED；
     * 尚未启动的任务只有在提交失败等情况下才能直接 FAILED。</p>
     */
    private static void validateFinishTransition(
            AgentRunState currentState,
            AgentRunState terminalState) {
        boolean allowed = switch (terminalState) {
            case SUCCEEDED, INCOMPLETE ->
                currentState == AgentRunState.RUNNING;

            case CANCELLED ->
                currentState == AgentRunState.STOP_REQUESTED;

            case FAILED ->
                currentState == AgentRunState.CREATED
                        || currentState == AgentRunState.RUNNING
                        || currentState == AgentRunState.STOP_REQUESTED;

            default -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "非法状态迁移："
                            + currentState
                            + " -> "
                            + terminalState);
        }
    }

    /**
     * 生命周期不可变快照。
     *
     * <p>将 state 和 stopReason 放进同一个 record，使它们能够通过
     * 一次 CAS 原子替换，避免读取到彼此不一致的组合。</p>
     */
    private record Lifecycle(
            AgentRunState state,
            AgentStopReason stopReason,
            EndReason endReason,
            AgentRuntimeError error) {

        private Lifecycle(AgentRunState state, AgentStopReason stopReason) {
            this(state, stopReason, null, null);
        }

        private Lifecycle {
            Objects.requireNonNull(state, "state 不能为空");
            Objects.requireNonNull(
                    stopReason,
                    "stopReason 不能为空");
        }
    }
}
