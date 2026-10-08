package com.zhku.knowledge.controlled;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 不同PID、实际HTTP、持久身份/Run历史；不是登录Session或业务Session恢复。 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ControlledCampusIdentityProcessTest {
    private static Data data;
    @BeforeAll static void seed() throws Exception { data = ControlledCampusTestFixtures.seed(); }
    @org.junit.jupiter.api.AfterAll static void cleanup() throws Exception { ControlledCampusTestFixtures.cleanup(data); }

    @Test void stableIdentityHistoryAndInterruptedOwnershipSurviveButOldCookieDoesNot() throws Exception {
        var children = new ArrayList<Child>();
        Path evidence = Path.of("target", "controlled-process-probes", UUID.randomUUID().toString());
        Files.createDirectories(evidence);
        try {
            Child old = launch(); children.add(old);
            int oldPort = old.ready();
            UUID completed;
            UUID blocked;
            try (var a = new User(oldPort); var b = new User(oldPort)) {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
                completed = a.submit(QUESTION, COURSES);
                assertThat(a.awaitTerminal(completed).path("resultVisibility").asText()).isEqualTo("VISIBLE");
                blocked = b.submit("受控阻塞验证，公共选修课学分上限是多少？", COURSES);
                old.await("CONTROLLED_BLOCKED", Duration.ofSeconds(5));
                assertThat(JSON.readTree(b.send("GET", PREFIX + "/runs/" + blocked, null, false).body())
                        .path("run").path("terminated").asBoolean()).isFalse();
                old.stop();
                Child next = launch(); children.add(next);
                int nextPort = next.ready();
                assertThat(next.process.pid()).isNotEqualTo(old.process.pid());
                try (var restoredA = new User(nextPort); var restoredB = new User(nextPort)) {
                    a.copyCookiesTo(restoredA);
                    assertThat(restoredA.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
                    var login = restoredA.login(data.a());
                    assertThat(login.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(login.body()).path("userId").asText()).isEqualTo(data.a().id().toString());
                    assertThat(restoredB.login(data.b()).statusCode()).isEqualTo(200);
                    var finished = restoredA.send("GET", PREFIX + "/history/" + completed, null, false);
                    assertThat(finished.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(finished.body()).path("archiveStatus").asText()).isEqualTo("FINALIZED");
                    assertThat(JSON.readTree(finished.body()).path("result").path("answer").asText()).contains("3.5");
                    assertThat(restoredB.send("GET", PREFIX + "/history/" + completed, null, false).statusCode()).isEqualTo(404);
                    assertThat(restoredA.send("GET", PREFIX + "/history/" + blocked, null, false).statusCode()).isEqualTo(404);
                    var interrupted = restoredB.send("GET", PREFIX + "/history/" + blocked, null, false);
                    assertThat(interrupted.statusCode()).isEqualTo(200);
                    var record = JSON.readTree(interrupted.body());
                    assertThat(record.path("archiveStatus").asText()).isEqualTo("INTERRUPTED");
                    assertThat(record.path("execution").isNull()).isTrue();
                    assertThat(record.path("result").isNull()).isTrue();
                    assertThat(restoredA.send("GET", PREFIX + "/runs/" + completed, null, false).statusCode()).isEqualTo(404);
                }
                next.stop();
                assertThat(next.closedCalls).isZero();
                JSON.writeValue(evidence.resolve("phase.json").toFile(), Map.ofEntries(
                        Map.entry("phase", "VERIFIED"), Map.entry("oldPid", old.process.pid()),
                        Map.entry("newPid", next.process.pid()), Map.entry("oldPort", oldPort), Map.entry("newPort", nextPort),
                        Map.entry("oldExited", !old.process.isAlive()), Map.entry("newExited", !next.process.isAlive()),
                        Map.entry("stableIdentity", true), Map.entry("oldCookieRejected", true),
                        Map.entry("interruptedOwnership", true), Map.entry("replayedModels", next.closedCalls)));
            }
        } finally {
            boolean forced = false;
            for (var child : children) forced |= child.cleanup();
            assertThat(forced).as("强杀仅用于finally清理，不能当优雅退出验收通过").isFalse();
        }
    }

    private static Child launch() throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var builder = new ProcessBuilder(executable, "-cp", classpath,
                ControlledCampusIdentityProcessProbe.class.getName()).redirectErrorStream(true);
        // 源码/命令只有非秘密目标；随机临时密码仅继承进程环境，禁止参数或临时口令文件。
        if (System.getenv("ZHKU_DB_PASSWORD") == null) throw new IllegalArgumentException("缺少测试子进程数据库环境");
        builder.environment().put("ZHKU_DB_URL", ((org.springframework.jdbc.datasource.DriverManagerDataSource) data.source()).getUrl());
        builder.environment().put("ZHKU_DB_USER", "zhkuagent");
        return new Child(builder.start());
    }

    @Test void abruptOwnedExecutionExitReconcilesWithoutReplayOrCrossUserAccess() throws Exception {
        var children = new ArrayList<Child>();
        Path evidence = Path.of("target", "controlled-process-probes", UUID.randomUUID().toString());
        Files.createDirectories(evidence);
        try {
            Child old = launch(); children.add(old);
            int oldPort = old.ready();
            UUID interrupted;
            try (var a = new User(oldPort)) {
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                interrupted = a.submit("受控阻塞验证，公共选修课学分上限是多少？", COURSES);
                old.await("CONTROLLED_BLOCKED", Duration.ofSeconds(5));
                // 明确的故障注入：此强杀不冒充正常关闭，也不在finally中用来补造绿色。
                old.process.destroyForcibly();
                assertThat(old.process.waitFor(5, TimeUnit.SECONDS)).isTrue();
                assertThat(old.process.isAlive()).isFalse();
                Child next = launch(); children.add(next);
                int nextPort = next.ready();
                assertThat(next.process.pid()).isNotEqualTo(old.process.pid());
                try (var nextA = new User(nextPort); var nextB = new User(nextPort)) {
                    assertThat(nextA.login(data.a()).statusCode()).isEqualTo(200);
                    assertThat(nextB.login(data.b()).statusCode()).isEqualTo(200);
                    assertThat(nextB.send("GET", PREFIX + "/history/" + interrupted, null, false).statusCode()).isEqualTo(404);
                    assertThat(nextB.send("POST", PREFIX + "/runs/" + interrupted + "/cancel", "{}", true).statusCode()).isEqualTo(404);
                    var owned = nextA.send("GET", PREFIX + "/history/" + interrupted, null, false);
                    assertThat(owned.statusCode()).isEqualTo(200);
                    var body = JSON.readTree(owned.body());
                    assertThat(body.path("archiveStatus").asText()).isEqualTo("INTERRUPTED");
                    assertThat(body.path("result").isNull()).isTrue(); assertThat(body.path("execution").isNull()).isTrue();
                }
                next.stop();
                assertThat(next.closedCalls).isZero();
                JSON.writeValue(evidence.resolve("phase.json").toFile(), Map.of(
                        "phase", "VERIFIED_ABRUPT_EXIT", "intentionalCrash", true,
                        "oldPid", old.process.pid(), "newPid", next.process.pid(), "oldExited", !old.process.isAlive(),
                        "newExited", !next.process.isAlive(), "replayedModels", next.closedCalls));
            }
        } finally {
            boolean forced = false;
            for (var child : children) forced |= child.cleanup();
            assertThat(forced).as("finally强杀不是故障场景的预期强杀").isFalse();
        }
    }

    private static final class Child {
        final Process process;
        final ArrayBlockingQueue<String> phases = new ArrayBlockingQueue<>(16);
        final Thread reader;
        int closedCalls = -1;
        Child(Process process) {
            this.process = process;
            reader = Thread.ofPlatform().name("controlled-process-output").start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = lines.readLine()) != null;) {
                        // 不留模型/框架自由日志，也不把无限输出装入无界队列。
                        if (line.matches("CONTROLLED_(READY [0-9]+ [0-9]+|BLOCKED|CLOSED [0-9]+ [0-9]+|FAILED [A-Za-z0-9]+)"))
                            if (!phases.offer(line)) return;
                    }
                } catch (java.io.IOException closed) { }
            });
        }
        int ready() throws Exception {
            String[] ready = await("CONTROLLED_READY", Duration.ofSeconds(20)).split(" ");
            assertThat(Long.parseLong(ready[1])).isEqualTo(process.pid());
            return Integer.parseInt(ready[2]);
        }
        String await(String prefix, Duration budget) throws Exception {
            long deadline = System.nanoTime() + budget.toNanos();
            do {
                var line = phases.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (line == null) break;
                if (line.startsWith("CONTROLLED_FAILED")) throw new AssertionError(line);
                if (line.startsWith(prefix)) return line;
            } while (System.nanoTime() < deadline);
            throw new AssertionError("子进程阶段预算耗尽: " + prefix);
        }
        void stop() throws Exception {
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            if (process.exitValue() != 0) await("CONTROLLED_FAILED", Duration.ofMillis(200));
            assertThat(process.exitValue()).isZero();
            String[] closed = await("CONTROLLED_CLOSED", Duration.ofMillis(200)).split(" ");
            closedCalls = Integer.parseInt(closed[1]);
            assertThat(Integer.parseInt(closed[2])).isZero();
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse();
        }
        boolean cleanup() throws Exception {
            boolean forced = process.isAlive();
            if (forced) { process.destroyForcibly(); assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); }
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse(); assertThat(process.isAlive()).isFalse();
            return forced;
        }
    }
}
