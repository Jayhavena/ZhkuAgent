package com.zhku.knowledge.conversation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 受控Session唯一I/O资源owner；actor view不建池，取消Future不等于连接已归还。 */
public final class ConversationJdbcAccess implements AutoCloseable {
    public enum WriteKind { NONE, CREATE, WRITE }
    public record Status(boolean closed, boolean resourceFailure, int inFlight, int liveThreads) { }
    @FunctionalInterface public interface Job<T> { T run(Transaction transaction) throws Exception; }

    private final DataSource source;
    private final Object admission = new Object();
    private final Semaphore slots = new Semaphore(2);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean resourceFailure = new AtomicBoolean();
    private final Set<Work<?>> active = ConcurrentHashMap.newKeySet();
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;

    public ConversationJdbcAccess(DataSource dataSource) {
        source = bounded(Objects.requireNonNull(dataSource, "数据源不能为空"));
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            threads.removeIf(thread -> thread.getState() == Thread.State.TERMINATED);
            Thread thread = new Thread(task, "zhku-conversation-jdbc");
            threads.add(thread);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T execute(ConversationOperationBudget budget, WriteKind kind, Job<T> job) {
        Objects.requireNonNull(kind, "写事实类型不能为空");
        Objects.requireNonNull(job, "数据库操作不能为空");
        Work<T> work = new Work<>(Objects.requireNonNull(budget).begin(), kind);
        synchronized (admission) {
            if (closed.get() || resourceFailure.get() || !slots.tryAcquire()) throw unavailable();
            active.add(work);
            try { workers.execute(() -> run(work, job)); }
            catch (RejectedExecutionException rejected) {
                active.remove(work);
                slots.release();
                throw unavailable();
            }
        }
        try {
            long remaining = work.operation.remainingNanos();
            if (remaining <= 0) throw new TimeoutException();
            return work.result.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            try { return onAbandoned(work); }
            finally { Thread.currentThread().interrupt(); }
        } catch (TimeoutException timeout) {
            return onAbandoned(work);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof ConversationStoreFailure expected) throw expected;
            throw unavailable();
        }
    }

    private <T> T onAbandoned(Work<T> work) {
        synchronized (work) {
            work.cancel();
            if (work.commitConfirmed && work.writeIssued) {
                // 已确定提交，close还未结束也不能谎称未写；封准入并单列资源故障。
                resourceFailure.set(true);
                return work.confirmed;
            }
            if (work.writeIssued && !work.rollbackConfirmed) {
                resourceFailure.set(true);
                throw unknown(work.kind);
            }
            throw unavailable();
        }
    }

    private <T> void run(Work<T> work, Job<T> job) {
        Connection connection = null;
        T value = null;
        ConversationStoreFailure failure = null;
        boolean physicallyReleased = true;
        long cleanupStarted = 0;
        Thread.interrupted();
        synchronized (work) { work.runner = Thread.currentThread(); }
        try {
            work.check();
            connection = source.getConnection();
            physicallyReleased = false;
            work.check();
            connection.setAutoCommit(false);
            connection.setReadOnly(false); // PostgreSQL的FOR SHARE不能在只读事务里执行。
            var transaction = new Transaction(connection, work);
            value = job.run(transaction);
            transaction.beforeCommit();
            connection.commit();
            synchronized (work) {
                work.commitConfirmed = true;
                work.confirmed = value;
            }
        } catch (Exception error) {
            cleanupStarted = System.nanoTime();
            boolean rolledBack = false;
            if (connection != null) {
                // 清理用独立有限预算，但不重试业务SQL/commit；中断不能跳过rollback。
                Thread.interrupted();
                try {
                    connection.setNetworkTimeout(Runnable::run, cleanupMillis(cleanupStarted));
                    connection.rollback();
                    rolledBack = true;
                } catch (Exception cleanupFailure) { resourceFailure.set(true); }
            }
            synchronized (work) {
                // commit ACK丢失后rollback成功也不能证明此前未提交。
                work.rollbackConfirmed = rolledBack && !work.commitAttempted;
                if (work.writeIssued && !work.rollbackConfirmed) failure = unknown(work.kind);
                else failure = error instanceof ConversationStoreFailure expected ? expected : unavailable();
            }
        } finally {
            if (connection != null) {
                if (cleanupStarted == 0) cleanupStarted = System.nanoTime();
                Thread.interrupted();
                try {
                    connection.setNetworkTimeout(Runnable::run, cleanupMillis(cleanupStarted));
                    connection.close();
                    physicallyReleased = true;
                } catch (Exception closeFailure) {
                    resourceFailure.set(true);
                    // abort在同一worker执行，绝不为不能关闭的连接无限新建清理线程。
                    try {
                        connection.setNetworkTimeout(Runnable::run, cleanupMillis(cleanupStarted));
                        connection.abort(Runnable::run);
                        physicallyReleased = connection.isClosed();
                    } catch (Exception abortFailure) { physicallyReleased = false; }
                    synchronized (work) {
                        if ((!work.commitConfirmed || !work.writeIssued) && failure == null) failure = unavailable();
                    }
                }
            }
            synchronized (work) { work.runner = null; }
            Thread.interrupted();
            if (physicallyReleased) {
                active.remove(work);
                slots.release();
            } else {
                // 无法证实真实释放时保留占用状态，即使执行线程已结束也不放空名额。
                resourceFailure.set(true);
            }
        }
        synchronized (work) {
            if (work.commitConfirmed && work.writeIssued) work.result.complete(work.confirmed);
            else if (failure != null) work.result.completeExceptionally(failure);
            else work.result.complete(value);
        }
    }

    /** 只在owner工作线程使用；不把Connection交给HTTP线程或留下跨请求事务。 */
    public static final class Transaction {
        private final Connection connection;
        private final Work<?> work;
        private int statements;
        private Transaction(Connection connection, Work<?> work) { this.connection = connection; this.work = work; }

        public PreparedStatement prepare(String sql) throws SQLException {
            work.check();
            // 至多5次statement，另预留BEGIN、commit及故障rollback，整事务不超过8条SQL。
            if (++statements > 5) throw unavailable();
            long millis = Math.min(1000, TimeUnit.NANOSECONDS.toMillis(work.operation.remainingNanos()));
            if (millis <= 0) throw unavailable();
            // JDBC queryTimeout最小粒度是秒；网络时限进一步收紧到本次剩余毫秒。
            connection.setNetworkTimeout(Runnable::run, (int) millis);
            PreparedStatement statement = connection.prepareStatement(sql);
            try { statement.setQueryTimeout(1); return statement; }
            catch (SQLException failure) { statement.close(); throw failure; }
        }

        public void check() { work.check(); }
        public void writeIssued() {
            synchronized (work) {
                work.check();
                if (work.kind == WriteKind.NONE) throw new IllegalStateException("只读操作不能写入");
                work.writeIssued = true;
            }
        }

        private void beforeCommit() throws SQLException {
            work.check();
            long millis = Math.min(1000, TimeUnit.NANOSECONDS.toMillis(work.operation.remainingNanos()));
            if (millis <= 0) throw unavailable();
            connection.setNetworkTimeout(Runnable::run, (int) millis);
            synchronized (work) { work.check(); work.commitAttempted = true; }
        }
    }

    private static final class Work<T> {
        final ConversationOperationBudget.Operation operation;
        final WriteKind kind;
        final CompletableFuture<T> result = new CompletableFuture<>();
        boolean cancelled;
        boolean writeIssued;
        boolean commitAttempted;
        boolean commitConfirmed;
        boolean rollbackConfirmed;
        T confirmed;
        Thread runner;
        Work(ConversationOperationBudget.Operation operation, WriteKind kind) { this.operation = operation; this.kind = kind; }
        synchronized void check() { if (cancelled) throw unavailable(); operation.check(); }
        synchronized void cancel() {
            cancelled = true;
            // 与runner清空同一锁，迟到超时不得中断worker承接的下一请求。
            if (runner != null) runner.interrupt();
        }
    }

    private static DataSource bounded(DataSource original) {
        if (!(original instanceof DriverManagerDataSource driver)) return original;
        var copy = new DriverManagerDataSource();
        copy.setUrl(driver.getUrl());
        copy.setUsername(driver.getUsername());
        copy.setPassword(driver.getPassword());
        var settings = new Properties();
        if (driver.getConnectionProperties() != null) settings.putAll(driver.getConnectionProperties());
        settings.setProperty("connectTimeout", "1");
        settings.setProperty("socketTimeout", "2");
        settings.setProperty("cancelSignalTimeout", "1");
        // 不设置loginTimeout，避免驱动另起不受本owner名额控制的连接线程。
        copy.setConnectionProperties(settings);
        return copy;
    }

    private static int cleanupMillis(long began) throws SQLException {
        long remaining = TimeUnit.SECONDS.toNanos(2) - (System.nanoTime() - began);
        if (remaining <= 0) throw new SQLException("Session清理预算已耗尽");
        // rollback/close/abort共享这一份2秒预算，不为每一步重新启动2秒。
        return (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
    }

    public Status status() {
        return new Status(closed.get(), resourceFailure.get(), 2 - slots.availablePermits(),
                (int) threads.stream().filter(Thread::isAlive).count());
    }

    public void close(Duration budget) {
        Objects.requireNonNull(budget, "关闭预算不能为空");
        if (budget.isNegative() || budget.compareTo(Duration.ofSeconds(2)) > 0)
            throw new IllegalArgumentException("Session资源关闭预算须在0至2秒内");
        long began = System.nanoTime();
        synchronized (admission) {
            closed.set(true);
            active.forEach(Work::cancel);
            workers.shutdownNow();
        }
        long remaining = budget.toNanos() - (System.nanoTime() - began);
        try {
            if (remaining > 0) workers.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            // executor的TERMINATED信号早于最后worker线程真正退出；仍用同一关闭deadline核实。
            for (Thread thread : threads) {
                remaining = budget.toNanos() - (System.nanoTime() - began);
                if (remaining <= 0) break;
                if (thread != Thread.currentThread() && thread.isAlive())
                    TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (!active.isEmpty() || threads.stream().anyMatch(Thread::isAlive)) resourceFailure.set(true);
    }

    @Override public void close() { close(Duration.ofSeconds(2)); }
    private static ConversationStoreFailure unavailable() {
        return new ConversationStoreFailure(ConversationStoreFailure.Code.STORE_UNAVAILABLE);
    }
    private static ConversationStoreFailure unknown(WriteKind kind) {
        return new ConversationStoreFailure(kind == WriteKind.CREATE ? ConversationStoreFailure.Code.CREATION_UNCONFIRMED
                : ConversationStoreFailure.Code.WRITE_UNCONFIRMED);
    }
}
