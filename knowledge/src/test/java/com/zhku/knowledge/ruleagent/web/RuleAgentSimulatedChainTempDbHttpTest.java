package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import com.zhku.knowledge.security.*;
import com.zhku.agent.agent.runtime.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/**
 * T06 真实身份／真实已审核资料的 SIMULATED 工程模式 HTTP 整链验收。
 *
 * <p>为什么这样设计：
 * <ul>
 *   <li><b>身份与资料都不用替身。</b>身份仓储是 {@link PreviewIdentityRepository} 直连专用临时库，
 *       检索／读取／原文走 {@link RuleAgentReviewedToolsConfiguration} 装配的真实已审核资料组件；
 *       只有"模型决策"这一段是宿主显式提供的模拟执行器，并且它输出标为
 *       {@link Mode#ENGINEERING_REPLAY}、模型尝试 0，不能被读成在线能力。</li>
 *   <li><b>资料根必须是已核对的只读根目录。</b>{@code zhku.t06.root} 指向主工作目录；用 worktree 当根会被
 *       批准指纹校验拒绝，这条拒绝本身就是"指纹未被放宽"的反证，不能用"换根绕过"来让测试变绿。</li>
 *   <li><b>目录实例必须活到测试结束。</b>{@link Reviewed2025ControlledCatalogue#close()} 会卸载本次安装数据，
 *       因此安装后不能提前关闭，否则"查不到资料"会被误读成装配失败。</li>
 *   <li><b>端口显式取随机值。</b>本机存在 {@code SERVER__PORT} 环境变量且优先级高于默认属性，会覆盖成宿
 *       主已占用的端口；系统属性优先级更高，用它固定随机端口并证明实际绑定端口不是注入值。</li>
 * </ul>
 *
 * <p>边界：真实供应商调用为 0、密钥未读取；取消后的资源结束、浏览器验收、代理转发不在本类范围。
 */
@Timeout(240)
class RuleAgentSimulatedChainTempDbHttpTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String P = "/api/zhku/controlled-preview/v2";
    static final Pattern ISOLATED = Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$");

    static final AtomicInteger runs = new AtomicInteger();
    /** 仅用于把模拟执行器内部的真实组件异常暴露成可读断言，不参与通过判据。 */
    static final AtomicReference<Throwable> RUN_FAILURE = new AtomicReference<>();
    static final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    static final List<Map<String, Object>> exchanges = Collections.synchronizedList(new ArrayList<>());

    static final String CORRECT = "t06-chain-correct-pass";
    static final String WRONG = "t06-chain-wrong-pass";
    static final String ASK_QUESTION = "需要澄清";
    /** 取消/终态矩阵用的提问：同一执行器按问题文本走不同分支，输出仍标 ENGINEERING_REPLAY。 */
    static final String CANCEL_QUESTION = "直接查证";
    static final String PARTIAL_QUESTION = "部分覆盖";
    static final String GAP_QUESTION = "检索未命中";
    static final String FAIL_QUESTION = "处理失败";
    /** 保证检索不命中的提问：走真实检索链路证明"未命中"也是真实搜索结果，而非跳过检索。 */
    static final String GAP_QUERY = "不存在的检索词-zzz-确保未命中";

    static DriverManagerDataSource db;
    static Reviewed2025ControlledCatalogue catalogue;
    static Path root;
    static String query;
    static UUID userA;
    static String evidenceDirectory = "";

    @BeforeAll static void installReviewedAssetsAndIdentities() throws Exception {
        String url = System.getProperty("zhku.db.url", System.getenv("ZHKU_DB_URL"));
        Assumptions.assumeTrue(url != null, "未提供 zhku.db.url / ZHKU_DB_URL：专用临时库未配置，本项未验证");
        var matcher = ISOLATED.matcher(url);
        assertThat(matcher.matches()).as("必须使用专用回环临时库").isTrue();
        assertThat(Integer.parseInt(matcher.group(1))).as("拒绝原演示库端口").isNotEqualTo(54329);
        String password = System.getenv("ZHKU_DB_PASSWORD");
        Assumptions.assumeTrue(password != null && !password.isBlank(), "缺少本次数据库凭据：本项未验证");
        evidenceDirectory = System.getProperty("zhku.rulecheck.evidenceDir", "");

        db = new DriverManagerDataSource(url, "zhkuagent", password);
        var jdbc = new JdbcTemplate(db);
        // 先证明空库再迁移：顺序颠倒会让"空库约束"被自己的写入破坏。
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema NOT IN ('pg_catalog','information_schema')", Integer.class)).as("临时库必须为空").isZero();
        ZhkuFlywayTestSupport.migrate(url, "zhkuagent", password);

        String configuredRoot = System.getProperty("zhku.t06.root");
        Assumptions.assumeTrue(configuredRoot != null, "未提供 zhku.t06.root：已审核资料根未配置，本项未验证");
        root = Path.of(configuredRoot).toAbsolutePath().normalize();
        catalogue = new Reviewed2025ControlledCatalogue(root);
        catalogue.installOnIsolatedDatabase(db);
        assertThat(catalogue.settings().referenceRevision()).isEqualTo("2025.9.2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunk", Integer.class)).as("必须装有已审核片段").isPositive();

        // 检索词取自真实已审核绑定正文：既保证命中真实片段，也避免用合成文本冒充资料证据。
        query = catalogue.reviewedCatalogue(root).stream()
                .flatMap(entry -> entry.template().facts().stream())
                .flatMap(fact -> fact.evidenceBindings().stream())
                .map(RuleCheckRuleTemplate.EvidenceBinding::quote)
                .filter(text -> text != null && !text.isBlank())
                .findFirst().orElseThrow(() -> new IllegalStateException("已审核资料缺少可用绑定正文"));
        assertThat(List.of(CORRECT, WRONG)).allSatisfy(value -> assertThat(value.length()).isBetween(16, 64));

        var encoder = new BCryptPasswordEncoder();
        userA = UUID.randomUUID();
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled) VALUES (?,?,?,'USER',true)",
                userA, "t06-chain-a", encoder.encode(CORRECT));
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled) VALUES (?,?,?,'USER',true)",
                UUID.randomUUID(), "t06-chain-b", encoder.encode(CORRECT));
        jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled) VALUES (?,?,?,'MAINTAINER',true)",
                UUID.randomUUID(), "t06-chain-admin", encoder.encode(CORRECT));
    }

    @AfterAll static void uninstall() throws Exception {
        // 关闭即卸载本次安装数据；不作为"安装失败"的判据。
        if (catalogue != null) catalogue.close();
    }

    /** 受控启动的公共设置；端口/绑定地址由调用方覆盖，便于同一个装配分别跑直连与代理两种验收。 */
    private static Map<String, Object> baseSettings() {
        var settings = new HashMap<String, Object>();
        settings.put("server.port", "0"); settings.put("server.address", "127.0.0.1"); settings.put("server.servlet.context-path", "/api");
        settings.put("server.servlet.session.timeout", "15m"); settings.put("server.servlet.session.persistent", "false");
        settings.put("server.servlet.session.tracking-modes", "cookie");
        settings.put("server.servlet.session.cookie.name", "ZHKU_RULE_CHECK_SESSION"); settings.put("server.servlet.session.cookie.path", P);
        settings.put("server.servlet.session.cookie.http-only", "true"); settings.put("server.servlet.session.cookie.secure", "false");
        settings.put("server.servlet.session.cookie.same-site", "strict");
        settings.put("zhku.rule-check-preview.enabled", "true"); settings.put("zhku.rule-agent-http.enabled", "true");
        settings.put("zhku.rule-agent-launch.enabled", "true"); settings.put("zhku.rule-agent-http.reviewed-tools-enabled", "true");
        settings.put(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, RuleAgentControlledLaunchConfiguration.SIMULATED);
        settings.put("spring.flyway.enabled", "false"); settings.put("logging.level.root", "ERROR");
        settings.put("spring.config.location", "optional:classpath:/http-fixture-no-config.yml");
        settings.put("spring.main.allow-bean-definition-overriding", "true");
        return settings;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class, RuleCheckAuthController.class,
            RuleAgentHttpConfiguration.class, RuleAgentControlledLaunchConfiguration.class,
            RuleAgentReviewedToolsConfiguration.class})
    static class App {
        @Bean Clock clock() { return Clock.systemUTC(); }
        /** 必须是专用直连数据源：受控资料组件会逐个核对库名与端口，连接池或代理都会被拒。 */
        @Bean javax.sql.DataSource dataSource() { return db; }
        /**
         * 安装是任务级作用域：{@code close()} 会卸载本次安装的数据。因此显式禁止 Spring 在上下文关闭时
         * 调用销毁方法——否则本类第一个应用的关闭就会把资料卸载掉，第二个应用（以及后续断言）将查无数据。
         * 数据生命周期由 {@code @AfterAll} 统一结束。
         */
        @Bean(destroyMethod = "") Reviewed2025ControlledCatalogue catalogue() { return catalogue; }
        @Bean RuleAgentReviewedToolsConfiguration.Assets assets() { return new RuleAgentReviewedToolsConfiguration.Assets(root); }
        @Bean(destroyMethod = "close") PreviewIdentityRepository identities() { return new PreviewIdentityRepository(db); }
        /** 在线装配被 SIMULATED 准入排除，工具索引由本宿主显式装配，来源仍是真实已审核组件。 */
        @Bean(destroyMethod = "close") RuleAgentTaskToolsRegistry registry(RuleAgentTaskToolsRegistry.ToolFactory tools, Clock clock) {
            return new RuleAgentTaskToolsRegistry(tools, clock, RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
        }
        @Bean(name = RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN)
        RuleAgentTaskStore.Factory ruleAgentSimulatedExecutor(RuleAgentTaskToolsRegistry registry) {
            return (owner, task) -> new RuleAgentTaskStore.Work() {
                public boolean revalidate(ResultEnvelope old) {
                    return old.evidence().stream().allMatch(e -> registry.read(owner, task, e.originalToken()).isPresent());
                }
                public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                    runs.incrementAndGet();
                    context.start();
                    // 模拟执行器内部的真实组件异常必须可见：否则任务会被统一记成 FAILED，掩盖真实卡点。
                    try { return execute(context, input); }
                    catch (RuntimeException failure) { RUN_FAILURE.set(failure); throw failure; }
                }
                private RuleAgentRunner.Outcome execute(AgentRunContext context, RuleAgentRunner.Input input) {
                    boolean waiting = ASK_QUESTION.equals(input.question()) && input.userConditions().isEmpty();
                    // 终态矩阵分支：部分覆盖（有真实证据但不完整）、检索未命中（走真实检索证明没命中）、
                    // 处理失败（不检索，直接失败）。取消不需要分支——取消发生在执行之前。
                    boolean partial = PARTIAL_QUESTION.equals(input.question());
                    boolean gap = GAP_QUESTION.equals(input.question());
                    boolean failed = FAIL_QUESTION.equals(input.question());
                    var tools = registry.open(owner, task);
                    List<Evidence> evidence = List.of();
                    int steps = 0;
                    if (!waiting && !failed) {
                        var found = tools.search(owner, task, context.runId(), 1, new Search("q1", gap ? GAP_QUERY : query));
                        steps = 1;
                        if (found.status() == ObservationStatus.SUCCEEDED && !found.candidates().isEmpty()) {
                            var read = tools.read(owner, task, context.runId(), 2,
                                    new Read("q1", List.of(found.candidates().getFirst().handle())));
                            steps = 2;
                            if (read.observation().status() == ObservationStatus.SUCCEEDED) evidence = read.observation().evidence();
                        }
                    }
                    Issue issue = waiting
                            ? new Issue("condition", IssueKind.CLARIFICATION, ReasonCode.MISSING_CONDITION, "请确认课程类别", "补充必要条件")
                            : failed ? new Issue("failure", IssueKind.PROCESSING_FAILURE, ReasonCode.MODEL_UNAVAILABLE, "模型处理失败", "稍后重新查证")
                            : gap ? new Issue("gap", IssueKind.DATA_GAP, ReasonCode.RETRIEVAL_NO_MATCH, "未检索到已审核条款", "更换关键词或核对当期通知")
                            : partial ? new Issue("partial", IssueKind.PROCESSING_FAILURE, ReasonCode.ANSWER_COVERAGE_INCOMPLETE, "覆盖不完整", "补充说明后重新查证")
                            : null;
                    // 回放覆盖的诚实记账：等待澄清与有证据都是"回放达成了它的产出"（HIT）；
                    // 没有证据时必须标 MISS，而合同要求 MISS 的非 ACTIVE 快照必须显式披露
                    // REPLAY_NOT_COVERED——不能把"回放没覆盖"伪装成"资料没有规定"或静默留空。
                    boolean replayHit = waiting || !evidence.isEmpty();
                    var issues = new ArrayList<Issue>();
                    if (issue != null) issues.add(issue);
                    if (!replayHit) {
                        issues.add(new Issue("replay-miss", IssueKind.REPLAY_MISS, ReasonCode.REPLAY_NOT_COVERED,
                                "本次为工程模拟回放，未产出可核验证据", "以真实查证结果为准"));
                    }
                    Coverage coverage = waiting || failed || gap ? Coverage.NONE : partial ? Coverage.PARTIAL : Coverage.COMPLETE;
                    var items = List.of(new Subquestion("q1", coverage,
                            !waiting && !failed && !gap && !evidence.isEmpty()
                                    ? List.of(new Claim("answer", "chain-fixture", "工程模拟确认内容", Map.of(), ConditionSource.USER_DECLARED)) : List.of(),
                            issues,
                            // 合同要求澄清必须给出至少两个可取值：单选的"澄清"无法被用户区分，也不构成真正的追问。
                            waiting ? List.of(new Clarification("courseType", "课程类别？", List.of("PUBLIC", "PROFESSIONAL"))) : List.of()));
                    var relations = evidence.isEmpty() ? List.<EvidenceRelation>of()
                            : List.of(new EvidenceRelation(evidence.getFirst().id(), "q1", EvidenceRole.CLAIM, "answer"));
                    var result = new ResultEnvelope(input.version(), context.runId(), "chain-fixture-v1", "2025.9.2",
                            coverage, items, evidence, relations);
                    var state = failed ? AgentRunState.FAILED : waiting || gap || partial ? AgentRunState.INCOMPLETE : AgentRunState.SUCCEEDED;
                    context.finish(state); context.cleanupOnce(() -> {}); context.signalTermination(); context.publishTermination();
                    // 模拟执行不得声称在线成功：模型尝试 0、未收到供应商响应；没有证据时不得声称已核验。
                    // 合同硬约束：ENGINEERING_REPLAY 的 replayCoverage 只能是 HIT/MISS，
                    // NOT_APPLICABLE 是 ONLINE 专属——"没有可回放的内容"必须标成 MISS 而不是"不适用"，
                    // 且 MISS 的非 ACTIVE 快照必须带 REPLAY_NOT_COVERED 披露（见上方 issues 组装）。
                    return new RuleAgentRunner.Outcome(new TaskSnapshot(3, task, UUID.randomUUID(), context.runId(), input.version(),
                            waiting ? TaskStatus.WAITING_USER : failed ? TaskStatus.FAILED : TaskStatus.RESOLVED, state, true, result,
                            waiting ? UUID.randomUUID() : null,
                            new Execution(Mode.ENGINEERING_REPLAY, 0, steps, false,
                                    evidence.isEmpty() ? AnswerSource.NONE : AnswerSource.VERIFIED_RULE_ONLY,
                                    replayHit ? ReplayCoverage.HIT : ReplayCoverage.MISS)), List.of());
                }
            };
        }
        @Bean(name = "ruleAgentHttpExecutor") java.util.concurrent.Executor executor() { return queue::add; }
    }

    @Test void realIdentityAndReviewedAssetsOverActualHttpWithCsrfIdempotencyAndIsolation() throws Exception {
        runs.set(0); queue.clear(); exchanges.clear();
        String injectedPort = System.getenv("SERVER__PORT");
        String previousPort = System.getProperty("server.port");
        System.setProperty("server.port", "0");
        int port = -1;
        try {
            var app = new SpringApplication(App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = baseSettings();
            app.setDefaultProperties(settings);
            try (var context = (ServletWebServerApplicationContext) app.run()) {
                port = context.getWebServer().getPort();
                // (1) 绑定端口必须不是被注入的 SERVER__PORT：证明随机端口确实生效。
                assertThat(port).isNotEqualTo(Integer.parseInt(injectedPort == null ? "-1" : injectedPort));
                var anonymous = new User(port);
                var a = new User(port); var b = new User(port); var admin = new User(port);
                assertThat(anonymous.get("/tasks/capabilities").statusCode()).isEqualTo(401);
                // (2) 真实身份：错误口令被拒，正确口令放行；维护者角色不继承 USER。
                assertThat(a.loginStatus("t06-chain-a", WRONG)).isEqualTo(401);
                a.login("t06-chain-a", CORRECT);
                b.login("t06-chain-b", CORRECT);
                admin.login("t06-chain-admin", CORRECT);
                assertThat(admin.get("/tasks/capabilities").statusCode()).isEqualTo(403);
                assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(200);
                // (3) CSRF 缺失必须拒绝，且不带令牌时不得产生任务。
                String askKey = UUID.randomUUID().toString();
                assertThat(a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", askKey), false).statusCode()).isEqualTo(403);
                assertThat(a.get("/tasks/requests/CREATE/" + askKey).statusCode()).isEqualTo(404);
                // (4) 提问 → 受理 → 等待澄清（ASK）。
                var accepted = a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", askKey), true);
                assertThat(accepted.statusCode()).isEqualTo(202);
                assertThat(accepted.headers().firstValue("Cache-Control")).hasValue("no-store");
                String task = JSON.readTree(accepted.body()).path("taskId").asText();
                // (5) 幂等：同键重复受理不产生第二次执行；同键换问题才冲突。
                int beforeDuplicate = runs.get();
                assertThat(JSON.readTree(a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", askKey), true).body())
                        .path("duplicate").asBoolean()).isTrue();
                assertThat(runs.get()).as("重复受理不得增加执行次数").isEqualTo(beforeDuplicate);
                assertThat(a.post("/tasks", Map.of("question", "改了问题", "clientRequestId", askKey), true).statusCode()).isEqualTo(409);
                // (6) 跨主体不可见。
                assertThat(b.get("/tasks/" + task).statusCode()).isEqualTo(404);
                assertThat(b.get("/tasks/requests/CREATE/" + askKey).statusCode()).isEqualTo(404);
                assertThat(runs.get()).isZero(); queue.remove().run(); assertThat(runs.get()).isEqualTo(1);
                var waiting = JSON.readTree(a.get("/tasks/" + task).body());
                if (RUN_FAILURE.get() != null) RUN_FAILURE.get().printStackTrace();
                assertThat(waiting.path("taskStatus").asText())
                        .as("等待澄清必须进入 WAITING_USER；模拟执行器内部异常=%s", RUN_FAILURE.get())
                        .isEqualTo("WAITING_USER");
                long version = waiting.path("version").asLong();
                assertThat(version).isGreaterThan(1L);
                // (7) 旧版本续答明确拒绝。
                var message = new HashMap<String, Object>();
                message.put("message", "PUBLIC"); message.put("continuationToken", waiting.path("continuationToken").asText());
                message.put("expectedVersion", version - 1); message.put("clientRequestId", UUID.randomUUID());
                assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(409);
                // (8) 续答 → 结果（真实资料证据）。
                message.put("expectedVersion", version);
                assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(202);
                queue.remove().run(); assertThat(runs.get()).isEqualTo(2);
                var resolved = JSON.readTree(a.get("/tasks/" + task).body());
                assertThat(resolved.path("taskStatus").asText()).isEqualTo("RESOLVED");
                assertThat(resolved.path("result").path("evidence")).as("必须来自真实已审核片段").isNotEmpty();
                String quote = resolved.path("result").path("evidence").get(0).path("quote").asText();
                String originalToken = resolved.path("result").path("evidence").get(0).path("originalToken").asText();
                // (9) 原文：跨主体 404、随机令牌 404、本人 200 且正文一致。
                assertThat(b.get("/tasks/" + task + "/originals/" + originalToken).statusCode()).isEqualTo(404);
                assertThat(a.get("/tasks/" + task + "/originals/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
                var original = a.get("/tasks/" + task + "/originals/" + originalToken);
                assertThat(original.statusCode()).isEqualTo(200);
                assertThat(original.body()).contains(quote.substring(0, Math.min(8, quote.length())));
                // (10) 身份失效：重新登录后不再拥有上一会话的任务归属。
                a.logout(); a.login("t06-chain-a", CORRECT);
                assertThat(a.get("/tasks/" + task).statusCode()).isEqualTo(404);
                // (11) 退出：无在途任务、无待执行、任务仓储已关闭。
                assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
                assertThat(queue).isEmpty();
            }
            int boundPort = port;
            try (var socket = new java.net.Socket()) {
                assertThatThrownBy(() -> socket.connect(new InetSocketAddress("127.0.0.1", boundPort), 500)).isInstanceOf(java.io.IOException.class);
            }
        } finally {
            if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", previousPort);
        }
        if (!evidenceDirectory.isBlank()) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(evidenceDirectory, "simulated-chain-tempdb.json").toFile(),
                    Map.of("schemaVersion", 3, "executionMode", "SIMULATED_ENGINEERING_REPLAY",
                            "database", "zhkuagent_rulecheck_e2e(temp,一次性容器)", "assetsRevision", catalogue == null ? "" : catalogue.settings().referenceRevision(),
                            "providerCalls", 0, "modelAttemptCount", 0, "injectedServerPort", String.valueOf(injectedPort),
                            "resourcesExited", true, "exchanges", exchanges));
        }
    }

    /**
     * 代理贯通：使用仓库自带的 {@code agent/frontend/nginx.conf}（官方镜像模板机制生成）起真实 nginx，
     * 后端绑定到 Docker 网桥地址，客户端只经代理端口访问。计划明确要求"不能仅用 mock 控制器证明代理
     * 正确"，因此归档的是代理实际响应，而不是控制器直连响应。
     *
     * <p>为什么单独占一个方法而不是并进直连验收：端口与绑定地址都不同（代理场景后端必须可被容器访问），
     * 且必须显式提供 {@code zhku.proxy.*} 才会执行，避免没有 nginx 的机器上把"未验证"跑成"失败"。
     */
    @Test void sameChainThroughRepoNginxProxyWithActualProxiedResponses() throws Exception {
        String bindAddress = System.getProperty("zhku.proxy.bindAddress", "172.17.0.1");
        int backendPort = Integer.parseInt(System.getProperty("zhku.proxy.backendPort", "-1"));
        int publicPort = Integer.parseInt(System.getProperty("zhku.proxy.publicPort", "-1"));
        Assumptions.assumeTrue(backendPort > 0 && publicPort > 0,
                "未提供 zhku.proxy.backendPort / zhku.proxy.publicPort：代理端口未配置，本项未验证");
        exchanges.clear();
        String previousPort = System.getProperty("server.port");
        System.setProperty("server.port", String.valueOf(backendPort));
        try {
            var app = new SpringApplication(App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = baseSettings();
            settings.put("server.port", String.valueOf(backendPort));
            settings.put("server.address", bindAddress);
            app.setDefaultProperties(settings);
            try (var context = (ServletWebServerApplicationContext) app.run()) {
                assertThat(context.getWebServer().getPort()).isEqualTo(backendPort);
                var anonymous = new User(publicPort);
                var a = new User(publicPort); var b = new User(publicPort);
                assertThat(anonymous.get("/tasks/capabilities").statusCode()).as("匿名仍须被拒").isEqualTo(401);
                a.login("t06-chain-a", CORRECT);
                b.login("t06-chain-b", CORRECT);
                assertThat(a.get("/tasks/capabilities").statusCode()).as("经代理的能力查询").isEqualTo(200);
                // 上游 Cache-Control 被 nginx 隐藏，no-store 必须由代理自己补齐，否则浏览器可能缓存任务状态。
                assertThat(a.get("/tasks/capabilities").headers().firstValue("Cache-Control")).hasValue("no-store");
                String key = UUID.randomUUID().toString();
                assertThat(a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", key), false)
                        .statusCode()).as("经代理同样必须校验 CSRF").isEqualTo(403);
                var accepted = a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", key), true);
                assertThat(accepted.statusCode()).isEqualTo(202);
                assertThat(JSON.readTree(a.post("/tasks", Map.of("question", ASK_QUESTION, "clientRequestId", key), true).body())
                        .path("duplicate").asBoolean()).isTrue();
                String task = JSON.readTree(accepted.body()).path("taskId").asText();
                assertThat(b.get("/tasks/" + task).statusCode()).as("代理不授予跨主体访问").isEqualTo(404);
                assertThat(runs.get()).isZero(); queue.remove().run(); assertThat(runs.get()).isEqualTo(1);
                var waiting = JSON.readTree(a.get("/tasks/" + task).body());
                assertThat(waiting.path("taskStatus").asText()).isEqualTo("WAITING_USER");
                var message = new HashMap<String, Object>();
                message.put("message", "PUBLIC"); message.put("continuationToken", waiting.path("continuationToken").asText());
                message.put("expectedVersion", waiting.path("version").asLong()); message.put("clientRequestId", UUID.randomUUID());
                assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(202);
                queue.remove().run(); assertThat(runs.get()).isEqualTo(2);
                var resolved = JSON.readTree(a.get("/tasks/" + task).body());
                assertThat(resolved.path("taskStatus").asText()).isEqualTo("RESOLVED");
                assertThat(resolved.path("result").path("evidence")).as("代理不得替换或裁剪证据").isNotEmpty();
                String token = resolved.path("result").path("evidence").get(0).path("originalToken").asText();
                assertThat(a.get("/tasks/" + task + "/originals/" + token).statusCode()).isEqualTo(200);
                assertThat(b.get("/tasks/" + task + "/originals/" + token).statusCode()).isEqualTo(404);
                assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
                assertThat(queue).isEmpty();
            }
            try (var socket = new java.net.Socket()) {
                assertThatThrownBy(() -> socket.connect(new InetSocketAddress(bindAddress, backendPort), 500))
                        .isInstanceOf(java.io.IOException.class);
            }
        } finally {
            if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", previousPort);
        }
        if (!evidenceDirectory.isBlank()) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(evidenceDirectory, "proxied-chain.json").toFile(),
                    Map.of("schemaVersion", 3, "executionMode", "SIMULATED_ENGINEERING_REPLAY_VIA_REPO_NGINX",
                            "proxy", Map.of("image", "nginx:1.27-alpine", "config", "agent/frontend/nginx.conf(官方镜像模板机制生成)",
                                    "publicPort", publicPort, "backendPort", backendPort, "backendAddress", bindAddress),
                            "providerCalls", 0, "modelAttemptCount", 0, "resourcesExited", true, "exchanges", exchanges));
        }
    }

    /**
     * 真实库 + 真实已审核资料上的取消、容量与终态矩阵。
     *
     * <p>为什么单列一个方法：这些路径在合成层（{@code RuleAgentTaskHttpTest}）已验证过接口形状，
     * 但那里的身份是替身、资料是合成 fixture；这里把它们搬到真实身份仓储 + 真实已审核资料上，
     * 且断言的是<b>结果内容</b>而不只是状态码——部分覆盖必须带真实片段证据，检索未命中必须真的是
     * 真实检索的结果，取消后迟到的执行结果必须被丢弃而不是改写成有结论的状态。
     */
    @Test void realTempDbCancelCapacityAndTerminalStatesOverActualHttp() throws Exception {
        runs.set(0); queue.clear(); exchanges.clear();
        String injectedPort = System.getenv("SERVER__PORT");
        String previousPort = System.getProperty("server.port");
        System.setProperty("server.port", "0");
        int port = -1;
        try {
            var app = new SpringApplication(App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = baseSettings();
            app.setDefaultProperties(settings);
            try (var context = (ServletWebServerApplicationContext) app.run()) {
                port = context.getWebServer().getPort();
                assertThat(port).isNotEqualTo(Integer.parseInt(injectedPort == null ? "-1" : injectedPort));
                var a = new User(port); var b = new User(port);
                a.login("t06-chain-a", CORRECT);
                b.login("t06-chain-b", CORRECT);
                // (1) 容量：超长请求体在过滤器层被拒，且按 clientRequestId 查不到任务（未产生任务）。
                String oversizeKey = UUID.randomUUID().toString();
                var oversize = a.send("POST", "/tasks",
                        JSON.writeValueAsString(Map.of("question", "x".repeat(70000), "clientRequestId", oversizeKey)), true);
                assertThat(oversize.statusCode()).isEqualTo(413);
                assertThat(a.get("/tasks/requests/CREATE/" + oversizeKey).statusCode()).isEqualTo(404);
                // (2) 取消：受理后、执行前取消；执行完成后迟到结果不得被改写成有结论的状态。
                String cancelKey = UUID.randomUUID().toString();
                var accepted = a.post("/tasks", Map.of("question", CANCEL_QUESTION, "clientRequestId", cancelKey), true);
                assertThat(accepted.statusCode()).isEqualTo(202);
                var ticket = JSON.readTree(accepted.body());
                String cancelTask = ticket.path("taskId").asText();
                var cancel = Map.of("runId", ticket.path("runId").asText(), "expectedVersion", ticket.path("version").asLong(),
                        "clientRequestId", UUID.randomUUID().toString());
                assertThat(a.post("/tasks/" + cancelTask + "/cancel", cancel, true).statusCode()).isEqualTo(202);
                var stopping = JSON.readTree(a.get("/tasks/" + cancelTask).body());
                assertThat(stopping.path("terminated").asBoolean()).as("执行尚未完成时不得虚称已终止").isFalse();
                queue.remove().run();
                var stopped = JSON.readTree(a.get("/tasks/" + cancelTask).body());
                if (RUN_FAILURE.get() != null) RUN_FAILURE.get().printStackTrace();
                assertThat(stopped.path("taskStatus").asText()).as("取消后迟到的执行结果必须保持 STOPPED；模拟执行器内部异常=%s", RUN_FAILURE.get()).isEqualTo("STOPPED");
                assertThat(stopped.path("terminated").asBoolean()).isTrue();
                assertThat(stopped.path("result").path("coverage").asText()).isEqualTo("NONE");
                assertThat(stopped.path("result").path("evidence")).as("已取消的任务不得留下证据").isEmpty();
                assertThat(stopped.path("result").path("items").get(0).path("issues").get(0).path("reasonCode").asText()).isEqualTo("CANCELLED");
                assertThat(stopped.path("execution").path("answerSource").asText()).as("已取消的任务不得声称答案来源").isEqualTo("NONE");
                assertThat(a.get("/tasks/" + cancelTask + "/originals/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
                assertThat(b.get("/tasks/" + cancelTask).statusCode()).isEqualTo(404);
                // (3) 并发幂等：同一 clientRequestId 并行受理，只允许一个任务、一次执行。
                String concurrentKey = UUID.randomUUID().toString();
                int parallelism = 4;
                var latch = new CountDownLatch(1);
                var responses = new CopyOnWriteArrayList<HttpResponse<String>>();
                var clientFailures = new CopyOnWriteArrayList<Throwable>();
                var threads = new ArrayList<Thread>();
                for (int i = 0; i < parallelism; i++) {
                    var thread = new Thread(() -> {
                        try {
                            latch.await();
                            responses.add(a.post("/tasks", Map.of("question", CANCEL_QUESTION, "clientRequestId", concurrentKey), true));
                        } catch (Throwable failure) { clientFailures.add(failure); }
                    });
                    threads.add(thread); thread.start();
                }
                latch.countDown();
                for (var thread : threads) thread.join(30_000);
                assertThat(clientFailures).as("并行受理不得出现客户端异常").isEmpty();
                assertThat(responses).hasSize(parallelism);
                var ids = new HashSet<String>();
                int duplicates = 0;
                for (var response : responses) {
                    assertThat(response.statusCode()).as("同一幂等键的并行受理都必须被受理").isEqualTo(202);
                    var node = JSON.readTree(response.body());
                    ids.add(node.path("taskId").asText());
                    if (node.path("duplicate").asBoolean()) duplicates++;
                }
                assertThat(ids).as("同一 clientRequestId 并行受理只允许产生一个任务").hasSize(1);
                assertThat(duplicates).as("只有首个受理可以不是重复").isEqualTo(parallelism - 1);
                assertThat(queue).as("只允许排队一个执行").hasSize(1);
                int beforeConcurrent = runs.get();
                queue.remove().run();
                assertThat(runs.get()).as("同键并发不得产生第二次执行").isEqualTo(beforeConcurrent + 1);
                var concurrent = JSON.readTree(a.get("/tasks/" + ids.iterator().next()).body());
                assertThat(concurrent.path("taskStatus").asText()).isEqualTo("RESOLVED");
                assertThat(concurrent.path("result").path("evidence")).as("并发受理的任务同样必须带真实已审核证据").isNotEmpty();
                // (4) 终态矩阵：部分覆盖（有真实证据但不完整）、检索未命中（真实检索没命中）、处理失败。
                for (String question : List.of(PARTIAL_QUESTION, GAP_QUESTION, FAIL_QUESTION)) {
                    var t = JSON.readTree(a.post("/tasks", Map.of("question", question, "clientRequestId", UUID.randomUUID().toString()), true).body());
                    String id = t.path("taskId").asText();
                    queue.remove().run();
                    var s = JSON.readTree(a.get("/tasks/" + id).body());
                    if (RUN_FAILURE.get() != null) RUN_FAILURE.get().printStackTrace();
                    if (PARTIAL_QUESTION.equals(question)) {
                        assertThat(s.path("taskStatus").asText()).isEqualTo("RESOLVED");
                        assertThat(s.path("result").path("coverage").asText()).isEqualTo("PARTIAL");
                        assertThat(s.path("result").path("evidence")).as("部分覆盖也必须带真实已审核证据").isNotEmpty();
                        assertThat(s.path("result").path("items").get(0).path("issues").get(0).path("reasonCode").asText())
                                .isEqualTo("ANSWER_COVERAGE_INCOMPLETE");
                    } else if (GAP_QUESTION.equals(question)) {
                        assertThat(s.path("taskStatus").asText()).isEqualTo("RESOLVED");
                        assertThat(s.path("result").path("coverage").asText()).isEqualTo("NONE");
                        assertThat(s.path("result").path("evidence")).isEmpty();
                        assertThat(reasonCodesOf(s)).as("未命中必须同时披露资料缺口与回放未覆盖").containsExactly("RETRIEVAL_NO_MATCH", "REPLAY_NOT_COVERED");
                        assertThat(s.path("execution").path("replayCoverage").asText()).isEqualTo("MISS");
                    } else {
                        assertThat(s.path("taskStatus").asText()).isEqualTo("FAILED");
                        assertThat(s.path("result").path("coverage").asText()).isEqualTo("NONE");
                        assertThat(reasonCodesOf(s)).as("失败必须同时披露处理失败与回放未覆盖").containsExactly("MODEL_UNAVAILABLE", "REPLAY_NOT_COVERED");
                        assertThat(s.path("execution").path("replayCoverage").asText()).isEqualTo("MISS");
                        assertThat(s.path("execution").path("modelAttemptCount").asInt()).as("模拟执行不得声称模型尝试").isZero();
                    }
                    assertThat(a.get("/tasks/" + id + "/originals/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
                }
                // (5) 退出：无在途任务、无待执行。
                assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
                assertThat(queue).isEmpty();
            }
            int boundPort = port;
            try (var socket = new java.net.Socket()) {
                assertThatThrownBy(() -> socket.connect(new InetSocketAddress("127.0.0.1", boundPort), 500)).isInstanceOf(java.io.IOException.class);
            }
        } finally {
            if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port", previousPort);
        }
        if (!evidenceDirectory.isBlank()) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(evidenceDirectory, "cancel-capacity-terminal.json").toFile(),
                    Map.of("schemaVersion", 3, "executionMode", "SIMULATED_ENGINEERING_REPLAY",
                            "database", "zhkuagent_rulecheck_e2e(temp,一次性容器)", "assetsRevision", catalogue == null ? "" : catalogue.settings().referenceRevision(),
                            "providerCalls", 0, "modelAttemptCount", 0, "injectedServerPort", String.valueOf(injectedPort),
                            "resourcesExited", true, "exchanges", exchanges));
        }
    }

    /** 取出快照里第一个子问题的全部 reasonCode（顺序即序列化顺序），用于整表比对而不是只查第一条。 */
    private static List<String> reasonCodesOf(JsonNode snapshot) {
        var codes = new ArrayList<String>();
        snapshot.path("result").path("items").get(0).path("issues")
                .forEach(issue -> codes.add(issue.path("reasonCode").asText()));
        return codes;
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
            var response = client.send(req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (path.startsWith("/tasks")) exchanges.add(Map.of("method", method, "path", path, "input", body == null ? "" : body.length() > 4096 ? "OVERSIZE" : body, "status", response.statusCode(), "actual", response.body()));
            return response;
        }
        JsonNode csrf() throws Exception { return JSON.readTree(get("/auth/csrf").body()); }
        int loginStatus(String login, String password) throws Exception {
            csrf = csrf();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + P + "/auth/login")).header("Content-Type", "application/x-www-form-urlencoded")
                    .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + login + "&password=" + password)).build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        void login(String login, String password) throws Exception {
            assertThat(loginStatus(login, password)).as("正确口令必须登录成功").isEqualTo(200);
            csrf = csrf();
        }
        void logout() throws Exception { assertThat(send("POST", "/auth/logout", null, true).statusCode()).isEqualTo(204); }
    }
}
