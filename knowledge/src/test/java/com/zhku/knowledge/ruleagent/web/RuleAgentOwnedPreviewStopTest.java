package com.zhku.knowledge.ruleagent.web;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 不启动真实进程，验证宿主对其拥有的预览进程的停止协议。 */
class RuleAgentOwnedPreviewStopTest {
    @Test void forcedStopWaitsForExitBeforeReturning() throws Exception {
        var process = new SlowProcess();
        RuleAgentThreeQuestionHostTest.stopOwnedPreview(process);
        assertThat(process.events).containsExactly("destroy", "wait", "force", "wait");
        assertThat(process.isAlive()).isFalse();
    }

    @Test void interruptionStillForcesAndWaitsForOwnedProcess() {
        var process = new InterruptedProcess();
        try {
            assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.stopOwnedPreview(process))
                    .isInstanceOf(InterruptedException.class);
            assertThat(process.events).containsExactly("destroy", "wait-interrupted", "force", "wait");
            assertThat(process.isAlive()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // 不让本测试的中断状态影响同线程的其他测试。
        }
    }

    @Test void failedStopStillWritesTruthfulExitReceipt(@TempDir Path batch) throws Exception {
        var process = new SlowProcess() {
            @Override public boolean waitFor(long timeout, TimeUnit unit) { events.add("wait"); return false; }
        };
        assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.finishOwnedPreview(process, 12345, batch))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("未退出");
        var receipt = RuleAgentThreeQuestionHostTest.JSON.readTree(Files.readString(batch.resolve("page-proxy-exit.json")));
        assertThat(receipt.path("proxyPort").asInt()).isEqualTo(12345);
        assertThat(receipt.path("exited").asBoolean(true)).isFalse();
        assertThat(receipt.path("browserExecutedByHost").asBoolean(true)).isFalse();
        assertThat(process.events).containsExactly("destroy", "wait", "force", "wait");
    }

    @Test void cleanupFailureDoesNotReplacePageFailure(@TempDir Path batch) throws Exception {
        var process = new SlowProcess() {
            @Override public boolean waitFor(long timeout, TimeUnit unit) { events.add("wait"); return false; }
        };
        var original = new AssertionError("page failed");
        RuleAgentThreeQuestionHostTest.finishPage(process, 12345, batch, original);
        assertThat(original.getSuppressed()).hasSize(1);
        assertThat(original.getSuppressed()[0]).isInstanceOf(IllegalStateException.class).hasMessageContaining("未退出");
        assertThat(RuleAgentThreeQuestionHostTest.JSON.readTree(Files.readAllBytes(batch.resolve("page-proxy-exit.json")))
                .path("exited").asBoolean(true)).isFalse();
    }

    private static final class InterruptedProcess extends SlowProcess {
        boolean first = true;
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (first) { first = false; events.add("wait-interrupted"); throw new InterruptedException("synthetic"); }
            return super.waitFor(timeout, unit);
        }
    }

    private static class SlowProcess extends Process {
        final List<String> events = new ArrayList<>();
        boolean alive = true;
        boolean forced;
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { throw new AssertionError("等待必须有界"); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            assertThat(timeout).isPositive();
            assertThat(unit.toMillis(timeout)).isLessThanOrEqualTo(5000);
            events.add("wait");
            if (forced) alive = false;
            return !alive;
        }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { events.add("destroy"); }
        @Override public Process destroyForcibly() { events.add("force"); forced = true; return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
