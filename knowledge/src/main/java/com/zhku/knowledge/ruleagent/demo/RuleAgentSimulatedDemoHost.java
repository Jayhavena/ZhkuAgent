package com.zhku.knowledge.ruleagent.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.ruleagent.web.*;
import com.zhku.knowledge.rulecheck.web.RuleCheckAuthController;
import com.zhku.knowledge.security.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 正式 classpath 的独立工程模拟宿主；不扫描其它应用，不读取供应商凭证，不接入旧 replay。 */
public final class RuleAgentSimulatedDemoHost {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String LOGIN = "m104b-demo-student";

    record Options(Path assets, Path run, String batch, String jdbc, int port, int leaseSeconds) {
        static Options read(Map<String, String> env) {
            String batch = required(env, "ZHKU_SIM_DEMO_BATCH");
            if (!batch.matches("m104b-[a-z0-9-]{8,80}") || !batch.equals(required(env, "ZHKU_SIM_DEMO_APPROVED_BATCH")))
                throw new IllegalArgumentException("缺少本批临时数据库运行批准标记");
            String jdbc = required(env, "ZHKU_DB_URL");
            var m = java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(jdbc);
            if (!m.matches()) throw new IllegalArgumentException("必须是专用回环临时库");
            int dbPort = Integer.parseInt(m.group(1)); int port = Integer.parseInt(required(env, "ZHKU_SIM_DEMO_PORT"));
            int seconds = Integer.parseInt(required(env, "ZHKU_SIM_DEMO_SECONDS"));
            if (dbPort < 1024 || dbPort > 65535 || dbPort == 54329 || port < 1024 || port > 65535 || port == 54329 || port == dbPort
                    || seconds < 30 || seconds > 900) throw new IllegalArgumentException("端口或租期非法");
            Path root = Path.of(required(env, "ZHKU_SIM_DEMO_ASSETS")); Path run = Path.of(required(env, "ZHKU_SIM_DEMO_RUN"));
            if (!root.isAbsolute() || !run.isAbsolute() || !Files.isDirectory(root.resolve("knowledge/src")) || !Files.isDirectory(run)
                    || !run.getFileName().toString().equals(batch)) throw new IllegalArgumentException("资料/证据根或批次不符");
            return new Options(root.normalize(), run.normalize(), batch, jdbc, port, seconds);
        }
    }
    record Inputs(Options options, DriverManagerDataSource source, Reviewed2025ControlledCatalogue catalogue) {}

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import({RuleCheckPreviewSecurityConfiguration.class, RuleCheckAuthController.class,
            RuleAgentHttpConfiguration.class, RuleAgentControlledLaunchConfiguration.class, RuleAgentReviewedToolsConfiguration.class})
    static class App {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean DataSource dataSource(Inputs inputs) { return inputs.source(); }
        // 外层先停任务和身份线程，再卸载资料；不能让 Spring 提前销毁共享目录。
        @Bean(destroyMethod = "") Reviewed2025ControlledCatalogue catalogue(Inputs inputs) { return inputs.catalogue(); }
        @Bean RuleAgentReviewedToolsConfiguration.Assets assets(Inputs inputs) { return new RuleAgentReviewedToolsConfiguration.Assets(inputs.options().assets()); }
        @Bean(destroyMethod = "close") PreviewIdentityRepository identities(DataSource source) { return new PreviewIdentityRepository(source); }
        @Bean(destroyMethod = "close") RuleAgentTaskToolsRegistry registry(RuleAgentTaskToolsRegistry.ToolFactory tools, Clock clock) {
            return new RuleAgentTaskToolsRegistry(tools, clock, RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
        }
        @Bean(name = RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN)
        RuleAgentSimulatedDemoFactory simulated(RuleAgentTaskToolsRegistry registry, Clock clock) { return new RuleAgentSimulatedDemoFactory(registry, clock); }
    }

    static String[] arguments(int port) {
        return new String[]{"--server.address=127.0.0.1", "--server.port=" + port, "--server.servlet.context-path=/api",
                "--server.servlet.session.timeout=15m", "--server.servlet.session.persistent=false", "--server.servlet.session.tracking-modes=cookie",
                "--server.servlet.session.cookie.name=ZHKU_RULE_CHECK_SESSION", "--server.servlet.session.cookie.path=/api/zhku/controlled-preview/v2",
                "--server.servlet.session.cookie.http-only=true", "--server.servlet.session.cookie.secure=false", "--server.servlet.session.cookie.same-site=strict",
                "--spring.config.location=optional:classpath:/m104a-no-external-config.yml", "--spring.profiles.active=zhku-rule-check-preview",
                "--spring.main.allow-bean-definition-overriding=false", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--zhku.rule-check-preview.enabled=true", "--zhku.rule-agent-http.enabled=true", "--zhku.rule-agent-launch.enabled=true",
                "--zhku.rule-agent-launch.executor=SIMULATED", "--zhku.rule-agent-http.online-enabled=false", "--zhku.rule-agent-http.reviewed-tools-enabled=true",
                "--zhku.controlled-preview.enabled=false", "--zhku.controlled-preview.conversations.enabled=false", "--zhku.local-preview.enabled=false",
                "--zhku.local-admin.enabled=false", "--zhku.rag.enabled=false", "--zhku.streaming.enabled=false", "--zhku.campus-agent.enabled=false",
                "--zhku.campus-agent.archive.enabled=false", "--spring.flyway.enabled=false"};
    }

    static void validateAmbient(Set<String> environmentNames, Set<String> propertyNames) {
        // 直接 java 调用也先拒绝外部配置注入；只检查变量名，不读取供应商密钥值。
        if (environmentNames.stream().anyMatch(k -> k.matches("(?i)(SPRING_|LOADER_|SERVER_|ZHKU_RAG_|ZHKU_RULECHECK_|ZHKU_RULE_AGENT_).*"
                + "|.*(DASHSCOPE|OPENAI|ANTHROPIC|API.?KEY).*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS"))
                || propertyNames.stream().anyMatch(k -> k.matches("(?i)(spring|server|zhku|loader)\\..*") && !k.equals("loader.main")))
            throw new IllegalArgumentException("独立模拟宿主拒绝外部模型或 Spring/Loader 配置注入");
    }

    public static void main(String[] args) throws Exception {
        // 在真实打包 classpath 上验证入口，分支先于环境、Spring、文件和数据源初始化。
        if (Arrays.equals(args, new String[]{"--check-entry"})) {
            System.out.println("{\"main\":\"" + RuleAgentSimulatedDemoHost.class.getName()
                    + "\",\"mode\":\"SIMULATED\",\"databaseOperations\":0,\"providerCalls\":0,\"productionClasspathOnly\":true}");
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("只接受启动器环境；无业务参数覆盖入口");
        validateAmbient(System.getenv().keySet(), System.getProperties().stringPropertyNames());
        var options = Options.read(System.getenv());
        String dbPassword = required(System.getenv(), "ZHKU_DB_PASSWORD");
        String studentPassword = required(System.getenv(), "ZHKU_SIM_DEMO_STUDENT_PASSWORD");
        if (studentPassword.length() < 16 || studentPassword.length() > 64) throw new IllegalArgumentException("合成口令长度非法");
        var source = new DriverManagerDataSource(options.jdbc(), "zhkuagent", dbPassword);
        var connectionProperties = new Properties(); connectionProperties.setProperty("connectTimeout", "5"); connectionProperties.setProperty("socketTimeout", "8");
        source.setConnectionProperties(connectionProperties);
        int exit = 1; boolean catalogueClosed = false; boolean contextClosed = true; boolean workersClosed = true; boolean identityClosed = true;
        ServletWebServerApplicationContext context = null; ExecutorService worker = null; PreviewIdentityRepository identities = null;
        RuleAgentTaskStore tasks = null; RuleAgentSimulatedDemoFactory factory = null; UUID account = UUID.randomUUID(); boolean inserted = false;
        try {
            var catalogue = new Reviewed2025ControlledCatalogue(options.assets());
            try {
                var jdbc = new JdbcTemplate(source);
                if (jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')", Integer.class) != 0)
                    throw new IllegalStateException("演示只允许全新空数据库");
                Flyway.configure().dataSource(source).locations("classpath:db/migration").outOfOrder(false).load().migrate();
                catalogue.installOnIsolatedDatabase(source);
                jdbc.update("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled,auth_revision) VALUES(?,?,?,'USER',true,0)",
                        account, LOGIN, new BCryptPasswordEncoder().encode(studentPassword)); inserted = true; studentPassword = null;
                var app = new SpringApplication(App.class);
                app.addInitializers(c -> c.getBeanFactory().registerSingleton("demoInputs", new Inputs(options, source, catalogue)));
                context = (ServletWebServerApplicationContext) app.run(arguments(options.port())); contextClosed = false;
                worker = context.getBean("ruleAgentHttpExecutor", ExecutorService.class); identities = context.getBean(PreviewIdentityRepository.class);
                tasks = context.getBean(RuleAgentTaskStore.class); factory = context.getBean(RuleAgentSimulatedDemoFactory.class);
                write(options.run().resolve("host-ready.json"), Map.of("pid", ProcessHandle.current().pid(), "startTime", ProcessHandle.current().info().startInstant().orElseThrow().toString(),
                        "batch", options.batch(), "port", context.getWebServer().getPort(), "mode", "SIMULATED", "providerCalls", 0,
                        "loginName", LOGIN, "question", RuleAgentSimulatedDemoFactory.QUESTION, "productionClasspathOnly", true,
                        "executable", ProcessHandle.current().info().command().orElseThrow()));
                System.out.println("SIMULATED_HOST_READY batch=" + options.batch());
                long end = System.nanoTime() + Duration.ofSeconds(options.leaseSeconds()).toNanos();
                while (!Files.exists(options.run().resolve("STOP_HOST")) && System.nanoTime() < end) Thread.sleep(100);
                exit = Files.exists(options.run().resolve("STOP_HOST")) ? 0 : 124;
            } finally {
                if (tasks != null) tasks.close();
                if (context != null) { context.close(); contextClosed = !context.isActive(); }
                if (worker != null) { worker.shutdown(); workersClosed = worker.awaitTermination(5, TimeUnit.SECONDS); }
                if (identities != null) { identities.close(); var s = identities.status(); identityClosed = s.closed() && s.inFlight() == 0 && s.liveThreads() == 0; }
                // 任务未停时不声称资料已安全卸载；整轮失败交给所有权监督和账本恢复。
                if (!workersClosed || !identityClosed || tasks != null && tasks.activeJobs() != 0) throw new IllegalStateException("宿主工作资源未退出");
                if (inserted) new JdbcTemplate(source).update("DELETE FROM preview_identity WHERE user_id=?", account);
                catalogue.close(); catalogueClosed = true;
            }
        } catch (Exception failure) {
            exit = 1; System.err.println("SIMULATED_HOST_FAILED category=" + failure.getClass().getSimpleName());
        }
        boolean listenerGone;
        try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", options.port()), 300); listenerGone = false; }
        catch (java.io.IOException stopped) { listenerGone = true; }
        boolean closed = contextClosed && workersClosed && identityClosed && catalogueClosed && listenerGone;
        write(options.run().resolve("host-exit.json"), Map.of("exitCode", exit, "contextClosed", contextClosed, "workerExited", workersClosed,
                "identityExited", identityClosed, "catalogueUninstalled", catalogueClosed, "listenerGone", listenerGone,
                "resourcesClosed", closed, "providerCalls", 0, "simulatedPortCalls", factory == null ? 0 : factory.simulatedPortCalls()));
        System.exit(closed ? exit : 1);
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.write(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.CREATE_NEW);
    }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少启动环境：" + key); return value;
    }
}
