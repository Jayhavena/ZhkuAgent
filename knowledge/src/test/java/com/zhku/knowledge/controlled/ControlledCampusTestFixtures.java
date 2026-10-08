package com.zhku.knowledge.controlled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 仅测试源码持有合成账号、模型及Cookie；没有生产开关，也不发送供应商请求。 */
final class ControlledCampusTestFixtures {
    static final String PREFIX = "/api/zhku/controlled-preview/v1";
    static final String COURSES = "[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5},"
            + "{\"courseId\":\"HIS02\",\"category\":\"公共选修课\",\"credits\":2}]";
    static final String QUESTION = "这两门虚构公共选修课学分合计以及每学期上限是多少？";
    static final ObjectMapper JSON = new ObjectMapper();

    record Account(UUID id, String login, String password) { }
    record Data(DataSource source, Account a, Account b, Account maintainer, UUID sourceId) { }

    static Data seed() throws Exception {
        ArchiveTestDatabase.migrate();
        DataSource source = ArchiveTestDatabase.dataSource();
        var properties = new java.util.Properties();
        properties.setProperty("connectTimeout", "1"); properties.setProperty("socketTimeout", "2");
        properties.setProperty("cancelSignalTimeout", "1");
        ((DriverManagerDataSource) source).setConnectionProperties(properties);
        var accounts = new ArrayList<Account>();
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            for (String login : List.of("user-a", "user-b", "maintainer")) {
                var account = new Account(UUID.randomUUID(), login, "Synthetic-" + UUID.randomUUID());
                accounts.add(account);
                try (var statement = connection.prepareStatement("""
                        INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled,auth_revision)
                        VALUES(?,?,?,?,true,0)
                        """)) {
                    statement.setObject(1, account.id()); statement.setString(2, login);
                    statement.setString(3, new BCryptPasswordEncoder(10).encode(account.password()));
                    statement.setString(4, login.equals("maintainer") ? "MAINTAINER" : "USER");
                    statement.setQueryTimeout(1); statement.executeUpdate();
                }
            }
            UUID id = insertSource(connection);
            connection.commit();
            return new Data(source, accounts.get(0), accounts.get(1), accounts.get(2), id);
        }
    }

    static void cleanup(Data data) throws Exception {
        if (data == null) return;
        // 仅删除本夹具生成UUID的合成行；不重置整库，不删除迁移或其他测试数据。
        try (var connection = data.source().getConnection()) {
            connection.setAutoCommit(false);
            for (var account : List.of(data.a(), data.b(), data.maintainer())) {
                try (var statement = connection.prepareStatement("DELETE FROM campus_agent_run_submission WHERE owner_user_id=?")) {
                    statement.setObject(1, account.id()); statement.setQueryTimeout(1); statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("DELETE FROM campus_agent_run_archive WHERE owner_user_id=?")) {
                    statement.setObject(1, account.id()); statement.setQueryTimeout(1); statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("DELETE FROM preview_identity WHERE user_id=?")) {
                    statement.setObject(1, account.id()); statement.setQueryTimeout(1); statement.executeUpdate();
                }
            }
            for (String sql : List.of("DELETE FROM knowledge_chunk WHERE source_id=?", "DELETE FROM knowledge_source WHERE id=?")) {
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, data.sourceId()); statement.setQueryTimeout(1); statement.executeUpdate();
                }
            }
            connection.commit();
        }
    }

    private static UUID insertSource(Connection connection) throws Exception {
        UUID id = UUID.randomUUID();
        try (var statement = connection.prepareStatement("""
                INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,
                content_checksum,visibility_scope,publication_scope,sharing_rights_confirmed,document_family,knowledge_scope)
                VALUES(?,'合成学分规则','PDF','synthetic-v1','PUBLISHED',clock_timestamp(),?,'STUDENT_VISIBLE',
                '合成本科生',true,'STUDENT_HANDBOOK','PUBLIC_ELECTIVE_CREDIT_LIMIT')
                """)) {
            statement.setObject(1, id); statement.setString(2, "0".repeat(64));
            statement.setQueryTimeout(1); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,content,content_checksum)
                VALUES(?,?,0,'合成第1条','每学期公共选修课最高不得超过 4 学分',?)
                """)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, id);
            statement.setString(3, "0".repeat(64)); statement.setQueryTimeout(1); statement.executeUpdate();
        }
        return id;
    }

    static Running start(Data data, Model model) {
        return start(data,model,context->{});
    }
    static Running start(Data data,org.springframework.ai.chat.model.ChatModel model,
            java.util.function.Consumer<GenericApplicationContext> extra) {
        long started = System.nanoTime();
        var application = ControlledPreviewApplication.application();
        application.addInitializers(context -> {
            var generic=(GenericApplicationContext)context;
            generic.registerBean("controlledTestModel",ChatModel.class,()->model);extra.accept(generic);
        });
        var source = (DriverManagerDataSource) data.source();
        var context = (ServletWebServerApplicationContext) application.run(
                "--server.port=0", "--server.address=127.0.0.1", "--server.servlet.context-path=/api",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--zhku.controlled-preview.enabled=true", "--zhku.campus-agent.enabled=true",
                "--zhku.campus-agent.archive.enabled=true", "--zhku.db.url=" + source.getUrl(),
                "--zhku.db.username=" + source.getUsername());
        var running = new Running(context);
        if (System.nanoTime() - started > Duration.ofSeconds(20).toNanos()) {
            running.close(); throw new AssertionError("20秒启动预算耗尽");
        }
        return running;
    }

    static final class Running implements AutoCloseable {
        final ServletWebServerApplicationContext context;
        final int port;
        final DataSource source;
        Running(ServletWebServerApplicationContext context) {
            this.context = context; port = context.getWebServer().getPort();
            source = context.getBean(DataSource.class);
        }
        User user() { return new User(port); }
        @Override public void close() { close(() -> { }); }
        void close(Runnable releaseFixture) {
            try { context.close(); }
            finally { releaseFixture.run(); }
            // shutdown/terminated不是物理资源退出；继续确认实际线程和本次监听均消失。
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (System.nanoTime() < deadline && !ownedThreads().isEmpty()) {
                try { TimeUnit.MILLISECONDS.sleep(10); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new AssertionError("资源退出观察被中断");
                }
            }
            if (!ownedThreads().isEmpty()) {
                String category = ownedThreads().stream().allMatch(thread -> thread.getName().equals("zhku-campus-agent-timeout"))
                        ? "TIMER" : "OTHER";
                throw new AssertionError("CONTROLLED_THREADS_REMAINING_" + category);
            }
            int connections;
            do {
                connections = remainingConnections();
                if (connections == 0) break;
                try { TimeUnit.MILLISECONDS.sleep(10); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("连接退出观察被中断"); }
            } while (System.nanoTime() < deadline);
            if (connections != 0) throw new AssertionError("受控连接未在固定预算内退出");
            try (var socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                throw new AssertionError("受控监听关闭后仍可连接");
            } catch (java.io.IOException expectedClosed) { }
        }
        private List<Thread> ownedThreads() {
            return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
                    .filter(thread -> thread.getName().startsWith("zhku-campus-agent")
                            || thread.getName().startsWith("zhku-preview-identity")
                            || thread.getName().startsWith("campus-archive-"))
                    .toList();
        }
        private int remainingConnections() {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                try (var result = statement.executeQuery("""
                        SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                          AND backend_type='client backend' AND pid<>pg_backend_pid()
                        """)) {
                    result.next(); return result.getInt(1);
                }
            } catch (java.sql.SQLException unavailable) { throw new AssertionError("受控连接退出未确认"); }
        }
    }

    static final class User implements AutoCloseable {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
        final int port;
        private String csrfHeader;
        private String csrfParameter;
        private String csrf;
        User(int port) { this.port = port; }
        HttpResponse<String> send(String method, String path, String body, boolean withCsrf) throws Exception {
            return sendWithKey(method,path,body,withCsrf,null);
        }
        HttpResponse<String> sendWithKey(String method, String path, String body, boolean withCsrf,UUID key) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5));
            if (withCsrf) builder.header(csrfHeader, csrf);
            if (key != null) builder.header("Idempotency-Key",key.toString());
            if (body != null) builder.header("Content-Type", "application/json");
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        void refreshCsrf() throws Exception {
            var response = send("GET", PREFIX + "/auth/csrf", null, false);
            if (response.statusCode() != 200) throw new AssertionError("CSRF端点状态=" + response.statusCode());
            var json = JSON.readTree(response.body());
            csrfHeader = json.path("headerName").asText(); csrfParameter = json.path("parameterName").asText();
            csrf = json.path("token").asText();
        }
        HttpResponse<String> login(Account account) throws Exception {
            refreshCsrf();
            String form = "username=" + encode(account.login()) + "&password=" + encode(account.password())
                    + "&" + encode(csrfParameter) + "=" + encode(csrf);
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PREFIX + "/auth/login"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) refreshCsrf();
            return response;
        }
        UUID submit(String question, String courses) throws Exception {
            var response=submitWithKey(question,courses,UUID.randomUUID());
            if (response.statusCode() != 202) throw new AssertionError("受理状态=" + response.statusCode());
            return UUID.fromString(JSON.readTree(response.body()).path("runId").asText());
        }
        HttpResponse<String> submitWithKey(String question,String courses,UUID key)throws Exception {
            String body = JSON.writeValueAsString(Map.of("question", question))
                    .replaceFirst("}$", ",\"courses\":" + courses + "}");
            return sendWithKey("POST",PREFIX+"/runs",body,true,key);
        }
        JsonNode awaitTerminal(UUID run) throws Exception {
            return awaitTerminal(run, Duration.ofSeconds(10));
        }
        JsonNode awaitTerminal(UUID run, Duration window) throws Exception {
            if (!window.equals(Duration.ofSeconds(10)) && !window.equals(Duration.ofSeconds(35)))
                throw new IllegalArgumentException("只允许普通10秒及已冻结超时30+5秒观察窗");
            long deadline = System.nanoTime() + window.toNanos();
            do {
                var response = send("GET", PREFIX + "/runs/" + run, null, false);
                if (response.statusCode() != 200) throw new AssertionError("轮询状态=" + response.statusCode());
                var body = JSON.readTree(response.body());
                if (body.path("run").path("terminated").asBoolean()) return body;
                TimeUnit.MILLISECONDS.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("固定轮询预算耗尽");
        }
        @Override public void close() { client.close(); cookies.getCookieStore().removeAll(); csrf = null; }
        void copyCookiesTo(User target) {
            URI origin = URI.create("http://127.0.0.1:" + target.port + PREFIX);
            cookies.getCookieStore().getCookies().forEach(cookie ->
                    target.cookies.getCookieStore().add(origin, (java.net.HttpCookie) cookie.clone()));
        }
        private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    }

    static final class Model implements ChatModel {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean block;
        volatile boolean fail;
        volatile boolean clarify;
        volatile boolean disallowedTool;
        Runnable onEntered = () -> { };
        @Override public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet(); active.incrementAndGet();
            try {
                if (block || prompt.getContents().contains("受控阻塞验证")) {
                    entered.countDown(); onEntered.run();
                    try { release.await(); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException("合成中断"); }
                }
                if (fail) throw new IllegalStateException("合成模型故障");
                if (clarify) return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
                if (disallowedTool) return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(),
                        List.of(new AssistantMessage.ToolCall("synthetic-rejected", "function", "execute_sql", "{}"))))));
                // 依据本次Prompt的查询事实选工具，不用全局奇偶计数串扰不同Run。
                boolean calculated = prompt.getContents().contains("政策查询结果：");
                var call = new AssistantMessage.ToolCall("synthetic-" + UUID.randomUUID(), "function",
                        calculated ? "summarize_course_credits" : "lookup_public_elective_credit_limit",
                        calculated ? "{\"courses\":" + COURSES + "}" : "{}");
                return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
            } finally { active.decrementAndGet(); }
        }
    }
}
