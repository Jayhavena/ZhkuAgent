package com.zhku.knowledge.campusagent.archive;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

class CampusRunArchiveStoreIntegrationTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    private CampusRunArchiveOwner owner;
    private JdbcCampusRunArchiveStore store;
    private final List<UUID> ids = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        open(Limits.defaults(), null);
    }

    private void open(Limits limits, javax.sql.DataSource supplied) {
        var source = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        owner = new CampusRunArchiveOwner(source, limits);
        owner.start();
        store = new JdbcCampusRunArchiveStore(supplied == null ? source : supplied, owner, limits,
                new CampusRunHistoryVisibility());
    }

    @AfterEach
    void clean() throws Exception {
        if (store != null)
            store.close(Duration.ofSeconds(2));
        else if (owner != null)
            owner.close();
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
            for (var id : ids) {
                p.setObject(1, id);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private UUID id() {
        UUID id = UUID.randomUUID();
        ids.add(id);
        return id;
    }

    @Test
    void admissionAndAtomicFinalAreReadableAndCannotBeOverwritten() {
        var id = id();
        var admission = store.admit(id);
        assertThat(admission).isNotNull();
        assertThat(admission.revision()).isZero();
        assertThat(admission.owner()).isEqualTo(owner.token());
        assertThat(store.find(id)).isPresent();
        assertThat(store.finalizeRun(admission, facts(id), null)).isEqualTo(Finalization.CONFIRMED);
        var read = store.find(id).orElseThrow();
        assertThat(read.archive().archiveStatus()).isEqualTo(ArchiveStatus.FINALIZED);
        assertThat(read.archive().execution().state()).isEqualTo(AgentRunState.INCOMPLETE);
        assertThat(read.archive().revision()).isEqualTo(1);
        assertThat(read.visibility().status()).isEqualTo(VisibilityStatus.NOT_STORED);
        assertThat(store.finalizeRun(admission, facts(id), null)).isEqualTo(Finalization.OWNER_LOST);
        assertThat(store.find(id).orElseThrow().archive()).isEqualTo(read.archive());
    }

    @Test
    void finalPayloadIsWhitelistedAndWithdrawalHidesTextButKeepsSuccess() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            UUID source = CampusRunHistoryVisibilityIntegrationTest.insertSource(c);
            try {
                var id = id();
                var admission = store.admit(id);
                var facts = new FinalFacts(id, AgentRunState.SUCCEEDED, AgentStopReason.NONE, EndReason.COMPLETED, 2,
                        null, null, "ANSWERED", "NONE", CleanupOutcome.SUCCESS, Instant.now());
                assertThat(store.finalizeRun(admission, facts,
                        CampusRunHistoryVisibilityIntegrationTest.candidate(source)))
                        .isEqualTo(Finalization.CONFIRMED);
                assertThat(store.find(id).orElseThrow().result()).isNotNull();
                try (var p = c
                        .prepareStatement("SELECT result_payload::text FROM campus_agent_run_archive WHERE run_id=?")) {
                    p.setObject(1, id);
                    try (var r = p.executeQuery()) {
                        r.next();
                        assertThat(r.getString(1)).doesNotContain("question", "courses", "steps", "prompt", "arguments",
                                "password");
                    }
                }
                try (var p = c.prepareStatement(
                        "UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false WHERE id=?")) {
                    p.setObject(1, source);
                    p.executeUpdate();
                }
                var read = store.find(id).orElseThrow();
                assertThat(read.result()).isNull();
                assertThat(read.visibility().reason()).isEqualTo(Reason.SOURCE_NOT_VISIBLE);
                assertThat(read.archive().execution().state()).isEqualTo(AgentRunState.SUCCEEDED);
            } finally {
                CampusRunHistoryVisibilityIntegrationTest.deleteSource(c, source);
            }
        }
    }

    @Test
    void lastQuotaSlotIsNotOversoldUnderConcurrentAdmission() throws Exception {
        store.close(Duration.ofSeconds(2));
        open(new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofDays(7), 1),
                null);
        var executor = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        var competing = new JdbcCampusRunArchiveStore(ArchiveTestDatabase.dataSource(), owner,
                new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofDays(7), 1),
                new CampusRunHistoryVisibility());
        try {
            List<Future<Boolean>> submitted = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                var id = id();
                var selected = i % 2 == 0 ? store : competing;
                submitted.add(executor.submit(() -> {
                    gate.await();
                    try {
                        return selected.admit(id) != null;
                    } catch (Failure e) {
                        assertThat(e.code()).isEqualTo(Code.ARCHIVE_CAPACITY_EXCEEDED);
                        return false;
                    }
                }));
            }
            gate.countDown();
            int admitted = 0;
            for (var task : submitted)
                if (task.get(3, TimeUnit.SECONDS))
                    admitted++;
            assertThat(admitted).isEqualTo(1);
            assertThat(store.list(100, null).items()).hasSize(1);
        } finally {
            executor.shutdownNow();
            competing.close(Duration.ofSeconds(2));
        }
    }

    @Test
    void closedUnexpiredAndActiveRecordsAreNeverPressureEvicted() {
        store.close(Duration.ofSeconds(2));
        open(new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofDays(7), 2),
                null);
        var active = id();
        store.admit(active);
        var done = id();
        var a = store.admit(done);
        store.finalizeRun(a, facts(done), null);
        assertThatThrownBy(() -> store.admit(id())).isInstanceOf(Failure.class).extracting(e -> ((Failure) e).code())
                .isEqualTo(Code.ARCHIVE_CAPACITY_EXCEEDED);
        assertThat(store.find(active)).isPresent();
        assertThat(store.find(done)).isPresent();
    }

    @Test
    void expiredClosedRowsAreHiddenAndOnly200ArePrunedPerAdmission() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        """
                INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,
                revision,closed_at,retained_until,interruption_reason)
                VALUES(?,?,?,'INTERRUPTED',1,'retention-test',1,clock_timestamp()-interval '8 days',clock_timestamp()-interval '1 day',
                'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
                """)) {
            for (int i = 0; i < 201; i++) {
                p.setObject(1, id());
                p.setObject(2, owner.token().instanceId());
                p.setLong(3, owner.token().epoch());
                p.addBatch();
            }
            p.executeBatch();
        }
        assertThat(store.find(ids.getFirst())).isEmpty();
        var admitted = id();
        store.admit(admitted);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT count(*) FROM campus_agent_run_archive WHERE owner_instance_id=?")) {
            p.setObject(1, owner.token().instanceId());
            try (var r = p.executeQuery()) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void capacityRejectionCommitsExpiredBatchSoNextAdmissionCanMakeProgress() throws Exception {
        store.close(Duration.ofSeconds(2));
        open(new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofDays(7), 1),
                null);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        """
                INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,
                revision,closed_at,retained_until,interruption_reason)
                VALUES(?,?,?,'INTERRUPTED',1,'quota-cleanup-test',1,clock_timestamp()-interval '8 days',clock_timestamp()-interval '1 day',
                'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
                """)) {
            for (int i = 0; i < 201; i++) {
                p.setObject(1, id());
                p.setObject(2, owner.token().instanceId());
                p.setLong(3, owner.token().epoch());
                p.addBatch();
            }
            p.executeBatch();
        }
        var rejected = id();
        assertThatThrownBy(() -> store.admit(rejected)).isInstanceOf(Failure.class)
                .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_CAPACITY_EXCEEDED);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT count(*) FROM campus_agent_run_archive WHERE owner_instance_id=?")) {
            p.setObject(1, owner.token().instanceId());
            try (var r = p.executeQuery()) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
        }
        assertThat(store.isAccepting()).isTrue();
        assertThat(store.find(rejected)).isEmpty();
        var accepted = id();
        assertThat(store.admit(accepted)).isNotNull();
        assertThat(store.list(100, null).items()).extracting(CampusRunArchive::runId).containsExactly(accepted);
    }

    @Test
    void capacityCleanupCommitAckLossPausesAdmissionWithoutCreatingRun() throws Exception {
        store.close(Duration.ofSeconds(2));
        var armed = new AtomicBoolean(false);
        open(new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofDays(7), 1),
                ArchiveJdbcFaults.commitFault(CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource()),
                        armed, true));
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        """
                INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,
                revision,closed_at,retained_until,interruption_reason)
                VALUES(?,?,?,'INTERRUPTED',1,'quota-ack-test',1,clock_timestamp()-interval '8 days',clock_timestamp()-interval '1 day',
                'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
                """)) {
            for (int i = 0; i < 201; i++) {
                p.setObject(1, id());
                p.setObject(2, owner.token().instanceId());
                p.setLong(3, owner.token().epoch());
                p.addBatch();
            }
            p.executeBatch();
        }
        var rejected = id();
        armed.set(true);
        assertThatThrownBy(() -> store.admit(rejected)).isInstanceOf(Failure.class)
                .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_ADMISSION_UNCONFIRMED);
        assertThat(store.isAccepting()).isFalse();
        assertThat(store.find(rejected)).isEmpty();
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT count(*) FROM campus_agent_run_archive WHERE owner_instance_id=?")) {
            p.setObject(1, owner.token().instanceId());
            try (var r = p.executeQuery()) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void actualCommitThenLostAcknowledgementDoesNotDispatchOrReplayAdmission() throws Exception {
        store.close(Duration.ofSeconds(2));
        var armed = new AtomicBoolean(true);
        open(Limits.defaults(), ArchiveJdbcFaults
                .commitFault(CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource()), armed, true));
        var id = id();
        assertThatThrownBy(() -> store.admit(id)).isInstanceOf(Failure.class)
                .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_ADMISSION_UNCONFIRMED);
        assertThat(store.isAccepting()).isFalse();
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT archive_status FROM campus_agent_run_archive WHERE run_id=?")) {
            p.setObject(1, id);
            try (var r = p.executeQuery()) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("ADMITTED");
            }
        }
    }

    @Test
    void commitBeforeFailureRollsBackWithoutArchiveRow() {
        store.close(Duration.ofSeconds(2));
        var armed = new AtomicBoolean(true);
        open(Limits.defaults(), ArchiveJdbcFaults
                .commitFault(CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource()), armed, false));
        var id = id();
        assertThatThrownBy(() -> store.admit(id)).isInstanceOf(Failure.class);
        assertThat(store.find(id)).isEmpty();
    }

    @Test
    void actualFinalCommitAckLossNeverOverwritesResult() {
        store.close(Duration.ofSeconds(2));
        var armed = new AtomicBoolean(false);
        open(Limits.defaults(), ArchiveJdbcFaults
                .commitFault(CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource()), armed, true));
        var id = id();
        var admitted = store.admit(id);
        armed.set(true);
        assertThat(store.finalizeRun(admitted, facts(id), null)).isEqualTo(Finalization.UNCONFIRMED);
        assertThat(store.find(id).orElseThrow().archive().archiveStatus()).isEqualTo(ArchiveStatus.FINALIZED);
        assertThat(store.isAccepting()).isFalse();
    }

    @Test
    void newEpochReconciliationCannotBeOverwrittenByOldAdmissionToken() {
        var id = id();
        var old = store.admit(id);
        store.close(Duration.ofSeconds(2));
        open(Limits.defaults(), null);
        assertThat(store.find(id).orElseThrow().archive().archiveStatus()).isEqualTo(ArchiveStatus.INTERRUPTED);
        assertThat(store.finalizeRun(old, facts(id), null)).isEqualTo(Finalization.OWNER_LOST);
        var archive = store.find(id).orElseThrow().archive();
        assertThat(archive.archiveStatus()).isEqualTo(ArchiveStatus.INTERRUPTED);
        assertThat(archive.execution()).isNull();
        assertThat(archive.owner()).isEqualTo(old.owner());
    }

    @Test
    void finalizedBeforeEpochChangeRemainsCompleteAndUnknownSchemaHidesPayload() throws Exception {
        var id = id();
        var old = store.admit(id);
        store.finalizeRun(old, facts(id), null);
        store.close(Duration.ofSeconds(2));
        open(Limits.defaults(), null);
        assertThat(store.find(id).orElseThrow().archive().archiveStatus()).isEqualTo(ArchiveStatus.FINALIZED);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("UPDATE campus_agent_run_archive SET schema_version=2 WHERE run_id=?")) {
            p.setObject(1, id);
            p.executeUpdate();
        }
        var read = store.find(id).orElseThrow();
        assertThat(read.archive().execution()).isNull();
        assertThat(read.result()).isNull();
        assertThat(read.visibility().reason()).isEqualTo(Reason.UNSUPPORTED_SCHEMA);
    }

    @Test
    void identicalTimestampKeysetPaginationUsesPostgresUuidOrderWithoutDuplicates() throws Exception {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        ids.add(low);
        ids.add(high);
        store.admit(low);
        store.admit(high);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        "UPDATE campus_agent_run_archive SET admitted_at='2000-01-01T00:00:00Z' WHERE run_id IN (?,?)")) {
            p.setObject(1, low);
            p.setObject(2, high);
            p.executeUpdate();
        }
        var first = store.list(1, null);
        assertThat(first.items()).extracting(CampusRunArchive::runId).containsExactly(high);
        assertThat(first.nextCursor()).isNotNull();
        var last = store.list(1, first.nextCursor());
        assertThat(last.items()).extracting(CampusRunArchive::runId).containsExactly(low);
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    void oldFinalTransactionCommitsBeforeWaitingNewEpochReconciliation() throws Exception {
        store.close(Duration.ofSeconds(2));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var armed = new AtomicBoolean(false);
        var base = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        open(Limits.defaults(), ArchiveJdbcFaults.commitBarrier(base, armed, entered, release));
        var run = id();
        var admission = store.admit(run);
        armed.set(true);
        var pool = Executors.newFixedThreadPool(2);
        var next = new CampusRunArchiveOwner(base, Limits.defaults());
        try {
            var finalizing = pool.submit(() -> store.finalizeRun(admission, facts(run), null));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            terminateLockSession();
            var claiming = pool.submit(next::start);
            assertDatabaseWaiting("SELECT epoch FROM campus_agent_runtime_owner");
            release.countDown();
            assertThat(finalizing.get(2, TimeUnit.SECONDS)).isEqualTo(Finalization.CONFIRMED);
            assertThat(claiming.get(2, TimeUnit.SECONDS).epoch()).isGreaterThan(admission.owner().epoch());
            assertThat(store.find(run).orElseThrow().archive().archiveStatus()).isEqualTo(ArchiveStatus.FINALIZED);
        } finally {
            release.countDown();
            pool.shutdownNow();
            next.close();
        }
    }

    @Test
    void newEpochTransactionCommitsBeforeWaitingOldFinal() throws Exception {
        var run = id();
        var admission = store.admit(run);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var base = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        var next = new CampusRunArchiveOwner(
                ArchiveJdbcFaults.commitBarrier(base, new AtomicBoolean(true), entered, release), Limits.defaults());
        var pool = Executors.newFixedThreadPool(2);
        try {
            terminateLockSession();
            var claiming = pool.submit(next::start);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            var finalizing = pool.submit(() -> store.finalizeRun(admission, facts(run), null));
            assertDatabaseWaiting("SELECT epoch,instance_id FROM campus_agent_runtime_owner");
            release.countDown();
            assertThat(claiming.get(2, TimeUnit.SECONDS).epoch()).isGreaterThan(admission.owner().epoch());
            assertThat(finalizing.get(2, TimeUnit.SECONDS)).isEqualTo(Finalization.OWNER_LOST);
            var archive = store.find(run).orElseThrow().archive();
            assertThat(archive.archiveStatus()).isEqualTo(ArchiveStatus.INTERRUPTED);
            assertThat(archive.execution()).isNull();
        } finally {
            release.countDown();
            pool.shutdownNow();
            next.close();
        }
    }

    @Test
    void concurrentWithdrawalUsesOneReadSnapshotThenHidesOnNextRead() throws Exception {
        store.close(Duration.ofSeconds(2));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var armed = new AtomicBoolean(false);
        open(Limits.defaults(), ArchiveJdbcFaults.archiveReadBarrier(
                CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource()), armed, entered, release));
        var pool = Executors.newSingleThreadExecutor();
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            var source = CampusRunHistoryVisibilityIntegrationTest.insertSource(c);
            try {
                var run = id();
                var admission = store.admit(run);
                var success = new FinalFacts(run, AgentRunState.SUCCEEDED, AgentStopReason.NONE, EndReason.COMPLETED, 2,
                        null, null, "ANSWERED", "NONE", CleanupOutcome.SUCCESS, Instant.now());
                assertThat(store.finalizeRun(admission, success,
                        CampusRunHistoryVisibilityIntegrationTest.candidate(source))).isEqualTo(Finalization.CONFIRMED);
                armed.set(true);
                var reading = pool.submit(() -> store.find(run).orElseThrow());
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                try (var p = c.prepareStatement(
                        "UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false WHERE id=?")) {
                    p.setObject(1, source);
                    p.executeUpdate();
                }
                release.countDown();
                // 撤回在快照建立之后提交，不能把两个时点拼成一个授权判断。
                assertThat(reading.get(2, TimeUnit.SECONDS).visibility().status()).isEqualTo(VisibilityStatus.VISIBLE);
                var after = store.find(run).orElseThrow();
                assertThat(after.result()).isNull();
                assertThat(after.visibility().reason()).isEqualTo(Reason.SOURCE_NOT_VISIBLE);
                assertThat(after.archive().execution().state()).isEqualTo(AgentRunState.SUCCEEDED);
            } finally {
                release.countDown();
                CampusRunHistoryVisibilityIntegrationTest.deleteSource(c, source);
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void terminateLockSession() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        "SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND classid=?::oid AND objid=?::oid AND objsubid=2")) {
            p.setLong(1, CampusRunArchiveOwner.LOCK_NAMESPACE);
            p.setLong(2, CampusRunArchiveOwner.LOCK_RESOURCE);
            try (var r = p.executeQuery()) {
                assertThat(r.next()).isTrue();
                assertThat(r.getBoolean(1)).isTrue();
            }
        }
    }

    private static void assertDatabaseWaiting(String queryPrefix) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMillis(200).toNanos();
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE ? AND pid<>pg_backend_pid())")) {
            p.setString(1, queryPrefix + "%");
            do {
                try (var r = p.executeQuery()) {
                    r.next();
                    if (r.getBoolean(1))
                        return;
                }
                Thread.sleep(2);
            } while (System.nanoTime() < deadline);
        }
        throw new AssertionError("没有观测到真实数据库事务等待，不能用顺序调用冒充竞争");
    }

    static FinalFacts facts(UUID id) {
        return new FinalFacts(id, AgentRunState.INCOMPLETE, AgentStopReason.NONE, EndReason.DOMAIN_DECISION, 1,
                null, null, "NEED_CLARIFICATION", "NONE", CleanupOutcome.SUCCESS, Instant.now());
    }
}
