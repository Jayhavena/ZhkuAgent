package com.yupi.yuaiagent.agent.runtime;

/**
 * 表示一次 Agent 运行的生命周期状态。
 *
 * <p>该状态只描述一次具体运行，不描述整个 Agent Bean。
 * 因此，每个 AgentRunContext 都拥有自己独立的状态。</p>
 */
public enum AgentRunState {

    /**
     * 运行上下文已经创建，但工作线程还没有开始执行。
     */
    CREATED,

    /**
     * 工作线程正在执行模型调用、工具调用或步骤循环。
     */
    RUNNING,

    /**
     * 系统已经收到停止请求，但工作线程尚未确认退出。
     *
     * <p>这是一个非常重要的中间状态：
     * STOP_REQUESTED 不能被理解为任务已经终止。</p>
     */
    STOP_REQUESTED,

    /**
     * 工作线程正常完成了任务。
     */
    SUCCEEDED,

    /**
     * 工作线程因为异常而失败。
     */
    FAILED,

    /**
     * 工作线程响应停止请求后结束。
     */
    CANCELLED;

    /**
     * 判断当前状态是否属于最终业务状态。
     *
     * @return 如果运行已经得到成功、失败或取消结果，则返回 true
     */
    public boolean isTerminal() {
        return this == SUCCEEDED
                || this == FAILED
                || this == CANCELLED;
    }
}