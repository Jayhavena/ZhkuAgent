package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.web.RuleCheckPreviewApplication;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 两个全新专用临时 PostgreSQL，真实 HTTP 与独立 JVM；不得指向共享演示库。 */
class RuleCheckDualPostgresRememberMeRestartTest {
    private static final String REMEMBER = "ZHKU_RULE_CHECK_REMEMBER";
    private static final Pattern IDENTITY = Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_identity$");

    /*
     * 专用测试脚本注入：zhku.db.url（JVM 属性，规则库）、
     * ZHKU_TEST_IDENTITY_DB_URL（身份库）、ZHKU_DB_PASSWORD、
     * ZHKU_RULECHECK_IDENTITY_DB_USER、ZHKU_RULECHECK_IDENTITY_DB_PASSWORD，
     * ZHKU_TEST_DUAL_POSTGRES_DISPOSABLE=yes（确认两套实例均为新建一次性库）。
     * URL 不接受参数，规则用户固定 zhkuagent；密码只从环境继承，不写入参数或日志。
     * 空库检查只能辅助防误用，不能代替脚本独立确认两个实例不是共享演示实例。
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void rememberedAccountSurvivesProcessRestartOnlyInIdentityDatabase() throws Exception {
        String ruleUrl = System.getProperty("zhku.db.url");
        String identityUrl = System.getenv("ZHKU_TEST_IDENTITY_DB_URL");
        verifyTargets(ruleUrl, identityUrl);
        String rulePassword = required("ZHKU_DB_PASSWORD");
        String identityUser = required("ZHKU_RULECHECK_IDENTITY_DB_USER");
        if (!identityUser.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) throw new IllegalArgumentException("身份库用户名不合格");
        String identityPassword = required("ZHKU_RULECHECK_IDENTITY_DB_PASSWORD");
        var rule = source(ruleUrl, "zhkuagent", rulePassword);
        var identity = source(identityUrl, identityUser, identityPassword);
        // 在任何迁移、应用启动和账号写入前，两个目标均须是全新的空库。
        requireEmpty(rule);
        requireEmpty(identity);
        String username = "reg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String secret = "Synthetic-" + UUID.randomUUID();
        Child first = null, second = null;
        boolean registrationAttempted = false;
        try {
            first = Child.launch(ruleUrl, identityUrl);
            int port = first.ready();
            String original;
            String userId;
            try (var user = new User(port)) {
                user.csrf();
                String registration = JSON.writeValueAsString(Map.of("username", username, "password", secret, "confirmPassword", secret));
                registrationAttempted = true; // 即使响应丢失，也只能删除这个随机账号。
                assertThat(user.encoded(PREFIX + "/auth/register", "application/json", registration, true).statusCode()).isEqualTo(201);
                String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&password=" + URLEncoder.encode(secret, StandardCharsets.UTF_8) + "&remember-me=true";
                assertThat(user.post("/auth/login", form, true).statusCode()).isEqualTo(200);
                var me = user.get("/auth/me");
                assertThat(me.statusCode()).isEqualTo(200);
                userId = JSON.readTree(me.body()).path("userId").asText();
                UUID.fromString(userId);
                var cookie = remember(user);
                assertThat(cookie.getMaxAge()).isPositive();
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getPath()).isEqualTo(PREFIX);
                original = cookie.getValue();
                assertThat(original).isNotBlank();
            }
            assertIdentityOnly(identity);
            assertThat(countAccount(identity, username)).isEqualTo(1);
            assertThat(countAccount(rule, username)).isZero();
            first.stop();
            second = Child.launch(ruleUrl, identityUrl);
            int nextPort = second.ready();
            assertThat(second.process.pid()).isNotEqualTo(first.process.pid());
            try (var restored = new User(nextPort); var replay = new User(nextPort)) {
                // 不复制内存会话 Cookie，确保恢复来自持久登录凭据。
                putRemember(restored, original);
                var me = restored.get("/auth/me");
                assertThat(me.statusCode()).isEqualTo(200);
                assertThat(me.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                assertThat(JSON.readTree(me.body()).path("userId").asText()).isEqualTo(userId);
                String token = remember(restored).getValue();
                restored.csrf();
                assertThat(restored.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                putRemember(replay, token);
                assertThat(replay.get("/auth/me").statusCode()).isEqualTo(401);
            }
            assertIdentityOnly(identity);
            assertThat(countAccount(identity, username)).isEqualTo(1);
            assertThat(countAccount(rule, username)).isZero();
            second.stop();
        } finally {
            try {
                if (second != null) second.cleanup();
            } finally {
                try {
                    if (first != null) first.cleanup();
                } finally {
                    if (registrationAttempted) {
                        // 不清表、不删除库；仅删除本次合成账号及其级联凭据。
                        try (var c = identity.getConnection(); var p = c.prepareStatement("DELETE FROM preview_identity WHERE login_name=?")) {
                            p.setQueryTimeout(1);
                            p.setString(1, username);
                            p.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少测试环境变量: " + name);
        return value;
    }

    private static void verifyTargets(String ruleUrl, String identityUrl) {
        if (!"yes".equals(System.getenv("ZHKU_TEST_DUAL_POSTGRES_DISPOSABLE")))
            throw new IllegalArgumentException("必须由专用脚本确认两套一次性实例");
        RuleCheckDatabaseTestTarget.verify(ruleUrl);
        var match = IDENTITY.matcher(identityUrl == null ? "" : identityUrl);
        if (!match.matches()) throw new IllegalArgumentException("身份库必须是专用回环目标");
        int identityPort = Integer.parseInt(match.group(1));
        int rulePort = Integer.parseInt(ruleUrl.substring(ruleUrl.lastIndexOf(':') + 1, ruleUrl.lastIndexOf('/')));
        if (identityPort < 1024 || identityPort > 65535 || identityPort == 54329 || identityPort == rulePort)
            throw new IllegalArgumentException("身份库端口必须独立且不得指向演示库");
    }

    private static DriverManagerDataSource source(String url, String user, String password) {
        var source = new DriverManagerDataSource(url, user, password);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "1");
        limits.setProperty("socketTimeout", "2");
        source.setConnectionProperties(limits);
        return source;
    }

    private static void requireEmpty(DriverManagerDataSource source) throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.setQueryTimeout(2);
            try (var r = s.executeQuery("SELECT count(*) FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp_%' AND c.relkind IN ('r','p','v','m','S','f')")) {
                r.next();
                if (r.getLong(1) != 0) throw new IllegalStateException("测试库不是全新空库，拒绝启动");
            }
        }
    }

    private static void assertIdentityOnly(DriverManagerDataSource source) throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.setQueryTimeout(2);
            var names = new java.util.TreeSet<String>();
            try (var r = s.executeQuery("SELECT n.nspname || '.' || c.relname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND n.nspname NOT LIKE 'pg_temp_%' AND c.relkind IN ('r','p','v','m','f')")) {
                while (r.next()) names.add(r.getString(1));
            }
            assertThat(names).containsExactly("public.flyway_schema_history", "public.preview_identity", "public.preview_persistent_login");
        }
    }

    private static long countAccount(DriverManagerDataSource source, String username) throws Exception {
        try (var c = source.getConnection(); var p = c.prepareStatement("SELECT count(*) FROM preview_identity WHERE login_name=?")) {
            p.setQueryTimeout(2);
            p.setString(1, username);
            try (var r = p.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }

    private static HttpCookie remember(User user) {
        return user.cookies.getCookieStore().getCookies().stream().filter(c -> REMEMBER.equals(c.getName())).findFirst().orElseThrow();
    }

    private static void putRemember(User user, String value) {
        var cookie = new HttpCookie(REMEMBER, value);
        cookie.setPath(PREFIX);
        user.cookies.getCookieStore().add(URI.create("http://127.0.0.1:" + user.port + PREFIX), cookie);
    }

    private static final class Child {
        final Process process;
        final ArrayBlockingQueue<String> phases = new ArrayBlockingQueue<>(8);
        final Thread reader;
        Child(Process process) {
            this.process = process;
            reader = Thread.ofPlatform().start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = lines.readLine()) != null; )
                        if (line.matches("DUAL_(READY [0-9]+ [0-9]+|CLOSED|FAILED [A-Za-z0-9]+)")) phases.offer(line);
                } catch (java.io.IOException ignored) { }
            });
        }
        static Child launch(String ruleUrl, String identityUrl) throws Exception {
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), Probe.class.getName()).redirectErrorStream(true);
            builder.environment().put("ZHKU_TEST_DUAL_RULE_DB_URL", ruleUrl);
            builder.environment().put("ZHKU_TEST_IDENTITY_DB_URL", identityUrl);
            return new Child(builder.start());
        }
        String await(String expected, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String line = phases.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (line == null) break;
                if (line.startsWith("DUAL_FAILED")) throw new AssertionError(line);
                if (line.startsWith(expected)) return line;
            }
            throw new AssertionError("子进程阶段超时: " + expected);
        }
        int ready() throws Exception {
            String[] parts = await("DUAL_READY", Duration.ofSeconds(30)).split(" ");
            assertThat(Long.parseLong(parts[1])).isEqualTo(process.pid());
            return Integer.parseInt(parts[2]);
        }
        void stop() throws Exception {
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            await("DUAL_CLOSED", Duration.ofSeconds(2));
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse();
        }
        void cleanup() throws Exception {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("子进程未退出");
            }
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse();
        }
    }

    /** 不替换正式应用或身份仓库，子进程单独重复目标校验。 */
    public static final class Probe {
        public static void main(String[] args) {
            ServletWebServerApplicationContext context = null;
            try {
                if (args.length != 0) throw new IllegalArgumentException("不接受参数");
                String ruleUrl = System.getenv("ZHKU_TEST_DUAL_RULE_DB_URL");
                String identityUrl = System.getenv("ZHKU_TEST_IDENTITY_DB_URL");
                verifyTargets(ruleUrl, identityUrl);
                required("ZHKU_DB_PASSWORD");
                required("ZHKU_RULECHECK_IDENTITY_DB_USER");
                required("ZHKU_RULECHECK_IDENTITY_DB_PASSWORD");
                context = (ServletWebServerApplicationContext) RuleCheckPreviewApplication.application().run(
                        "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--zhku.rule-check-preview.enabled=true", "--zhku.rule-check-preview.initialize=true",
                        "--zhku.db.url=" + ruleUrl, "--zhku.db.username=zhkuagent",
                        "--zhku.rule-check-preview.persistent-identity.enabled=true", "--zhku.rule-check-preview.persistent-identity.url=" + identityUrl,
                        "--zhku.rule-check-preview.profile-id=synthetic-http", "--zhku.rule-check-preview.chunking-version=synthetic-v1",
                        "--zhku.rule-check-preview.required-scope=PUBLIC_ELECTIVE_CREDIT_LIMIT", "--zhku.rule-check-preview.reference-revision=2025.9.2",
                        "--zhku.rule-check-preview.candidate-limit=40", "--zhku.rule-check-preview.result-limit=5");
                var identity = context.getBean(PreviewIdentityRepository.class);
                System.out.println("DUAL_READY " + ProcessHandle.current().pid() + " " + context.getWebServer().getPort());
                System.out.flush();
                if (!"STOP".equals(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine())) throw new IllegalArgumentException("关闭协议不符");
                context.close();
                context = null;
                if (!identity.status().closed() || identity.status().inFlight() != 0 || identity.status().liveThreads() != 0)
                    throw new IllegalStateException("身份仓库未关闭");
                System.out.println("DUAL_CLOSED");
                System.out.flush();
            } catch (Throwable failure) {
                System.out.println("DUAL_FAILED " + failure.getClass().getSimpleName());
                System.out.flush();
                if (context != null) context.close();
                System.exit(1);
            }
        }
    }
}
