package com.zhku.knowledge.campusagent;

import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentRunTask;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import com.zhku.knowledge.security.TrustedActor;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmission;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 本机校园入口的有界异步运行管理；取消只请求停止，不提前宣布终止。 */
public final class CampusCreditAgentManager implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CampusCreditAgentManager.class);
    private final CampusCreditAgentService service;
    private final Executor worker;
    private final ScheduledExecutorService timer;
    private final Duration timeout;
    private final int capacity;
    private final Duration retention;
    private final Clock clock;
    private final CampusRunArchiveStore archive;
    private CampusAdmissionBudget admissionBudget = new CampusAdmissionBudget(2,18);
    private Duration queueTimeout = Duration.ofSeconds(10);
    private java.util.function.LongSupplier nanoClock = System::nanoTime;
    private final java.util.ArrayList<CampusAdmissionBudget.Reservation> unknownReservations = new java.util.ArrayList<>();
    private boolean closing;
    private final Object admissionLock = new Object();
    private final HashMap<UUID, ManagedRun> runs = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicLong completionOrder = new java.util.concurrent.atomic.AtomicLong();
    private record SubmissionKey(UUID userId, UUID key) { }
    private record PendingSubmission(CampusRunSubmission.Fingerprint fingerprint, CompletableFuture<Submitted> result) { }
    private final HashMap<SubmissionKey,PendingSubmission> submissions = new HashMap<>();
    public record Submitted(com.zhku.knowledge.campusagent.submission.CampusRunSubmission.Claim claim, Snapshot live) { }
    public Submitted submitOwned(TrustedActor actor, UUID submissionKey, CampusCreditAgentService.Request request,
            com.zhku.knowledge.campusagent.submission.CampusRunSubmission.Fingerprint fingerprint) {
        requireUser(actor);Objects.requireNonNull(submissionKey);Objects.requireNonNull(request);Objects.requireNonNull(fingerprint);
        if(archive==null)throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
        var key=new SubmissionKey(actor.userId(),submissionKey);PendingSubmission pending;boolean original=false;
        synchronized(admissionLock) {
            if(closing)throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
            pending=submissions.get(key);
            if(pending==null&&submissions.size()<18) {
                pending=new PendingSubmission(fingerprint,new CompletableFuture<>());submissions.put(key,pending);original=true;
            }
        }
        if(pending==null) {
            var row=archive.findSubmissionOwned(actor,submissionKey);
            if(row.isPresent())return replay(row.get(),fingerprint,submissionKey);
            synchronized(admissionLock){pending=submissions.get(key);}
            if(pending==null)throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.INSTANCE_CAPACITY_EXCEEDED,submissionKey);
        }
        if(!pending.fingerprint().equals(fingerprint))
            throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.SUBMISSION_CONTENT_CONFLICT,submissionKey);
        if(!original) {
            try {return replay(pending.result().get(5,TimeUnit.SECONDS).claim().record(),fingerprint,submissionKey);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw unknown(submissionKey);}
            catch(TimeoutException e){throw unknown(submissionKey);}
            catch(ExecutionException e){if(e.getCause() instanceof RuntimeException failure)throw failure;throw unknown(submissionKey);}
        }
        CampusAdmissionBudget.Reservation reservation = null;
        boolean transferred = false;
        try {
            var prior=archive.findSubmissionOwned(actor,submissionKey);
            Submitted submitted;
            if(prior.isPresent())submitted=replay(prior.get(),fingerprint,submissionKey);
            else {
                try {
                    synchronized(admissionLock) {
                        if(closing||!archive.isAccepting())throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
                        reservation=admissionBudget.reserve(actor.userId());
                    }
                } catch(CampusAdmissionBudget.Failure quota) {
                    // 配额拒绝前再确认原键；已持久受理的请求无需新的运行额度。
                    var existing=archive.findSubmissionOwned(actor,submissionKey);
                    if(existing.isPresent()) {
                        submitted=replay(existing.get(),fingerprint,submissionKey);pending.result().complete(submitted);return submitted;
                    }
                    throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.valueOf(quota.code()),submissionKey);
                }
                var claim=archive.admitSubmissionOwned(actor,new CampusRunSubmission.Command(submissionKey,UUID.randomUUID(),fingerprint));
                transferred=claim.decision()==CampusRunSubmission.Decision.CREATED;
                submitted=claim.decision()==CampusRunSubmission.Decision.REPLAYED
                        ? replay(claim.record(),fingerprint,submissionKey) : dispatchSubmission(actor,claim,request,reservation);
            }
            pending.result().complete(submitted);return submitted;
        } catch(RuntimeException failure) {
            if(failure instanceof CampusRunArchive.Failure f && f.code()==CampusRunArchive.Code.ARCHIVE_ADMISSION_UNCONFIRMED) {
                archive.stopAdmission();
                // 未知提交不能乐观释放后继续接单；只能在关准入、封写完成后丢弃预留。
                if(reservation!=null&&!transferred)synchronized(admissionLock){unknownReservations.add(reservation);transferred=true;}
            }
            RuntimeException safe=failure instanceof CampusRunArchive.Failure f
                    && f.code()==CampusRunArchive.Code.ARCHIVE_ADMISSION_UNCONFIRMED?unknown(submissionKey):failure;
            pending.result().completeExceptionally(safe);throw safe;
        } finally {synchronized(admissionLock){if(reservation!=null&&!transferred)reservation.release();submissions.remove(key,pending);}}
    }
    private static CampusRunSubmission.Failure unknown(UUID key) {
        return new CampusRunSubmission.Failure(CampusRunSubmission.Code.SUBMISSION_UNCONFIRMED,key);
    }
    private Submitted replay(CampusRunSubmission.Record row,CampusRunSubmission.Fingerprint fingerprint,UUID key) {
        if(!row.fingerprint().equals(fingerprint))throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.SUBMISSION_CONTENT_CONFLICT,key);
        Snapshot live=null;
        synchronized(admissionLock) {
            pruneExpired();var managed=runs.get(row.runId());
            if(managed!=null&&row.userId().equals(managed.userId))live=snapshot(managed);
        }
        return new Submitted(new CampusRunSubmission.Claim(CampusRunSubmission.Decision.REPLAYED,row,null),live);
    }
    private Submitted dispatchSubmission(TrustedActor actor,CampusRunSubmission.Claim claim,CampusCreditAgentService.Request request,
            CampusAdmissionBudget.Reservation reservation) {
        long began=nanoClock.getAsLong();
        AgentRunContext context;
        try {context=service.createContext(claim.record().runId(),request);}
        catch(RuntimeException cannotCreate) {try{rejectWithoutContext(claim);return new Submitted(claim,null);}
            finally{synchronized(admissionLock){reservation.release();}}}
        var managed=new ManagedRun(context,actor.userId());managed.admission=claim.admission();
        managed.reservation=reservation;managed.began=began;managed.request=request;
        managed.executionBudget=new CampusExecutionBudget(began,timeout,nanoClock,context::isStopRequested);
        boolean permitted;
        synchronized(admissionLock) {
            pruneExpired();
            if(runs.size()>=capacity)runs.values().stream().filter(CampusCreditAgentManager::evictable)
                    .min(Comparator.comparingLong(r->r.completion.order())).ifPresent(r->runs.remove(r.context.runId()));
            permitted=!closing&&archive.isAccepting()&&runs.size()<capacity;
            if(permitted)runs.put(context.runId(),managed);
        }
        if(!permitted) {rejectManaged(managed);return new Submitted(claim,snapshot(managed));}
        var task=AgentRunTask.create(context,()-> {
            managed.started.set(true);
            // 定时器可能迟到；出队时再次检验排队/总期限，不能重置为新的30秒。
            if(nanoClock.getAsLong()-managed.began>=Math.min(queueTimeout.toNanos(),timeout.toNanos()))
                context.requestStop(AgentStopReason.RUN_TIMEOUT);
            completeSubmission(managed);
        });managed.task=task;
        try {
            managed.deadline=timer.schedule(()->stopSubmission(managed,AgentStopReason.RUN_TIMEOUT),
                    Math.max(0,timeout.toNanos()-(nanoClock.getAsLong()-began)),TimeUnit.NANOSECONDS);
            managed.queueDeadline=timer.schedule(()->{if(!managed.started.get())stopSubmission(managed,AgentStopReason.RUN_TIMEOUT);},
                    Math.max(0,queueTimeout.toNanos()-(nanoClock.getAsLong()-began)),TimeUnit.NANOSECONDS);
            worker.execute(task);
            // 到期callback可能先于execute接受队列，此时remove会失败；入队后补一次安全移除。
            // 这里只补本地停止交接，绝不重派发；交接被关闭拒绝也保留原任务/名额供实际收尾。
            if(!managed.started.get()&&(context.isStopRequested()||nanoClock.getAsLong()-began>=queueTimeout.toNanos())) {
                try {timer.execute(()->{
                    if(context.isStopRequested())stopSubmission(managed,context.snapshot().stopReason());
                    else if(!managed.started.get()&&nanoClock.getAsLong()-began>=queueTimeout.toNanos())
                        stopSubmission(managed,AgentStopReason.RUN_TIMEOUT);
                });}catch(RejectedExecutionException closedTimer){/* 原任务仍有唯一收尾者，不假造物理释放。 */}
            }
        } catch(RuntimeException rejected) {task.cancel(false);rejectManaged(managed);}
        return new Submitted(claim,snapshot(managed));
    }
    private void stopSubmission(ManagedRun managed,AgentStopReason reason) {
        if(managed.completion!=null)return;
        managed.context.requestStop(reason);
        if(worker instanceof java.util.concurrent.ThreadPoolExecutor pool && managed.task!=null && pool.remove(managed.task)) {
            // 只有安全移除成功才能由timer收尾；Future.cancel成功不证明任务离开队列。
            managed.task.cancel(false);completeSubmission(managed);
        }
    }
    private void completeSubmission(ManagedRun managed) {
        if(!managed.finishing.compareAndSet(false,true))return;
        boolean published=false;
        try {
            var result=service.run(managed.context,managed.request,managed.executionBudget);cancelDeadline(managed);archiveFinal(managed,result,false);
            managed.completion=new PublishedCompletion(result,clock.instant(),false,completionOrder.getAndIncrement());
            managed.context.publishTermination();
            published=true;
        } finally {
            if(published)synchronized(admissionLock){managed.reservation.release();}
            else archive.stopAdmission();
        }
    }
    private void rejectWithoutContext(CampusRunSubmission.Claim claim) {
        // 受理已确认而装配失败也不能派发第二次；最多尝试保存有限终态。
        try {
            var facts=new CampusRunArchive.FinalFacts(claim.record().runId(),AgentRunState.FAILED,AgentStopReason.NONE,EndReason.ERROR,0,
                    AgentRuntimeError.Category.EXECUTOR_REJECTED,"EXECUTOR_REJECTED",null,"EXECUTOR_REJECTED",
                    CampusRunArchive.CleanupOutcome.SUCCESS,clock.instant());
            if(archive.finalizeRun(claim.admission(),facts,null)!=CampusRunArchive.Finalization.CONFIRMED)archive.stopAdmission();
        } catch(RuntimeException failure){archive.stopAdmission();}
    }
    private void rejectManaged(ManagedRun managed) {
        if(managed.reservation!=null&&!managed.finishing.compareAndSet(false,true))return;
        cancelDeadline(managed);var context=managed.context;
        context.finish(AgentRunState.FAILED,EndReason.ERROR,AgentRuntimeError.of(AgentRuntimeError.Category.EXECUTOR_REJECTED));
        context.cleanupOnce(()->{});context.signalTermination();archiveFinal(managed,null,true);
        managed.completion=new PublishedCompletion(null,clock.instant(),true,completionOrder.getAndIncrement());context.publishTermination();
        if(managed.reservation!=null)synchronized(admissionLock){managed.reservation.release();}
    }

    public CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout) {
        this(service, worker, timer, timeout, 128, Duration.ofMinutes(10), Clock.systemUTC());
    }

    CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout, int capacity, Duration retention, Clock clock) {
        this(service, worker, timer, timeout, capacity, retention, clock, null);
    }

    CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout, int capacity, Duration retention, Clock clock,
            CampusRunArchiveStore archive) {
        this.service = Objects.requireNonNull(service);
        this.worker = Objects.requireNonNull(worker);
        this.timer = Objects.requireNonNull(timer);
        this.timeout = Objects.requireNonNull(timeout);
        this.retention = Objects.requireNonNull(retention);
        this.clock = Objects.requireNonNull(clock);
        this.archive = archive;
        if (timeout.isZero() || timeout.isNegative() || retention.isZero() || retention.isNegative()
                || capacity < 1)
            throw new IllegalArgumentException("容量与时长必须为正数");
        this.capacity = capacity;
    }

    public CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout,
            CampusRunArchiveStore archive) {
        this(service, worker, timer, timeout, 128, Duration.ofMinutes(10), Clock.systemUTC(), archive);
    }

    CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout, CampusRunArchiveStore archive,
            CampusAdmissionBudget budget, Duration queueTimeout, java.util.function.LongSupplier nanoClock) {
        this(service, worker, timer, timeout, archive);
        this.admissionBudget=Objects.requireNonNull(budget);this.queueTimeout=Objects.requireNonNull(queueTimeout);
        if(queueTimeout.isZero()||queueTimeout.isNegative())throw new IllegalArgumentException("排队期限必须为正");
        this.nanoClock=Objects.requireNonNull(nanoClock);
    }

    public CampusCreditAgentManager(CampusCreditAgentService service, Executor worker,
            ScheduledExecutorService timer, Duration timeout, CampusRunArchiveStore archive,
            CampusAdmissionBudget budget, Duration queueTimeout) {
        this(service,worker,timer,timeout,archive,budget,queueTimeout,System::nanoTime);
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(5));
    }

    public void close(Duration budget) {
        Objects.requireNonNull(budget);
        if (budget.isNegative() || budget.compareTo(Duration.ofSeconds(5)) > 0)
            throw new IllegalArgumentException("关闭预算必须在0–5秒内");
        if (archive == null)
            return;
        List<ManagedRun> pending;
        synchronized (admissionLock) {
            if (closing)
                return;
            closing = true;
            pending = List.copyOf(runs.values());
        }
        long deadline = System.nanoTime() + budget.toNanos();
        archive.stopAdmission();
        // Future 可能先于结果完成；排空必须同时观察真实清理和宿主发布，关闭不伪造停止原因。
        while (pending.stream().anyMatch(run -> run.completion == null || !run.context.termination().isDone())
                && System.nanoTime() < deadline) {
            try {
                TimeUnit.NANOSECONDS
                        .sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(1), Math.max(1, deadline - System.nanoTime())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        archive.sealWrites();
        archive.close(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        synchronized(admissionLock){unknownReservations.forEach(CampusAdmissionBudget.Reservation::release);unknownReservations.clear();}
    }

    public Snapshot start(CampusCreditAgentService.Request request) {
        return start(request, null);
    }

    private Snapshot start(CampusCreditAgentService.Request request, TrustedActor actor) {
        Objects.requireNonNull(request, "请求不能为空");
        var context = service.createContext(request);
        var managed = new ManagedRun(context, actor == null ? null : actor.userId());
        synchronized (admissionLock) {
            if (archive != null && (closing || !archive.isAccepting()))
                throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
            pruneExpired();
            if (runs.size() >= capacity) {
                var oldest = runs.values().stream().filter(CampusCreditAgentManager::evictable)
                        // 同一时钟刻度可能有多次完成；用发布序号稳定打破平局，避免随机回收较新的记录。
                        .min(Comparator.comparing((ManagedRun run) -> run.completion.finishedAt())
                                .thenComparingLong(run -> run.completion.order()));
                oldest.ifPresent(run -> runs.remove(run.context.runId()));
            }
            if (runs.size() >= capacity)
                throw new RejectedExecutionException("校园 Agent 运行记录已满");
            runs.put(context.runId(), managed);
        }
        if (archive != null) {
            boolean permitted = false;
            try {
                managed.admission = Objects.requireNonNull(actor == null ? archive.admit(context.runId())
                        : archive.admitOwned(context.runId(), actor));
                if (!context.runId().equals(managed.admission.runId()))
                    throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_ADMISSION_UNCONFIRMED);
                synchronized (admissionLock) {
                    if (closing || !archive.isAccepting())
                        throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
                    // 此处是投递许可的线性化点；后续关门不撤销已获许可任务，不假造用户取消。
                    permitted = true;
                }
            } finally {
                if (!permitted)
                    synchronized (admissionLock) {
                        runs.remove(context.runId(), managed);
                    }
            }
        }
        // 锁只保护清理、计数和插入；模型、执行器和定时器不能占用准入锁。
        var task = AgentRunTask.create(context, () -> {
            CampusCreditAgentService.Result result = service.run(context, request);
            cancelDeadline(managed);
            archiveFinal(managed, result, false);
            // 这里只证明归档已经尝试；确认失败也继续发布业务结果，不能改写真实执行终态。
            // 一次 volatile 写入发布完整结果与完成时刻，不能先宣布 terminated 再补结果。
            managed.completion = new PublishedCompletion(result, clock.instant(), false,
                    completionOrder.getAndIncrement());
            context.publishTermination();
        });
        try {
            managed.deadline = timer.schedule(() -> context.requestStop(AgentStopReason.RUN_TIMEOUT),
                    timeout.toMillis(), TimeUnit.MILLISECONDS);
            worker.execute(task);
        } catch (RejectedExecutionException exception) {
            task.cancel(false);
            cancelDeadline(managed);
            context.finish(AgentRunState.FAILED, EndReason.ERROR,
                    AgentRuntimeError.of(AgentRuntimeError.Category.EXECUTOR_REJECTED));
            context.cleanupOnce(() -> {
            });
            context.signalTermination();
            archiveFinal(managed, null, true);
            managed.completion = new PublishedCompletion(null, clock.instant(), true,
                    completionOrder.getAndIncrement());
            context.publishTermination();
        }
        return snapshot(managed);
    }

    public Snapshot startOwned(CampusCreditAgentService.Request request, TrustedActor actor) {
        requireUser(actor);
        if (archive == null) throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_READY);
        // 归属与当前许可由ADMITTED事务确认；未确认前原调用链绝不schedule/execute。
        return start(request, actor);
    }
    public Snapshot findOwned(TrustedActor actor, UUID runId) {
        requireUser(actor);
        ManagedRun run = lookup(runId, actor.userId());
        requireOwnedArchive(actor, runId);
        return snapshot(run);
    }
    public Snapshot cancelOwned(TrustedActor actor, UUID runId) {
        requireUser(actor);
        lookup(runId, actor.userId());
        requireOwnedArchive(actor, runId);
        ManagedRun managed;Snapshot response;
        synchronized (admissionLock) {
            // 数据库许可是本次动作快照；锁内再核对final userId，不能从线程上下文或UUID直接停止。
            var run = lookup(runId, actor.userId());
            run.context.requestStop(AgentStopReason.USER_REQUEST);
            managed=run;response=snapshot(run);
        }
        // HTTP取消不等待数据库收尾，已有timer承担可安全移除的队列任务。
        if(managed.reservation!=null)try{timer.execute(()->stopSubmission(managed,AgentStopReason.USER_REQUEST));}
            catch(RejectedExecutionException closingTimer){/* 停止事实已记录，原工作线程仍须自行收尾。 */}
        return response;
    }

    private void requireOwnedArchive(TrustedActor actor, UUID id) {
        if (archive == null) throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_READ_UNAVAILABLE);
        if (archive.findOwned(actor, id).isEmpty()) throw new IllegalArgumentException("运行不存在");
    }
    private static void requireUser(TrustedActor actor) {
        Objects.requireNonNull(actor);
        if (actor.role() != TrustedActor.Role.USER)
            throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.ROLE_DENIED);
    }

    public Snapshot find(UUID runId) {
        return snapshot(lookup(runId));
    }

    private void archiveFinal(ManagedRun managed, CampusCreditAgentService.Result result, boolean rejected) {
        if (archive == null)
            return;
        // 业务已结束；只为有界归档暂存中断位，不能让取消跳过归档，也不能清除原停止事实。
        boolean restoreInterrupt = Thread.interrupted();
        try {
            var facts = CampusRunArchive.capture(managed.context, result, rejected, clock.instant());
            var candidate = !rejected && facts.state() == AgentRunState.SUCCEEDED
                    && result.status() == CampusCreditAgentService.Status.ANSWERED
                            ? new CampusRunArchive.PayloadCandidate(result.answer(), result.source())
                            : null;
            var outcome = archive.finalizeRun(managed.admission, facts, candidate);
            if (outcome != CampusRunArchive.Finalization.CONFIRMED) {
                archive.stopAdmission();
                LOG.warn("校园归档未确认 runId={} code={}", managed.context.runId(),
                        outcome == CampusRunArchive.Finalization.OWNER_LOST
                                ? CampusRunArchive.Code.ARCHIVE_OWNER_LOST
                                : CampusRunArchive.Code.ARCHIVE_FINALIZATION_UNCONFIRMED);
            }
        } catch (RuntimeException failure) {
            archive.stopAdmission();
            // 不记录异常原文或cause；无论归档失败与否都继续发布真实业务结果。
            LOG.warn("校园归档未确认 runId={} code={}", managed.context.runId(),
                    CampusRunArchive.Code.ARCHIVE_FINALIZATION_UNCONFIRMED);
        } finally {
            if (restoreInterrupt)
                Thread.currentThread().interrupt();
        }
    }

    public Snapshot cancel(UUID runId) {
        var run = lookup(runId);
        run.context.requestStop(AgentStopReason.USER_REQUEST);
        return snapshot(run);
    }

    private ManagedRun lookup(UUID runId) {
        return lookup(runId, null);
    }

    private ManagedRun lookup(UUID runId, UUID userId) {
        Objects.requireNonNull(runId, "运行编号不能为空");
        synchronized (admissionLock) {
            pruneExpired();
            var run = runs.get(runId);
            if (run == null || !Objects.equals(userId, run.userId))
                throw new IllegalArgumentException("运行不存在");
            return run;
        }
    }

    private void pruneExpired() {
        Instant cutoff = clock.instant().minus(retention);
        // TTL 从完整结果发布计时；查询不续期；活动和仍在收尾的任务永远不可回收。
        runs.values().removeIf(run -> evictable(run) && !run.completion.finishedAt().isAfter(cutoff));
    }

    private static boolean evictable(ManagedRun run) {
        return run.completion != null && run.context.termination().isDone();
    }

    private static void cancelDeadline(ManagedRun run) {
        if (run.deadline != null)
            run.deadline.cancel(false);
        if (run.queueDeadline != null)
            run.queueDeadline.cancel(false);
    }

    private static Snapshot snapshot(ManagedRun run) {
        var completion = run.completion;
        var result = completion == null ? null : completion.result();
        var runtime = run.context.snapshot();
        return new Snapshot(runtime.runId(), runtime.state(), runtime.stopReason(),
                completion != null && run.context.termination().isDone(),
                result == null ? null : result.status(), result == null ? null : result.answer(),
                completion != null && completion.rejected() ? "EXECUTOR_REJECTED"
                        : run.context.hasCleanupFailure() ? "CLEANUP_FAILURE"
                                : result == null ? null : result.failureCategory(),
                result == null ? List.of() : result.steps(), result == null ? null : result.source());
    }

    private record PublishedCompletion(CampusCreditAgentService.Result result, Instant finishedAt, boolean rejected,
            long order) {
    }

    private static final class ManagedRun {
        private final AgentRunContext context;
        private final UUID userId;
        private volatile PublishedCompletion completion;
        private volatile ScheduledFuture<?> deadline;
        private CampusRunArchive.Admission admission;
        private CampusAdmissionBudget.Reservation reservation;
        private CampusExecutionBudget executionBudget;
        private CampusCreditAgentService.Request request;
        private long began;
        private java.util.concurrent.FutureTask<Void> task;
        private final java.util.concurrent.atomic.AtomicBoolean started=new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean finishing=new java.util.concurrent.atomic.AtomicBoolean();
        private volatile ScheduledFuture<?> queueDeadline;

        private ManagedRun(AgentRunContext context, UUID userId) {
            this.context = context;
            this.userId = userId;
        }
    }

    /** terminated 表示业务、清理已收敛且结果已发布，不代表线程池工作线程已销毁。 */
    public record Snapshot(UUID runId, AgentRunState state, AgentStopReason stopReason,
            boolean terminated, CampusCreditAgentService.Status status, String answer,
            String failureCategory, List<CampusCreditAgentService.Step> steps,
            com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer source) {
    }
}
