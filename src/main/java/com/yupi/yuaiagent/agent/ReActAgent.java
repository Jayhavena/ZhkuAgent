package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.AgentStepDecision;
import com.yupi.yuaiagent.agent.runtime.AgentRunContext;

/**
 * ReAct（Reasoning and Acting）模式的 Agent 抽象类。
 *
 * @param <D> 当前步骤产生的不可变决策类型
 */
public abstract class ReActAgent<D extends AgentStepDecision>
        extends BaseAgent {

    /**
     * 根据当前运行上下文完成思考并返回本步骤决策。
     *
     * @param context 本次运行上下文
     * @return 只属于当前步骤的不可变决策
     */
    protected abstract D think(AgentRunContext context);

    /**
     * 执行当前步骤决策。
     *
     * @param context  本次运行上下文
     * @param decision 当前 think 直接返回的决策
     * @return 行动结果
     */
    protected abstract String act(
            AgentRunContext context,
            D decision
    );

    /**
     * 执行一个完整的“思考—行动”步骤。
     *
     * <p>这里不再吞掉异常。think 或 act 的异常会传播到 BaseAgent 运行边界，
     * 由运行边界统一设置 FAILED 并执行清理。</p>
     */
    @Override
    protected final String step(AgentRunContext context) {
        // 决策只存在于当前方法栈帧，不会被另一个运行覆盖。
        D decision = think(context);

        if (!decision.shouldAct()) {
            return "思考完成 - 无需行动";
        }

        // act 显式接收当前 think 返回的同一个 decision。
        return act(context, decision);
    }
}