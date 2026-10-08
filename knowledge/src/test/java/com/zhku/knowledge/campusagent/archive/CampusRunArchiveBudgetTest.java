package com.zhku.knowledge.campusagent.archive;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

class CampusRunArchiveBudgetTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    @Test
    void hostTimeoutSealsQueuedWritesAndIoEventuallyConvergesWithoutReplacementThreads() throws Exception {
        var limits = new Limits(Duration.ofMillis(200), Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofDays(7), 10000);
        var underlying = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocking = new AtomicBoolean(true);
        var source = blockingSource(underlying, entered, release, blocking);
        var callers = Executors.newFixedThreadPool(24);
        try (var owner = new CampusRunArchiveOwner(underlying, limits)) {
            owner.start();
            try (var store = new JdbcCampusRunArchiveStore(source, owner, limits, new CampusRunHistoryVisibility())) {
                var jobs = new ArrayList<Future<Code>>();
                for (int i = 0; i < 24; i++)
                    jobs.add(callers.submit(() -> {
                        try {
                            store.admit(UUID.randomUUID());
                            throw new AssertionError("Unexpected admission");
                        } catch (Failure failure) {
                            return failure.code();
                        }
                    }));
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                long beginning = System.nanoTime();
                var codes = new ArrayList<Code>();
                for (var job : jobs)
                    codes.add(job.get(1, TimeUnit.SECONDS));
                assertThat(Duration.ofNanos(System.nanoTime() - beginning)).isLessThan(Duration.ofSeconds(1));
                assertThat(codes).contains(Code.ARCHIVE_BUSY, Code.ARCHIVE_ADMISSION_UNCONFIRMED);
                assertThat(store.isAccepting()).isFalse();
                release.countDown();
                blocking.set(false);
                store.close(Duration.ofSeconds(2));
                try (var c = underlying.getConnection();
                        var s = c.createStatement();
                        var r = s.executeQuery("SELECT count(*) FROM campus_agent_run_archive WHERE owner_instance_id='"
                                + owner.token().instanceId() + "'")) {
                    r.next();
                    assertThat(r.getInt(1)).isZero();
                }
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
        assertArchiveThreadsConverged();
    }

    @Test
    void fifthConcurrentReadIsRejectedAndReadWaitingIsBounded() throws Exception {
        var limits = new Limits(Duration.ofMillis(400), Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofDays(7), 10000);
        var underlying = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var blocking = new AtomicBoolean(true);
        var callers = Executors.newFixedThreadPool(4);
        try (var owner = new CampusRunArchiveOwner(underlying, limits)) {
            owner.start();
            try (var store = new JdbcCampusRunArchiveStore(blockingSource(underlying, entered, release, blocking),
                    owner, limits, new CampusRunHistoryVisibility())) {
                var jobs = new ArrayList<Future<Code>>();
                for (int i = 0; i < 4; i++)
                    jobs.add(callers.submit(() -> {
                        try {
                            store.find(UUID.randomUUID());
                            throw new AssertionError("Unexpected read");
                        } catch (Failure e) {
                            return e.code();
                        }
                    }));
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> store.find(UUID.randomUUID())).isInstanceOf(Failure.class)
                        .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_BUSY);
                for (var job : jobs)
                    assertThat(job.get(1, TimeUnit.SECONDS)).isEqualTo(Code.ARCHIVE_READ_UNAVAILABLE);
                release.countDown();
                blocking.set(false);
                store.close(Duration.ofSeconds(2));
            }
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
        assertArchiveThreadsConverged();
    }

    private static void assertArchiveThreadsConverged() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(t -> t.isAlive() && t.getName().startsWith("campus-archive-")))
                return;
            Thread.sleep(10);
        }
        assertThat(Thread.getAllStackTraces().keySet())
                .noneMatch(t -> t.isAlive() && t.getName().startsWith("campus-archive-"));
    }

    private static DataSource blockingSource(DataSource original, CountDownLatch entered, CountDownLatch release,
            AtomicBoolean block) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection") && block.get()) {
                        entered.countDown();
                        // 测试故意忽略 interrupt，验证宿主返回不能被误认为 I/O 已完成。
                        while (release.getCount() != 0)
                            try {
                                release.await();
                            } catch (InterruptedException ignored) {
                            }
                    }
                    try {
                        return method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
