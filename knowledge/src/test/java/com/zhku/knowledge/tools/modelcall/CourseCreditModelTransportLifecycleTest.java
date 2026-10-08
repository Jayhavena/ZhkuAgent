package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class CourseCreditModelTransportLifecycleTest {
    @Test
    void normalCloseRejectsNewCallsAndReleasesActualOwnerOnlyOnce() {
        var owner = open();
        owner.close();
        assertThat(owner.isTerminated()).isTrue();
        owner.close();
        assertThatThrownBy(() -> owner.model().call(new Prompt("合成")))
                .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        try (var next = open()) {
            assertThatThrownBy(CourseCreditModelTransportLifecycleTest::open).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void senderSaturationRejectsNineteenthAndCloseDoesNotFakePhysicalRelease() throws Exception {
        var owner = open();
        var pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(owner, "senders");
        var io = (ThreadPoolExecutor) ReflectionTestUtils.getField(owner, "io");
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        List<Thread> blocked = new CopyOnWriteArrayList<>();
        try {
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
            assertThat(io.getMaximumPoolSize()).isEqualTo(4);
            assertThat(io.getQueue().remainingCapacity()).isEqualTo(64);
            for (int i = 0; i < 2; i++) pool.execute(() -> {
                blocked.add(Thread.currentThread());
                entered.countDown();
                while (release.getCount() != 0) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // 可释放永久阻塞替身，不能靠关闭请求声称已结束。
                    }
                }
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 16; i++) pool.execute(() -> {});
            assertThatThrownBy(() -> pool.execute(() -> {}))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            long started = System.nanoTime();
            owner.close();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThanOrEqualTo(1_200);
            assertThat(owner.isTerminated()).isFalse();
            assertThatThrownBy(CourseCreditModelTransportLifecycleTest::open).isInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();
            owner.close();
            assertThat(owner.isTerminated()).isTrue();
            for (Thread thread : blocked) {
                thread.join(2_000);
                assertThat(thread.isAlive()).isFalse();
            }
        }
        try (var next = open()) {
            assertThat(next).isNotNull();
        }
    }

    @Test
    void constructionFailureCleansClientAndDoesNotLoseOwnerSlot() {
        assertThatThrownBy(() -> new CourseCreditModelTransport(Duration.ofMillis(500), (client, sender) -> {
            throw new IllegalStateException("合成构建失败");
        })).isInstanceOf(IllegalStateException.class);
        try (var next = open()) {
            assertThat(next).isNotNull();
        }
    }

    @Test
    void invalidCredentialsAndSubMillisecondDeadlineAllocateNoOwner() {
        assertThatThrownBy(() -> CourseCreditChatModelFactory.open(profile(Duration.ofMillis(500)),
                new ApiKeyResolver(Map.of()))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CourseCreditChatModelFactory.open(profile(Duration.ofNanos(1)),
                new ApiKeyResolver(Map.of("ZHKU_LOCAL_OWNER_KEY", "sk-fake-only"))))
                .isInstanceOf(IllegalArgumentException.class);
        try (var next = open()) {
            assertThat(next).isNotNull();
        }
    }

    private static CourseCreditModelTransport open() {
        return CourseCreditChatModelFactory.open(profile(Duration.ofMillis(500)),
                new ApiKeyResolver(Map.of("ZHKU_LOCAL_OWNER_KEY", "sk-fake-only")));
    }

    private static RagProviderProfile profile(Duration timeout) {
        return new RagProviderProfile("local-owner", "http://127.0.0.1:1/v1", "ZHKU_LOCAL_OWNER_KEY",
                "stub", "stub-embed", 1, timeout, "stub-index");
    }
}
