package com.zhku.knowledge.campusagent.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 两个不同PID的真正JVM退出/重启；未调用真实模型或恢复旧执行。 */
class CampusRunArchiveProcessRestartTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<Process> children = new ArrayList<>();
    private final List<UUID> runs = new ArrayList<>();
    private UUID source;

    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    @BeforeEach
    void syntheticSource() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            source = CampusRunHistoryVisibilityIntegrationTest.insertSource(c);
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        for (var child : children) {
            if (child.isAlive()) {
                child.descendants().forEach(p -> p.destroyForcibly());
                child.destroyForcibly();
            }
            assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(child.isAlive()).isFalse();
        }
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            try (var p = c.prepareStatement("DELETE FROM campus_agent_run_archive WHERE run_id=?")) {
                for (var id : runs) {
                    p.setObject(1, id);
                    p.addBatch();
                }
                p.executeBatch();
            }
            if (source != null)
                CampusRunHistoryVisibilityIntegrationTest.deleteSource(c, source);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMIT_BEFORE_DISPATCH", "TOOL_BEFORE_RETURN", "CLEANUP_BEFORE_FINAL",
            "FINAL_BEFORE_PUBLICATION" })
    void realCrashWindowReconcilesWithoutModelOrToolReplay(String mode) throws Exception {
        Path oldDirectory = directory();
        Process old = launch(mode, oldDirectory);
        var before = awaitPhase(oldDirectory, mode, Duration.ofSeconds(10), old);
        UUID run = UUID.fromString(before.path("runId").asText());
        runs.add(run);
        assertThat(before.path("pid").asLong()).isEqualTo(old.pid());
        assertThat(before.path("modelCalls").asInt()).isEqualTo(mode.equals("ADMIT_BEFORE_DISPATCH") ? 0 : 2);
        assertThat(before.path("toolCalls").asInt()).isEqualTo(mode.equals("ADMIT_BEFORE_DISPATCH") ? 0 : 1);
        assertThat(before.path("terminated").asBoolean()).isFalse();
        assertThat(before.path("finalEvents").asInt()).isZero();
        assertThat(before.path("cleanupDone").asBoolean())
                .isEqualTo(mode.equals("CLEANUP_BEFORE_FINAL") || mode.equals("FINAL_BEFORE_PUBLICATION"));
        old.destroyForcibly();
        assertThat(old.waitFor(10, TimeUnit.SECONDS)).isTrue();
        Path nextDirectory = directory();
        Files.writeString(nextDirectory.resolve("run-id"), run.toString());
        Process next = launch("RECONCILE", nextDirectory);
        var after = awaitPhase(nextDirectory, "RECONCILED", Duration.ofSeconds(10), next);
        assertThat(next.pid()).isNotEqualTo(old.pid());
        assertThat(after.path("epoch").asLong()).isGreaterThan(before.path("epoch").asLong());
        assertThat(after.path("modelCalls").asInt()).isZero();
        assertThat(after.path("toolCalls").asInt()).isZero();
        boolean finalized = mode.equals("FINAL_BEFORE_PUBLICATION");
        assertThat(after.path("archiveStatus").asText()).isEqualTo(finalized ? "FINALIZED" : "INTERRUPTED");
        if (finalized) {
            assertThat(after.path("executionState").asText()).isEqualTo("SUCCEEDED");
            assertThat(after.path("hasResult").asBoolean()).isTrue();
        } else {
            assertThat(after.path("executionState").isNull()).isTrue();
            assertThat(after.path("hasResult").asBoolean()).isFalse();
        }
        System.out.println("M3A_RESTART mode=" + mode + " oldPid=" + old.pid() + " newPid=" + next.pid() + " archive="
                + after.path("archiveStatus").asText() + " replayedModels=0 replayedTools=0");
    }

    @Test
    void secondJvmCannotTakeOverOrReconcileFirstOwnersAdmittedRecord() throws Exception {
        Path firstDirectory = directory();
        Process first = launch("HOLD_LOCK", firstDirectory);
        var held = awaitPhase(firstDirectory, "HOLD_LOCK", Duration.ofSeconds(10), first);
        UUID run = UUID.randomUUID();
        runs.add(run);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        """
                INSERT INTO campus_agent_run_archive(run_id,owner_instance_id,owner_epoch,archive_status,schema_version,writer_version)
                VALUES(?,?,?,'ADMITTED',1,'process-test')
                """)) {
            p.setObject(1, run);
            p.setObject(2, UUID.fromString(held.path("instanceId").asText()));
            p.setLong(3, held.path("epoch").asLong());
            p.executeUpdate();
        }
        Path secondDirectory = directory();
        Files.writeString(secondDirectory.resolve("run-id"), run.toString());
        Process second = launch("RECONCILE", secondDirectory);
        var rejected = awaitPhase(secondDirectory, "LOCK_REJECTED", Duration.ofSeconds(10), second);
        assertThat(second.pid()).isNotEqualTo(first.pid());
        assertThat(rejected.path("code").asText()).isEqualTo("ARCHIVE_OWNER_LOST");
        assertThat(status(run)).isEqualTo("ADMITTED");
    }

    @Test
    void fencedOldJvmLateToolResultCannotOverwriteNewOwnersInterruption() throws Exception {
        Path oldDirectory = directory();
        Process old = launch("TOOL_BEFORE_RETURN", oldDirectory);
        var blocked = awaitPhase(oldDirectory, "TOOL_BEFORE_RETURN", Duration.ofSeconds(10), old);
        UUID run = UUID.fromString(blocked.path("runId").asText());
        runs.add(run);
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT pg_terminate_backend(?)")) {
            p.setInt(1, blocked.path("ownerBackendPid").asInt());
            try (var r = p.executeQuery()) {
                r.next();
                assertThat(r.getBoolean(1)).isTrue();
            }
        }
        Path nextDirectory = directory();
        Files.writeString(nextDirectory.resolve("run-id"), run.toString());
        Process next = launch("RECONCILE", nextDirectory);
        var reconciled = awaitPhase(nextDirectory, "RECONCILED", Duration.ofSeconds(10), next);
        assertThat(reconciled.path("archiveStatus").asText()).isEqualTo("INTERRUPTED");
        Files.writeString(oldDirectory.resolve("release"), "release");
        var completed = awaitPhase(oldDirectory, "COMPLETED", Duration.ofSeconds(10), old);
        assertThat(completed.path("onlineState").asText()).isEqualTo("SUCCEEDED");
        assertThat(completed.path("stopReason").asText()).isEqualTo("NONE");
        assertThat(completed.path("terminated").asBoolean()).isTrue();
        assertThat(completed.path("finalEvents").asInt()).isEqualTo(1);
        assertThat(completed.path("finalCallbackReadable").asBoolean()).isTrue();
        assertThat(status(run)).isEqualTo("INTERRUPTED");
    }

    @Test
    void boundedShutdownDoesNotFakeCancellationOfBlockedOldTool() throws Exception {
        Path directory = directory();
        Process old = launch("TOOL_BEFORE_RETURN", directory);
        var before = awaitPhase(directory, "TOOL_BEFORE_RETURN", Duration.ofSeconds(10), old);
        UUID run = UUID.fromString(before.path("runId").asText());
        runs.add(run);
        Files.writeString(directory.resolve("shutdown"), "shutdown");
        var closed = awaitPhase(directory, "CLOSED_WITH_PENDING_TASK", Duration.ofSeconds(10), old);
        assertThat(closed.path("stopReason").asText()).isEqualTo("NONE");
        assertThat(closed.path("terminated").asBoolean()).isFalse();
        assertThat(closed.path("cleanupDone").asBoolean()).isFalse();
        assertThat(status(run)).isEqualTo("ADMITTED");
        Path nextDirectory = directory();
        Files.writeString(nextDirectory.resolve("run-id"), run.toString());
        Process next = launch("RECONCILE", nextDirectory);
        assertThat(awaitPhase(nextDirectory, "RECONCILED", Duration.ofSeconds(10), next).path("archiveStatus").asText())
                .isEqualTo("INTERRUPTED");
        Files.writeString(directory.resolve("release"), "release");
        var complete = awaitPhase(directory, "COMPLETED", Duration.ofSeconds(10), old);
        assertThat(complete.path("onlineState").asText()).isEqualTo("SUCCEEDED");
        assertThat(complete.path("stopReason").asText()).isEqualTo("NONE");
        assertThat(status(run)).isEqualTo("INTERRUPTED");
    }

    @Test
    void completedPhaseIsReadAfterProcessExit() throws Exception {
        Path directory = directory();
        Files.writeString(directory.resolve("phase.json"), "{\"phase\":\"CLOSED_WITH_PENDING_TASK\"}");
        Process process = mock(Process.class);
        // 固定在第一次读旧文件之后、确认退出之前发布最终屏障；不用sleep碰运气。
        when(process.isAlive()).thenAnswer(invocation -> {
            Files.writeString(directory.resolve("phase.json"), "{\"phase\":\"COMPLETED\"}");
            return false;
        });
        assertThat(awaitPhase(directory, "COMPLETED", Duration.ofSeconds(10), process)
                .path("phase").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void failedProbeIsNotAcceptedAsCompletion() throws Exception {
        Path directory = directory();
        Files.writeString(directory.resolve("phase.json"), "{\"phase\":\"FAILED\"}");
        Process process = mock(Process.class);
        when(process.isAlive()).thenReturn(false);
        assertThatThrownBy(() -> awaitPhase(directory, "COMPLETED", Duration.ofSeconds(10), process))
                .isInstanceOf(AssertionError.class).hasMessageContaining("未到达屏障 COMPLETED");
    }

    private static Path directory() throws Exception {
        return Files.createDirectories(
                Path.of("target", "m3a-process-probes", UUID.randomUUID().toString()).toAbsolutePath());
    }

    private Process launch(String mode, Path directory) throws Exception {
        String password = ZhkuDatabaseTestConfig.databasePassword();
        assertThat(password).isNotBlank();
        ArchiveTestDatabase.dataSource();
        String executable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var builder = new ProcessBuilder(executable, "-cp", classpath, CampusRunArchiveProcessProbe.class.getName(),
                mode, directory.toString());
        builder.environment().put("ZHKU_DB_URL", System.getProperty("zhku.db.url"));
        builder.environment().put("ZHKU_DB_USER", "zhkuagent");
        builder.environment().put("ZHKU_DB_PASSWORD", password);
        builder.redirectErrorStream(true).redirectOutput(directory.resolve("process.log").toFile());
        Process child = builder.start();
        children.add(child);
        return child;
    }

    static JsonNode awaitPhase(Path directory, String expected, Duration timeout, Process process) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Path file = directory.resolve("phase.json");
        while (System.nanoTime() < deadline) {
            if (Files.exists(file)) {
                var json = JSON.readTree(Files.readString(file));
                if (expected.equals(json.path("phase").asText()))
                    return json;
            }
            if (!process.isAlive()) {
                // 退出确认发生在第一次文件读取之后；子进程可能已在这段窗口写完最终屏障。
                // 退出后再读一次固定文件，不增加等待，也不把FAILED或退出码0当完成。
                if (Files.exists(file)) {
                    var terminal = JSON.readTree(Files.readString(file));
                    if (expected.equals(terminal.path("phase").asText()))
                        return terminal;
                }
                throw new AssertionError("子进程提前退出，未到达屏障 " + expected + "，退出码 " + process.exitValue());
            }
            Thread.sleep(10);
        }
        throw new AssertionError("没有在10秒内到达屏障 " + expected);
    }

    private static String status(UUID id) throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement("SELECT archive_status FROM campus_agent_run_archive WHERE run_id=?")) {
            p.setObject(1, id);
            try (var r = p.executeQuery()) {
                assertThat(r.next()).isTrue();
                return r.getString(1);
            }
        }
    }
}
