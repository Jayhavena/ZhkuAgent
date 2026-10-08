package com.zhku.knowledge.security;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 身份I/O固定门槛：2物理并发、0执行队列、32等待登记；总2秒/检测2250ms。 */
@Timeout(15)
class PreviewIdentityBudgetTest {
    private static final TrustedActor ACTOR = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);

    @Test
    void thirdReadExpiresWithoutReleasingTimedOutPhysicalConnections() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var physicalThreads = ConcurrentHashMap.<Thread>newKeySet();
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(invocation -> {
            physicalThreads.add(Thread.currentThread());
            calls.incrementAndGet();
            entered.countDown();
            // 模拟不可协作的连接建立；必须finally释放，不能真正泄漏测试线程。
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            throw new SQLException("synthetic failure");
        });
        var callers = Executors.newFixedThreadPool(2);
        var repository = new PreviewIdentityRepository(source);
        try {
            var first = callers.submit(() -> failure(repository));
            var second = callers.submit(() -> failure(repository));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            long began = System.nanoTime();
            assertThat(failure(repository)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            // 3C有限修订：第三次当前许可核验有界等待，等待和查询仍共享原2秒期限。
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofMillis(2250));
            began = System.nanoTime();
            assertThat(first.get(2250, TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(second.get(2250, TimeUnit.MILLISECONDS)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofMillis(2250));
            // 宿主超时不是连接退出，不能让第三次调用占用新的物理资源。
            assertThat(repository.status().inFlight()).isEqualTo(2);
            assertThat(failure(repository)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
            assertThat(calls).hasValue(2);
            began = System.nanoTime();
            repository.close(Duration.ofSeconds(2));
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofMillis(2250));
            assertThat(repository.status().inFlight()).isEqualTo(2);
            assertThat(failure(repository)).isEqualTo(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE);
        } finally {
            release.countDown();
            repository.close(Duration.ofSeconds(2));
            callers.shutdownNow();
            assertThat(callers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (physicalThreads.stream().anyMatch(Thread::isAlive) && System.nanoTime() < until) Thread.sleep(1);
        assertThat(physicalThreads).hasSize(2);
        assertThat(physicalThreads.stream().anyMatch(Thread::isAlive)).isFalse();
        assertThat(repository.status().liveThreads()).isZero();
        assertThat(repository.status().inFlight()).isZero();
    }

    @Test
    void invalidPasswordIsClearedWithoutDatabaseAccess() throws Exception {
        DataSource source = mock(DataSource.class);
        try (var repository = new PreviewIdentityRepository(source)) {
            char[] password = "short".toCharArray();
            assertThat(repository.authenticate("user-a", password)).isEmpty();
            assertThat(java.util.Arrays.equals(password, new char[password.length])).isTrue();
            verifyNoInteractions(source);
        }
    }

    private static PreviewIdentityRepository.Code failure(PreviewIdentityRepository repository) {
        try {
            repository.requireCurrent(ACTOR);
            throw new AssertionError("应安全失败");
        } catch (PreviewIdentityRepository.Failure expected) {
            return expected.code();
        }
    }
}
