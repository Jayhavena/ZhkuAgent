package com.zhku.knowledge.controlled;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;

/** 人工演示宿主只在test classpath运行；不向生产入口加入假模型或治理能力。 */
public final class ControlledManualDemo implements AutoCloseable {
    private static final String CONVERSATIONS = PREFIX + "/conversations";
    private final Data data;
    private final UUID retake = UUID.randomUUID();
    private final PrintWriter output;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ServiceProcess service;
    private String selected = "a";
    private UUID lastRun;
    private UUID lastSession;
    private long revision;

    record Outcome(String action, Integer httpStatus, JsonNode body) { }

    static ControlledManualDemo open(PrintWriter output) throws Exception {
        // 先用既有专用库守卫验目标；账号表非空时拒绝复用，不能认领或重置他人的库。
        ArchiveTestDatabase.migrate();
        try (var connection = ArchiveTestDatabase.dataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(1);
            try (var result = statement.executeQuery("SELECT count(*) FROM preview_identity")) {
                result.next();
                if (result.getLong(1) != 0) throw new IllegalArgumentException("人工演示要求全新的专用临时库");
            }
        }
        var demo = new ControlledManualDemo(seed(), output);
        try {
            ControlledConversationFacadeTest.seedRetake(demo.data.source(), demo.retake);
            demo.startService();
            return demo;
        } catch (Exception | Error failure) {
            try { demo.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private ControlledManualDemo(Data data, PrintWriter output) { this.data = data; this.output = output; }

    private void startService() throws Exception {
        String password = System.getenv("ZHKU_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("缺少本次子进程环境凭据");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ConversationHttpRestartProbe.class.getName()).redirectErrorStream(true);
        builder.environment().put("ZHKU_DB_URL", ((DriverManagerDataSource) data.source()).getUrl());
        builder.environment().put("ZHKU_DB_USER", "zhkuagent");
        // 即使启动器继承过供应商配置，演示子进程也只允许既有test模型依赖。
        builder.environment().remove("DASHSCOPE_API_KEY");
        service = new ServiceProcess(builder.start());
        int port = service.ready();
        for (String name : new String[] { "a", "b", "m" }) users.put(name, new User(port));
        output.println("服务已就绪 PID=" + service.process.pid() + " URL=http://127.0.0.1:" + port + PREFIX);
        output.println("测试替身 / 合成资料 / 当前账号A（尚未登录）。账号密码、Cookie和CSRF不显示。默认演示上限30分钟。");
        output.flush();
    }

    void help() {
        output.println("a/b/m 切换独立账号（不会自动登录）；1 登录；2 提交固定3.5学分任务；3 查询最近Run；4 取消最近Run；");
        output.println("5 本人历史；6 最近Run历史详情；7 新建澄清Session；8 提问重修（需澄清）；9 回复专业选修课；");
        output.println("10 查询最近Session；11 取消Session；12 故意用revision=0补答；13 真正重启服务（旧Cookie失效，须再选1）；");
        output.println("14 撤回合成学分资料；15 恢复学分资料；16 撤回合成重修资料；17 恢复重修资料；h 帮助；q 退出并清理。");
        output.println("最近Run/Session ID在账号间共用作越权对照；授权始终由服务器判断。撤回命令是宿主准备动作，不是维护者API权限。");
        output.flush();
    }

    Outcome execute(String command) throws Exception {
        if (users.containsKey(command)) {
            selected = command;
            return control("切换账号", Map.of("account", selected, "autoLogin", false));
        }
        var user = users.get(selected);
        // 尚未登录时也走真实CSRF链，不能因客户端没有token而在本地抛NPE、掩盖401/403。
        if (java.util.Set.of("2", "4", "7", "8", "9", "11", "12").contains(command)) user.refreshCsrf();
        return switch (command) {
            case "1" -> reply("登录", user.login(account()));
            case "2" -> submit(user);
            case "3" -> reply("查询Run", user.send("GET", PREFIX + "/runs/" + requireRun(), null, false));
            case "4" -> reply("取消Run", user.send("POST", PREFIX + "/runs/" + requireRun() + "/cancel", "{}", true));
            case "5" -> reply("本人历史", user.send("GET", PREFIX + "/history?limit=20", null, false));
            case "6" -> reply("历史详情", user.send("GET", PREFIX + "/history/" + requireRun(), null, false));
            case "7" -> createSession(user);
            case "8" -> message(user, revision, "选修课挂科了可以重修吗？");
            case "9" -> message(user, revision, "专业选修课");
            case "10" -> state(user);
            case "11" -> cancelSession(user);
            case "12" -> message(user, 0, "专业选修课");
            case "13" -> restart();
            case "14", "15" -> visibility(data.sourceId(), command.equals("15"));
            case "16", "17" -> visibility(retake, command.equals("17"));
            case "h" -> { help(); yield control("帮助", Map.of()); }
            default -> throw new IllegalArgumentException("只接受菜单中的固定命令");
        };
    }

    private Account account() {
        return switch (selected) { case "a" -> data.a(); case "b" -> data.b(); default -> data.maintainer(); };
    }

    private Outcome submit(User user) throws Exception {
        String body = JSON.writeValueAsString(Map.of("question", QUESTION, "courses", JSON.readTree(COURSES)));
        var accepted = reply("受理Run", user.send("POST", PREFIX + "/runs", body, true));
        if (accepted.httpStatus() != 202) return accepted;
        lastRun = UUID.fromString(accepted.body().path("run").path("runId").asText());
        // 替身正常任务很快收敛；轮询仍只观察事实，最多10秒，不自动重试提交。
        JsonNode terminal = user.awaitTerminal(lastRun);
        return emit(new Outcome("Run终态查询", 200, terminal));
    }

    private Outcome createSession(User user) throws Exception {
        var created = reply("创建Session", user.send("POST", CONVERSATIONS, "{}", true));
        if (created.httpStatus() == 201) {
            lastSession = UUID.fromString(created.body().path("sessionId").asText()); revision = created.body().path("revision").asLong();
        }
        return created;
    }

    private Outcome state(User user) throws Exception {
        var state = reply("查询Session", user.send("GET", CONVERSATIONS + "/" + requireSession(), null, false));
        if (state.httpStatus() == 200) revision = state.body().path("revision").asLong();
        return state;
    }

    private Outcome message(User user, long expectedRevision, String text) throws Exception {
        var result = reply("澄清消息", user.send("POST", CONVERSATIONS + "/" + requireSession() + "/messages",
                JSON.writeValueAsString(Map.of("message", text, "expectedRevision", expectedRevision)), true));
        if (result.httpStatus() == 200) state(user); // 答复DTO不含revision；从真实GET读取确认状态。
        return result;
    }

    private Outcome cancelSession(User user) throws Exception {
        var result = reply("取消Session", user.send("POST", CONVERSATIONS + "/" + requireSession() + "/cancel",
                JSON.writeValueAsString(Map.of("expectedRevision", revision)), true));
        if (result.httpStatus() == 200) revision = result.body().path("revision").asLong();
        return result;
    }

    private Outcome restart() throws Exception {
        long oldPid = service.process.pid();
        var previous = new LinkedHashMap<>(users);
        service.stop();
        users.clear();
        try {
            startService();
            for (var entry : previous.entrySet()) entry.getValue().copyCookiesTo(users.get(entry.getKey()));
        } finally { previous.values().forEach(User::close); }
        if (oldPid == service.process.pid()) throw new AssertionError("重启没有更换PID");
        var oldCookie = reply("重启后旧Cookie探测", users.get(selected).send("GET", PREFIX + "/auth/me", null, false));
        if (oldCookie.httpStatus() != 401) throw new AssertionError("重启后旧Cookie仍获授权");
        return control("实际进程重启", Map.of("oldPid", oldPid, "newPid", service.process.pid(), "oldCookieStatus", 401,
                "requiresLogin", true, "databasePreserved", true));
    }

    private Outcome visibility(UUID id, boolean visible) throws Exception {
        // 仅本次合成UUID的测试准备权限；不会新增或冒用任何校园治理HTTP入口。
        try (var connection = data.source().getConnection(); var statement = connection.prepareStatement(
                "UPDATE knowledge_source SET visibility_scope=?,sharing_rights_confirmed=? WHERE id=?")) {
            statement.setQueryTimeout(1); statement.setString(1, visible ? "STUDENT_VISIBLE" : "INTERNAL_ONLY");
            statement.setBoolean(2, visible); statement.setObject(3, id);
            if (statement.executeUpdate() != 1) throw new AssertionError("本次合成来源不存在");
        }
        return control("合成资料准备", Map.of("sourceId", id, "studentVisible", visible));
    }

    private UUID requireRun() { if (lastRun == null) throw new IllegalArgumentException("请先用USER登录并提交Run"); return lastRun; }
    private UUID requireSession() { if (lastSession == null) throw new IllegalArgumentException("请先用USER登录并创建Session"); return lastSession; }
    private Outcome reply(String action, HttpResponse<String> response) throws Exception {
        JsonNode body = response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
        return emit(new Outcome(action, response.statusCode(), body));
    }
    private Outcome control(String action, Map<String, ?> fields) { return emit(new Outcome(action, null, JSON.valueToTree(fields))); }
    private Outcome emit(Outcome outcome) {
        output.println("[账号" + selected.toUpperCase() + "] " + outcome.action()
                + (outcome.httpStatus() == null ? "（宿主控制，非HTTP接口）" : " HTTP " + outcome.httpStatus()));
        output.println(outcome.body().toPrettyString()); output.flush(); return outcome;
    }

    @Override public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) return;
        try {
            if (service != null) service.close();
        } finally {
            users.values().forEach(User::close); users.clear();
            ControlledConversationFacadeTest.cleanupConversations(data, retake);
            ControlledCampusTestFixtures.cleanup(data);
        }
        output.println("MANUAL_DEMO_CLOSED：服务PID、监听、业务线程和连接已退出；本次合成行已清理。"); output.flush();
    }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--smoke"))) throw new IllegalArgumentException("只允许--smoke");
        var output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        ControlledManualDemo demo = null; Thread hook = null;
        try {
            demo = open(output); var active = demo;
            hook = new Thread(() -> { try { active.close(); } catch (Exception failure) { System.err.println("MANUAL_DEMO_SHUTDOWN_FAILED"); } }, "manual-demo-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            demo.help();
            if (args.length == 1) {
                for (String command : SMOKE_COMMANDS) demo.execute(command);
            } else {
                int minutes = Integer.parseInt(System.getenv().getOrDefault("ZHKU_MANUAL_DEMO_MINUTES", "30"));
                if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("人工演示上限须为1至30分钟");
                // 前台输入也有总预算；daemon读取线程最多一个，EOF/队列满时结束输入，不扩容。
                var input = new ArrayBlockingQueue<String>(16);
                Thread.ofPlatform().daemon().name("manual-demo-input").start(() -> {
                    try (var lines = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                        for (String line; (line = lines.readLine()) != null;) if (!input.offer(line.strip().toLowerCase())) return;
                    } catch (IOException ignored) { }
                    finally { try { input.put("q"); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); } }
                });
                long deadline = System.nanoTime() + Duration.ofMinutes(minutes).toNanos();
                while (System.nanoTime() < deadline) {
                    output.println("输入命令（当前" + demo.selected.toUpperCase() + "）：");
                    String command = input.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    if (command == null || command.equals("q")) break;
                    try { demo.execute(command); }
                    catch (IllegalArgumentException invalid) { output.println("操作未执行：" + invalid.getMessage()); }
                }
            }
            demo.close(); demo = null;
            Runtime.getRuntime().removeShutdownHook(hook); hook = null;
        } catch (Exception | Error failure) {
            output.println("MANUAL_DEMO_FAILED " + failure.getClass().getSimpleName());
            if (demo != null) try { demo.close(); } catch (Exception cleanup) { output.println("MANUAL_DEMO_CLEANUP_FAILED"); }
            System.exit(1);
        }
    }

    static final String[] SMOKE_COMMANDS = { "1", "2", "3", "5", "6", "b", "1", "3", "4", "6", "m", "1", "3", "5",
            "a", "7", "8", "12", "14", "3", "6", "13", "10", "1", "6", "10", "9", "7", "8", "11", "9", "15" };

    /** 与旧探针相同有限阶段协议；允许正常模型调用计数非零，但active/线程/连接必须归零。 */
    private static final class ServiceProcess implements AutoCloseable {
        final Process process;
        final ArrayBlockingQueue<String> phases = new ArrayBlockingQueue<>(16);
        final Thread reader;
        int port;
        boolean stopped;
        ServiceProcess(Process process) {
            this.process = process;
            reader = Thread.ofPlatform().name("manual-demo-service-output").start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = lines.readLine()) != null;)
                        if (line.matches("CONVERSATION_(READY [0-9]+ [0-9]+|CLOSED [0-9]+ [0-9]+ [0-9]+ [0-9]+|FAILED [A-Za-z0-9]+)"))
                            if (!phases.offer(line)) return;
                } catch (IOException closed) { }
            });
        }
        int ready() throws Exception {
            String[] line = await("CONVERSATION_READY", Duration.ofSeconds(20)).split(" ");
            if (Long.parseLong(line[1]) != process.pid()) throw new AssertionError("子进程PID不符");
            return port = Integer.parseInt(line[2]);
        }
        String await(String prefix, Duration budget) throws Exception {
            long deadline = System.nanoTime() + budget.toNanos();
            while (System.nanoTime() < deadline) {
                String line = phases.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (line == null) break;
                if (line.startsWith("CONVERSATION_FAILED")) throw new AssertionError("服务子进程报告失败");
                if (line.startsWith(prefix)) return line;
            }
            throw new AssertionError("固定子进程阶段预算耗尽");
        }
        synchronized void stop() throws Exception {
            if (stopped) return;
            process.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new AssertionError("服务未正常退出");
            String[] line = await("CONVERSATION_CLOSED", Duration.ofMillis(200)).split(" ");
            for (int i = 2; i < line.length; i++) if (Integer.parseInt(line[i]) != 0) throw new AssertionError("服务仍占执行资源");
            reader.join(2000); if (reader.isAlive()) throw new AssertionError("协议读取线程未退出");
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200); throw new AssertionError("服务监听未关闭");
            } catch (IOException expectedClosed) { }
            stopped = true;
        }
        @Override public synchronized void close() throws Exception {
            try { stop(); }
            finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("强制清理也未实际退出");
                }
                reader.join(2000); if (reader.isAlive()) throw new AssertionError("协议读取线程残留");
            }
        }
    }
}
