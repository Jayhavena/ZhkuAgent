package com.zhku.agent.task.domain;

/**
 * 表示调用方试图执行未被领域状态机允许的任务迁移。
 *
 * <p>使用异常而非返回模糊的 {@code false}，使应用服务不能忽略一次违法的生命周期调用。</p>
 */
public class InvalidTaskTransitionException extends IllegalStateException {

    public InvalidTaskTransitionException(TaskState from, TaskState to) {
        super("非法任务状态迁移: " + from + " -> " + to);
    }
}
