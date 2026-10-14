package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import com.zhku.knowledge.security.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import com.zhku.agent.agent.runtime.*;

/**
 * T06 受控启动入口验收：模式准入 + 真实 Spring 上下文装配 + 显式模拟执行下的 HTTP 生命周期。
 *
 * <p>为什么必须真启动应用而不是只做 bean 定义扫描：本项目已两次出现"测试全绿、真机启动才炸"的装配期缺陷
 * （{@code @Profile}/{@code @ConditionalOnProperty} 让整组 bean 在扫描期被排除）。因此这里用真实
 * {@link SpringApplication} 启动，失败用例也走真实刷新路径。
 *
 * <p>边界：身份仓储是显式测试替身，执行器是显式模拟；零数据库连接、零供应商调用。本测试不证明真实临时库
 * 身份安装，也不证明在线可用性。
 */
@Timeout(120)
class RuleAgentControlledLaunchTest {
    static final ObjectMapper JSON = new ObjectMapper();

    /* ---------- 准入校验：最小上下文，只装配受控启动配置本身 ---------- */

    @SpringBootConfiguration(proxyBeanMethods = false)
    @Import(RuleAgentControlledLaunchConfiguration.class)
    static class MinimalApp {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    /** 取出异常链里最贴近业务的那条消息，避免把 Spring 包装层当结论。 */
    private static Throwable launchFailure(Map<String, Object> properties) {
        var app = new SpringApplication(MinimalApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(properties);
        return catchThrowable(() -> app.run());
    }

    @Test void launchIsDefaultOffWhenSwitchAbsent() {
        // 只打开 HTTP 开关、不打开 launch 开关：不应产生任何执行工厂，也不应启动失败。
        var app = new SpringApplication(MinimalApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("zhku.rule-agent-http.enabled", "true", "logging.level.root", "ERROR"));
        try (var context = app.run()) {
            assertThat(context.getBeanNamesForType(RuleAgentTaskStore.Factory.class)).isEmpty();
        }
    }

    @Test void launchRejectsMissingOrUnknownExecutorMode() {
        assertThat(launchFailure(Map.of("zhku.rule-agent-http.enabled", "true", "zhku.rule-agent-launch.enabled", "true")))
                .as("缺省模式必须拒绝启动")
                .hasStackTraceContaining(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY);
        assertThat(launchFailure(Map.of("zhku.rule-agent-http.enabled", "true", "zhku.rule-agent-launch.enabled", "true",
                RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "simulated")))
                .as("未知/小写模式必须拒绝启动，不能当作默认值")
                .hasStackTraceContaining("不接受缺省或未知取值");
    }

    @Test void simulatedModeMustNotCoexistWithOnlineAssembly() {
        assertThat(launchFailure(Map.of("zhku.rule-agent-http.enabled", "true", "zhku.rule-agent-launch.enabled", "true",
                RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, RuleAgentControlledLaunchConfiguration.SIMULATED,
                "zhku.rule-agent-http.online-enabled", "true")))
                .as("模拟执行不得与在线装配共存")
                .hasStackTraceContaining("不得同时开启在线装配");
    }

    @Test void simulatedModeRequiresExplicitHostExecutorAndNeverSubstitutesOnline() {
        Throwable failure = launchFailure(Map.of("zhku.rule-agent-http.enabled", "true", "zhku.rule-agent-launch.enabled", "true",
                RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, RuleAgentControlledLaunchConfiguration.SIMULATED));
        assertThat(failure)
                .as("模拟模式缺宿主执行器时必须启动失败，而不是退回在线或静默可用")
                .hasStackTraceContaining(RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN);
    }

    @Test void onlineModeWithoutRealProviderSettingsFailsFast() {
        // 在线模式：装配期就要求真实供应商设置与额度，本地配置值不能代替外发许可。
        assertThat(launchFailure(Map.of("zhku.rule-agent-http.enabled", "true", "zhku.rule-agent-launch.enabled", "true",
                RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, RuleAgentControlledLaunchConfiguration.ONLINE)))
                .as("缺少在线装配时必须失败，不能启用任何替代执行器")
                .hasStackTraceContaining("online-enabled");
    }

    /* ---------- 真实装配 + 显式模拟执行的 HTTP 生命周期 ---------- */

    static final String P = "/api/zhku/controlled-preview/v2";
    static final TrustedActor A = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
    static final AtomicInteger runs = new AtomicInteger();
    static final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    static final AtomicBoolean originalCurrent = new AtomicBoolean(true);
    static final UUID TOKEN = UUID.randomUUID(), SOURCE = UUID.randomUUID(), CHUNK = UUID.randomUUID();
    static final String QUOTE = "合成条款：仅用于受控启动装配验收。";
    static final String HASH = sha(QUOTE);
    static String sha(String value) { try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class, RuleCheckAuthController.class,
            RuleAgentHttpConfiguration.class, RuleAgentControlledLaunchConfiguration.class})
    static class FullApp {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PreviewIdentityRepository identities() {
            var repo = mock(PreviewIdentityRepository.class);
            when(repo.authenticate(eq("student-a"), any(char[].class))).thenReturn(Optional.of(new PreviewPrincipal(A)));
            return repo;
        }
        /** 显式模拟执行器：确定性、零供应商、零数据库；仅在 SIMULATED 模式下被采纳。 */
        @Bean(name = RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN)
        RuleAgentTaskStore.Factory ruleAgentSimulatedExecutor(RuleAgentTaskToolsRegistry registry) {
            return (owner, task) -> new RuleAgentTaskStore.Work() {
                public boolean revalidate(ResultEnvelope old) {
                    return old.evidence().stream().allMatch(e -> registry.read(owner, task, e.originalToken()).isPresent());
                }
                public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                    runs.incrementAndGet();
                    context.start();
                    boolean waiting = input.question().equals("澄清") && input.userConditions().isEmpty();
                    // 等待澄清必须同时带 CLARIFICATION issue 与一项必要条件，不能只给空 coverage。
                    var items = List.of(new Subquestion("q1", waiting ? Coverage.NONE : Coverage.COMPLETE,
                            waiting ? List.of() : List.of(new Claim("answer", "fixture-fact", "合成确认内容", Map.of(), ConditionSource.USER_DECLARED)),
                            waiting ? List.of(new Issue("condition", IssueKind.CLARIFICATION, ReasonCode.MISSING_CONDITION, "请确认课程类别", "补充必要条件")) : List.of(),
                            waiting ? List.of(new Clarification("courseType", "课程类别？", List.of("PUBLIC"))) : List.of()));
                    var tools = registry.open(owner, task);
                    List<Evidence> evidence = List.of();
                    if (!waiting) {
                        var found = tools.search(owner, task, context.runId(), 1, new Search("q1", "合成条款"));
                        evidence = tools.read(owner, task, context.runId(), 2, new Read("q1", List.of(found.candidates().getFirst().handle()))).observation().evidence();
                    }
                    var relations = waiting ? List.<EvidenceRelation>of() : List.of(new EvidenceRelation(evidence.getFirst().id(), "q1", EvidenceRole.CLAIM, "answer"));
                    var result = new ResultEnvelope(input.version(), context.runId(), "launch-fixture-v1", "2025.9.2",
                            items.getFirst().coverage(), items, evidence, relations);
                    var state = waiting ? AgentRunState.INCOMPLETE : AgentRunState.SUCCEEDED;
                    context.finish(state); context.cleanupOnce(() -> {}); context.signalTermination(); context.publishTermination();
                    return new RuleAgentRunner.Outcome(new TaskSnapshot(3, task, UUID.randomUUID(), context.runId(), input.version(),
                            waiting ? TaskStatus.WAITING_USER : TaskStatus.RESOLVED, state, true, result,
                            waiting ? UUID.randomUUID() : null,
                            // 模拟执行不得声称在线成功：标 ENGINEERING_REPLAY，模型尝试0且未收到供应商响应。
                            // 合同校验要求该模式下 replayCoverage 不得为 NOT_APPLICABLE，故标 HIT（仅表示本次工程运行被覆盖）。
                            new Execution(Mode.ENGINEERING_REPLAY, 0, 0, false, AnswerSource.VERIFIED_RULE_ONLY, ReplayCoverage.HIT)), List.of());
                }
            };
        }
        @Bean(name = "ruleAgentHttpExecutor") java.util.concurrent.Executor executor() { return queue::add; }
        @Bean RuleAgentTaskToolsRegistry registry(Clock clock) {
            return new RuleAgentTaskToolsRegistry((owner, task) -> {
                var binding = new RuleCheckRuleTemplate.EvidenceBinding(SOURCE, CHUNK, "2025.9.2", HASH, 0, QUOTE.length(), QUOTE);
                var fact = new RuleCheckRuleTemplate.Fact("fixture-fact", "合成确认内容", true, Map.of(), List.of(binding));
                var rule = new RuleCheckRuleTemplate("fixture", "2025.9.2", RuleCheckRuleTemplate.ReviewState.APPROVED, "SYNTHETIC_TEST", java.time.LocalDate.of(2026, 10, 14), List.of(fact), List.of(), List.of(), List.of());
                var candidate = new com.zhku.knowledge.retrieval.RetrievedChunk(CHUNK, QUOTE, "synthetic", "synthetic", 1, "2025.9.2", HASH, SOURCE);
                return new RuleAgentEvidenceTools(owner, task, new RuleCheckRuntimeSettings("p", "c", "s", "2025.9.2", 20, 5),
                        () -> List.of(new RuleCheckApplicationService.CatalogEntry(rule, "合成范围")), (q, n) -> List.of(candidate),
                        (s, c) -> Optional.of(new RuleCheckEvidenceSnapshot("current", SOURCE, CHUNK, "2025.9.2", HASH, QUOTE, originalCurrent.get())), r -> {}, clock);
            }, clock, RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
        }
        @Bean RuleCheckOriginalEvidenceService locator() {
            return new RuleCheckOriginalEvidenceService((s, c) -> Optional.of(new RuleCheckLocatedEvidence(new RuleCheckEvidenceSnapshot("current", SOURCE, CHUNK, "2025.9.2", HASH, QUOTE, originalCurrent.get()), "合成手册", null, "合成条款", null, null)), "2025.9.2", Set.of());
        }
    }

    @Test void controlledLaunchAssemblesIdentityTaskStoreFactoryToolsAndOriginalsOverHttp() throws Exception {
        runs.set(0); queue.clear(); originalCurrent.set(true);
        // 本机存在 SERVER__PORT 环境变量（被 Spring 宽松绑定解析为 server.port），其优先级高于
        // setDefaultProperties，会导致应用去绑定宿主已占用的端口。系统属性优先级高于环境变量，用它显式固定随机端口。
        String previousPort = System.getProperty("server.port");
        System.setProperty("server.port", "0");
        try {
        var app = new SpringApplication(FullApp.class);
        app.setAdditionalProfiles("zhku-rule-check-preview");
        var settings = new HashMap<String, Object>();
        settings.put("server.port", "0"); settings.put("server.address", "127.0.0.1"); settings.put("server.servlet.context-path", "/api");
        settings.put("server.servlet.session.timeout", "15m"); settings.put("server.servlet.session.persistent", "false"); settings.put("server.servlet.session.tracking-modes", "cookie");
        settings.put("server.servlet.session.cookie.name", "ZHKU_RULE_CHECK_SESSION"); settings.put("server.servlet.session.cookie.path", P);
        settings.put("server.servlet.session.cookie.http-only", "true"); settings.put("server.servlet.session.cookie.secure", "false"); settings.put("server.servlet.session.cookie.same-site", "strict");
        settings.put("zhku.rule-check-preview.enabled", "true"); settings.put("zhku.rule-agent-http.enabled", "true");
        settings.put("zhku.rule-agent-launch.enabled", "true");
        settings.put(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, RuleAgentControlledLaunchConfiguration.SIMULATED);
        settings.put("logging.level.root", "ERROR"); settings.put("spring.config.location", "optional:classpath:/http-fixture-no-config.yml");
        // 与既有HTTP验收一致：本测试用显式队列执行器覆盖默认线程池，保证调度确定、无真实并发线程。
        settings.put("spring.main.allow-bean-definition-overriding", "true");
        app.setDefaultProperties(settings);
        int port;
        try (var context = (ServletWebServerApplicationContext) app.run()) {
            port = context.getWebServer().getPort();
            // 装配自检：身份仓储、TaskStore、工厂、资料工具索引、原文定位服务都在同一上下文里。
            assertThat(context.getBean(RuleAgentTaskStore.class)).isNotNull();
            assertThat(context.getBean(RuleAgentTaskStore.Factory.class)).isNotNull();
            assertThat(context.getBean(RuleAgentTaskToolsRegistry.class)).isNotNull();
            assertThat(context.getBean(RuleCheckOriginalEvidenceService.class)).isNotNull();
            var a = new User(port);
            assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(401);
            a.login("student-a");
            assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(200);
            String key = UUID.randomUUID().toString();
            var accepted = a.post("/tasks", Map.of("question", "问题", "clientRequestId", key), true);
            assertThat(accepted.statusCode()).isEqualTo(202);
            var ticket = JSON.readTree(accepted.body());
            String task = ticket.path("taskId").asText();
            assertThat(JSON.readTree(a.post("/tasks", Map.of("question", "问题", "clientRequestId", key), true).body()).path("duplicate").asBoolean()).isTrue();
            assertThat(a.post("/tasks", Map.of("question", "改了问题", "clientRequestId", key), true).statusCode()).isEqualTo(409);
            assertThat(runs.get()).isZero(); queue.remove().run(); assertThat(runs.get()).isEqualTo(1);
            var resolved = JSON.readTree(a.get("/tasks/" + task).body());
            assertThat(resolved.path("taskStatus").asText()).isEqualTo("RESOLVED");
            String originalToken = resolved.path("result").path("evidence").get(0).path("originalToken").asText();
            assertThat(a.get("/tasks/" + task + "/originals/" + originalToken).statusCode()).isEqualTo(200);
            originalCurrent.set(false);
            assertThat(a.get("/tasks/" + task + "/originals/" + originalToken).statusCode()).isEqualTo(409);
            originalCurrent.set(true);
            // 身份失效：重新登录后同一会话不再拥有该任务归属。
            a.logout(); a.login("student-a");
            assertThat(a.get("/tasks/" + task).statusCode()).isEqualTo(404);
            assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
            assertThat(queue).isEmpty();
        }
        try (var socket = new java.net.Socket()) {
            assertThatThrownBy(() -> socket.connect(new InetSocketAddress("127.0.0.1", port), 500)).isInstanceOf(java.io.IOException.class);
        }
        String directory = System.getProperty("zhku.rulecheck.evidenceDir", "");
        if (!directory.isBlank()) JSON.writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(directory, "controlled-launch.json").toFile(),
                Map.of("schemaVersion", 3, "executionMode", "CONTROLLED_LAUNCH_SIMULATED_EXECUTOR", "providerCalls", 0, "databaseCalls", 0, "resourcesExited", true));
        } finally {
            if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", previousPort);
        }
    }

    static class User {
        final HttpClient client; final int port; JsonNode csrf;
        User(int port) { this.port = port; var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL); client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(2)).build(); }
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, false); }
        HttpResponse<String> post(String path, Object body, boolean token) throws Exception { return send("POST", path, JSON.writeValueAsString(body), token); }
        HttpResponse<String> send(String method, String path, String body, boolean token) throws Exception {
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + P + path)).timeout(Duration.ofSeconds(3)).header("Accept", "application/json");
            if (body != null) req.header("Content-Type", "application/json; charset=UTF-8");
            if (token) req.header(csrf.path("headerName").asText(), csrf.path("token").asText());
            return client.send(req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        void login(String login) throws Exception {
            csrf = JSON.readTree(get("/auth/csrf").body());
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + P + "/auth/login")).header("Content-Type", "application/x-www-form-urlencoded")
                    .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + login + "&password=synthetic-only-password")).build();
            assertThat(client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            csrf = JSON.readTree(get("/auth/csrf").body());
        }
        void logout() throws Exception { assertThat(send("POST", "/auth/logout", null, true).statusCode()).isEqualTo(204); }
    }
}
