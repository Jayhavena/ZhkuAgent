package com.zhku.agent.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可恢复步骤的生命周期契约。
 *
 * <p>步骤成功后不能重做，进程中断后的步骤却允许重新运行；两者的差异是断点恢复
 * 不重复外部副作用的基础。</p>
 */
class TaskStepStateMachineTest {

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void shouldAcceptLegalTransitions(TaskStepState from, TaskStepState to) {
        assertThatCode(() -> TaskStepStateMachine.requireTransition(from, to))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectReexecutionOfSucceededStep() {
        assertThatThrownBy(() -> TaskStepStateMachine.requireTransition(
                TaskStepState.SUCCEEDED,
                TaskStepState.RUNNING
        ))
                .isInstanceOf(InvalidTaskStepTransitionException.class)
                .hasMessageContaining("SUCCEEDED")
                .hasMessageContaining("RUNNING");
    }

    @Test
    void shouldClassifyFinalStepStates() {
        assertThat(Stream.of(TaskStepState.values())
                .filter(TaskStepState::isTerminal)
                .toList())
                .containsExactlyInAnyOrder(
                        TaskStepState.SUCCEEDED,
                        TaskStepState.FAILED,
                        TaskStepState.SKIPPED
                );
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(TaskStepState.PENDING, TaskStepState.RUNNING),
                Arguments.of(TaskStepState.PENDING, TaskStepState.SKIPPED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.SUCCEEDED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.WAITING_RETRY),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.FAILED),
                Arguments.of(TaskStepState.RUNNING, TaskStepState.INTERRUPTED),
                Arguments.of(TaskStepState.WAITING_RETRY, TaskStepState.RUNNING),
                Arguments.of(TaskStepState.WAITING_RETRY, TaskStepState.FAILED),
                Arguments.of(TaskStepState.INTERRUPTED, TaskStepState.RUNNING)
        );
    }
}
