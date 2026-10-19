package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.RuleAgentTaskStore;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** 仅测试源码提供显式浏览器租约；身份、审核资料和 HTTP 装配复用真实临时库整链。 */
@Timeout(1000)
class RuleAgentReviewedBrowserLeaseTest {
    @Test void actualReviewedIdentityBrowserLeaseRequiresExplicitStopAndReleasesOwnedResources() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("zhku.t06.reviewedBrowserLease"), "浏览器租约默认关闭，未验收");
        Path evidence = Path.of(Objects.requireNonNull(System.getProperty("zhku.rulecheck.evidenceDir"), "须指定独立证据目录")).toAbsolutePath();
        Files.createDirectories(evidence);
        assertThat(Files.exists(evidence.resolve("STOP"))).as("新租约不得复用旧 STOP").isFalse();
        var stopped = new AtomicBoolean();
        var workerFailure = new AtomicReference<Throwable>();
        var queue = RuleAgentSimulatedChainTempDbHttpTest.queue;
        queue.clear(); RuleAgentSimulatedChainTempDbHttpTest.runs.set(0);
        RuleAgentSimulatedChainTempDbHttpTest.RUN_FAILURE.set(null);
        // 故意保留真实排队与轮询窗口；不把模拟结果同步塞回浏览器。
        Thread worker = new Thread(() -> {
            try {
                while (!stopped.get() || !queue.isEmpty()) {
                    var job = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (job != null) { Thread.sleep(1500); job.run(); }
                }
            } catch (Throwable failure) { workerFailure.set(failure); }
        }, "zhku-reviewed-browser-simulated-worker");
        String previousPort = System.getProperty("server.port");
        System.setProperty("server.port", "0");
        int port = -1;
        boolean stopReceived = false, reviewRevoked = false;
        try {
            RuleAgentSimulatedChainTempDbHttpTest.installReviewedAssetsAndIdentities();
            var app = new SpringApplication(RuleAgentSimulatedChainTempDbHttpTest.App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            app.setDefaultProperties(RuleAgentSimulatedChainTempDbHttpTest.baseSettings());
            try (var context = (ServletWebServerApplicationContext) app.run()) {
                port = context.getWebServer().getPort(); worker.start();
                RuleAgentSimulatedChainTempDbHttpTest.JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("browser-ready.json").toFile(), Map.of(
                        "backendPort", port, "executionMode", "SIMULATED_ENGINEERING_REPLAY_REAL_IDENTITY_REVIEWED_ASSETS",
                        "schemaVersion", 3, "providerCalls", 0, "modelAttemptCount", 0,
                        "assetsRevision", "2025.9.2", "sourceSha", System.getProperty("zhku.sourceSha", "uncommitted-with-manifest")));
                Instant until = Instant.now().plusSeconds(900);
                try {
                    while (!Files.exists(evidence.resolve("STOP")) && Instant.now().isBefore(until)) {
                        if (!reviewRevoked && Files.exists(evidence.resolve("REVOKE_REVIEW"))) {
                            // 资格撤销须与可见范围一并降级，避免违反学生可见资料必须已获分享授权的数据库约束。
                            var jdbc = new JdbcTemplate(RuleAgentSimulatedChainTempDbHttpTest.db);
                            int changed = jdbc.update("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY', sharing_rights_confirmed=false WHERE id IN (SELECT c.source_id FROM knowledge_chunk c JOIN retrieval_index_entry e ON e.chunk_id=c.id WHERE e.profile_id=?)",
                                    RuleAgentSimulatedChainTempDbHttpTest.catalogue.settings().profileId());
                            assertThat(changed).isPositive(); reviewRevoked = true;
                            Files.writeString(evidence.resolve("review-revoked.json"), "{\"temporaryDatabaseOnly\":true,\"realEligibilityChanged\":true,\"providerCalls\":0}");
                        }
                        if (workerFailure.get() != null) throw new AssertionError("浏览器模拟工作线程异常", workerFailure.get());
                        Thread.sleep(100);
                    }
                    stopReceived = Files.exists(evidence.resolve("STOP"));
                } finally {
                    stopped.set(true); worker.join(10_000);
                }
                assertThat(worker.isAlive()).isFalse(); assertThat(workerFailure.get()).isNull();
                assertThat(RuleAgentSimulatedChainTempDbHttpTest.RUN_FAILURE.get()).isNull();
                assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
                assertThat(queue).isEmpty();
            }
            final int boundPort = port;
            try (var socket = new Socket()) {
                assertThatThrownBy(() -> socket.connect(new InetSocketAddress("127.0.0.1", boundPort), 500)).isInstanceOf(java.io.IOException.class);
            }
            RuleAgentSimulatedChainTempDbHttpTest.JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("browser-host-exit.json").toFile(), Map.of(
                    "backendListenerExited", true, "workerExited", true, "activeJobs", 0, "providerCalls", 0,
                    "explicitStopReceived", stopReceived, "reviewEligibilityRevokedInTempDb", reviewRevoked,
                    "executions", RuleAgentSimulatedChainTempDbHttpTest.runs.get()));
            assertThat(stopReceived).as("租约必须显式 STOP；超时退出不能冒充浏览器验收通过").isTrue();
        } finally {
            stopped.set(true); if (worker.isAlive()) worker.join(10_000);
            RuleAgentSimulatedChainTempDbHttpTest.uninstall();
            if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", previousPort);
        }
    }
}
