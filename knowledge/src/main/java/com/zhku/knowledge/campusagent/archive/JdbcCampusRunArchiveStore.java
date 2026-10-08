package com.zhku.knowledge.campusagent.archive;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category;
import com.zhku.knowledge.security.TrustedActor;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmission;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** 一次准入、一次原子收尾；提交未知不重放，业务完成不依赖归档成功。 */
public final class JdbcCampusRunArchiveStore implements CampusRunArchiveStore {
    private final CampusRunArchiveOwner owner;
    private final DataSource source;
    private final Limits limits;
    private final CampusRunHistoryVisibility visibility;
    private final ThreadPoolExecutor writer;
    private final ThreadPoolExecutor readers;
    private final Semaphore ownedReadWaiters = new Semaphore(32);
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean abortQueued = new AtomicBoolean();
    private final Set<Connection> active = ConcurrentHashMap.newKeySet();

    public JdbcCampusRunArchiveStore(DataSource source, CampusRunArchiveOwner owner, Limits limits,
            CampusRunHistoryVisibility visibility) {
        this.source = CampusRunArchiveOwner.boundedDataSource(Objects.requireNonNull(source));
        this.owner = Objects.requireNonNull(owner);
        this.limits = Objects.requireNonNull(limits);
        this.visibility = Objects.requireNonNull(visibility);
        if (owner.token() == null)
            throw new Failure(Code.ARCHIVE_NOT_READY);
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                factory("campus-archive-write"), new ThreadPoolExecutor.AbortPolicy());
        readers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
                factory("campus-archive-read"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory factory(String name) {
        return r -> {
            var t = new Thread(r, name);
            // daemon 只影响 JVM 退出条件；资源是否收敛仍需由关闭路径实际检查。
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public boolean isAccepting() {
        return !sealed.get() && !closed.get() && owner.isAccepting();
    }

    @Override
    public Admission admit(UUID id) {
        return admit(id, null);
    }

    @Override public CampusRunSubmission.Claim admitSubmissionOwned(TrustedActor actor, CampusRunSubmission.Command command) {
        requireUser(actor); Objects.requireNonNull(command);
        if (!isAccepting()) throw new Failure(Code.ARCHIVE_NOT_READY);
        var committing = new AtomicBoolean();
        return call(writer, true, Code.ARCHIVE_ADMISSION_UNCONFIRMED, deadline -> {
            try (var c = connection(deadline)) {
                c.setAutoCommit(false);
                try {
                    CampusRunArchiveOwner.transactionSettings(c,deadline); lockOwner(c); requireIdentity(c,actor); guardWrite(deadline);
                    var existing = submission(c,actor,command.submissionKey());
                    if (existing.isPresent()) {
                        if (!existing.get().fingerprint().equals(command.fingerprint()))
                            throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.SUBMISSION_CONTENT_CONFLICT,command.submissionKey());
                        c.commit();
                        // 不返回Admission：查到原Run并不能重新取得派发许可。
                        return new CampusRunSubmission.Claim(CampusRunSubmission.Decision.REPLAYED,existing.get(),null);
                    }
                    prune(c);
                    try (var s=c.createStatement();var count=s.executeQuery("SELECT count(*) FROM campus_agent_run_archive")) {
                        count.next(); if(count.getLong(1)>=limits.maxRecords()) {
                            guardWrite(deadline); committing.set(true); c.commit(); throw new Failure(Code.ARCHIVE_CAPACITY_EXCEEDED);
                        }
                    }
                    try (var p=c.prepareStatement("""
                            INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,
                            schema_version,writer_version,owner_user_id) VALUES(?,?,?,'ADMITTED',?,?,?)
                            """)) {
                        p.setObject(1,command.proposedRunId());p.setObject(2,owner.token().instanceId());p.setLong(3,owner.token().epoch());
                        p.setInt(4,SCHEMA_VERSION);p.setString(5,WRITER_VERSION);p.setObject(6,actor.userId());p.executeUpdate();
                    }
                    CampusRunSubmission.Record record;
                    try (var p=c.prepareStatement("""
                            INSERT INTO campus_agent_run_submission(owner_user_id,task_kind,submission_key,run_id,fingerprint_version,request_sha256)
                            VALUES(?,'CAMPUS_CREDIT_SUMMARY',?,?,?,?) RETURNING accepted_at
                            """)) {
                        p.setObject(1,actor.userId());p.setObject(2,command.submissionKey());p.setObject(3,command.proposedRunId());
                        p.setInt(4,command.fingerprint().version());p.setString(5,command.fingerprint().sha256());
                        try(var r=p.executeQuery()) {r.next();record=new CampusRunSubmission.Record(actor.userId(),command.submissionKey(),
                                command.proposedRunId(),command.fingerprint(),r.getTimestamp(1).toInstant());}
                    }
                    guardWrite(deadline); committing.set(true); c.commit();
                    // 两张表及身份绑定同连接commit确认之后，原调用才持有CREATED。
                    return new CampusRunSubmission.Claim(CampusRunSubmission.Decision.CREATED,record,
                            new Admission(record.runId(),owner.token(),0));
                } catch (SQLException | RuntimeException e) { rollback(c); throw e; }
                finally {active.remove(c);}
            } catch (SQLException e) {
                owner.pause(); throw new Failure(committing.get()?Code.ARCHIVE_ADMISSION_UNCONFIRMED:Code.ARCHIVE_UNAVAILABLE);
            }
        });
    }

    @Override public Optional<CampusRunSubmission.Record> findSubmissionOwned(TrustedActor actor, UUID key) {
        requireUser(actor);Objects.requireNonNull(key);
        // 只读不依赖准入开放状态：ACK丢失之后允许确认事实，禁止从读取补派发。
        return call(readers,false,Code.ARCHIVE_READ_UNAVAILABLE,true,deadline->{
            try(var c=connection(deadline)) {
                c.setAutoCommit(false);
                try {
                    CampusRunArchiveOwner.transactionSettings(c,deadline);requireIdentity(c,actor);
                    var row=submission(c,actor,key);CampusRunArchiveOwner.checkDeadline(deadline);c.commit();return row;
                } catch(SQLException|RuntimeException e){rollback(c);throw e;}
                finally {active.remove(c);}
            } catch(SQLException e){throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);}
        });
    }
    private static Optional<CampusRunSubmission.Record> submission(Connection c,TrustedActor actor,UUID key)throws SQLException {
        try(var p=c.prepareStatement("""
                SELECT s.* FROM campus_agent_run_submission s JOIN campus_agent_run_archive a ON a.run_id=s.run_id
                WHERE s.owner_user_id=? AND s.task_kind='CAMPUS_CREDIT_SUMMARY' AND s.submission_key=?
                  AND a.owner_user_id=s.owner_user_id AND a.task_kind=s.task_kind
                  AND (a.retained_until IS NULL OR a.retained_until>clock_timestamp())
                """)) {
            p.setObject(1,actor.userId());p.setObject(2,key);
            try(var r=p.executeQuery()) {
                if(!r.next())return Optional.empty();
                return Optional.of(new CampusRunSubmission.Record(actor.userId(),key,r.getObject("run_id",UUID.class),
                        new CampusRunSubmission.Fingerprint(r.getInt("fingerprint_version"),r.getString("request_sha256")),
                        r.getTimestamp("accepted_at").toInstant()));
            }
        }
    }

    @Override public Admission admitOwned(UUID id, TrustedActor actor) {
        requireUser(actor);
        return admit(id, actor);
    }

    private Admission admit(UUID id, TrustedActor actor) {
        Objects.requireNonNull(id);
        if (!isAccepting())
            throw new Failure(owner.mayWrite() ? Code.ARCHIVE_NOT_READY : Code.ARCHIVE_OWNER_LOST);
        var committing = new AtomicBoolean();
        return call(writer, true, Code.ARCHIVE_ADMISSION_UNCONFIRMED, deadline -> {
            try (var c = connection(deadline)) {
                try {
                    c.setAutoCommit(false);
                    CampusRunArchiveOwner.transactionSettings(c, deadline);
                    lockOwner(c);
                    // 固定owner→identity锁序；当前许可与不可变归属同一事务提交，不能后补认领。
                    if (actor != null) requireIdentity(c, actor);
                    guardWrite(deadline);
                    prune(c);
                    boolean full;
                    try (var p = c.prepareStatement("SELECT count(*) FROM campus_agent_run_archive");
                            var r = p.executeQuery()) {
                        r.next();
                        full = r.getLong(1) >= limits.maxRecords();
                    }
                    if (full) {
                        // 拒绝准入仍需提交到期清理，否则调低容量后每次回滚同一批，永远无法恢复。
                        // 沿用owner行锁与commit前门禁；清理ACK未知同样关门，不插入或重放任务。
                        guardWrite(deadline);
                        committing.set(true);
                        c.commit();
                        throw new Failure(Code.ARCHIVE_CAPACITY_EXCEEDED);
                    }
                    try (var p = c.prepareStatement(
                            """
                            INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,owner_user_id)
                            VALUES(?,?,?,'ADMITTED',?,?,?)
                            """)) {
                        p.setObject(1, id);
                        p.setObject(2, owner.token().instanceId());
                        p.setLong(3, owner.token().epoch());
                        p.setInt(4, SCHEMA_VERSION);
                        p.setString(5, WRITER_VERSION);
                        p.setObject(6, actor == null ? null : actor.userId());
                        p.executeUpdate();
                    }
                    guardWrite(deadline);
                    committing.set(true);
                    c.commit();
                    return new Admission(id, owner.token(), 0);
                } catch (SQLException | RuntimeException e) {
                    rollback(c);
                    throw e;
                } finally {
                    active.remove(c);
                }
            } catch (SQLException e) {
                owner.pause();
                throw new Failure(committing.get() ? Code.ARCHIVE_ADMISSION_UNCONFIRMED : Code.ARCHIVE_UNAVAILABLE);
            }
        });
    }

    @Override
    public Finalization finalizeRun(Admission admission, FinalFacts facts, PayloadCandidate candidate) {
        Objects.requireNonNull(admission);
        Objects.requireNonNull(facts);
        if (!admission.runId().equals(facts.runId()))
            throw new IllegalArgumentException("归档运行编号不一致");
        if (!owner.mayWrite())
            return Finalization.OWNER_LOST;
        if (sealed.get() || closed.get())
            return Finalization.UNCONFIRMED;
        try {
            return call(writer, true, Code.ARCHIVE_FINALIZATION_UNCONFIRMED, deadline -> {
                try (var c = connection(deadline)) {
                    try {
                        c.setAutoCommit(false);
                        CampusRunArchiveOwner.transactionSettings(c, deadline);
                        lockOwner(c);
                        guardWrite(deadline);
                        PayloadCandidate eligible = facts.state() == AgentRunState.SUCCEEDED
                                && "ANSWERED".equals(facts.businessStatus()) ? candidate : null;
                        Encoded encoded = visibility.encode(eligible);
                        if (encoded.disposition() == PayloadDisposition.STORED) {
                            var gate = visibility.evaluate(c, eligible);
                            if (gate.status() != VisibilityStatus.VISIBLE)
                                encoded = new Encoded(PayloadDisposition.SUPPRESSED, gate.reason(), null);
                        }
                        try (var p = c.prepareStatement(
                                """
                                WITH closing AS (SELECT clock_timestamp() AS at)
                                UPDATE campus_agent_run_archive SET archive_status='FINALIZED',revision=revision+1,
                                execution_finished_at=?,execution_state=?,stop_reason=?,end_reason=?,current_step=?,error_category=?,error_code=?,
                                business_status=?,failure_category=?,cleanup_outcome=?,payload_disposition=?,payload_reason=?,result_payload=?::json,
                                closed_at=closing.at,retained_until=closing.at+(? * interval '1 second')
                                FROM closing WHERE run_id=? AND archive_status='ADMITTED' AND revision=? AND owner_instance_id=? AND owner_epoch=?
                                """)) {
                            int i = 1;
                            p.setTimestamp(i++, Timestamp.from(facts.observedAt()));
                            p.setString(i++, facts.state().name());
                            p.setString(i++, facts.stopReason().name());
                            p.setString(i++, facts.endReason().name());
                            p.setInt(i++, facts.currentStep());
                            p.setString(i++, facts.errorCategory() == null ? null : facts.errorCategory().name());
                            p.setString(i++, facts.errorCode());
                            p.setString(i++, facts.businessStatus());
                            p.setString(i++, facts.failureCategory());
                            p.setString(i++, facts.cleanupOutcome().name());
                            p.setString(i++, encoded.disposition().name());
                            p.setString(i++, encoded.reason().name());
                            p.setString(i++, encoded.bytes() == null ? null
                                    : new String(encoded.bytes(), StandardCharsets.UTF_8));
                            p.setLong(i++, limits.retention().toSeconds());
                            p.setObject(i++, admission.runId());
                            p.setLong(i++, admission.revision());
                            p.setObject(i++, admission.owner().instanceId());
                            p.setLong(i, admission.owner().epoch());
                            if (p.executeUpdate() != 1) {
                                rollback(c);
                                owner.pause();
                                return Finalization.OWNER_LOST;
                            }
                        }
                        guardWrite(deadline);
                        c.commit();
                        return Finalization.CONFIRMED;
                    } catch (SQLException | RuntimeException e) {
                        rollback(c);
                        throw e;
                    } finally {
                        active.remove(c);
                    }
                } catch (SQLException e) {
                    owner.pause();
                    throw new Failure(Code.ARCHIVE_FINALIZATION_UNCONFIRMED);
                }
            });
        } catch (Failure e) {
            owner.pause();
            return e.code() == Code.ARCHIVE_OWNER_LOST ? Finalization.OWNER_LOST : Finalization.UNCONFIRMED;
        }
    }

    @Override
    public Optional<Read> find(UUID id) {
        return find(id, null);
    }

    @Override public Optional<Read> findOwned(TrustedActor actor, UUID id) {
        requireUser(actor);
        return find(id, actor);
    }

    private Optional<Read> find(UUID id, TrustedActor actor) {
        Objects.requireNonNull(id);
        return call(readers, false, Code.ARCHIVE_READ_UNAVAILABLE, actor != null, deadline -> {
            try (var c = connection(deadline)) {
                try {
                    // PostgreSQL只读事务禁止FOR SHARE；owned只读业务仍需锁身份直到许可/载荷提交。
                    c.setReadOnly(actor == null);
                    c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    c.setAutoCommit(false);
                    CampusRunArchiveOwner.transactionSettings(c, deadline);
                    if (actor != null) requireIdentity(c, actor);
                    try (var p = c.prepareStatement(
                            "SELECT * FROM campus_agent_run_archive WHERE run_id=?" + ownershipPredicate(actor)
                                    + " AND (retained_until IS NULL OR retained_until>clock_timestamp())")) {
                        p.setObject(1, id);
                        if (actor != null) { p.setObject(2, actor.userId()); p.setLong(3, actor.authRevision()); }
                        try (var r = p.executeQuery()) {
                            if (!r.next()) {
                                c.commit();
                                return Optional.empty();
                            }
                            var archive = map(r);
                            Visibility gate;
                            PayloadCandidate result = null;
                            if (archive.schemaVersion() != SCHEMA_VERSION)
                                gate = new Visibility(VisibilityStatus.HIDDEN, Reason.UNSUPPORTED_SCHEMA);
                            else if (archive.payloadDisposition() != PayloadDisposition.STORED)
                                gate = new Visibility(VisibilityStatus.NOT_STORED,
                                        archive.payloadReason() == null ? Reason.NO_RESULT_FOR_OUTCOME
                                                : archive.payloadReason());
                            else {
                                try {
                                    result = visibility
                                            .decode(r.getString("result_payload").getBytes(StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException e) {
                                    result = null;
                                }
                                gate = result == null
                                        ? new Visibility(VisibilityStatus.HIDDEN, Reason.UNSUPPORTED_SCHEMA)
                                        : visibility.evaluate(c, result);
                                if (gate.status() != VisibilityStatus.VISIBLE)
                                    result = null;
                            }
                            CampusRunArchiveOwner.checkDeadline(deadline);
                            c.commit();
                            return Optional.of(new Read(archive, gate, result));
                        }
                    }
                } catch (SQLException | RuntimeException e) {
                    rollback(c);
                    throw e;
                } finally {
                    active.remove(c);
                }
            } catch (SQLException | IllegalArgumentException e) {
                throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
            }
        });
    }

    @Override
    public Page list(int limit, Cursor cursor) {
        return list(null, limit, cursor);
    }

    @Override public Page listOwned(TrustedActor actor, int limit, Cursor cursor) {
        requireUser(actor);
        return list(actor, limit, cursor);
    }

    private Page list(TrustedActor actor, int limit, Cursor cursor) {
        if (limit < 1 || limit > 100)
            throw new Failure(Code.INVALID_REQUEST);
        return call(readers, false, Code.ARCHIVE_READ_UNAVAILABLE, actor != null, deadline -> {
            try (var c = connection(deadline)) {
                try {
                    c.setReadOnly(actor == null);
                    c.setAutoCommit(false);
                    CampusRunArchiveOwner.transactionSettings(c, deadline);
                    if (actor != null) requireIdentity(c, actor);
                    String after = cursor == null ? "" : " AND (admitted_at,run_id)<(?,?)";
                    try (var p = c.prepareStatement(
                            "SELECT * FROM campus_agent_run_archive WHERE (retained_until IS NULL OR retained_until>clock_timestamp())"
                                    + ownershipPredicate(actor)
                                    + after + " ORDER BY admitted_at DESC,run_id DESC LIMIT ?")) {
                        int i = 1;
                        if (actor != null) { p.setObject(i++, actor.userId()); p.setLong(i++, actor.authRevision()); }
                        if (cursor != null) {
                            p.setTimestamp(i++, Timestamp.from(cursor.admittedAt()));
                            p.setObject(i++, cursor.runId());
                        }
                        p.setInt(i, limit + 1);
                        var rows = new ArrayList<CampusRunArchive>();
                        try (var r = p.executeQuery()) {
                            while (r.next())
                                rows.add(map(r));
                        }
                        Cursor next = null;
                        if (rows.size() > limit) {
                            rows.removeLast();
                            var last = rows.getLast();
                            next = new Cursor(last.admittedAt(), last.runId());
                        }
                        CampusRunArchiveOwner.checkDeadline(deadline);
                        c.commit();
                        return new Page(rows, next);
                    }
                } catch (SQLException | RuntimeException e) {
                    rollback(c);
                    throw e;
                } finally {
                    active.remove(c);
                }
            } catch (SQLException | IllegalArgumentException e) {
                throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
            }
        });
    }

    private Connection connection(long deadline) throws SQLException {
        CampusRunArchiveOwner.checkDeadline(deadline);
        Connection c = source.getConnection();
        active.add(c);
        try {
            if (closed.get())
                throw new Failure(Code.ARCHIVE_NOT_READY);
            CampusRunArchiveOwner.checkDeadline(deadline);
            return c;
        } catch (RuntimeException e) {
            active.remove(c);
            c.close();
            throw e;
        }
    }

    private static String ownershipPredicate(TrustedActor actor) {
        // SQL先过滤，之后才decode或LIMIT；分页游标不能使其他用户或NULL行进入结果。
        return actor == null ? " AND owner_user_id IS NULL" : """
             AND owner_user_id=? AND EXISTS (SELECT 1 FROM preview_identity i
             WHERE i.user_id=owner_user_id AND i.enabled AND i.role='USER' AND i.auth_revision=?)
            """;
    }
    private static void requireUser(TrustedActor actor) {
        Objects.requireNonNull(actor);
        if (actor.role() != TrustedActor.Role.USER)
            throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.ROLE_DENIED);
    }
    private static void requireIdentity(Connection connection, TrustedActor actor) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT enabled,role,auth_revision FROM preview_identity WHERE user_id=? FOR SHARE")) {
            query.setQueryTimeout(1);
            query.setObject(1, actor.userId());
            try (var row = query.executeQuery()) {
                if (!row.next() || !row.getBoolean(1) || !"USER".equals(row.getString(2))
                        || row.getLong(3) != actor.authRevision())
                    throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED);
            }
        }
    }

    private void lockOwner(Connection c) throws SQLException {
        try (var p = c.prepareStatement(
                "SELECT epoch,instance_id FROM campus_agent_runtime_owner WHERE owner_key=? FOR UPDATE")) {
            p.setString(1, CampusRunArchiveOwner.OWNER_KEY);
            try (var r = p.executeQuery()) {
                if (!r.next() || r.getLong(1) != owner.token().epoch()
                        || !owner.token().instanceId().equals(r.getObject(2, UUID.class))) {
                    owner.fence();
                    throw new Failure(Code.ARCHIVE_OWNER_LOST);
                }
            }
        }
    }

    private void prune(Connection c) throws SQLException { CampusRunArchiveOwner.pruneClosed(c); }

    private void guardWrite(long deadline) {
        CampusRunArchiveOwner.checkDeadline(deadline);
        if (!owner.mayWrite())
            throw new Failure(Code.ARCHIVE_OWNER_LOST);
        if (sealed.get() || closed.get())
            throw new Failure(Code.ARCHIVE_NOT_READY);
    }

    @FunctionalInterface
    private interface Job<T> {
        T run(long deadline) throws Exception;
    }

    private <T> T call(ThreadPoolExecutor executor, boolean write, Code unconfirmed, Job<T> action) {
        return call(executor, write, unconfirmed, false, action);
    }

    private <T> T call(ThreadPoolExecutor executor, boolean write, Code unconfirmed,
            boolean allowReadWait, Job<T> action) {
        if (closed.get())
            throw new Failure(write ? Code.ARCHIVE_NOT_READY : Code.ARCHIVE_READ_UNAVAILABLE);
        long deadline = System.nanoTime() + limits.operation().toNanos();
        var future = new FutureTask<T>(() -> {
            if (write) guardWrite(deadline);
            else CampusRunArchiveOwner.checkDeadline(deadline);
            return action.run(deadline);
        });
        boolean waiting = false;
        try {
            while (true) {
                if (closed.get() || executor.isShutdown() || System.nanoTime() >= deadline)
                    throw new Failure(unconfirmed);
                try { executor.execute(future); break; }
                catch (RejectedExecutionException rejected) {
                    if (!allowReadWait) throw new Failure(Code.ARCHIVE_BUSY);
                    if (!waiting) {
                        if (!ownedReadWaiters.tryAcquire()) throw new Failure(Code.ARCHIVE_BUSY);
                        waiting = true;
                    }
                    // owned读取允许有限交付等待，仍为4线程、0执行队列；不缓存正文/许可。
                    // 未被执行器接受时才能重交同一个task，等待计入原operation期限。
                    TimeUnit.MILLISECONDS.sleep(1);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new Failure(unconfirmed);
        } finally { if (waiting) ownedReadWaiters.release(); }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            invalidate();
            throw new Failure(unconfirmed);
        } catch (TimeoutException e) {
            future.cancel(true);
            invalidate();
            throw new Failure(unconfirmed);
        } catch (CancellationException e) {
            throw new Failure(unconfirmed);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CampusRunSubmission.Failure conflict)
                throw conflict;
            if (e.getCause() instanceof PreviewIdentityRepository.Failure denied)
                throw denied;
            if (e.getCause() instanceof Failure failure)
                throw failure;
            if (write)
                owner.pause();
            throw new Failure(unconfirmed);
        }
    }

    private void invalidate() {
        owner.pause();
        sealWrites();
        abortActive();
    }

    private void abortActive() {
        if (!abortQueued.compareAndSet(false, true))
            return;
        owner.cleanup(() -> {
            try {
                for (var c : List.copyOf(active))
                    try {
                        c.abort(Runnable::run);
                    } catch (SQLException | RuntimeException ignored) {
                    }
            } finally {
                abortQueued.set(false);
            }
        });
    }

    @Override
    public void stopAdmission() {
        owner.pause();
    }

    @Override
    public void sealWrites() {
        if (sealed.compareAndSet(false, true)) {
            // 未开始任务不再取得连接；已经发出的commit仍可能成功，不能宣称cancel等于回滚。
            for (var queued : writer.shutdownNow())
                if (queued instanceof Future<?> future)
                    future.cancel(false);
        }
    }

    @Override
    public void close(Duration remaining) {
        Objects.requireNonNull(remaining);
        if (remaining.isNegative())
            throw new IllegalArgumentException("关闭预算非法");
        if (!closed.compareAndSet(false, true))
            return;
        long deadline = System.nanoTime() + remaining.toNanos();
        owner.pause();
        sealWrites();
        readers.shutdownNow();
        abortActive();
        try {
            writer.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            readers.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        owner.close(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
    }

    private static void rollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static Instant instant(ResultSet r, String name) throws SQLException {
        var t = r.getTimestamp(name);
        return t == null ? null : t.toInstant();
    }

    private static CampusRunArchive map(ResultSet r) throws SQLException {
        UUID id = r.getObject("run_id", UUID.class);
        int schema = r.getInt("schema_version");
        var status = ArchiveStatus.valueOf(r.getString("archive_status"));
        FinalFacts facts = null;
        if (schema == SCHEMA_VERSION && status == ArchiveStatus.FINALIZED) {
            String category = r.getString("error_category");
            facts = new FinalFacts(id, AgentRunState.valueOf(r.getString("execution_state")),
                    AgentStopReason.valueOf(r.getString("stop_reason")),
                    EndReason.valueOf(r.getString("end_reason")), r.getInt("current_step"),
                    category == null ? null : Category.valueOf(category),
                    r.getString("error_code"), r.getString("business_status"), r.getString("failure_category"),
                    CleanupOutcome.valueOf(r.getString("cleanup_outcome")), instant(r, "execution_finished_at"));
        }
        String disposition = r.getString("payload_disposition"), reason = r.getString("payload_reason");
        return new CampusRunArchive(id, schema, r.getString("writer_version"), status,
                new OwnerToken(r.getObject("owner_instance_id", UUID.class), r.getLong("owner_epoch")),
                r.getLong("revision"), instant(r, "admitted_at"), instant(r, "execution_finished_at"),
                instant(r, "closed_at"), instant(r, "retained_until"), facts,
                disposition == null ? null : PayloadDisposition.valueOf(disposition),
                reason == null ? null : Reason.valueOf(reason), r.getString("interruption_reason"));
    }
}
