package com.zhku.agent.task.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 持久任务步骤的合法迁移定义。
 *
 * <p>这个状态机表达“恢复时是否可以再执行步骤”的业务语义，不能替代数据库对并发更新
 * 的 version 条件限制。</p>
 */
public final class TaskStepStateMachine {

    private static final Map<TaskStepState, Set<TaskStepState>> ALLOWED =
            buildTransitions();

    private TaskStepStateMachine() {
    }

    public static void requireTransition(TaskStepState from, TaskStepState to) {
        Set<TaskStepState> targets = ALLOWED.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new InvalidTaskStepTransitionException(from, to);
        }
    }

    private static Map<TaskStepState, Set<TaskStepState>> buildTransitions() {
        EnumMap<TaskStepState, Set<TaskStepState>> transitions =
                new EnumMap<>(TaskStepState.class);
        transitions.put(TaskStepState.PENDING, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.SKIPPED
        ));
        transitions.put(TaskStepState.RUNNING, EnumSet.of(
                TaskStepState.SUCCEEDED,
                TaskStepState.WAITING_RETRY,
                TaskStepState.FAILED,
                TaskStepState.INTERRUPTED
        ));
        transitions.put(TaskStepState.WAITING_RETRY, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.FAILED,
                TaskStepState.INTERRUPTED
        ));
        transitions.put(TaskStepState.INTERRUPTED, EnumSet.of(
                TaskStepState.RUNNING,
                TaskStepState.FAILED
        ));
        return Map.copyOf(transitions);
    }
}
