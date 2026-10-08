package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.knowledge.tools.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import java.math.BigDecimal;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** 真实宿主/线程，模型仍是合成替身；不启动HTTP或数据库。 */
class CampusObserverIsolationIntegrationTest {
    AgentRunObservationDispatcher owner;
    final CountDownLatch releaseObserver = new CountDownLatch(1), releaseModel = new CountDownLatch(1);
    @BeforeEach void open() { owner = AgentRunObservationDispatcher.createOwned(); }
    @AfterEach void close() throws Exception {
        releaseObserver.countDown(); releaseModel.countDown(); owner.close();
        await(() -> owner.status().liveThreadCount() == 0, 2000);
    }
    static void await(java.util.function.BooleanSupplier condition, long milliseconds) throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(1);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    static void hold(CountDownLatch latch) {
        while (latch.getCount() != 0) try { latch.await(); } catch (InterruptedException ignored) { }
    }
    static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("合成补充课程", List.of());
    }
    CampusCreditAgentService service(ChatModel model, java.util.function.Consumer<AgentRunEvent> observer) {
        return new CampusCreditAgentService(model, new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                q -> { throw new AssertionError("不得调用政策入口"); }, 2, observer, owner);
    }
    static ChatResponse clarification() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("请补充课程"))));
    }

    @Test void oneTimerStopsTwoRunsAndInterruptsBusinessWhileFirstObserverBlocks() throws Exception {
        var enteredObserver = new CountDownLatch(1); var enteredModels = new CountDownLatch(2);
        var seenInterrupt = new CountDownLatch(2); var interrupts = new CopyOnWriteArrayList<Long>();
        var first = new AtomicBoolean(true);
        var service = service(p -> {
            enteredModels.countDown();
            while (releaseModel.getCount() != 0) try { releaseModel.await(); }
            catch (InterruptedException expected) { interrupts.add(System.nanoTime()); seenInterrupt.countDown(); }
            return clarification();
        }, e -> {
            if (e.type() == AgentRunEvent.Type.RUN_STARTED && first.compareAndSet(true, false)) {
                enteredObserver.countDown(); hold(releaseObserver);
            }
        });
        var business = Executors.newFixedThreadPool(2);
        var timer = new ControlledTimer();
        try {
            var manager = new CampusCreditAgentManager(service, business, timer, Duration.ofSeconds(30));
            var a = manager.start(request()); var b = manager.start(request());
            assertThat(enteredModels.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(enteredObserver.await(2, TimeUnit.SECONDS)).isTrue();
            var ready = new CountDownLatch(1); var gate = new CountDownLatch(1); var done = new CountDownLatch(1);
            timer.execute(() -> { ready.countDown(); hold(gate); for (var timeout : timer.timeouts) timeout.run(); done.countDown(); });
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            long at = System.nanoTime(); gate.countDown();
            assertThat(done.await(200, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(System.nanoTime() - at).isLessThanOrEqualTo(200_000_000L);
            assertThat(seenInterrupt.await(Math.max(0, 200_000_000L - (System.nanoTime() - at)), TimeUnit.NANOSECONDS)).isTrue();
            assertThat(interrupts).hasSize(2).allSatisfy(t -> assertThat(t - at).isLessThanOrEqualTo(200_000_000L));
            for (UUID id : List.of(a.runId(), b.runId())) {
                assertThat(manager.find(id).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
                assertThat(manager.cancel(id).stopReason()).isEqualTo(AgentStopReason.RUN_TIMEOUT);
            }
            assertThat(releaseObserver.getCount()).isEqualTo(1);
            releaseModel.countDown();
            await(() -> manager.find(a.runId()).terminated() && manager.find(b.runId()).terminated(), 2000);
            assertThat(manager.find(b.runId()).state()).isEqualTo(AgentRunState.CANCELLED);
        } finally {
            releaseModel.countDown(); releaseObserver.countDown();
            business.shutdownNow(); timer.shutdownNow();
            assertThat(business.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(timer.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void rejectedUnstartedContextsDoNotConsumeRegistryAndFinalReadsCompleteResult() throws Exception {
        var queue = new ArrayList<Runnable>(); var finalRead = new AtomicReference<CampusCreditAgentManager.Snapshot>();
        var managerRef = new AtomicReference<CampusCreditAgentManager>(); var returned = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        try (var timer = new ControlledTimer()) {
            var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
                if (e.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                    try { finalRead.set(managerRef.get().find(e.runId())); }
                    catch (Throwable error) { failure.set(error); } finally { returned.countDown(); }
                }
            }), queue::add, timer, Duration.ofSeconds(30), 1, Duration.ofMinutes(10), Clock.systemUTC());
            managerRef.set(manager);
            var accepted = manager.start(request());
            for (int i = 0; i < 64; i++) assertThatThrownBy(() -> manager.start(request())).isInstanceOf(RejectedExecutionException.class);
            assertThat(owner.status().registeredCount()).isZero();
            queue.removeFirst().run();
            assertThat(returned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(finalRead.get()).isEqualTo(manager.find(accepted.runId()));
            assertThat(finalRead.get().terminated()).isTrue();
            assertThat(finalRead.get().answer()).isNotBlank();
            await(() -> owner.status().registeredCount() == 0, 1000);
        }
    }

    @Test void delayedFinalMayMissEvictedRecordWithoutChangingPublishedSnapshot() throws Exception {
        var entered = new CountDownLatch(1); var completed = new CountDownLatch(1);
        var otherFinal = new CountDownLatch(1);
        var managerRef = new AtomicReference<CampusCreditAgentManager>(); var first = new AtomicBoolean(true);
        var finalFact = new AtomicReference<AgentRunEvent>(); var readFailure = new AtomicReference<Throwable>();
        try (var timer = new ControlledTimer()) {
            var manager = new CampusCreditAgentManager(service(p -> clarification(), e -> {
                if (e.type() == AgentRunEvent.Type.RUN_TERMINATED && first.compareAndSet(true, false)) {
                    finalFact.set(e); entered.countDown(); hold(releaseObserver);
                    try { managerRef.get().find(e.runId()); } catch (Throwable failure) { readFailure.set(failure); }
                    finally { completed.countDown(); }
                } else if (e.type() == AgentRunEvent.Type.RUN_TERMINATED) otherFinal.countDown();
            }), Runnable::run, timer, Duration.ofSeconds(30), 1, Duration.ofMinutes(10), Clock.systemUTC());
            managerRef.set(manager);
            long at = System.nanoTime(); var old = manager.start(request());
            assertThat(System.nanoTime() - at).isLessThanOrEqualTo(200_000_000L);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(manager.find(old.runId()).terminated()).isTrue();
            assertThat(manager.find(old.runId()).answer()).isNotBlank();
            manager.start(request()); // 容量压力合法回收，不能为迟到诊断钉住记录。
            assertThat(otherFinal.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseObserver.getCount()).isEqualTo(1);
            assertThatThrownBy(() -> manager.find(old.runId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(finalFact.get().runSnapshot().terminated()).isTrue();
            releaseObserver.countDown();
            assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(readFailure.get()).isInstanceOf(IllegalArgumentException.class);
        } finally { releaseObserver.countDown(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANSWERED", "NEED_CLARIFICATION", "HANDOFF"})
    void directServicePublishesFactAndDeliversFinalIndependently(String expected) throws Exception {
        var events = new CopyOnWriteArrayList<AgentRunEvent>();
        var calls = new AtomicInteger(); var policyCalls = new AtomicInteger();
        String courses = "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5}]}";
        ChatModel model = p -> {
            int number = calls.incrementAndGet();
            if (expected.equals("NEED_CLARIFICATION")) return clarification();
            var call = new AssistantMessage.ToolCall("synthetic-id", "function",
                    number == 1 ? "lookup_public_elective_credit_limit" : "summarize_course_credits",
                    number == 1 ? "{}" : courses);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
        };
        var service = new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), q -> {
                    policyCalls.incrementAndGet();
                    var answer = new LocalPreviewConversationFacade.ConversationAnswer("每学期最高4学分", "以培养方案为准",
                            UUID.fromString("00000000-0000-0000-0000-000000000001"), "合成手册", "测试版", "第8条", "本科生");
                    return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                            "合成政策", expected.equals("HANDOFF") ? null : answer,
                            expected.equals("HANDOFF") ? AnswerRoute.INSUFFICIENT_EVIDENCE : AnswerRoute.HIGH_RISK_RULE,
                            expected.equals("HANDOFF"), List.of(), List.of());
                }, 2, events::add, owner);
        var request = expected.equals("NEED_CLARIFICATION") ? request()
                : new CampusCreditAgentService.Request("虚构公选课合计及上限", List.of(
                        new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5"))));
        var result = service.run(request);
        assertThat(result.status().name()).isEqualTo(expected);
        assertThat(result.terminated()).isTrue();
        await(() -> events.stream().anyMatch(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)
                && owner.status().inFlightCount() == 0, 1000);
        var snapshot = events.getLast().runSnapshot();
        assertThat(snapshot.runId()).isEqualTo(result.runId());
        assertThat(snapshot.state()).isEqualTo(result.state());
        assertThat(snapshot.terminated()).isTrue(); assertThat(snapshot.error()).isNull();
        assertThat(snapshot.state()).isEqualTo(expected.equals("ANSWERED") ? AgentRunState.SUCCEEDED : AgentRunState.INCOMPLETE);
        assertThat(events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)).hasSize(1);
        assertThat(calls.get()).isEqualTo(expected.equals("ANSWERED") ? 2 : 1);
        assertThat(policyCalls.get()).isEqualTo(expected.equals("NEED_CLARIFICATION") ? 0 : 1);
    }

    private static final class ControlledTimer extends ScheduledThreadPoolExecutor {
        final List<Runnable> timeouts = new CopyOnWriteArrayList<>();
        ControlledTimer() { super(1); setExecuteExistingDelayedTasksAfterShutdownPolicy(false); }
        // execute是屏障控制任务，不能被下面的“截获生产超时”重载延迟一天。
        public void execute(Runnable command) { super.schedule(command, 0, TimeUnit.NANOSECONDS); }
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            timeouts.add(command); return super.schedule(command, 1, TimeUnit.DAYS);
        }
        public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
            timeouts.add(() -> { try { command.call(); } catch (Exception failure) { throw new AssertionError(failure); } });
            return super.schedule(command, 1, TimeUnit.DAYS);
        }
        public void close() {
            shutdownNow();
            try { assertThat(awaitTermination(2, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        }
    }
}
