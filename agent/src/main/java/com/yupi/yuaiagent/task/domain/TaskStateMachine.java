package com.yupi.yuaiagent.task.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 持久任务状态迁移的唯一领域入口。
 *
 * <p>本类只判断业务迁移是否合法；应用服务仍需使用数据库的 version 条件更新处理
 * 并发竞争。二者缺一不可：状态机防止“语义上不允许”，乐观锁防止“两个请求同时写”。</p>
 */
public final class TaskStateMachine {

    private static final Map<TaskState, Set<TaskState>> ALLOWED = buildTransitions();

    private TaskStateMachine() {
    }

    /**
     * 校验一次任务状态迁移。终态不在映射中，因此不会产生任何出边。
     */
    public static void requireTransition(TaskState from, TaskState to) {
        Set<TaskState> targets = ALLOWED.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new InvalidTaskTransitionException(from, to);
        }
    }

    private static Map<TaskState, Set<TaskState>> buildTransitions() {
        EnumMap<TaskState, Set<TaskState>> transitions = new EnumMap<>(TaskState.class);
        transitions.put(TaskState.CREATED, EnumSet.of(
                TaskState.PLANNING, TaskState.CANCEL_REQUESTED,
                TaskState.FAILED, TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.PLANNING, EnumSet.of(
                TaskState.READY, TaskState.WAITING_RETRY, TaskState.CANCEL_REQUESTED,
                TaskState.FAILED, TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.READY, EnumSet.of(
                TaskState.RUNNING, TaskState.CANCEL_REQUESTED,
                TaskState.FAILED, TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.RUNNING, EnumSet.of(
                TaskState.READY, TaskState.WAITING_RETRY, TaskState.CANCEL_REQUESTED,
                TaskState.SUCCEEDED, TaskState.FAILED, TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.WAITING_RETRY, EnumSet.of(
                TaskState.READY, TaskState.CANCEL_REQUESTED,
                TaskState.FAILED, TaskState.TIMED_OUT
        ));
        transitions.put(TaskState.CANCEL_REQUESTED, EnumSet.of(
                TaskState.CANCELLED, TaskState.FAILED
        ));
        return Map.copyOf(transitions);
    }
}
