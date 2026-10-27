package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.security.*;
import com.zhku.agent.agent.runtime.AgentRunContext;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 非产品入口：只从测试classpath运行；prepare/verify/assembly都不能读取供应商凭证或创建Provider。 */
@Timeout(660)
class RuleAgentThreeQuestionHostTest {
    static final ObjectMapper JSON = RuleAgentThreeQuestionPackage.JSON;
    static Path root, assets, batch;
    static String hash;
    static JsonNode plan;
    static boolean live;
    static boolean single;
    static String hardBudgetRmb;
    static int maximumCalls;
    static RuleAgentThreeQuestionScope scope;
    static RuleAgentE02Scope e02Scope;
    static RuleAgentTaskStore store;
    static RuleAgentOnlineFactory factory;
    static ExecutorService workers;
    static final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    static final AtomicInteger keyReads = new AtomicInteger();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile boolean stopped;
    static BigDecimal reserved = BigDecimal.ZERO;
    static final List<JsonNode> outcomes = new ArrayList<>();

    @Test void dispatchOnlyExplicitTestOperation() throws Exception {
        String operation = System.getProperty("zhku.three.operation", "");
        Assumptions.assumeTrue(Set.of("prepare", "verify", "assembly", "live", "page-fixture").contains(operation), "无显式操作，不构造外部资源");
        if (System.getProperty("zhku.three.pageProxyPort") != null)
            RuleAgentOnlinePagePreflight.validate(operation, Integer.parseInt(System.getProperty("zhku.three.pageProxyPort")));
        // 页面接线验收必须与Live审包/数据库/凭证入口分离，不能因缺少真实配置自动降级。
        if (operation.equals("page-fixture")) { RuleAgentTaskBrowserHttpTest.runFixture(); return; }
        root = Path.of(System.getProperty("zhku.three.codeRoot")).toRealPath();
        assets = Path.of(System.getProperty("zhku.t06.root")).toRealPath();
        batch = Path.of(System.getProperty("zhku.three.batch")).toAbsolutePath().normalize();
        plan = RuleAgentThreeQuestionPackage.plan(root);
        single = RuleAgentThreeQuestionPackage.e02Profile();
        hardBudgetRmb = single ? RuleAgentE02Policy.HARD_BUDGET_RMB : "16.10";
        maximumCalls = single ? RuleAgentE02Policy.MAXIMUM_PROVIDER_CALLS : 20;
        if (operation.equals("prepare")) {
            hash = RuleAgentThreeQuestionPackage.prepare(root, assets, batch);
            System.out.println("THREE_QUESTION_PREPARED_NOT_AUTHORIZED sha256=" + hash); return;
        }
        hash = System.getProperty("zhku.three.sha256");
        RuleAgentThreeQuestionPackage.verify(root, assets, batch, hash);
        if (operation.equals("verify")) { System.out.println("THREE_QUESTION_VERIFIED_NO_RESOURCES"); return; }
        live = operation.equals("live");
        if (live) {
            // 参数表达人工授权，不是包文件自授；脚本仍须由用户明确授权后才可运行live。
            if (!hash.equals(System.getProperty("zhku.three.approvedSha256")) || !hardBudgetRmb.equals(System.getProperty("zhku.three.approvedBudgetRmb"))) throw new IllegalStateException("缺少本包SHA与" + hardBudgetRmb + "元硬帽的明确授权参数");
            RuleAgentThreeQuestionPackage.tariff(plan, Clock.systemUTC());
            RuleAgentThreeQuestionPackage.claim(root, batch, hash, System.getProperty("zhku.three.authorizationId"));
        }
        runHttpFixture();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class, RuleCheckAuthController.class, RuleAgentTaskController.class,
            RuleAgentControlledLaunchConfiguration.class, RuleAgentReviewedToolsConfiguration.class})
    static class App {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean javax.sql.DataSource dataSource() { return RuleAgentSimulatedChainTempDbHttpTest.db; }
        @Bean(destroyMethod = "") Reviewed2025ControlledCatalogue catalogue() { return RuleAgentSimulatedChainTempDbHttpTest.catalogue; }
        @Bean RuleAgentReviewedToolsConfiguration.Assets assets() { return new RuleAgentReviewedToolsConfiguration.Assets(assets); }
        @Bean(destroyMethod = "close") PreviewIdentityRepository identities() { return new PreviewIdentityRepository(dataSource()); }
        @Bean(destroyMethod = "close") RuleAgentTaskToolsRegistry registry(RuleAgentTaskToolsRegistry.ToolFactory tools, Clock clock) {
            return new RuleAgentTaskToolsRegistry(tools, clock, 3);
        }
        @Bean(name = RuleAgentControlledLaunchConfiguration.ONLINE_FACTORY_BEAN, destroyMethod = "close")
        RuleAgentOnlineFactory actualFactory(RuleAgentTaskToolsRegistry registry, Clock clock) {
            // 沿用真实工厂、Runner、ActionBroker和Gate；测试仅增加不可扩额的审包/审计与输入约束。
            var settings = new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING, RuleAgentProvider.MODEL,
                    live ? System.getProperty("zhku.three.authorizationId") : "THREE_QUESTION_ASSEMBLY_NO_PROVIDER");
            factory = new RuleAgentOnlineFactory(settings, () -> {
                keyReads.incrementAndGet();
                if (!live || stopped) throw new IllegalStateException("零调用或已停止，禁止读取密钥");
                return System.getenv("DASHSCOPE_API_KEY");
            }, registry::open, RuleAgentThreeQuestionHostTest::audit, RuleAgentThreeQuestionHostTest::exchange, single ? RuleAgentE02Policy.MAXIMUM_PROVIDER_CALLS : 20, clock);
            return factory;
        }
        @Bean(destroyMethod = "close") RuleAgentTaskStore tasks(RuleAgentOnlineFactory actual, Clock clock) {
            RuleAgentTaskStore.Factory guarded = (owner, task) -> new RuleAgentTaskStore.Work() {
                private RuleAgentTaskStore.Work work;
                public boolean revalidate(ResultEnvelope previous) { return work != null && work.revalidate(previous); }
                public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                    // raw补充来自TaskStore历史，不能只检查已规范化的PUBLIC而放入未审中文回复。
                    String raw = store.history(owner, task).getLast().message();
                    if (single) e02Scope.begin(input, raw, context.runId()); else scope.begin(input, raw, context.runId());
                    try {
                        if (!live || stopped) throw new IllegalStateException("本宿主未授权外发");
                        RuleAgentThreeQuestionPackage.verify(root, assets, batch, hash);
                        if (work == null) work = actual.open(owner, task); // 拒绝在工具/Provider创建前完成
                        var outcome = work.run(context, input);
                        if (failClosedTerminal(outcome)) return outcome;
                        if (single) e02Scope.complete(input, outcome); else scope.complete(input, outcome);
                        return outcome;
                    } catch (Exception failure) { failScope(); stopped = true; throw new IllegalStateException("三题批次已停止，不重试"); }
                }
            };
            store = new RuleAgentTaskStore(live ? workers : queue::add, guarded, clock,
                    single ? new RuleAgentTaskStore.Limits(1, 1, 32) : new RuleAgentTaskStore.Limits(3, 3, 32));
            return store;
        }
    }

    /** Keep Runner's actual terminal failure; never mask it with TaskStore's generic exception fallback. */
    static boolean failClosedTerminal(RuleAgentRunner.Outcome outcome) {
        var status = outcome.snapshot().taskStatus();
        if (status != TaskStatus.FAILED && status != TaskStatus.STOPPED) return false;
        failScope();
        stopped = true;
        return true;
    }

    static void failScope() {
        if (single) { if (e02Scope != null) e02Scope.fail(); } else if (scope != null) scope.fail();
    }

    private static void runHttpFixture() throws Exception {
        keyReads.set(0); calls.set(0); stopped = false; reserved = BigDecimal.ZERO; queue.clear(); outcomes.clear();
        scope = single ? null : new RuleAgentThreeQuestionScope(plan);
        e02Scope = single ? new RuleAgentE02Scope(plan) : null;
        if (live) workers = Executors.newSingleThreadExecutor(r -> new Thread(r, "zhku-three-question-worker"));
        ServletWebServerApplicationContext context = null;
        int port = -1;
        String oldPort = System.getProperty("server.port");
        boolean passed = false, closed = false;
        try {
            RuleAgentSimulatedChainTempDbHttpTest.installReviewedAssetsAndIdentities();
            System.setProperty("server.port", "0");
            var app = new SpringApplication(App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = RuleAgentSimulatedChainTempDbHttpTest.baseSettings();
            settings.put(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "ONLINE");
            settings.put("zhku.rule-agent-http.online-enabled", "true");
            settings.put("spring.main.allow-bean-definition-overriding", "false");
            settings.put("server.address", "127.0.0.1");
            app.setDefaultProperties(settings);
            context = (ServletWebServerApplicationContext) app.run();
            port = context.getWebServer().getPort();
            var anonymous = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            var a = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            var otherSession = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            var admin = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            assertThat(anonymous.get("/tasks/capabilities").statusCode()).isEqualTo(401);
            assertThat(a.loginStatus("t06-chain-a", RuleAgentSimulatedChainTempDbHttpTest.WRONG)).isEqualTo(401);
            a.login("t06-chain-a", RuleAgentSimulatedChainTempDbHttpTest.CORRECT);
            otherSession.login("t06-chain-a", RuleAgentSimulatedChainTempDbHttpTest.CORRECT);
            admin.login("t06-chain-admin", RuleAgentSimulatedChainTempDbHttpTest.CORRECT);
            assertThat(admin.get("/tasks/capabilities").statusCode()).isEqualTo(403);
            assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(200);
            if (!live && System.getProperty("zhku.three.pageProxyPort") != null)
                RuleAgentOnlinePagePreflight.run(port, Integer.parseInt(System.getProperty("zhku.three.pageProxyPort")), root, batch);
            else if (!live) assembly(a, otherSession);
            else if (single) pageDrivenE02(port);
            else executeCases(a, otherSession);
            passed = true;
        } finally {
            stopped = true;
            if (store != null) store.close();
            if (factory != null) factory.close();
            if (workers != null) { workers.shutdown(); if (!workers.awaitTermination(5, TimeUnit.SECONDS)) { workers.shutdownNow(); if (!workers.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("worker未退出"); } }
            // 取消尚未执行的assembly任务后再排空，证明不会创建Provider。
            Runnable pending; while ((pending = queue.poll()) != null) pending.run();
            if (context != null) context.close();
            RuleAgentSimulatedChainTempDbHttpTest.uninstall();
            if (oldPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", oldPort);
            closed = (store == null || store.activeJobs() == 0) && (factory == null || factory.resourcesClosed()) && (workers == null || workers.isTerminated());
            if (port > 0) try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 300); closed = false; } catch (java.io.IOException expected) { /* 已停止监听 */ }
            var receipt = JSON.createObjectNode().put("mode", live ? "LIVE" : "ASSEMBLY_NO_MODEL")
                    .put("httpChecksPassed", passed).put("resourcesClosed", closed).put("providerCalls", calls.get()).put("keyReads", keyReads.get())
                    .put("reservedRmb", reserved.toPlainString()).put("hardBudgetRmb", hardBudgetRmb).put("packageSha256", hash)
                    .put("browserTested", false).put("humanProductReview", live ? "REQUIRED_NOT_AUTOMATICALLY_PASSED" : "NOT_EXECUTED")
                    .put("billing", live ? "SUPPLIER_BILL_NOT_RECONCILED" : "ZERO_PROVIDER_CALLS")
                    .put("pageProxyPreflight", System.getProperty("zhku.three.pageProxyPort") != null)
                    .put("profile", single ? "E02" : "Three")
                    .put("scopeRuns", single ? e02Scope.runs() : scope.runs()).put("remainingProviderCalls", factory == null ? -1 : factory.remainingCalls());
            receipt.set("outcomes", JSON.valueToTree(outcomes));
            Files.write(batch.resolve(live ? "live-result.json" : "assembly-result.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt), StandardOpenOption.CREATE_NEW);
        }
        assertThat(closed).isTrue();
        if (!live) { assertThat(calls.get()).isZero(); assertThat(keyReads.get()).isZero(); }
    }

    /**
     * E02 单题：唯一的业务 POST 必须由真实页面发出。宿主只启动真实前端（本轮回环代理）、
     * 写页面可读的就绪票据、等本批显式结束，然后按 Scope 记录的同一任务回读取证；
     * 不代替页面提交、不发业务POST、不跑第二次。
     */
    private static void pageDrivenE02(int port) throws Exception {
        assertThat(queue).isEmpty();
        Path frontend = root.resolve("agent/frontend");
        Path dist = root.resolve(plan.path("page").path("dist").asText());
        assertThat(Files.isRegularFile(dist.resolve("index.html"))).as("须先构建本包冻结的student页面").isTrue();
        int proxyPort;
        try (var probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) { proxyPort = probe.getLocalPort(); }
        Process preview = null;
        try {
            var serve = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "preview", "--outDir", dist.toString(),
                    "--host", "127.0.0.1", "--port", String.valueOf(proxyPort), "--strictPort").directory(frontend.toFile())
                    .redirectErrorStream(true).redirectOutput(batch.resolve("page-preview.log").toFile());
            serve.environment().put("VITE_RULE_TASK_PROXY_TARGET", "http://127.0.0.1:" + port);
            serve.environment().put("VITE_ENTRY_MODE", "student");
            // 前端进程不继承模型密钥或旧回放变量；代理只指向本轮回环后端。
            for (String name : List.of("DASHSCOPE_API_KEY", "VITE_DEMO_MODE", "ZHKU_RULE_AGENT_MODE",
                    "ZHKU_RULECHECK_TASK_BATCH_DIRECTORY", "ZHKU_RULECHECK_TASK_PACKAGE_SHA256")) serve.environment().remove(name);
            preview = serve.start();
            var http = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(1)).build();
            long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            boolean ready = false;
            while (preview.isAlive() && System.nanoTime() < readyDeadline) {
                try {
                    var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + proxyPort + "/rules"))
                            .timeout(java.time.Duration.ofSeconds(1)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    ready = response.statusCode() == 200 && response.body().contains("/assets/");
                    if (ready) break;
                } catch (java.io.IOException expected) { /* 只等本轮preview就绪，不重试模型。 */ }
                Thread.sleep(100);
            }
            assertThat(ready).as("本包冻结的student构建须经本轮回环代理提供").isTrue();
            var readyTicket = JSON.createObjectNode()
                    .put("profile", "E02").put("executionMode", "ONLINE").put("browserRequired", true)
                    .put("backendPort", port).put("proxyPort", proxyPort).put("schemaVersion", 4)
                    .put("question", plan.path("cases").get(0).path("question").asText())
                    .put("maximumTasks", 1).put("maximumRuns", 1)
                    .put("maximumProviderCalls", maximumCalls).put("hardBudgetRmb", hardBudgetRmb)
                    .put("packageSha256", hash)
                    .put("javaPostAllowed", false).put("driver", plan.path("page").path("driver").asText());
            Files.write(batch.resolve("browser-ready.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(readyTicket), StandardOpenOption.CREATE_NEW);
            // 页面租约必须有界：上限只由本宿主预算与计划时长决定，不无限等待。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            while (!Files.exists(batch.resolve("STOP")) && System.nanoTime() < deadline) Thread.sleep(100);
            assertThat(Files.exists(batch.resolve("STOP"))).as("页面租约须显式结束，不无限等待").isTrue();
            // 页面结束后只回读同一任务：taskId/owner 全部来自 Scope 在 begin 时冻结的值。
            // 不新建HTTP会话（页面会话在Chrome里），直接按同一Owner从TaskStore读回执。
            UUID task = e02Scope.taskId();
            Owner owner = e02Scope.owner();
            assertThat(task).as("页面提交后须有唯一任务").isNotNull();
            assertThat(owner).isNotNull();
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            TaskSnapshot snapshot = null;
            while (System.nanoTime() < waitDeadline) {
                snapshot = store.snapshot(owner, task);
                if (snapshot.terminated()) break;
                Thread.sleep(100);
            }
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.terminated()).as("页面任务须在宿主预算内到达终态").isTrue();
            assertThat(snapshot.execution().mode()).isEqualTo(Mode.ONLINE);
            assertThat(snapshot.taskStatus()).isNotEqualTo(TaskStatus.FAILED);
            recordOutcome("E02", JSON.valueToTree(snapshot));
            assertThat(e02Scope.tasks()).isEqualTo(1);
            assertThat(e02Scope.runs()).isEqualTo(1);
            assertThat(calls.get()).isBetween(1, maximumCalls);
        } finally {
            if (preview != null && preview.isAlive()) {
                preview.destroy();
                if (!preview.waitFor(3, TimeUnit.SECONDS)) preview.destroyForcibly();
            }
            Files.writeString(batch.resolve("page-proxy-exit.json"),
                    "{\"proxyPort\":" + proxyPort + ",\"exited\":" + (preview == null || !preview.isAlive())
                            + ",\"browserExecutedByHost\":false}", StandardOpenOption.CREATE_NEW);
        }
    }

    private static void assembly(RuleAgentSimulatedChainTempDbHttpTest.User a, RuleAgentSimulatedChainTempDbHttpTest.User b) throws Exception {
        for (JsonNode c : plan.path("cases")) {
            String key = UUID.randomUUID().toString();
            var body = Map.of("question", c.path("question").asText(), "clientRequestId", key);
            assertThat(a.post("/tasks", body, false).statusCode()).isEqualTo(403);
            assertThat(a.get("/tasks/requests/CREATE/" + key).statusCode()).isEqualTo(404);
            var accepted = a.post("/tasks", body, true);
            assertThat(accepted.statusCode()).isEqualTo(202);
            JsonNode value = JSON.readTree(accepted.body()); String task = value.path("taskId").asText();
            assertThat(JSON.readTree(a.post("/tasks", body, true).body()).path("duplicate").asBoolean()).isTrue();
            assertThat(a.post("/tasks", Map.of("question", "改问", "clientRequestId", key), true).statusCode()).isEqualTo(409);
            assertThat(b.get("/tasks/" + task).statusCode()).isEqualTo(404);
            assertThat(b.get("/tasks/requests/CREATE/" + key).statusCode()).isEqualTo(404);
            assertThat(a.get("/tasks/" + task + "/originals/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
            outcomes.add(JSON.createObjectNode().put("caseId", c.path("id").asText()).put("httpAccepted", true).put("runnerExecuted", false).put("scopeAndBudget", "UNIT_TESTED_SEPARATELY"));
        }
        assertThat(queue.size()).isEqualTo(plan.path("cases").size());
        assertThat(factory.remainingCalls()).isEqualTo(maximumCalls);
    }

    private static void executeCases(RuleAgentSimulatedChainTempDbHttpTest.User a, RuleAgentSimulatedChainTempDbHttpTest.User b) throws Exception {
        for (JsonNode c : plan.path("cases")) {
            if (stopped) throw new IllegalStateException("批次已停止");
            var response = a.post("/tasks", Map.of("question", c.path("question").asText(), "clientRequestId", UUID.randomUUID()), true);
            assertThat(response.statusCode()).isEqualTo(202);
            String task = JSON.readTree(response.body()).path("taskId").asText();
            JsonNode snapshot = await(a, task);
            recordOutcome(c.path("id").asText(), snapshot);
            if (c.has("reply") && snapshot.path("taskStatus").asText().equals("WAITING_USER")) {
                Map<String, Object> continuation = Map.of("message", c.path("reply").asText(), "continuationToken", snapshot.path("continuationToken").asText(), "expectedVersion", snapshot.path("version").asLong(), "clientRequestId", UUID.randomUUID());
                assertThat(b.post("/tasks/" + task + "/messages", continuation, true).statusCode()).isEqualTo(404);
                var stale = new HashMap<>(continuation); stale.put("expectedVersion", snapshot.path("version").asLong() - 1);
                int before = calls.get();
                assertThat(a.post("/tasks/" + task + "/messages", stale, true).statusCode()).isEqualTo(409);
                assertThat(calls.get()).isEqualTo(before);
                assertThat(a.post("/tasks/" + task + "/messages", continuation, true).statusCode()).isEqualTo(202);
                snapshot = await(a, task); recordOutcome(c.path("id").asText() + "-PUBLIC", snapshot);
            }
            assertThat(b.get("/tasks/" + task).statusCode()).isEqualTo(404);
            for (JsonNode evidence : snapshot.path("result").path("evidence")) {
                String path = "/tasks/" + task + "/originals/" + evidence.path("originalToken").asText();
                assertThat(b.get(path).statusCode()).isEqualTo(404);
                var original = a.get(path); assertThat(original.statusCode()).isEqualTo(200);
                JsonNode view = JSON.readTree(original.body());
                assertThat(view.path("contextText").asText()).contains(evidence.path("quote").asText());
                journal("originals.jsonl", JSON.createObjectNode().put("caseId", c.path("id").asText()).put("status", 200).set("original", redact(view)));
            }
        }
    }
    private static JsonNode await(RuleAgentSimulatedChainTempDbHttpTest.User user, String task) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            var response = user.get("/tasks/" + task); assertThat(response.statusCode()).isEqualTo(200);
            JsonNode snapshot = JSON.readTree(response.body());
            if (snapshot.path("terminated").asBoolean()) return snapshot;
            Thread.sleep(100);
        }
        throw new IllegalStateException("任务等待超时，停止不重试");
    }
    private static void recordOutcome(String id, JsonNode value) throws Exception {
        ObjectNode item = JSON.createObjectNode().put("caseId", id).put("safetyVerdict", "HUMAN_REVIEW_REQUIRED").put("productVerdict", "HUMAN_REVIEW_REQUIRED");
        item.set("snapshot", redact(value)); outcomes.add(item); journal("task-results.jsonl", item);
    }
    static JsonNode redact(JsonNode value) {
        if (value.isObject()) { ObjectNode safe = JSON.createObjectNode(); value.fields().forEachRemaining(e -> { if (!Set.of("continuationToken", "originalToken", "sessionId", "actorId", "password", "token").contains(e.getKey())) safe.set(e.getKey(), redact(e.getValue())); }); return safe; }
        if (value.isArray()) { ArrayNode safe = JSON.createArrayNode(); value.forEach(e -> safe.add(redact(e))); return safe; }
        return value;
    }
    private static synchronized void audit(RuleAgentProvider.Trace trace) {
        if (trace.phase() == RuleAgentProvider.Phase.BEGIN) {
            // 预算/审包必须先于任何网络外发；这里失败必须抛出以阻断发送。
            try {
                if (!live || stopped || calls.get() >= maximumCalls) throw new IllegalStateException("批次额度不可用");
                RuleAgentThreeQuestionPackage.tariff(plan, Clock.systemUTC());
                RuleAgentThreeQuestionPackage.verify(root, assets, batch, hash);
                BigDecimal next = reserved.add(RuleAgentThreeQuestionPackage.perCallReserve(plan));
                if (next.compareTo(new BigDecimal(hardBudgetRmb)) > 0) throw new IllegalStateException("硬预算不足");
                reserved = next; calls.incrementAndGet();
            } catch (RuntimeException failure) { stopped = true; failScope(); throw failure; }
            catch (Exception failure) { stopped = true; failScope(); throw new IllegalStateException("审包失败，禁止外发"); }
            // 计数已落账才写trace；journal失败仍要阻止后续外发。
            try { journal("provider-traces.jsonl", JSON.valueToTree(trace)); }
            catch (RuntimeException failure) { stopped = true; failScope(); throw failure; }
            catch (Exception failure) { stopped = true; failScope(); throw new IllegalStateException(failure); }
            return;
        }
        if (trace.phase() != RuleAgentProvider.Phase.FINISHED) {
            try { journal("provider-traces.jsonl", JSON.valueToTree(trace)); }
            catch (Exception failure) { stopped = true; failScope(); throw new IllegalStateException(failure); }
            return;
        }
        // FINISHED：先落盘真实trace，再分别处理“供应商已给出失败原因”和“本宿主的usage守卫”。
        try { journal("provider-traces.jsonl", JSON.valueToTree(trace)); }
        catch (Exception failure) { stopped = true; failScope(); throw new IllegalStateException(failure); }
        // 供应商失败是它自己的原因码，必须原样交给Provider抛出；这里抛异常会被包成AUDIT_UNAVAILABLE。
        if (trace.reason() != null) { stopped = true; failScope(); return; }
        // usage缺失/越界是本宿主的守卫，不能冒充供应商reason；保留全额预留并阻断成功发布。
        if (trace.inputTokens() == null || trace.outputTokens() == null || trace.inputTokens() > 1000000 || trace.outputTokens() > 1034) {
            stopped = true; failScope(); throw new IllegalStateException("HOST_USAGE_INVALID：用量未知或越界，保留全额预留并停止");
        }
    }
    private static synchronized void exchange(RuleAgentProvider.Exchange exchange) {
        try {
            if (exchange.direction() == RuleAgentProvider.Direction.REQUEST) {
                JsonNode wire = JSON.readTree(exchange.body());
                var fields = new HashSet<String>(); wire.fieldNames().forEachRemaining(fields::add);
                if (exchange.redacted() || !fields.equals(Set.of("model", "stream", "enable_thinking", "max_completion_tokens", "response_format", "messages"))
                        || stopped || !RuleAgentProvider.MODEL.equals(wire.path("model").asText()) || wire.path("stream").asBoolean(true) || wire.path("enable_thinking").asBoolean(true) || wire.path("max_completion_tokens").asInt() != 1024 || exchange.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) throw new IllegalStateException("实际外发信封不匹配");
                JsonNode messages = wire.path("messages");
                if (!messages.isArray() || messages.size() != 2 || !"system".equals(messages.get(0).path("role").asText()) || !"user".equals(messages.get(1).path("role").asText())) throw new IllegalStateException("消息信封不匹配");
                for (JsonNode message : messages) if (message.size() != 2 || !message.path("content").isTextual()) throw new IllegalStateException("拒绝额外消息字段或非纯文本");
                String format = wire.path("response_format").path("type").asText();
                if (!(exchange.purpose() == RuleAgentProvider.Purpose.DECISION ? "json_schema" : "json_object").equals(format)) throw new IllegalStateException("决策/核验格式不匹配");
            }
            journal("provider-exchanges.jsonl", JSON.valueToTree(exchange));
        } catch (Exception failure) { stopped = true; failScope(); throw new IllegalStateException("正文审计失败，禁止外发"); }
    }
    private static synchronized void journal(String name, JsonNode value) throws Exception {
        Files.writeString(batch.resolve(name), JSON.writeValueAsString(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
