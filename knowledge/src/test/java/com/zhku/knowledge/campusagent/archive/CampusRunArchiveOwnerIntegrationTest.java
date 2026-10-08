package com.zhku.knowledge.campusagent.archive;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

class CampusRunArchiveOwnerIntegrationTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    @AfterEach
    void verifyDatabaseLockEventuallyReleased() throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        boolean diagnostic = false;
        while (true) {
            try (var c = ArchiveTestDatabase.dataSource().getConnection(); var p = c.prepareStatement("""
                    SELECT locks.pid,activity.wait_event FROM pg_locks locks
                    LEFT JOIN pg_stat_activity activity ON activity.pid=locks.pid
                    WHERE locks.locktype='advisory' AND locks.classid=?::oid AND locks.objid=?::oid AND locks.objsubid=2
                    """)) {
                p.setLong(1, 0x5A484B55);
                p.setLong(2, 0x43524152);
                try (var r = p.executeQuery()) {
                    if (!r.next())
                        return;
                    if (!diagnostic) {
                        System.out.println(
                                "M3A_LOCK_RELEASE_PENDING pid=" + r.getInt(1) + " waitEvent=" + r.getString(2));
                        diagnostic = true;
                    }
                }
            }
            if (System.nanoTime() >= deadline)
                throw new AssertionError("专用验收库锁资源没有在3秒收敛");
            Thread.sleep(10);
        }
    }

    @Test
    void competingOwnerCannotReconcileWhileFirstSessionHoldsLock() {
        try (var first = owner(); var second = owner()) {
            assertThat(first.isAccepting()).isFalse();
            var token = first.start();
            assertThat(token).isNotNull();
            assertThatThrownBy(second::start).isInstanceOf(Failure.class)
                    .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_OWNER_LOST);
            assertThat(first.token()).isEqualTo(token);
            assertThat(first.isAccepting()).isTrue();
        }
    }

    @Test
    void nextOwnerMarksOldAdmittedInterruptedWithoutExecutionFacts() throws Exception {
        UUID run = UUID.randomUUID();
        OwnerToken old;
        try (var first = owner()) {
            old = first.start();
            assertThat(old).isNotNull();
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            """
                    INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version)
                    VALUES(?,?,?,'ADMITTED',1,'owner-test')
                    """)) {
                p.setObject(1, run);
                p.setObject(2, old.instanceId());
                p.setLong(3, old.epoch());
                p.executeUpdate();
            }
        }
        try (var next = owner()) {
            var token = next.start();
            assertThat(token.epoch()).isGreaterThan(old.epoch());
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            "SELECT archive_status,execution_state,closed_at,revision,owner_epoch FROM campus_agent_run_archive WHERE run_id=?")) {
                p.setObject(1, run);
                try (var r = p.executeQuery()) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getString(1)).isEqualTo("INTERRUPTED");
                    assertThat(r.getString(2)).isNull();
                    assertThat(r.getTimestamp(3)).isNotNull();
                    assertThat(r.getLong(4)).isEqualTo(1);
                    assertThat(r.getLong(5)).isEqualTo(old.epoch());
                }
            }
        } finally {
            delete(run);
        }
    }

    @Test
    void lostSpecificLockSessionFencesAndNeverReclaims() throws Exception {
        try (var owner = owner()) {
            var token = owner.start();
            assertThat(token).isNotNull();
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            "SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND classid=?::oid AND objid=?::oid AND objsubid=2")) {
                p.setLong(1, 0x5A484B55);
                p.setLong(2, 0x43524152);
                try (var r = p.executeQuery()) {
                    assertThat(r.next()).isTrue();
                    assertThat(r.getBoolean(1)).isTrue();
                }
            }
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
            while (owner.mayWrite() && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertThat(owner.mayWrite()).isFalse();
            assertThat(owner.isAccepting()).isFalse();
            assertThat(owner.token()).isEqualTo(token);
            assertThatThrownBy(owner::start).isInstanceOf(Failure.class);
            owner.close();
            owner.close();
        }
    }

    private static CampusRunArchiveOwner owner() {
        return new CampusRunArchiveOwner(ArchiveTestDatabase.dataSource(), Limits.defaults());
    }

    @Test
    void startupDatabaseFailureReleasesExclusiveSessionAndDoesNotBecomeReady() {
        var base = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var broken = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                javax.sql.DataSource.class.getClassLoader(),
                new Class<?>[] { javax.sql.DataSource.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection") && attempts.incrementAndGet() == 2)
                        throw new java.sql.SQLException("SYNTHETIC_STARTUP_FAILURE", "08006");
                    try {
                        return method.invoke(base, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        try (var owner = new CampusRunArchiveOwner(broken, Limits.defaults())) {
            assertThatThrownBy(owner::start).isInstanceOf(Failure.class)
                    .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_UNAVAILABLE);
            assertThat(owner.isAccepting()).isFalse();
            assertThat(owner.mayWrite()).isFalse();
            try (var next = owner()) {
                assertThat(next.start()).isNotNull();
            }
        }
    }

    @Test
    void startupPrunesOnlyOneBoundedBatchOfExpiredClosedRecords() throws Exception {
        var ids = new java.util.ArrayList<UUID>();
        OwnerToken token;
        try (var first = owner()) {
            token = first.start();
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            """
                    INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version,
                    revision,closed_at,retained_until,interruption_reason)
                    VALUES(?,?,?,'INTERRUPTED',1,'startup-test',1,clock_timestamp()-interval '8 days',clock_timestamp()-interval '1 day',
                    'PREVIOUS_OWNER_RESULT_UNCONFIRMED')
                    """)) {
                for (int i = 0; i < 201; i++) {
                    UUID id = UUID.randomUUID();
                    ids.add(id);
                    p.setObject(1, id);
                    p.setObject(2, token.instanceId());
                    p.setLong(3, token.epoch());
                    p.addBatch();
                }
                p.executeBatch();
            }
        }
        try (var next = owner()) {
            next.start();
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            "SELECT count(*) FROM campus_agent_run_archive WHERE owner_instance_id=?")) {
                p.setObject(1, token.instanceId());
                try (var r = p.executeQuery()) {
                    r.next();
                    assertThat(r.getInt(1)).isEqualTo(1);
                }
            }
        } finally {
            for (var id : ids)
                delete(id);
        }
    }

    @Test
    void probeSocketTimeoutFencesWithoutCreatingReplacementOwner() throws Exception {
        var base = CampusRunArchiveOwner.boundedDataSource(ArchiveTestDatabase.dataSource());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var source = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                javax.sql.DataSource.class.getClassLoader(),
                new Class<?>[] { javax.sql.DataSource.class }, (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(base, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (!method.getName().equals("getConnection"))
                        return result;
                    var connection = (java.sql.Connection) result;
                    return java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),
                            new Class<?>[] { java.sql.Connection.class }, (cp, cm, ca) -> {
                                if (cm.getName().equals("prepareStatement")
                                        && ((String) ca[0]).contains("SELECT EXISTS")) {
                                    var delayed = connection.prepareStatement("SELECT pg_sleep(3)");
                                    return java.lang.reflect.Proxy.newProxyInstance(
                                            java.sql.PreparedStatement.class.getClassLoader(),
                                            new Class<?>[] { java.sql.PreparedStatement.class }, (sp, sm, sa) -> {
                                                if (sm.getName().equals("setLong"))
                                                    return null;
                                                if (sm.getName().equals("executeQuery"))
                                                    entered.countDown();
                                                try {
                                                    return sm.invoke(delayed, sa);
                                                } catch (java.lang.reflect.InvocationTargetException e) {
                                                    throw e.getCause();
                                                }
                                            });
                                }
                                try {
                                    return cm.invoke(connection, ca);
                                } catch (java.lang.reflect.InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                });
        try (var owner = new CampusRunArchiveOwner(source, Limits.defaults())) {
            var token = owner.start();
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
            while (owner.mayWrite() && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertThat(owner.mayWrite()).isFalse();
            assertThat(owner.token()).isEqualTo(token);
        }
    }

    private static void delete(UUID id) throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
            p.setObject(1, id);
            p.executeUpdate();
        }
    }
}
