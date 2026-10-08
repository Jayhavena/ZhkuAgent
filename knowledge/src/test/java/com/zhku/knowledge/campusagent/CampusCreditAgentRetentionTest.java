package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.tools.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 时钟和发布屏障可控，不靠等待十分钟或反复碰撞竞态验证回收约束。 */
class CampusCreditAgentRetentionTest {
    @Test
    void sameClockTickEvictsEarlierPublishedCompletion() {
        var clock = new TestClock();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(), Runnable::run, timer, Duration.ofSeconds(5), 2,
                    Duration.ofMinutes(10), clock);
            var first = manager.start(request());
            var second = manager.start(request());
            var third = manager.start(request());
            assertThatThrownBy(() -> manager.find(first.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(manager.find(second.runId()).terminated()).isTrue();
            assertThat(manager.find(third.runId()).terminated()).isTrue();
        }
    }

    private static CampusCreditAgentService service() {
        return new CampusCreditAgentService(
                prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("补充课程")))),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> {
                    throw new AssertionError("不查询政策");
                }, 3);
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("公选课学分", List.of());
    }

    private static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    @Test
    void ttlDoesNotSlideOnReadOrCancel() {
        var clock = new TestClock();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(), Runnable::run, timer, Duration.ofSeconds(5), 2,
                    Duration.ofMinutes(10), clock);
            var first = manager.start(request());
            clock.advance(599);
            assertThat(manager.find(first.runId()).terminated()).isTrue();
            assertThat(manager.cancel(first.runId()).terminated()).isTrue();
            clock.advance(1);
            assertThatThrownBy(() -> manager.find(first.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> manager.cancel(first.runId())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void pressureEvictsOldestCompletedBeforeTtl() {
        var clock = new TestClock();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(), Runnable::run, timer, Duration.ofSeconds(5), 2,
                    Duration.ofMinutes(10), clock);
            var first = manager.start(request());
            clock.advance(1);
            var second = manager.start(request());
            clock.advance(1);
            var third = manager.start(request());
            assertThatThrownBy(() -> manager.find(first.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(manager.find(second.runId()).terminated()).isTrue();
            assertThat(manager.find(third.runId()).terminated()).isTrue();
        }
    }

    @Test
    void queuedAndStoppedRunsCannotBeEvicted() {
        var clock = new TestClock();
        var queue = new ArrayList<Runnable>();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(), queue::add, timer, Duration.ofMinutes(30), 1,
                    Duration.ofMinutes(10), clock);
            var first = manager.start(request());
            clock.advance(3600);
            assertThat(manager.find(first.runId()).state()).isEqualTo(AgentRunState.CREATED);
            assertThatThrownBy(() -> manager.start(request())).isInstanceOf(RejectedExecutionException.class);
            manager.cancel(first.runId());
            assertThatThrownBy(() -> manager.start(request())).isInstanceOf(RejectedExecutionException.class);
            assertThat(manager.find(first.runId()).state()).isEqualTo(AgentRunState.STOP_REQUESTED);
            queue.remove(0).run();
            assertThat(manager.find(first.runId()).state()).isEqualTo(AgentRunState.CANCELLED);
            var next = manager.start(request());
            manager.cancel(next.runId());
            queue.remove(0).run();
        }
    }

    @Test
    void concurrentAdmissionsCannotExceedCapacity() throws Exception {
        var queue = new ConcurrentLinkedQueue<Runnable>();
        try (var timer = Executors.newSingleThreadScheduledExecutor(); var callers = Executors.newFixedThreadPool(8)) {
            var manager = new CampusCreditAgentManager(service(), queue::add, timer, Duration.ofMinutes(5), 2,
                    Duration.ofMinutes(10), new TestClock());
            var gate = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++)
                futures.add(callers.submit(() -> {
                    gate.await();
                    try {
                        manager.start(request());
                        return true;
                    } catch (RejectedExecutionException expected) {
                        return false;
                    }
                }));
            gate.countDown();
            int accepted = 0;
            for (var future : futures)
                if (future.get(5, TimeUnit.SECONDS))
                    accepted++;
            assertThat(accepted).isEqualTo(2);
            assertThat(queue.size()).isEqualTo(2);
            queue.forEach(Runnable::run);
        }
    }

    @Test
    void unpublishedCompletionCannotBeReportedTerminatedOrEvicted() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var clock = new TestClock() {
            @Override
            public Instant instant() {
                if (Thread.currentThread().getName().equals("publication-worker")) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new AssertionError("等待发布超时");
                    } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                    }
                }
                return super.instant();
            }
        };
        var queue = new ArrayList<Runnable>();
        try (var timer = Executors.newSingleThreadScheduledExecutor()) {
            var manager = new CampusCreditAgentManager(service(), queue::add, timer, Duration.ofMinutes(5), 1,
                    Duration.ofMinutes(10), clock);
            var first = manager.start(request());
            var worker = new Thread(queue.remove(0), "publication-worker");
            worker.start();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(manager.find(first.runId()).terminated()).isFalse();
                assertThatThrownBy(() -> manager.start(request())).isInstanceOf(RejectedExecutionException.class);
            } finally {
                release.countDown();
                worker.join(5000);
            }
            assertThat(worker.isAlive()).isFalse();
            assertThat(manager.find(first.runId()).terminated()).isTrue();
            assertThat(manager.find(first.runId()).status())
                    .isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
        } finally {
            release.countDown();
        }
    }

    @Test
    void schedulerRejectionPublishesExplicitFailure() {
        var timer = Executors.newSingleThreadScheduledExecutor();
        timer.shutdown();
        var manager = new CampusCreditAgentManager(service(), task -> {
            throw new AssertionError("不可执行");
        }, timer, Duration.ofSeconds(1));
        var result = manager.start(request());
        assertThat(result.terminated()).isTrue();
        assertThat(result.failureCategory()).isEqualTo("EXECUTOR_REJECTED");
    }
}
