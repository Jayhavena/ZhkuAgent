package com.zhku.knowledge.ruleagent.web;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** 仅给现有Assembly增加真实前端代理预检；不是新HTTP服务，也没有Live页面执行分支。 */
final class RuleAgentOnlinePagePreflight {
    private RuleAgentOnlinePagePreflight() {}
    static void validate(String operation, int port) {
        if (!"assembly".equals(operation) || port < 1024 || port > 65535)
            throw new IllegalArgumentException("页面代理预检仅允许Assembly独立回环端口");
    }
    static void run(int backendPort, int proxyPort, Path root, Path batch) throws Exception {
        validate("assembly", proxyPort);
        assertThat(RuleAgentThreeQuestionHostTest.live).isFalse();
        assertThat(System.getenv("DASHSCOPE_API_KEY")).as("Assembly子进程不继承模型密钥").isNull();
        Path frontend = root.resolve("agent/frontend");
        Path dist = frontend.resolve("dist-page-online-preflight");
        assertThat(Files.isRegularFile(dist.resolve("index.html"))).as("须先独立构建真实student页面").isTrue();
        Path marker = batch.resolve("page-cancel-ready");
        assertThat(Files.exists(marker)).isFalse();
        try (var check = new ServerSocket()) { check.bind(new InetSocketAddress("127.0.0.1", proxyPort)); }
        Process preview = null, client = null;
        boolean passed = false, exited = false;
        try {
            var serve = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "preview", "--outDir", dist.toString(),
                    "--host", "127.0.0.1", "--port", String.valueOf(proxyPort), "--strictPort").directory(frontend.toFile())
                    .redirectErrorStream(true).redirectOutput(batch.resolve("page-preview.log").toFile());
            serve.environment().put("VITE_RULE_TASK_PROXY_TARGET", "http://127.0.0.1:" + backendPort);
            serve.environment().put("VITE_ENTRY_MODE", "student"); serve.environment().remove("VITE_DEMO_MODE");
            // 不继承旧回放/供应商凭据；页面标识不是安全开关，真正保护是Assembly队列与!live守卫。
            serve.environment().remove("DASHSCOPE_API_KEY");
            preview = serve.start();
            var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            boolean ready = false;
            while (preview.isAlive() && System.nanoTime() < deadline) {
                try {
                    var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxyPort + "/rules"))
                            .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    ready = response.statusCode() == 200 && response.body().contains("/assets/");
                    if (ready) break;
                } catch (java.io.IOException expected) { /* 仅等待本轮preview就绪，不重试模型。 */ }
                Thread.sleep(100);
            }
            assertThat(ready).as("真实student构建经本轮Vite提供").isTrue();
            var state = RuleAgentThreeQuestionHostTest.JSON.createObjectNode().put("mode", "ONLINE_ASSEMBLY_PAGE_PREFLIGHT_NO_MODEL")
                    .put("backendPort", backendPort).put("proxyPort", proxyPort).put("previewPid", preview.pid())
                    .put("providerCalls", 0).put("keyReads", 0).put("runnerEnabled", false).put("browserExecuted", false);
            Files.write(batch.resolve("page-preflight-ready.json"), RuleAgentThreeQuestionHostTest.JSON.writeValueAsBytes(state), StandardOpenOption.CREATE_NEW);
            client = new ProcessBuilder("node", root.resolve("knowledge/src/test/browser/rule-agent-online-page-preflight.mjs").toString(),
                    String.valueOf(proxyPort), batch.toString()).directory(root.toFile())
                    .redirectErrorStream(true).redirectOutput(batch.resolve("page-client.log").toFile()).start();
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50);
            boolean drained = false;
            while (client.isAlive() && System.nanoTime() < deadline) {
                if (!drained && Files.exists(marker)) {
                    // 已由真实客户端成功请求取消；只消费已stop票据，execute在factory.open前返回。
                    assertThat(RuleAgentThreeQuestionHostTest.queue).hasSize(1);
                    RuleAgentThreeQuestionHostTest.queue.remove().run(); drained = true;
                    assertThat(RuleAgentThreeQuestionHostTest.scope.runs()).isZero();
                }
                Thread.sleep(50);
            }
            assertThat(client.isAlive()).as("客户端预检须在有界时间退出").isFalse();
            assertThat(client.exitValue()).isZero(); assertThat(drained).isTrue();
            assertThat(RuleAgentThreeQuestionHostTest.factory.remainingCalls()).isEqualTo(20);
            assertThat(RuleAgentThreeQuestionHostTest.keyReads.get()).isZero();
            assertThat(RuleAgentThreeQuestionHostTest.calls.get()).isZero();
            assertThat(RuleAgentThreeQuestionHostTest.scope.runs()).isZero();
            assertThat(RuleAgentThreeQuestionHostTest.store.activeJobs()).isZero();
            passed = true;
        } finally {
            if (client != null && client.isAlive()) stop(client);
            if (preview != null) stop(preview);
            // Windows进程句柄退出与TCP监听回收可有短暂时差；有界等到端口确实拒绝连接，仍不通过即失败。
            long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            do { exited = closedPort(proxyPort); if (!exited) Thread.sleep(50); }
            while (!exited && System.nanoTime() < closeDeadline);
            Files.write(batch.resolve("page-preflight-process-exit.json"), RuleAgentThreeQuestionHostTest.JSON.writeValueAsBytes(
                    Map.of("passed", passed, "previewExited", preview == null || !preview.isAlive(), "clientExited", client == null || !client.isAlive(),
                            "listenerExited", exited, "proxyPort", proxyPort, "browserExecuted", false)), StandardOpenOption.CREATE_NEW);
        }
        assertThat(exited).isTrue();
    }
    private static void stop(Process process) throws Exception {
        if (process.isAlive()) { process.destroy(); if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly(); }
        if (!process.waitFor(3, TimeUnit.SECONDS)) throw new IllegalStateException("本轮前端子进程未退出");
    }
    private static boolean closedPort(int port) {
        try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 300); return false; }
        catch (java.io.IOException expected) { return true; }
    }
}
