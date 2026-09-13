package com.yupi.yuaiagent.agent.runtime;

import java.util.Objects;
import java.util.concurrent.FutureTask;

/**
 * 为一次 Agent 后台运行创建可取消的任务句柄。
 *
 * <p>该类位于 runtime 包中，因此可以调用 AgentRunContext 的包级生命周期方法，
 * 同时避免把 bindTask 和 markWorkerStarted 暴露为公共 API。</p>
 */
public final class AgentRunTask {

    /**
     * 工具类不保存实例状态，因此禁止外部创建对象。
     */
    private AgentRunTask() {
    }

    /**
     * 创建并绑定一次后台任务。
     *
     * <p>必须先把 FutureTask 绑定到 context，再把返回值交给线程池执行。
     * 这样停止请求不会落入“任务已经提交，但句柄尚未保存”的竞态窗口。</p>
     *
     * @param context 当前运行独享的上下文
     * @param action  工作线程中执行的完整运行逻辑
     * @return 已绑定但尚未提交给线程池的任务
     */
    public static FutureTask<Void> create(
            AgentRunContext context,
            Runnable action
    ) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(action, "action 不能为空");

        FutureTask<Void> task = new FutureTask<>(() -> {
            // 工作线程进入包装器后立即标记启动。
            // 后续 requestStop 才能安全地对已运行任务调用 cancel(true)。
            if (!context.markWorkerStarted()) {
                throw new IllegalStateException(
                        "同一个 Agent 运行任务不能启动多次"
                );
            }

            // action 自己负责业务状态、cleanup 和 termination 的最终收尾。
            action.run();
            return null;
        });

        // 绑定必须发生在线程池 execute 之前，消除“先运行、后保存句柄”的窗口。
        context.bindTask(task);
        return task;
    }
}