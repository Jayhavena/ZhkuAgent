package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.web.RuleCheckPreviewApplication;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.Socket;
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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 真实 v2 HTTP 与隔离 PostgreSQL，跨两个独立 JVM 验证长期登录。 */
class RuleCheckRememberMeHttpRestartTest {
    private static final String REMEMBER = "ZHKU_RULE_CHECK_REMEMBER";

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void registeredRememberedUserSurvivesRestartAndLogoutRevokesToken() throws Exception {
        String url = System.getProperty("zhku.db.url");
        // 校验先于任何连接、迁移和子进程；绝不能回退共享演示库。
        RuleCheckDatabaseTestTarget.verify(url);
        String password = System.getenv("ZHKU_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("缺少数据库环境凭据");
        var source = new DriverManagerDataSource(url, "zhkuagent", password);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "1");
        limits.setProperty("socketTimeout", "2");
        source.setConnectionProperties(limits);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").outOfOrder(false).load().migrate();

        String username = "reg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String secret = "Synthetic-" + UUID.randomUUID();
        Child first = null, second = null;
        try {
            first = Child.launch(url);
            int port = first.ready();
            String original;
            String userId;
            try (var user = new User(port)) {
                user.csrf();
                String registration = JSON.writeValueAsString(Map.of("username", username, "password", secret, "confirmPassword", secret));
                assertThat(user.encoded(PREFIX + "/auth/register", "application/json", registration, false).statusCode()).isEqualTo(403);
                assertThat(user.encoded(PREFIX + "/auth/register", "application/json", registration, true).statusCode()).isEqualTo(201);
                assertThat(user.get("/auth/me").statusCode()).isEqualTo(401);
                String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&password=" + URLEncoder.encode(secret, StandardCharsets.UTF_8) + "&remember-me=true";
                assertThat(user.post("/auth/login", form, false).statusCode()).isEqualTo(403);
                assertThat(user.post("/auth/login", form, true).statusCode()).isEqualTo(200);
                var me = user.get("/auth/me");
                assertThat(me.statusCode()).isEqualTo(200);
                assertThat(me.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                userId = JSON.readTree(me.body()).path("userId").asText();
                UUID.fromString(userId);
                var cookie = remember(user);
                assertThat(cookie.getMaxAge()).isPositive();
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getPath()).isEqualTo(PREFIX);
                original = cookie.getValue();
                assertThat(original.isBlank()).isFalse();
            }
            first.stop();
            second = Child.launch(url);
            int nextPort = second.ready();
            assertThat(second.process.pid()).isNotEqualTo(first.process.pid());
            try (var restored = new User(nextPort); var replay = new User(nextPort)) {
                // 只复制长期凭据，不能让失效的内存会话冒充跨进程恢复。
                putRemember(restored, original);
                var me = restored.get("/auth/me");
                assertThat(me.statusCode()).isEqualTo(200);
                assertThat(me.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                assertThat(JSON.readTree(me.body()).path("userId").asText()).isEqualTo(userId);
                // 不规定令牌轮换策略；关键是恢复和登出后服务端撤销。
                String restoredToken = remember(restored).getValue();
                restored.csrf();
                assertThat(restored.post("/auth/logout", "", false).statusCode()).isEqualTo(403);
                assertThat(restored.get("/auth/me").statusCode()).isEqualTo(200);
                assertThat(restored.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                assertThat(restored.get("/auth/me").statusCode()).isEqualTo(401);
                // 在全新浏览器中重放登出前最新 token，排除仅清 Cookie 而未撤销服务端凭据。
                putRemember(replay, restoredToken);
                assertThat(replay.get("/auth/me").statusCode()).isEqualTo(401);
                try (var stale = new User(nextPort)) {
                    putRemember(stale, original);
                    assertThat(stale.get("/auth/me").statusCode()).isEqualTo(401);
                }
            }
            second.stop();
        } finally {
            try {
                if (second != null) second.cleanup();
            } finally {
                try {
                    if (first != null) first.cleanup();
                } finally {
                    // 仅删除本测试随机注册的身份；外键级联清理它的长期凭据。
                    try (var connection = source.getConnection(); var statement = connection.prepareStatement("DELETE FROM preview_identity WHERE login_name=?")) {
                        statement.setQueryTimeout(1);
                        statement.setString(1, username);
                        statement.executeUpdate();
                    }
                }
            }
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
        int port;

        private Child(Process process) {
            this.process = process;
            reader = Thread.ofPlatform().name("rulecheck-restart-output").start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = lines.readLine()) != null; ) {
                        if (line.matches("RULE_RESTART_(READY [0-9]+ [0-9]+|CLOSED [0-9]+ [0-9]+|FAILED [A-Za-z0-9]+)")) phases.offer(line);
                    }
                } catch (java.io.IOException ignored) { }
            });
        }

        static Child launch(String url) throws Exception {
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), Probe.class.getName())
                    .redirectErrorStream(true);
            builder.environment().put("ZHKU_TEST_RESTART_DB_URL", url);
            return new Child(builder.start());
        }

        String await(String expected, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String line = phases.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (line == null) break;
                if (line.startsWith("RULE_RESTART_FAILED")) throw new AssertionError(line);
                if (line.startsWith(expected)) return line;
            }
            throw new AssertionError("子进程阶段超时: " + expected);
        }

        int ready() throws Exception {
            String[] parts = await("RULE_RESTART_READY", Duration.ofSeconds(30)).split(" ");
            assertThat(Long.parseLong(parts[1])).isEqualTo(process.pid());
            return port = Integer.parseInt(parts[2]);
        }

        void stop() throws Exception {
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            String[] parts = await("RULE_RESTART_CLOSED", Duration.ofSeconds(2)).split(" ");
            assertThat(Integer.parseInt(parts[1])).isZero();
            assertThat(Integer.parseInt(parts[2])).isZero();
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse();
            portClosed();
        }

        void cleanup() throws Exception {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("子进程未退出");
            }
            reader.join(2000);
            assertThat(reader.isAlive()).isFalse();
            if (port != 0) portClosed();
        }

        void portClosed() throws Exception {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                throw new AssertionError("子进程退出后端口仍在监听");
            } catch (java.io.IOException expected) { }
        }
    }

    /** 测试专用子进程入口；使用正式应用和安全链，不提供任何替身。 */
    public static final class Probe {
        public static void main(String[] args) {
            ServletWebServerApplicationContext context = null;
            try {
                if (args.length != 0) throw new IllegalArgumentException("不接受参数");
                String url = System.getenv("ZHKU_TEST_RESTART_DB_URL");
                RuleCheckDatabaseTestTarget.verify(url);
                if (System.getenv("ZHKU_DB_PASSWORD") == null || System.getenv("ZHKU_DB_PASSWORD").isBlank())
                    throw new IllegalArgumentException("缺少数据库环境凭据");
                context = (ServletWebServerApplicationContext) RuleCheckPreviewApplication.application().run(
                        "--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--zhku.rule-check-preview.enabled=true", "--zhku.rule-check-preview.initialize=true",
                        "--zhku.db.url=" + url, "--zhku.db.username=zhkuagent",
                        "--zhku.rule-check-preview.profile-id=synthetic-http", "--zhku.rule-check-preview.chunking-version=synthetic-v1",
                        "--zhku.rule-check-preview.required-scope=PUBLIC_ELECTIVE_CREDIT_LIMIT", "--zhku.rule-check-preview.reference-revision=2025.9.2",
                        "--zhku.rule-check-preview.candidate-limit=40", "--zhku.rule-check-preview.result-limit=5");
                var identity = context.getBean(PreviewIdentityRepository.class);
                System.out.println("RULE_RESTART_READY " + ProcessHandle.current().pid() + " " + context.getWebServer().getPort());
                System.out.flush();
                if (!"STOP".equals(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine()))
                    throw new IllegalArgumentException("关闭协议不符");
                context.close();
                context = null;
                if (!identity.status().closed()) throw new IllegalStateException("身份仓库未关闭");
                System.out.println("RULE_RESTART_CLOSED " + identity.status().inFlight() + " " + identity.status().liveThreads());
                System.out.flush();
            } catch (Throwable failure) {
                System.out.println("RULE_RESTART_FAILED " + failure.getClass().getSimpleName());
                System.out.flush();
                if (context != null) context.close();
                System.exit(1);
            }
        }
    }
}
