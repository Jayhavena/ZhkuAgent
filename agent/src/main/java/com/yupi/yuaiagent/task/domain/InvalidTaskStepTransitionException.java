package com.yupi.yuaiagent.task.domain;

/**
 * 表示持久步骤发生非法状态迁移。
 */
public class InvalidTaskStepTransitionException extends IllegalStateException {

    public InvalidTaskStepTransitionException(TaskStepState from, TaskStepState to) {
        super("非法任务步骤状态迁移: " + from + " -> " + to);
    }
}
