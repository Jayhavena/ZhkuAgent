package com.zhku.knowledge.security;

import java.nio.CharBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 身份只读边界；许可快照不代替Run/Session各自事务内的当前许可。 */
public final class PreviewIdentityRepository implements AutoCloseable {
    public enum Code { AUTH_REVOKED, ROLE_DENIED, IDENTITY_UNAVAILABLE }
    public static final class Failure extends RuntimeException {
        private final Code code;
        public Failure(Code code) {
            // 只携带固定分类，不携SQL/驱动cause、用户名、密码或hash。
            super(code.name(), null, false, false);
            this.code = code;
        }
        public Code code() { return code; }
    }
    public record Status(boolean closed, int inFlight, int liveThreads) { }

    private static final long OPERATION_NANOS = TimeUnit.SECONDS.toNanos(2);
    private final DataSource source;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash;
    private final Semaphore slots = new Semaphore(2);
    private final Object admission = new Object();
    private int waiting;
    private static final int MAX_WAITING = 32;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Thread> live = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;

    public PreviewIdentityRepository(DataSource source) {
        this.source = bounded(Objects.requireNonNull(source));
        char[] dummy = UUID.randomUUID().toString().replace("-", "").toCharArray();
        try { dummyHash = encoder.encode(CharBuffer.wrap(dummy)); }
        finally { Arrays.fill(dummy, '\0'); }
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            // 保留Thread引用直到isAlive真实为false，不能在run的finally提前宣称物理退出。
            live.removeIf(thread -> thread.getState() == Thread.State.TERMINATED);
            Thread thread = new Thread(task, "zhku-preview-identity");
            live.add(thread);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public Optional<PreviewPrincipal> authenticate(String loginName, char[] password) {
        char[] copy = null;
        boolean handedOff = false;
        try {
            if (loginName == null || !loginName.matches("[a-z][a-z0-9-]{2,31}") || !validPassword(password))
                return Optional.empty();
            copy = password.clone();
            final char[] transientPassword = copy;
            Work<Optional<PreviewPrincipal>> work = new Work<>(() -> Arrays.fill(transientPassword, '\0'));
            handedOff = true;
            return execute(work, (connection, budget) -> {
                // SHARE与账号变更序列化；身份事务不反向获取Run宿主owner锁。
                connection.setAutoCommit(false);
                try {
                    TrustedActor actor = null;
                    boolean enabled = false;
                    String hash = dummyHash;
                    budget.check();
                    try (var statement = connection.prepareStatement(
                            "SELECT user_id,role,auth_revision,enabled,password_hash FROM preview_identity WHERE login_name=? FOR SHARE")) {
                        statement.setQueryTimeout(1);
                        statement.setString(1, loginName);
                        try (var row = statement.executeQuery()) {
                            if (row.next()) {
                                actor = new TrustedActor(row.getObject(1, UUID.class),
                                        TrustedActor.Role.valueOf(row.getString(2)), row.getLong(3));
                                enabled = row.getBoolean(4);
                                hash = row.getString(5);
                            }
                        }
                    }
                    boolean matched = encoder.matches(CharBuffer.wrap(transientPassword), hash);
                    budget.check();
                    connection.commit();
                    return actor != null && enabled && matched ? Optional.of(new PreviewPrincipal(actor)) : Optional.empty();
                } catch (Exception failure) {
                    try { connection.rollback(); } catch (SQLException ignored) { }
                    throw failure;
                }
            });
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (!handedOff && copy != null) Arrays.fill(copy, '\0');
        }
    }

    public void requireCurrent(TrustedActor actor) {
        Objects.requireNonNull(actor);
        execute(new Work<Void>(() -> { }), (connection, budget) -> {
            budget.check();
            // 不在主体中缓存账号许可，停用再启用也不能复活旧Cookie。
            try (var statement = connection.prepareStatement(
                    "SELECT role,auth_revision,enabled FROM preview_identity WHERE user_id=?")) {
                statement.setQueryTimeout(1);
                statement.setObject(1, actor.userId());
                try (var row = statement.executeQuery()) {
                    if (!row.next() || !row.getBoolean(3) || row.getLong(2) != actor.authRevision()
                            || !actor.role().name().equals(row.getString(1)))
                        throw new Failure(Code.AUTH_REVOKED);
                }
            }
            return null;
        }, true);
    }

    private <T> T execute(Work<T> work, Job<T> job) {
        return execute(work, job, false);
    }

    private void acquire(Work<?> work, boolean allowWait) {
        synchronized (admission) {
            work.check();
            if (closed.get()) throw new Failure(Code.IDENTITY_UNAVAILABLE);
            if (slots.tryAcquire()) return;
            if (!allowWait || waiting >= MAX_WAITING) throw new Failure(Code.IDENTITY_UNAVAILABLE);
            waiting++;
            try {
                while (true) {
                    work.check();
                    if (closed.get()) throw new Failure(Code.IDENTITY_UNAVAILABLE);
                    if (slots.tryAcquire()) return;
                    // 等待包含在原2秒预算；只登记调用者，绝不保存/共享已经完成的许可。
                    TimeUnit.NANOSECONDS.timedWait(admission,
                            Math.max(1, Math.min(TimeUnit.MILLISECONDS.toNanos(10), work.deadline - System.nanoTime())));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new Failure(Code.IDENTITY_UNAVAILABLE);
            } finally { waiting--; }
        }
    }

    private void releaseSlot() {
        synchronized (admission) { slots.release(); admission.notifyAll(); }
    }

    private <T> T execute(Work<T> work, Job<T> job, boolean allowWait) {
        try { acquire(work, allowWait); }
        catch (Failure failure) {
            work.clearSecret.run();
            throw failure;
        }
        Runnable task = () -> {
                T result = null;
                Failure failure = null;
                Thread.interrupted();
                synchronized (work) { work.runner = Thread.currentThread(); }
                try {
                    work.check();
                    try (var connection = source.getConnection()) {
                        work.check();
                        result = job.run(connection, work);
                        work.check();
                    }
                } catch (Failure expected) { failure = expected; }
                catch (Exception unavailable) { failure = new Failure(Code.IDENTITY_UNAVAILABLE); }
                finally {
                    work.clearSecret.run();
                    synchronized (work) { work.runner = null; }
                    // 只有I/O和close实际返回才归还名额；取消/超时不增加新线程或放空槽位。
                    Thread.interrupted();
                    releaseSlot();
                }
                if (failure == null) work.result.complete(result);
                else work.result.completeExceptionally(failure);
            };
        boolean delivered = false;
        try {
            while (!delivered) {
                work.check();
                if (closed.get()) throw new Failure(Code.IDENTITY_UNAVAILABLE);
                try { workers.execute(task); delivered = true; }
                catch (RejectedExecutionException rejected) {
                    if (!allowWait || workers.isShutdown()) throw new Failure(Code.IDENTITY_UNAVAILABLE);
                    // SynchronousQueue交接窗口：名额归还后worker可能尚未返回take。
                    // 只重交尚未接受的Runnable，不重做SQL，不增加队列/线程，仍用原截止点。
                    TimeUnit.MILLISECONDS.sleep(1);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(Code.IDENTITY_UNAVAILABLE);
        } finally {
            if (!delivered) { releaseSlot(); work.clearSecret.run(); }
        }
        try {
            return work.result.get(Math.max(1, work.deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            work.cancel();
            Thread.currentThread().interrupt();
            throw new Failure(Code.IDENTITY_UNAVAILABLE);
        } catch (TimeoutException timeout) {
            work.cancel();
            throw new Failure(Code.IDENTITY_UNAVAILABLE);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Failure failure) throw failure;
            throw new Failure(Code.IDENTITY_UNAVAILABLE);
        }
    }

    private static boolean validPassword(char[] password) {
        if (password == null || password.length < 16 || password.length > 64) return false;
        for (char value : password) if (value < 32 || value > 126) return false;
        return true;
    }

    private static DataSource bounded(DataSource original) {
        if (!(original instanceof DriverManagerDataSource driver)) return original;
        var copy = new DriverManagerDataSource(driver.getUrl(), driver.getUsername(), driver.getPassword());
        var settings = new Properties();
        if (driver.getConnectionProperties() != null) settings.putAll(driver.getConnectionProperties());
        settings.setProperty("connectTimeout", "1");
        settings.setProperty("socketTimeout", "2");
        settings.setProperty("cancelSignalTimeout", "1");
        copy.setConnectionProperties(settings);
        return copy;
    }

    public Status status() {
        return new Status(closed.get(), 2 - slots.availablePermits(), (int) live.stream().filter(Thread::isAlive).count());
    }

    public void close(Duration budget) {
        Objects.requireNonNull(budget);
        if (budget.isNegative() || budget.compareTo(Duration.ofSeconds(2)) > 0)
            throw new IllegalArgumentException("身份资源关闭预算须在0至2秒内");
        synchronized (admission) { closed.set(true); admission.notifyAll(); }
        workers.shutdownNow();
        try { workers.awaitTermination(budget.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    @Override public void close() { close(Duration.ofSeconds(2)); }

    @FunctionalInterface private interface Job<T> {
        T run(Connection connection, Work<T> budget) throws Exception;
    }
    private static final class Work<T> {
        final long deadline = System.nanoTime() + OPERATION_NANOS;
        final CompletableFuture<T> result = new CompletableFuture<>();
        final Runnable clearSecret;
        volatile boolean cancelled;
        Thread runner;
        Work(Runnable clearSecret) { this.clearSecret = clearSecret; }
        void check() {
            if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)
                throw new Failure(Code.IDENTITY_UNAVAILABLE);
        }
        synchronized void cancel() {
            cancelled = true;
            // runner清空与中断在同一锁内，避免迟到超时中断线程随后承接的其他请求。
            if (runner != null) runner.interrupt();
        }
    }
}
