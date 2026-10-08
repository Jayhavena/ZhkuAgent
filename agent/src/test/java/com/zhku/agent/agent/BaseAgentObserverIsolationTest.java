package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class BaseAgentObserverIsolationTest {
    @ParameterizedTest
    @EnumSource(value = AgentRunEvent.Type.class, names = {"RUN_STARTED", "RUN_TERMINATED"})
    void blockedObserverDoesNotHoldBusinessReturn(AgentRunEvent.Type blocked) throws Exception {
        var owner = AgentRunObservationDispatcher.createOwned();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1); var failure = new AtomicReference<Throwable>();
        var result = new AtomicReference<String>(); var contextRef = new AtomicReference<AgentRunContext>();
        var agent = new BaseAgent(Runnable::run) {
            protected AgentRunObservationDispatcher observationDispatcher() { return owner; }
            protected String step(AgentRunContext context) {
                contextRef.set(context); context.finish(AgentRunState.SUCCEEDED); return "固定完整结果";
            }
            protected void onRunEvent(AgentRunEvent event) {
                if (event.type() == blocked) {
                    entered.countDown();
                    while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { }
                }
            }
        };
        var business = new Thread(() -> {
            try { result.set(agent.run("合成任务")); } catch (Throwable error) { failure.set(error); }
            finally { returned.countDown(); }
        }, "r13-generic-business");
        try {
            long at = System.nanoTime(); business.start();
            assertThat(returned.await(200, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(System.nanoTime() - at).isLessThanOrEqualTo(200_000_000L);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(failure.get()).isNull();
            assertThat(result.get()).contains("固定完整结果");
            assertThat(contextRef.get().snapshot().terminated()).isTrue();
        } finally {
            release.countDown(); business.join(2000); owner.close();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (owner.status().liveThreadCount() != 0 && System.nanoTime() < until) Thread.sleep(1);
            assertThat(business.isAlive()).isFalse();
            assertThat(owner.status().liveThreadCount()).isZero();
        }
    }
}
