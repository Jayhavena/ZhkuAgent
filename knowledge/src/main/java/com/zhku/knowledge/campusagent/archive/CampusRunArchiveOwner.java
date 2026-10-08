package com.zhku.knowledge.campusagent.archive;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** session 锁负责参与实例排他；owner 行负责迟到事务 fencing，不负责停止业务工具。 */
public final class CampusRunArchiveOwner implements AutoCloseable {
    static final String OWNER_KEY = "campus-credit-preview";
    static final int LOCK_NAMESPACE = 0x5A484B55;
    static final int LOCK_RESOURCE = 0x43524152;
    private final DataSource source;
    private final Limits limits;
    private final ScheduledExecutorService monitor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean invalidated = new AtomicBoolean();
    private volatile boolean accepting;
    private volatile boolean writable;
    private volatile OwnerToken token;
    private volatile Connection lockConnection;

    public CampusRunArchiveOwner(DataSource source, Limits limits) {
        this.source = boundedDataSource(Objects.requireNonNull(source));
        this.limits = Objects.requireNonNull(limits);
        this.monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "campus-archive-owner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public OwnerToken start() {
        if (closed.get() || !started.compareAndSet(false, true))
            throw new Failure(Code.ARCHIVE_NOT_READY);
        long deadline = System.nanoTime() + limits.startup().toNanos();
        var future = monitor.submit(() -> claim(deadline));
        try {
            return future.get(limits.startup().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fence();
            throw new Failure(Code.ARCHIVE_NOT_READY);
        } catch (TimeoutException e) {
            fence();
            throw new Failure(Code.ARCHIVE_UNAVAILABLE);
        } catch (ExecutionException e) {
            fence();
            if (e.getCause() instanceof Failure failure)
                throw failure;
            throw new Failure(Code.ARCHIVE_UNAVAILABLE);
        }
    }

    private OwnerToken claim(long deadline) {
        boolean ready = false;
        try {
            lockConnection = source.getConnection();
            try (var p = lockConnection.prepareStatement("SELECT pg_try_advisory_lock(?,?)")) {
                p.setInt(1, LOCK_NAMESPACE);
                p.setInt(2, LOCK_RESOURCE);
                try (var r = p.executeQuery()) {
                    if (!r.next() || !r.getBoolean(1))
                        throw new Failure(Code.ARCHIVE_OWNER_LOST);
                }
            }
            UUID instance = UUID.randomUUID();
            long epoch;
            // 排他连接永不重连；对账事务使用另一条连接，不能让事务回滚释放 session 所有权。
            try (var c = source.getConnection()) {
                c.setAutoCommit(false);
                try {
                    transactionSettings(c, deadline);
                    try (var p = c.prepareStatement("""
                            INSERT INTO campus_agent_runtime_owner(owner_key,epoch,instance_id,claimed_at)
                            VALUES (?,0,?,clock_timestamp()) ON CONFLICT DO NOTHING
                            """)) {
                        p.setString(1, OWNER_KEY);
                        p.setObject(2, instance);
                        p.executeUpdate();
                    }
                    try (var p = c.prepareStatement(
                            "SELECT epoch FROM campus_agent_runtime_owner WHERE owner_key=? FOR UPDATE")) {
                        p.setString(1, OWNER_KEY);
                        try (var r = p.executeQuery()) {
                            if (!r.next() || r.getLong(1) == Long.MAX_VALUE)
                                throw new Failure(Code.ARCHIVE_UNAVAILABLE);
                            epoch = r.getLong(1) + 1;
                        }
                    }
                    try (var p = c.prepareStatement(
                            "UPDATE campus_agent_runtime_owner SET epoch=?,instance_id=?,claimed_at=clock_timestamp() WHERE owner_key=?")) {
                        p.setLong(1, epoch);
                        p.setObject(2, instance);
                        p.setString(3, OWNER_KEY);
                        p.executeUpdate();
                    }
                    try (var p = c.prepareStatement("""
                            WITH closed AS (SELECT clock_timestamp() AS at)
                            UPDATE campus_agent_run_archive SET archive_status='INTERRUPTED',revision=revision+1,
                            closed_at=closed.at,retained_until=closed.at+(? * interval '1 second'),
                            interruption_reason='PREVIOUS_OWNER_RESULT_UNCONFIRMED'
                            FROM closed WHERE archive_status='ADMITTED' AND owner_epoch < ?
                            """)) {
                        p.setLong(1, limits.retention().toSeconds());
                        p.setLong(2, epoch);
                        p.executeUpdate();
                    }
                    // 启动只清理一批关闭且到期的记录；ADMITTED 先对账，不能直接按年龄删除。
                    pruneClosed(c);
                    checkDeadline(deadline);
                    if (closed.get() || invalidated.get())
                        throw new Failure(Code.ARCHIVE_NOT_READY);
                    c.commit();
                } catch (SQLException | RuntimeException e) {
                    try {
                        c.rollback();
                    } catch (SQLException ignored) {
                    }
                    throw e;
                }
            }
            checkDeadline(deadline);
            if (closed.get() || invalidated.get())
                throw new Failure(Code.ARCHIVE_NOT_READY);
            token = new OwnerToken(instance, epoch);
            writable = true;
            accepting = true;
            monitor.scheduleWithFixedDelay(this::probe, 1, 1, TimeUnit.SECONDS);
            ready = true;
            return token;
        } catch (SQLException e) {
            throw new Failure(Code.ARCHIVE_UNAVAILABLE);
        } finally {
            if (!ready) {
                fence();
                closePhysical();
            }
        }
    }

    /** 先选同一批ID再分两条语句删除，FK不能被CASCADE或半提交绕过。调用者拥有事务。 */
    static void pruneClosed(Connection c) throws SQLException {
        var ids = new java.util.ArrayList<UUID>();
        try (var s = c.createStatement(); var r = s.executeQuery("""
                SELECT run_id FROM campus_agent_run_archive WHERE archive_status IN ('FINALIZED','INTERRUPTED')
                AND retained_until<=clock_timestamp() ORDER BY retained_until,run_id LIMIT 200
                """)) { while (r.next()) ids.add(r.getObject(1, UUID.class)); }
        try (var child = c.prepareStatement("DELETE FROM campus_agent_run_submission WHERE run_id=?");
             var parent = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
            for (var id : ids) { child.setObject(1,id); child.addBatch(); parent.setObject(1,id); parent.addBatch(); }
            child.executeBatch(); parent.executeBatch();
        }
    }
    private void probe() {
        if (!writable || closed.get())
            return;
        try (var p = lockConnection.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory'
                AND classid=?::oid AND objid=?::oid AND objsubid=2 AND granted)
                """)) {
            p.setLong(1, LOCK_NAMESPACE);
            p.setLong(2, LOCK_RESOURCE);
            try (var r = p.executeQuery()) {
                if (!r.next() || !r.getBoolean(1)) {
                    fence();
                    closePhysical();
                }
            }
        } catch (SQLException | RuntimeException e) {
            fence();
            closePhysical();
        }
    }

    public OwnerToken token() {
        return token;
    }

    public boolean isAccepting() {
        return accepting && !closed.get();
    }

    public boolean mayWrite() {
        return writable && !closed.get();
    }

    public void pause() {
        accepting = false;
    }

    public void fence() {
        invalidated.set(true);
        accepting = false;
        writable = false;
    }

    @Override
    public void close() {
        close(limits.operation());
    }

    public void close(Duration remaining) {
        Objects.requireNonNull(remaining);
        if (remaining.isNegative())
            throw new IllegalArgumentException("关闭预算非法");
        if (!closed.compareAndSet(false, true))
            return;
        fence();
        Future<?> cleanup = monitor.submit(this::closePhysical);
        monitor.shutdown();
        try {
            if (!remaining.isZero() && !remaining.isNegative())
                cleanup.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
        }
    }

    private void closePhysical() {
        var c = lockConnection;
        lockConnection = null;
        if (c != null)
            try {
                c.close();
            } catch (SQLException ignored) {
            }
    }

    void cleanup(Runnable action) {
        try {
            monitor.execute(action);
        } catch (RejectedExecutionException ignored) {
        }
    }

    static DataSource boundedDataSource(DataSource original) {
        if (!(original instanceof DriverManagerDataSource driver))
            return original;
        var copy = new DriverManagerDataSource(driver.getUrl(), driver.getUsername(), driver.getPassword());
        Properties properties = new Properties();
        if (driver.getConnectionProperties() != null)
            properties.putAll(driver.getConnectionProperties());
        properties.setProperty("connectTimeout", "1");
        properties.setProperty("socketTimeout", "2");
        properties.setProperty("cancelSignalTimeout", "1");
        copy.setConnectionProperties(properties);
        return copy;
    }

    static void transactionSettings(Connection c, long deadline) throws SQLException {
        checkDeadline(deadline);
        long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
        try (var p = c
                .prepareStatement("SELECT set_config('statement_timeout',?,true),set_config('lock_timeout',?,true)")) {
            p.setString(1, Long.toString(Math.min(1000, remaining)));
            p.setString(2, Long.toString(Math.min(250, remaining)));
            p.execute();
        }
    }

    static void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
            throw new Failure(Code.ARCHIVE_UNAVAILABLE);
    }
}
