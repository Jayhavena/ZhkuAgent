package com.zhku.knowledge.campusagent.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunObservationDispatcher;
import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.knowledge.campusagent.*;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.*;
import com.zhku.knowledge.conversation.publicelective.*;
import com.zhku.knowledge.tools.*;
import com.zhku.knowledge.web.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** 实际进程验收入口，模型只能由测试侧提供。 */
public final class CampusRunArchiveProcessProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger MODELS = new AtomicInteger(), TOOLS = new AtomicInteger(),
            FINAL_EVENTS = new AtomicInteger();
    private static final AtomicBoolean FINAL_READABLE = new AtomicBoolean(), FUTURE_READABLE = new AtomicBoolean(),
            DONE = new AtomicBoolean();
    private static final AtomicReference<Throwable> CALLBACK_FAILURE = new AtomicReference<>();
    private static Path directory;
    private static String mode;
    private static UUID runId;
    private static CampusRunArchiveOwner owner;
    private static JdbcCampusRunArchiveStore store;
    private static CampusCreditAgentManager manager;
    private static int backendPid;
    private static AgentRunObservationDispatcher observations;
    private static final CountDownLatch FINAL_RETURNED = new CountDownLatch(1);
    private static String diagnosticStage = "STARTING", failureKind;
    private static Boolean returnedTerminated, finalAwaitReturned;

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("测试入口参数无效");
        mode = args[0];
        directory = Path.of(args[1]).toAbsolutePath().normalize();
        if (!directory.startsWith(Path.of("target", "m3a-process-probes").toAbsolutePath().normalize())
                || !Set.of("ADMIT_BEFORE_DISPATCH", "TOOL_BEFORE_RETURN", "CLEANUP_BEFORE_FINAL",
                        "FINAL_BEFORE_PUBLICATION", "RECONCILE", "HOLD_LOCK").contains(mode))
            throw new IllegalArgumentException("测试控制目录或模式无效");
        if (Files.exists(directory.resolve("run-id")))
            runId = UUID.fromString(Files.readString(directory.resolve("run-id")));
        ScheduledExecutorService timer = null;
        try {
            DataSource source = ArchiveTestDatabase.dataSource();
            owner = new CampusRunArchiveOwner(source, Limits.defaults());
            owner.start();
            backendPid = lockBackendPid();
            store = new JdbcCampusRunArchiveStore(source, owner, Limits.defaults(), new CampusRunHistoryVisibility());
            if (mode.equals("RECONCILE")) {
                phase("RECONCILED", null);
                return;
            }
            if (mode.equals("HOLD_LOCK")) {
                phase("HOLD_LOCK", null);
                barrier();
                return;
            }
            var facade = facade(source);
            var pure = new CourseCreditSummaryTool();
            CampusTool calculator = new CampusTool() {
                public CampusToolDescriptor descriptor() {
                    return pure.descriptor();
                }

                public CampusToolResult execute(String arguments) {
                    var result = pure.execute(arguments);
                    TOOLS.incrementAndGet();
                    if (mode.equals("TOOL_BEFORE_RETURN")) {
                        try {
                            phase("TOOL_BEFORE_RETURN", null);
                            barrier();
                        } catch (Exception e) {
                            throw new IllegalStateException("测试屏障失败");
                        }
                    }
                    return result;
                }
            };
            ChatModel model = prompt -> {
                int number = MODELS.incrementAndGet();
                var call = new AssistantMessage.ToolCall("synthetic-provider-id", "function",
                        number == 1 ? "lookup_public_elective_credit_limit" : "summarize_course_credits",
                        number == 1 ? "{}"
                                : "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5},{\"courseId\":\"HIS02\",\"category\":\"公共选修课\",\"credits\":2}]}");
                return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(call)))));
            };
            observations = AgentRunObservationDispatcher.createOwned();
            var service = new CampusCreditAgentService(model, new CampusToolRegistry(List.of(calculator)), q -> {
                var session = facade.createConversation();
                return facade.sendMessage(session.sessionId(), q);
            }, 3, event -> {
                if (event.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                    FINAL_EVENTS.incrementAndGet();
                    try {
                        var result = manager.find(event.runId());
                        FINAL_READABLE.set(result.terminated() && result.status() != null);
                    } catch (Throwable e) {
                        CALLBACK_FAILURE.set(e);
                    } finally { FINAL_RETURNED.countDown(); }
                }
            }, observations);
            timer = Executors.newSingleThreadScheduledExecutor();
            manager = new CampusCreditAgentManager(service, Runnable::run, timer, Duration.ofSeconds(30),
                    new Barriers());
            var shutdownWatcher = new Thread(() -> {
                while (!DONE.get()) {
                    if (Files.exists(directory.resolve("shutdown"))) {
                        manager.close(Duration.ofMillis(100));
                        try {
                            phase("CLOSED_WITH_PENDING_TASK", null);
                        } catch (Exception e) {
                            CALLBACK_FAILURE.set(e);
                        }
                        return;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "m3a-test-shutdown");
            shutdownWatcher.setDaemon(true);
            shutdownWatcher.start();
            diagnosticStage = "MANAGER_START";
            var result = manager.start(new CampusCreditAgentService.Request("这两门虚构公选课学分合计及每学期上限？", List.of(
                    new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                    new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2")))));
            returnedTerminated = result.terminated();
            diagnosticStage = "FINAL_CALLBACK_WAIT";
            finalAwaitReturned = FINAL_RETURNED.await(1, TimeUnit.SECONDS);
            diagnosticStage = "FINAL_CALLBACK_CHECK";
            if (!finalAwaitReturned || !returnedTerminated || CALLBACK_FAILURE.get() != null)
                throw new IllegalStateException("测试结果发布失败");
            diagnosticStage = "COMPLETED_PUBLICATION";
            phase("COMPLETED", null);
        } catch (Throwable error) {
            failureKind = safeFailureKind(error);
            Code code = error instanceof Failure failure ? failure.code() : Code.ARCHIVE_UNAVAILABLE;
            phase(code == Code.ARCHIVE_OWNER_LOST ? "LOCK_REJECTED" : "FAILED", code);
            // 不打印异常原文或环境；父进程只读取固定元数据和失败分类。
            System.out.println("M3A_PROCESS_FAILURE code=" + code);
        } finally {
            DONE.set(true);
            if (manager != null)
                manager.close(Duration.ZERO);
            if (store != null)
                store.close(Duration.ofSeconds(2));
            else if (owner != null)
                owner.close(Duration.ofSeconds(2));
            if (timer != null)
                timer.shutdownNow();
            if (observations != null) {
                observations.close();
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (observations.status().liveThreadCount() != 0 && System.nanoTime() < until) Thread.sleep(1);
                if (observations.status().liveThreadCount() != 0) throw new AssertionError("观察资源未实际退出");
            }
        }
    }

    private static LocalPreviewConversationFacade facade(DataSource source) {
        var sessions = new InMemoryConversationStore();
        var clock = Clock.systemUTC();
        var ttl = Duration.ofMinutes(10);
        var router = new PublicElectiveConversationRouter(sessions, new ElectiveRetakeConversationService(sessions,
                new JdbcElectiveRetakeEvidenceFinder(source), clock, ttl),
                new PublicElectiveCreditLimitService(new JdbcCreditLimitEvidenceFinder(source)), clock);
        return new LocalPreviewConversationFacade(sessions,
                new LocalPreviewAnswerChain(sessions, router, null, null, clock), clock, ttl);
    }

    private static AgentRunContext context() throws Exception {
        if (manager == null || runId == null)
            return null;
        var field = CampusCreditAgentManager.class.getDeclaredField("runs");
        field.setAccessible(true);
        var managed = ((Map<?, ?>) field.get(manager)).get(runId);
        if (managed == null)
            return null;
        var context = managed.getClass().getDeclaredField("context");
        context.setAccessible(true);
        return (AgentRunContext) context.get(managed);
    }

    private static void barrier() throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (!Files.exists(directory.resolve("release"))) {
            if (System.nanoTime() > deadline)
                throw new IllegalStateException("测试屏障过期");
            // 测试故意忽略中断，证明fencing只禁止归档，不能承诺停止外部工具。
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static int lockBackendPid() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection();
                var p = c.prepareStatement(
                        "SELECT pid FROM pg_locks WHERE locktype='advisory' AND classid=?::oid AND objid=?::oid AND objsubid=2")) {
            p.setLong(1, 0x5A484B55);
            p.setLong(2, 0x43524152);
            try (var r = p.executeQuery()) {
                if (!r.next())
                    throw new IllegalStateException("无测试锁");
                return r.getInt(1);
            }
        }
    }

    private static synchronized void phase(String phase, Code code) throws Exception {
        var values = new LinkedHashMap<String, Object>();
        values.put("pid", ProcessHandle.current().pid());
        values.put("runId", runId == null ? null : runId.toString());
        var token = owner == null ? null : owner.token();
        values.put("instanceId", token == null ? null : token.instanceId().toString());
        values.put("epoch", token == null ? null : token.epoch());
        values.put("phase", phase);
        values.put("modelCalls", MODELS.get());
        values.put("toolCalls", TOOLS.get());
        values.put("ownerBackendPid", backendPid);
        values.put("code", code == null ? null : code.name());
        values.put("finalEvents", FINAL_EVENTS.get());
        values.put("finalCallbackReadable", FINAL_READABLE.get());
        values.put("futureCallbackReadable", FUTURE_READABLE.get());
        // 只输出有限诊断，不携带异常消息/堆栈；最终采样不能冒充先前判断时刻的值。
        values.put("diagnosticStage", diagnosticStage);
        values.put("failureKind", failureKind);
        values.put("returnedTerminated", returnedTerminated);
        values.put("finalAwaitReturned", finalAwaitReturned);
        values.put("callbackFailureKind", safeFailureKind(CALLBACK_FAILURE.get()));
        values.put("samplingThreadInterrupted", Thread.currentThread().isInterrupted());
        var context = context();
        var snapshot = context == null ? null : manager.find(runId);
        // terminated只诊断当前进程宿主快照；不是历史API字段，也不推断旧进程事件已发布。
        values.put("cleanupDone", context != null && context.termination().isDone());
        values.put("terminated", snapshot != null && snapshot.terminated());
        values.put("onlineState", snapshot == null ? null : snapshot.state().name());
        values.put("stopReason", snapshot == null ? null : snapshot.stopReason().name());
        if (context != null) {
            var status = context.observationStatus();
            values.put("observationPhase", status.phase().name());
            values.put("observationDetachReason", status.detachReason().name());
        }
        String archiveStatus = null, executionState = null;
        boolean hasResult = false;
        if (runId != null) {
            try (var c = ArchiveTestDatabase.dataSource().getConnection();
                    var p = c.prepareStatement(
                            "SELECT archive_status,execution_state FROM campus_agent_run_archive WHERE run_id=?")) {
                p.setObject(1, runId);
                try (var r = p.executeQuery()) {
                    if (r.next()) {
                        archiveStatus = r.getString(1);
                        executionState = r.getString(2);
                    }
                }
            }
            if (store != null)
                try {
                    hasResult = store.find(runId).map(r -> r.result() != null).orElse(false);
                } catch (Failure ignored) {
                }
        }
        values.put("archiveStatus", archiveStatus);
        values.put("executionState", executionState);
        values.put("hasResult", hasResult);
        var temporary = directory.resolve("phase.json.tmp");
        Files.writeString(temporary, JSON.writeValueAsString(values));
        Files.move(temporary, directory.resolve("phase.json"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static String safeFailureKind(Throwable error) {
        if (error == null) return null;
        if (error instanceof Failure) return "ARCHIVE_FAILURE";
        if (error instanceof InterruptedException) return "INTERRUPTED";
        if (error instanceof IllegalStateException) return "ILLEGAL_STATE";
        if (error instanceof java.io.IOException) return "IO_FAILURE";
        if (error instanceof AssertionError) return "ASSERTION";
        return "OTHER";
    }

    private static final class Barriers implements CampusRunArchiveStore {
        public boolean isAccepting() {
            return store.isAccepting();
        }

        public Admission admit(UUID id) {
            runId = id;
            // 准入在派发前执行；不能依赖异步RUN_STARTED来抢登记清理信号。
            try { context().termination().thenRun(() -> FUTURE_READABLE.set(manager.find(runId).terminated())); }
            catch (Exception failure) { throw new IllegalStateException("测试清理登记失败"); }
            var admitted = store.admit(id);
            if (mode.equals("ADMIT_BEFORE_DISPATCH"))
                try {
                    phase(mode, null);
                    barrier();
                } catch (Exception e) {
                    throw new IllegalStateException("测试屏障失败");
                }
            return admitted;
        }

        public Finalization finalizeRun(Admission admission, FinalFacts facts, PayloadCandidate candidate) {
            try {
                if (mode.equals("CLEANUP_BEFORE_FINAL")) {
                    phase(mode, null);
                    barrier();
                }
                var result = store.finalizeRun(admission, facts, candidate);
                if (mode.equals("FINAL_BEFORE_PUBLICATION")) {
                    if (result != Finalization.CONFIRMED)
                        throw new IllegalStateException("最终归档未确认");
                    phase(mode, null);
                    barrier();
                }
                return result;
            } catch (Exception e) {
                throw new IllegalStateException("测试屏障失败");
            }
        }

        public Optional<Read> find(UUID id) {
            return store.find(id);
        }

        public Page list(int limit, Cursor cursor) {
            return store.list(limit, cursor);
        }

        public void stopAdmission() {
            store.stopAdmission();
        }

        public void sealWrites() {
            store.sealWrites();
        }

        public void close(Duration remaining) {
            store.close(remaining);
        }
    }
}
