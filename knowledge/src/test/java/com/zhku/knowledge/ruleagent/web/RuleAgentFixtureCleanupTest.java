package com.zhku.knowledge.ruleagent.web;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 不启动宿主，验证资源清理失败时其余步骤仍执行且异常不丢失。 */
class RuleAgentFixtureCleanupTest {
    @Test void failedCleanupDoesNotSkipRemainingSteps() {
        var events = new ArrayList<String>();
        var first = new IllegalStateException("first cleanup");
        var second = new IllegalArgumentException("second cleanup");
        Throwable thrown = catchThrowable(() -> RuleAgentThreeQuestionHostTest.runCleanup(
                () -> { events.add("store"); throw first; },
                () -> { events.add("factory"); throw second; },
                () -> events.add("context and receipt")));
        assertThat(events).isEqualTo(List.of("store", "factory", "context and receipt"));
        assertThat(thrown).isSameAs(first);
        assertThat(thrown.getSuppressed()).containsExactly(second);
    }

    @Test void interruptedCleanupPreservesPrimaryAndStillRunsLaterSteps() throws Exception {
        var events = new ArrayList<String>();
        var primary = new AssertionError("original page failure");
        var interrupted = new InterruptedException("worker wait interrupted");
        var cleanup = new RuleAgentThreeQuestionHostTest.Cleanup();
        try {
            cleanup.attempt(() -> { events.add("worker"); throw interrupted; });
            cleanup.attempt(() -> {
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                events.add("context and receipt");
            });
            cleanup.finish(primary);
            assertThat(events).containsExactly("worker", "context and receipt");
            assertThat(primary.getSuppressed()).containsExactly(interrupted);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test void interruptedCleanupWithoutPrimaryRethrowsAndRestoresFlag() {
        var interruption = new InterruptedException("worker interrupted");
        var cleanup = new RuleAgentThreeQuestionHostTest.Cleanup();
        try {
            cleanup.attempt(() -> { throw interruption; });
            assertThatThrownBy(() -> cleanup.finish(null)).isSameAs(interruption);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
}
