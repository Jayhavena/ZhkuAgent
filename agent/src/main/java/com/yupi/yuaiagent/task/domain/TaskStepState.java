package com.yupi.yuaiagent.task.domain;

/**
 * 一个持久计划步骤的状态。
 *
 * <p>{@code INTERRUPTED} 表示本次进程执行未确认完成，可以在新的执行尝试中重新进入
 * {@code RUNNING}；{@code SUCCEEDED} 是恢复时唯一可无条件跳过的成功检查点。</p>
 */
public enum TaskStepState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    WAITING_RETRY,
    FAILED,
    INTERRUPTED,
    SKIPPED;

    /**
     * 终态步骤不可再次调度，避免恢复流程重复执行已确定结束的工作。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED;
    }
}
