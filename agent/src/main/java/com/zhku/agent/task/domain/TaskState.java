package com.zhku.agent.task.domain;

/**
 * 持久研究任务的业务状态。
 *
 * <p>该状态跨请求和进程重启存在，与单次 JVM 执行的 AgentRunState 不同。
 * {@link #isTerminal()} 的结果决定恢复器能否再次调度该任务。</p>
 */
public enum TaskState {
    CREATED,
    PLANNING,
    READY,
    RUNNING,
    WAITING_RETRY,
    CANCEL_REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    /**
     * 只有四种最终结果不可被恢复器重新激活；取消请求仍是可继续收敛的中间状态。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELLED
                || this == TIMED_OUT;
    }
}
