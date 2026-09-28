package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentRunTask;
import com.zhku.agent.agent.runtime.AgentStopReason;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 本机校园入口的有界异步运行管理；取消只请求停止，不提前宣布终止。 */
public final class CampusCreditAgentManager {
    private static final int MAX_RETAINED_RUNS = 128;
    private final CampusCreditAgentService service;
    private final Executor worker;
    private final ScheduledExecutorService timer;
    private final Duration timeout;
    private final ConcurrentHashMap<UUID, ManagedRun> runs = new ConcurrentHashMap<>();

    public CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout) {
        this.service = Objects.requireNonNull(service, "Agent 服务不能为空");
        this.worker = Objects.requireNonNull(worker, "执行器不能为空");
        this.timer = Objects.requireNonNull(timer, "定时器不能为空");
        this.timeout = Objects.requireNonNull(timeout, "超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("超时必须为正数");
    }

    public Snapshot start(CampusCreditAgentService.Request request) {
        Objects.requireNonNull(request, "请求不能为空");
        if (runs.size() >= MAX_RETAINED_RUNS) {
            // 本机原型限制快照保留量，避免无法持久化时内存无限增长。
            throw new RejectedExecutionException("校园 Agent 运行记录已满");
        }
        AgentRunContext context = AgentRunContext.create(request.question());
        ManagedRun managed = new ManagedRun(context);
        runs.put(context.runId(), managed);
        var task = AgentRunTask.create(context, () -> {
            try {
                managed.result = service.run(context, request);
            } finally {
                ScheduledFuture<?> deadline = managed.deadline;
                if (deadline != null) deadline.cancel(false);
            }
        });
        try {
            managed.deadline = timer.schedule(
                    () -> context.requestStop(AgentStopReason.RUN_TIMEOUT), timeout.toMillis(), TimeUnit.MILLISECONDS);
            worker.execute(task);
        } catch (RejectedExecutionException exception) {
            task.cancel(false);
            if (managed.deadline != null) managed.deadline.cancel(false);
            context.finish(AgentRunState.FAILED);
            context.cleanupOnce(() -> { });
            context.signalTermination();
            managed.rejected = true;
        }
        return snapshot(managed);
    }

    public Snapshot find(UUID runId) {
        ManagedRun run = runs.get(Objects.requireNonNull(runId, "运行编号不能为空"));
        if (run == null) throw new IllegalArgumentException("运行不存在");
        return snapshot(run);
    }

    public Snapshot cancel(UUID runId) {
        ManagedRun run = runs.get(Objects.requireNonNull(runId, "运行编号不能为空"));
        if (run == null) throw new IllegalArgumentException("运行不存在");
        run.context.requestStop(AgentStopReason.USER_REQUEST);
        return snapshot(run);
    }

    private static Snapshot snapshot(ManagedRun run) {
        CampusCreditAgentService.Result result = run.result;
        return new Snapshot(run.context.runId(), run.context.state(), run.context.stopReason(),
                run.context.termination().isDone(),
                result == null ? null : result.status(), result == null ? null : result.answer(),
                run.rejected ? "EXECUTOR_REJECTED" : result == null ? null : result.failureCategory(),
                result == null ? List.of() : result.steps(), result == null ? null : result.source());
    }

    private static final class ManagedRun {
        private final AgentRunContext context;
        private volatile CampusCreditAgentService.Result result;
        private volatile ScheduledFuture<?> deadline;
        private volatile boolean rejected;
        private ManagedRun(AgentRunContext context) { this.context = context; }
    }

    /** v1 JSON 快照：state 是业务状态，terminated 才表示清理与线程退出均已完成。 */
    public record Snapshot(UUID runId, AgentRunState state, AgentStopReason stopReason,
            boolean terminated, CampusCreditAgentService.Status status, String answer,
            String failureCategory, List<CampusCreditAgentService.Step> steps,
            com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer source) { }
}
