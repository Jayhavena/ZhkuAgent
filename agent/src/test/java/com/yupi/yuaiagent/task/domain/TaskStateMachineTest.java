package com.yupi.yuaiagent.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务状态机的领域契约。
 *
 * <p>测试断言对外可见的迁移行为，而非 Map 等内部实现细节；以后更换实现时，
 * 只要迁移规则不变，测试仍应成立。</p>
 */
class TaskStateMachineTest {

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void shouldAcceptLegalTransitions(TaskState from, TaskState to) {
        assertThatCode(() -> TaskStateMachine.requireTransition(from, to))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTerminalStateTransition() {
        // 终态一旦写入持久化任务，不能被后续恢复器或重复请求重新激活。
        assertThatThrownBy(() -> TaskStateMachine.requireTransition(
                TaskState.SUCCEEDED,
                TaskState.RUNNING
        ))
                .isInstanceOf(InvalidTaskTransitionException.class)
                .hasMessageContaining("SUCCEEDED")
                .hasMessageContaining("RUNNING");
    }

    @Test
    void shouldClassifyOnlyFinalStatesAsTerminal() {
        assertThat(Stream.of(TaskState.values())
                .filter(TaskState::isTerminal)
                .toList())
                .containsExactlyInAnyOrder(
                        TaskState.SUCCEEDED,
                        TaskState.FAILED,
                        TaskState.CANCELLED,
                        TaskState.TIMED_OUT
                );
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(TaskState.CREATED, TaskState.PLANNING),
                Arguments.of(TaskState.CREATED, TaskState.CANCEL_REQUESTED),
                Arguments.of(TaskState.PLANNING, TaskState.READY),
                Arguments.of(TaskState.PLANNING, TaskState.WAITING_RETRY),
                Arguments.of(TaskState.READY, TaskState.RUNNING),
                Arguments.of(TaskState.RUNNING, TaskState.SUCCEEDED),
                Arguments.of(TaskState.RUNNING, TaskState.WAITING_RETRY),
                Arguments.of(TaskState.RUNNING, TaskState.CANCEL_REQUESTED),
                Arguments.of(TaskState.RUNNING, TaskState.READY),
                Arguments.of(TaskState.WAITING_RETRY, TaskState.READY),
                Arguments.of(TaskState.CANCEL_REQUESTED, TaskState.CANCELLED),
                Arguments.of(TaskState.CANCEL_REQUESTED, TaskState.FAILED)
        );
    }
}
