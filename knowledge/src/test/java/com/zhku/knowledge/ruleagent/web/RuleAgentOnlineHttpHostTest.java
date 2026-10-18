package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import com.zhku.knowledge.security.*;
import com.zhku.agent.agent.runtime.AgentRunContext;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 默认关闭的真实 ONLINE HTTP 测试宿主；构包／校包不授外发许可，也不初始化模型与数据库。 */
@Timeout(1000)
class RuleAgentOnlineHttpHostTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static volatile Live live;
    private record Paths(Path code, Path assets, Path dist, Path directory, String sha) {}
    private static final class Live {
        final Path directory;
        final RuleAgentProvider.Settings settings;
        final boolean assemblyOnly;
        final AtomicInteger keyReads = new AtomicInteger();
        final List<RuleAgentProvider.Trace> traces = new CopyOnWriteArrayList<>();
        final List<RuleAgentRunner.Outcome> outcomes = new CopyOnWriteArrayList<>();
        Live(Path directory, RuleAgentProvider.Settings settings, boolean assemblyOnly) { this.directory = directory; this.settings = settings; this.assemblyOnly = assemblyOnly; }
        String key() {
            keyReads.incrementAndGet();
            if (assemblyOnly) throw new IllegalStateException("零外发装配禁止读取任何模型密钥");
            return System.getenv("DASHSCOPE_API_KEY");
        }
        synchronized void trace(RuleAgentProvider.Trace trace) {
            traces.add(trace);
            try { Files.writeString(directory.resolve("provider-trace.jsonl"), JSON.writeValueAsString(trace)+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (java.io.IOException failure) { throw new UncheckedIOException("本批调用审计不可写，停止执行", failure); }
            if (assemblyOnly) throw new IllegalStateException("零外发装配发生供应商尝试，必须失败");
        }
        synchronized void outcome(RuleAgentRunner.Outcome outcome) {
            outcomes.add(outcome);
            try { JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("outcomes.json").toFile(), outcomes); }
            catch (java.io.IOException failure) { throw new UncheckedIOException("本批结果审计不可写，停止执行", failure); }
        }
    }

    private static boolean selected(String action) {
        var actions = List.of("preparePackage", "checkPackage", "liveLease", "checkAssembly");
        if (actions.stream().filter(name -> Boolean.getBoolean("zhku.http."+name)).count() > 1)
            throw new IllegalArgumentException("构包、校包、零外发装配与获准执行必须互斥选择");
        return Boolean.getBoolean("zhku.http."+action);
    }
    private static Paths paths(boolean requireSha) {
        String sha = System.getProperty("zhku.http.packageSha256", "");
        if (requireSha && !sha.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("须指定完整请求包 SHA256");
        return new Paths(path("codeRoot"),path("assetsRoot"),path("studentDist"),path("packageDir"),sha);
    }
    private static Path path(String name) {
        String value = System.getProperty("zhku.http."+name);
        if (value == null || value.isBlank() || !Path.of(value).isAbsolute()) throw new IllegalArgumentException("须指定绝对路径 zhku.http."+name);
        return Path.of(value).normalize();
    }
    @Test void prepareRequestPackage() throws Exception {
        Assumptions.assumeTrue(selected("preparePackage"), "构包默认关闭");
        var p = paths(false);
        String sha = RuleAgentHttpRequestPackage.prepare(p.code(),p.assets(),p.dist(),p.directory());
        System.out.println("HTTP_PACKAGE_SHA256="+sha);
    }
    @Test void checkRequestPackage() throws Exception {
        Assumptions.assumeTrue(selected("checkPackage"), "校包默认关闭");
        var p = paths(true);
        RuleAgentHttpRequestPackage.verify(p.code(),p.assets(),p.dist(),p.directory(),p.sha());
        System.out.println("HTTP_PACKAGE_CHECKED_SHA256="+p.sha());
    }

    /** 正面装配验收只用本机 HTTP 与新空临时库；没有 live 批准也不能触及模型密钥或调用端口。 */
    @Test void zeroCallHttpAssembly() throws Exception {
        Assumptions.assumeTrue(selected("checkAssembly"), "零外发装配默认关闭");
        Path assets = path("assetsRoot");
        Path directory = Path.of(Objects.requireNonNull(System.getProperty("zhku.rulecheck.evidenceDir"),"须指定独立装配证据目录")).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        assertThat(Files.exists(directory.resolve("online-assembly-exit.json"))).as("装配验收不得覆盖旧回执").isFalse();
        // 直接构造固定北京配置；不调用 Settings.environment，不读取、清洗或伪造真实模型密钥。
        var state = new Live(directory,new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING,RuleAgentProvider.MODEL,"HTTP_ASSEMBLY_ONLY"),true);
        live = state;
        String previousRoot = System.getProperty("zhku.t06.root"), previousPort = System.getProperty("server.port");
        System.setProperty("zhku.t06.root",assets.toString()); System.setProperty("server.port","0");
        ServletWebServerApplicationContext context = null;
        ExecutorService executor = null;
        RuleAgentTaskStore store = null;
        RuleAgentOnlineFactory factory = null;
        int port = -1;
        boolean workersExited = true, listenerExited = false, providersExited = true;
        Throwable problem = null;
        try {
            RuleAgentSimulatedChainTempDbHttpTest.installReviewedAssetsAndIdentities();
            var app = new SpringApplication(App.class); app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = RuleAgentSimulatedChainTempDbHttpTest.baseSettings();
            settings.put(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY,"ONLINE");
            settings.put("zhku.rule-agent-http.online-enabled","true"); settings.put("spring.main.allow-bean-definition-overriding","false");
            app.setDefaultProperties(settings);
            context = (ServletWebServerApplicationContext) app.run("--server.port=0","--server.address=127.0.0.1",
                    "--zhku.rule-agent-launch.executor=ONLINE","--zhku.rule-agent-http.online-enabled=true","--spring.main.allow-bean-definition-overriding=false");
            port = context.getWebServer().getPort();
            executor = context.getBean("ruleAgentHttpExecutor",ExecutorService.class); store = context.getBean(RuleAgentTaskStore.class);
            factory = context.getBean(RuleAgentOnlineFactory.class);
            assertThat(context.getBeansOfType(RuleAgentTaskStore.Factory.class)).hasSize(1);
            var anonymous = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            assertThat(anonymous.get("/tasks/capabilities").statusCode()).isEqualTo(401);
            var user = new RuleAgentSimulatedChainTempDbHttpTest.User(port);
            user.login("t06-chain-a",RuleAgentSimulatedChainTempDbHttpTest.CORRECT);
            assertThat(user.get("/tasks/capabilities").statusCode()).isEqualTo(200);
            // 包外请求在真实 store/worker 上被 scope 拒绝；不能以合成 Factory 替代真实在线装配。
            var accepted = user.post("/tasks",Map.of("question","本题不在获准外发范围","clientRequestId",UUID.randomUUID().toString()),true);
            assertThat(accepted.statusCode()).isEqualTo(202);
            String task = JSON.readTree(accepted.body()).path("taskId").asText();
            JsonNode snapshot = null;
            Instant until = Instant.now().plusSeconds(5);
            while (Instant.now().isBefore(until)) {
                var response = user.get("/tasks/"+task); assertThat(response.statusCode()).isEqualTo(200);
                snapshot = JSON.readTree(response.body());
                if (!"ACTIVE".equals(snapshot.path("taskStatus").asText())) break;
                Thread.sleep(25);
            }
            assertThat(snapshot).isNotNull(); assertThat(snapshot.path("taskStatus").asText()).isEqualTo("FAILED");
            assertThat(snapshot.path("execution").path("modelAttemptCount").asInt()).isZero();
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("online-assembly-denied-task.json").toFile(),snapshot);
            assertThat(factory.remainingCalls()).isEqualTo(10); assertThat(state.keyReads).hasValue(0); assertThat(state.traces).isEmpty();
            assertThat(state.outcomes).isEmpty();
            // FAILED 可先于 worker 的 finally 发布；仍在原5秒截止内确认真实退出，不把已发布当作线程结束。
            while (store.activeJobs() != 0 && Instant.now().isBefore(until)) Thread.sleep(10);
            assertThat(store.activeJobs()).isZero();
        } catch (Throwable failure) { problem = failure; }
        finally {
            try { if (store != null) store.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            try { if (factory != null) factory.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            if (executor != null) {
                executor.shutdown();
                try { workersExited = executor.awaitTermination(10,TimeUnit.SECONDS); if (!workersExited) { executor.shutdownNow(); workersExited = executor.awaitTermination(5,TimeUnit.SECONDS); } }
                catch (InterruptedException interrupted) { executor.shutdownNow(); workersExited = false; Thread.currentThread().interrupt(); problem = add(problem,interrupted); }
            }
            try { if (context != null) context.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            try { RuleAgentSimulatedChainTempDbHttpTest.uninstall(); } catch (Throwable failure) { problem = add(problem,failure); }
            if (port > 0) listenerExited = closedPort(port);
            if (factory != null) providersExited = factory.resourcesClosed();
            try {
                assertThat(workersExited && listenerExited && providersExited).isTrue();
                assertThat(state.keyReads).hasValue(0); assertThat(state.traces).isEmpty();
                if (factory != null) assertThat(factory.remainingCalls()).isEqualTo(10);
                if (store != null) assertThat(store.activeJobs()).isZero();
            } catch (Throwable failure) { problem = add(problem,failure); }
            var exit = new LinkedHashMap<String,Object>();
            exit.put("executionMode","ONLINE_ASSEMBLY_ONLY_NO_PROVIDER_CALLS"); exit.put("status",problem == null ? "PASS" : "FAIL");
            exit.put("fixtureIdentityOnly",true); exit.put("backendPort",port); exit.put("bindAddress","127.0.0.1");
            exit.put("backendListenerExited",listenerExited); exit.put("workerExited",workersExited); exit.put("providerResourcesClosed",providersExited);
            exit.put("remainingCalls",factory == null ? null : factory.remainingCalls()); exit.put("keyReadCount",state.keyReads.get());
            exit.put("providerTraceCount",state.traces.size()); exit.put("activeJobs",store == null ? null : store.activeJobs());
            exit.put("failureType",problem == null ? null : problem.getClass().getSimpleName()); exit.put("databaseContainerCleanup","launcher-owned; not performed by JVM");
            try { JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("online-assembly-exit.json").toFile(),exit); }
            finally {
                if (previousRoot == null) System.clearProperty("zhku.t06.root"); else System.setProperty("zhku.t06.root",previousRoot);
                if (previousPort == null) System.clearProperty("server.port"); else System.setProperty("server.port",previousPort);
                live = null;
            }
        }
        if (problem != null) throw new AssertionError("零外发真实 HTTP 装配未通过；保留拒绝与资源退出证据",problem);
    }

    @Test void approvedOnlineBrowserLease() throws Exception {
        Assumptions.assumeTrue(selected("liveLease"), "真实 ONLINE 浏览器租约默认关闭，未验收");
        var p = paths(true);
        JsonNode requestPackage = RuleAgentHttpRequestPackage.verify(p.code(),p.assets(),p.dist(),p.directory(),p.sha());
        if (LocalDate.now(ZoneId.of("Asia/Shanghai")).isAfter(LocalDate.of(2026,10,25)))
            throw new IllegalStateException("本批核价有效期已结束，须重新核价构包并批准后才能外发");
        var env = System.getenv();
        // 静态包校验在先；即使机器已有密钥，也不能把准备包或旧批次额度当作本次批准。
        if (!p.sha().equals(env.get("ZHKU_HTTP_APPROVED_PACKAGE_SHA256")) || !"ONLINE".equals(env.get("ZHKU_RULE_AGENT_MODE"))
                || !p.sha().equals(env.get("ZHKU_RULE_AGENT_AUTHORIZATION_ID"))) throw new IllegalStateException("本次请求包尚无匹配的独立 ONLINE 外发批准");
        var providerSettings = RuleAgentProvider.Settings.environment(env);
        // 同一包的额度只消费一次：全局原子占用在运行目录、临时库与 Provider 之前完成，复制包或重启不退款。
        RuleAgentHttpRequestPackage.claimReservedExecution(p.code(),p.directory(),p.sha(),System.getenv("ZHKU_HTTP_RESERVATION_TOKEN"));
        String sourceSha = requestPackage.path("buildIdentity").path("sourceCommit").asText();
        if (!sourceSha.matches("[a-f0-9]{40}")) throw new IllegalStateException("请求包缺少固定源码提交身份");
        Path directory = p.directory().resolve("live-"+UUID.randomUUID());
        Files.createDirectory(directory); // 原包只读；运行证据必须落在新的独立子目录，禁止覆盖前次执行。
        live = new Live(directory,providerSettings,false);
        var properties = List.of("server.port", "zhku.t06.root", "zhku.rulecheck.evidenceDir");
        var previous = new HashMap<String,String>(); properties.forEach(key -> previous.put(key,System.getProperty(key)));
        System.setProperty("server.port","0");
        System.setProperty("zhku.t06.root",p.assets().toString());
        System.setProperty("zhku.rulecheck.evidenceDir",directory.toString());
        ServletWebServerApplicationContext context = null;
        ExecutorService executor = null;
        RuleAgentTaskStore store = null;
        RuleAgentOnlineFactory factory = null;
        RuleAgentOnlineHttpScope scope = null;
        int port = -1;
        boolean explicitStop = false, listenerExited = false, workerExited = true, providerExited = true;
        Throwable problem = null;
        try {
            RuleAgentSimulatedChainTempDbHttpTest.installReviewedAssetsAndIdentities();
            var app = new SpringApplication(App.class);
            app.setAdditionalProfiles("zhku-rule-check-preview");
            var settings = RuleAgentSimulatedChainTempDbHttpTest.baseSettings();
            settings.put(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY,"ONLINE");
            settings.put("zhku.rule-agent-http.online-enabled","true");
            settings.put("spring.main.allow-bean-definition-overriding","false");
            app.setDefaultProperties(settings);
            // 高优先级启动参数固定回环与 ONLINE，防止本机注入的 SERVER__PORT 或旧模式覆盖宿主准入。
            context = (ServletWebServerApplicationContext) app.run("--server.port=0","--server.address=127.0.0.1",
                    "--zhku.rule-agent-launch.executor=ONLINE","--zhku.rule-agent-http.online-enabled=true",
                    "--spring.main.allow-bean-definition-overriding=false");
            port = context.getWebServer().getPort();
            executor = context.getBean("ruleAgentHttpExecutor",ExecutorService.class);
            store = context.getBean(RuleAgentTaskStore.class); factory = context.getBean(RuleAgentOnlineFactory.class);
            scope = context.getBean(RuleAgentOnlineHttpScope.class);
            Instant started = Instant.now(), until = started.plusSeconds(900);
            var ready = new LinkedHashMap<String,Object>();
            ready.put("backendPort",port); ready.put("bindAddress","127.0.0.1"); ready.put("executionMode","ONLINE"); ready.put("schemaVersion",3);
            ready.put("sourceSha",sourceSha); ready.put("packageSha256",p.sha()); ready.put("liveDirectory",directory.toString());
            ready.put("studentDist",p.dist().toString()); ready.put("question",RuleAgentHttpRequestPackage.QUESTION); ready.put("reply",RuleAgentHttpRequestPackage.REPLY);
            ready.put("remainingCalls",factory.remainingCalls()); ready.put("maximumProviderCalls",RuleAgentHttpRequestPackage.MAX_CALLS); ready.put("leaseSeconds",900);
            ready.put("leaseStartedAt",started.toString()); ready.put("leaseExpiresAt",until.toString());
            ready.put("fixtureIdentityOnly",true); ready.put("providerCalls",0);
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("browser-ready.json").toFile(),ready);
            while (!Files.exists(directory.resolve("STOP")) && Instant.now().isBefore(until)) Thread.sleep(100);
            explicitStop = Files.exists(directory.resolve("STOP"));
        } catch (Throwable failure) { problem = failure; }
        finally {
            // STOP 与异常走同一清理路径：先阻止任务继续，再取消供应商端口，最后证明线程与监听退出。
            try { if (store != null) store.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            try { if (factory != null) factory.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            if (executor != null) {
                executor.shutdown();
                try { workerExited = executor.awaitTermination(15,TimeUnit.SECONDS); if (!workerExited) { executor.shutdownNow(); workerExited = executor.awaitTermination(5,TimeUnit.SECONDS); } }
                catch (InterruptedException interrupted) { executor.shutdownNow(); workerExited = false; Thread.currentThread().interrupt(); problem = add(problem,interrupted); }
            }
            try { if (context != null) context.close(); } catch (Throwable failure) { problem = add(problem,failure); }
            try { RuleAgentSimulatedChainTempDbHttpTest.uninstall(); } catch (Throwable failure) { problem = add(problem,failure); }
            if (port > 0) listenerExited = closedPort(port);
            if (factory != null) providerExited = factory.resourcesClosed();
            try {
                if (!explicitStop) throw new IllegalStateException("未收到显式 STOP，不能把租约超时记为验收通过");
                if (scope == null) throw new IllegalStateException("ONLINE 宿主未完成装配");
                scope.requireSuccessfulAskPublicComplete();
                if (!workerExited || !listenerExited || !providerExited || store.activeJobs() != 0) throw new IllegalStateException("本批自有资源尚未核销");
            } catch (Throwable failure) { problem = add(problem,failure); }
            var exit = new LinkedHashMap<String,Object>();
            exit.put("executionMode","ONLINE"); exit.put("sourceSha",sourceSha); exit.put("packageSha256",p.sha());
            exit.put("explicitStopReceived",explicitStop); exit.put("backendListenerExited",listenerExited); exit.put("workerExited",workerExited); exit.put("providerResourcesClosed",providerExited);
            exit.put("activeJobs",store == null ? null : store.activeJobs()); exit.put("remainingCalls",factory == null ? RuleAgentHttpRequestPackage.MAX_CALLS : factory.remainingCalls());
            exit.put("attemptsReserved",factory == null ? 0 : RuleAgentHttpRequestPackage.MAX_CALLS-factory.remainingCalls());
            exit.put("transportDispatched",live.traces.stream().filter(t -> t.diagnostics() != null && t.diagnostics().dispatchedMillis() != null).map(t -> t.taskId()+"/"+t.runId()+"/"+t.attempt()).distinct().count());
            exit.put("outcomes",live.outcomes.size()); exit.put("status",problem == null ? "PASS" : "FAIL");
            exit.put("failureType",problem == null ? null : problem.getClass().getSimpleName());
            exit.put("databaseContainerCleanup","launcher-owned; not performed by JVM");
            try { JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("browser-host-exit.json").toFile(),exit); }
            finally { properties.forEach(key -> { if (previous.get(key) == null) System.clearProperty(key); else System.setProperty(key,previous.get(key)); }); live = null; }
        }
        if (problem != null) throw new AssertionError("本批 ONLINE HTTP 浏览器工程验收未通过；保留全部尝试与退出证据",problem);
    }

    private static Throwable add(Throwable first, Throwable next) { if (first == null) return next; first.addSuppressed(next); return first; }
    private static boolean closedPort(int port) {
        try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1",port),500); return false; }
        catch (java.io.IOException closed) { return true; }
    }

    @SpringBootConfiguration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class,RuleCheckAuthController.class,RuleAgentTaskController.class,
            RuleAgentOnlineHttpConfiguration.class,RuleAgentReviewedToolsConfiguration.class,RuleAgentControlledLaunchConfiguration.class})
    static class App {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean javax.sql.DataSource dataSource() { return RuleAgentSimulatedChainTempDbHttpTest.db; }
        @Bean(destroyMethod="") Reviewed2025ControlledCatalogue catalogue() { return RuleAgentSimulatedChainTempDbHttpTest.catalogue; }
        @Bean RuleAgentReviewedToolsConfiguration.Assets assets() { return new RuleAgentReviewedToolsConfiguration.Assets(RuleAgentSimulatedChainTempDbHttpTest.root); }
        @Bean(destroyMethod="close") PreviewIdentityRepository identities() { return new PreviewIdentityRepository(RuleAgentSimulatedChainTempDbHttpTest.db); }
        @Bean RuleAgentOnlineHttpScope scope() { return new RuleAgentOnlineHttpScope(); }
        @Bean RuleAgentOnlineHttpConfiguration.Options options() {
            var state = Objects.requireNonNull(live,"须先完成包校验与独立外发批准");
            return new RuleAgentOnlineHttpConfiguration.Options(state.settings,state::key,state::trace,RuleAgentHttpRequestPackage.MAX_CALLS);
        }
        @Bean(name="ruleAgentHttpExecutor",destroyMethod="shutdown") ExecutorService executor() {
            return new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r -> new Thread(r,"zhku-approved-online-http"),new ThreadPoolExecutor.AbortPolicy());
        }
        @Bean(destroyMethod="close") RuleAgentTaskStore tasks(ExecutorService executor,RuleAgentOnlineFactory factory,Clock clock,RuleAgentOnlineHttpScope scope) {
            // 包装只存在于这个测试宿主的 store 构造参数内，不新增第二个 Factory bean，不替代生产循环。
            var reference = new AtomicReference<RuleAgentTaskStore>();
            var store = new RuleAgentTaskStore(executor,(owner,task) -> {
                var actual = factory.open(owner,task);
                return new RuleAgentTaskStore.Work() {
                    public boolean revalidate(ResultEnvelope previous) { return actual.revalidate(previous); }
                    public RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input) {
                        // 读取实际已受理的原始回复；不以标准化后的 PUBLIC 替未经审包批准的同义输入自证。
                        String rawMessage = Objects.requireNonNull(reference.get()).history(owner,task).getLast().message();
                        scope.begin(input,rawMessage);
                        var outcome = actual.run(context,input);
                        Objects.requireNonNull(live).outcome(outcome);
                        scope.complete(outcome); return outcome;
                    }
                };
            },clock,new RuleAgentTaskStore.Limits(1,1,8));
            reference.set(store); return store;
        }
    }
}
