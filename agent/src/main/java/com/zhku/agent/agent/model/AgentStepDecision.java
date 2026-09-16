package com.zhku.agent.agent.model;

/**
 * Agent 单个 ReAct 步骤产生的决策。
 *
 * <p>think 阶段返回一个不可变决策，ReActAgent 再根据 shouldAct 判断是否进入
 * act 阶段。决策对象只通过当前方法调用链传递，不保存在 Agent 实例字段中，
 * 从而避免两个并发运行覆盖彼此的思考结果。</p>
 */
public interface AgentStepDecision {

    /**
     * 判断当前步骤是否需要执行行动。
     *
     * @return true 表示继续执行 act，false 表示本步骤无需行动
     */
    boolean shouldAct();
}