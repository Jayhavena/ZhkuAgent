package com.zhku.agent.agent.runtime;

import com.zhku.agent.agent.runtime.contract.AgentRunEvent;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** 有界、尽力而为的进程内观察交付；诊断丢失不改变运行事实或业务结果。 */
public final class AgentRunObservationDispatcher implements AutoCloseable {
    private static final Object OWNER_GATE = new Object();
    private static final AtomicLong OWNER_IDS = new AtomicLong();
    private static AgentRunObservationDispatcher owned;
    private static final long CALLBACK_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private static final long DRAIN_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long SCAN_NANOS = TimeUnit.MILLISECONDS.toNanos(25);

    public enum OwnerPhase { OPEN, DRAINING, CLOSED }
    public enum Phase { DISABLED, NEW, REGISTERED, FINAL_PENDING, DRAINED, DETACHED }
    public enum DetachReason { NONE, SATURATED, OWNER_CLOSED, CALLBACK_TIMEOUT, FINAL_TIMEOUT, OBSERVER_ERROR, BOOT_FAILED }
    public enum DropReason { NONE, OVERFLOW, OVERSIZED, DETACHED, OWNER_CLOSED }

    public record ObservationStatus(Phase phase, DetachReason detachReason, boolean registered,
            boolean ticketQueued, boolean inFlight, int normalPending, int finalPending,
            long lastProducedSequence, long lastStartedSequence, long lastReturnedSequence,
            long normalReturnCount, long exceptionCount, long dropCount,
            long firstDroppedSequence, long lastDroppedSequence, DropReason lastDropReason) { }

    public record Status(OwnerPhase phase, boolean initializationComplete, int registeredCount,
            int ticketCount, int inFlightCount, int liveThreadCount, long liveSampleNanos, boolean bootFailed) { }

    private final Object deliveryLock = new Object();
    private final Set<Subscription> registry = new HashSet<>();
    private final ArrayDeque<Subscription> tickets = new ArrayDeque<>(32);
    private final Slot[] slots = {new Slot(), new Slot(), new Slot(), new Slot()};
    private final Thread[] threads = new Thread[5];
    private final boolean processOwned;
    private final long ownerId = OWNER_IDS.incrementAndGet();
    private volatile OwnerPhase phase = OwnerPhase.OPEN;
    private volatile boolean initializationComplete;
    private volatile boolean bootFailed;
    private long generation;
    private long closeDeadline;

    private AgentRunObservationDispatcher(boolean processOwned) {
        this.processOwned = processOwned;
    }

    private static final class DefaultHolder {
        private static final AgentRunObservationDispatcher INSTANCE = createDefault();
    }

    public static AgentRunObservationDispatcher processDefault() {
        return DefaultHolder.INSTANCE;
    }

    private static AgentRunObservationDispatcher createDefault() {
        var owner = new AgentRunObservationDispatcher(true);
        owner.initialize();
        return owner;
    }

    public static AgentRunObservationDispatcher createOwned() {
        AgentRunObservationDispatcher previous;
        synchronized (OWNER_GATE) { previous = owned; }
        // G不持有D，也不在最后一条线程自己的finally里提前释放物理额度。
        boolean reusable = previous == null || previous.physicallyClosed();
        var candidate = new AgentRunObservationDispatcher(false);
        synchronized (OWNER_GATE) {
            if (owned != previous || !reusable)
                throw new IllegalStateException("显式观察资源尚未实际退出");
            owned = candidate;
        }
        candidate.initialize();
        return candidate;
    }

    private void initialize() {
        try {
            if (processOwned) {
                // 无观察器不会触及holder；唯一关闭者是JVM，借用者不能关闭默认资源。
                Runtime.getRuntime().addShutdownHook(new Thread(
                        () -> closeInternal(Duration.ofSeconds(1)), "agent-observation-shutdown"));
            }
            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(i == 4 ? this::supervise : () -> work(slots[index]),
                        "agent-observation-" + ownerId + "-" + i);
                threads[i].setDaemon(true);
            }
            for (Thread thread : threads) {
                if (phase != OwnerPhase.OPEN) break;
                thread.start();
            }
        } catch (RuntimeException exception) {
            bootFailed = true;
            closeInternal(Duration.ofSeconds(1));
            // 不传播装配异常原文，也不让观察资源不可用变成模型/业务失败。
        } finally {
            initializationComplete = true;
        }
    }

    private boolean physicallyClosed() {
        if (!initializationComplete || phase != OwnerPhase.CLOSED) return false;
        for (Thread thread : threads) if (thread != null && thread.isAlive()) return false;
        return true;
    }

    Subscription subscribe(UUID runId, Consumer<AgentRunEvent> observer) {
        // 创建Context早于宿主准入；只有首次offer才占32个登记名额。
        return new Subscription(runId, Objects.requireNonNull(observer));
    }

    static ObservationStatus disabledStatus() {
        return new ObservationStatus(Phase.DISABLED, DetachReason.NONE, false, false, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, DropReason.NONE);
    }

    ObservationStatus observationStatus(Subscription subscription) {
        synchronized (deliveryLock) {
            return new ObservationStatus(subscription.phase, subscription.detachReason,
                    registry.contains(subscription), subscription.ticketQueued, subscription.inFlight,
                    subscription.normal.size(), subscription.finalEvent == null ? 0 : 1,
                    subscription.produced, subscription.started, subscription.returned,
                    subscription.normalReturns, subscription.exceptions, subscription.drops,
                    subscription.firstDropped, subscription.lastDropped, subscription.lastDropReason);
        }
    }

    public Status status() {
        OwnerPhase value;
        int registered, queued, active;
        boolean initialized, failed;
        synchronized (deliveryLock) {
            value = phase;
            registered = registry.size();
            queued = tickets.size();
            active = inFlightCount();
            initialized = initializationComplete;
            failed = bootFailed;
        }
        // 物理存活在D外采样，不把它与上面的逻辑视图假称同时原子。
        int live = 0;
        for (Thread thread : threads) if (thread != null && thread.isAlive()) live++;
        return new Status(value, initialized, registered, queued, active, live, System.nanoTime(), failed);
    }

    void offer(Subscription subscription, AgentRunEvent event) {
        synchronized (deliveryLock) {
            subscription.produced = event.sequence();
            if (subscription.phase == Phase.NEW) {
                if (phase != OwnerPhase.OPEN || registry.size() == 32) {
                    detach(subscription, bootFailed ? DetachReason.BOOT_FAILED
                            : phase != OwnerPhase.OPEN ? DetachReason.OWNER_CLOSED : DetachReason.SATURATED);
                } else {
                    registry.add(subscription);
                    subscription.phase = Phase.REGISTERED;
                }
            }
            if (phase != OwnerPhase.OPEN || !registered(subscription)) {
                drop(subscription, event, phase != OwnerPhase.OPEN ? DropReason.OWNER_CLOSED : DropReason.DETACHED);
                return;
            }
            if (!withinPayloadBudget(event)) {
                drop(subscription, event, DropReason.OVERSIZED);
                return;
            }
            if (event.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                subscription.finalEvent = event;
                subscription.finalAt = System.nanoTime();
                subscription.phase = Phase.FINAL_PENDING;
            } else {
                if (subscription.normal.size() == 32) drop(subscription, subscription.normal.removeFirst(),
                        DropReason.OVERFLOW);
                subscription.normal.addLast(event);
            }
            schedule(subscription);
            deliveryLock.notifyAll();
        }
    }

    private boolean registered(Subscription subscription) {
        return subscription.phase == Phase.REGISTERED || subscription.phase == Phase.FINAL_PENDING;
    }

    private void schedule(Subscription subscription) {
        if (registered(subscription) && !subscription.ticketQueued && !subscription.inFlight
                && (!subscription.normal.isEmpty() || subscription.finalEvent != null)) {
            tickets.addLast(subscription);
            subscription.ticketQueued = true;
        }
    }

    private void work(Slot slot) {
        while (true) {
            // Consumer可自己设置中断位；仅在真实返回后清理观察线程，绝不清业务线程的停止事实。
            Thread.interrupted();
            Subscription subscription;
            AgentRunEvent event;
            long token;
            synchronized (deliveryLock) {
                while (tickets.isEmpty() && phase != OwnerPhase.CLOSED) {
                    try { deliveryLock.wait(); } catch (InterruptedException ignored) { }
                }
                if (phase == OwnerPhase.CLOSED) return;
                subscription = tickets.removeFirst();
                subscription.ticketQueued = false;
                event = subscription.normal.isEmpty() ? subscription.finalEvent : subscription.normal.removeFirst();
                if (event == subscription.finalEvent) subscription.finalEvent = null;
                token = ++generation;
                slot.subscription = subscription;
                slot.event = event;
                slot.token = token;
                slot.calling = false;
                subscription.inFlight = true;
            }
            boolean permitted;
            synchronized (deliveryLock) {
                permitted = registered(subscription) && slot.token == token && phase != OwnerPhase.CLOSED;
                if (permitted) {
                    slot.calling = true;
                    slot.startedAt = System.nanoTime();
                    subscription.started = event.sequence();
                }
            }
            boolean exceptional = false;
            Error fatal = null;
            try {
                // 调用许可在D内提交，accept在D/E之外；已经许可的在途调用不能假称可追回。
                if (permitted) subscription.observer.accept(event);
            } catch (RuntimeException exception) {
                exceptional = true;
            } catch (Error error) {
                exceptional = true;
                synchronized (deliveryLock) { detach(subscription, DetachReason.OBSERVER_ERROR); }
                if (error instanceof VirtualMachineError || error instanceof ThreadDeath) fatal = error;
            } finally {
                synchronized (deliveryLock) {
                    if (slot.token == token) {
                        if (permitted) {
                            subscription.returned = event.sequence();
                            if (exceptional) subscription.exceptions = increment(subscription.exceptions);
                            else subscription.normalReturns = increment(subscription.normalReturns);
                        } else drop(subscription, event, DropReason.DETACHED);
                        slot.event = null;
                        slot.subscription = null;
                        slot.calling = false;
                        subscription.inFlight = false;
                        if (registered(subscription) && event.type() == AgentRunEvent.Type.RUN_TERMINATED) {
                            registry.remove(subscription);
                            subscription.phase = Phase.DRAINED;
                        } else schedule(subscription);
                        deliveryLock.notifyAll();
                    }
                }
            }
            if (fatal != null) throw fatal;
        }
    }

    private void supervise() {
        synchronized (deliveryLock) {
            while (phase != OwnerPhase.CLOSED) {
                long now = System.nanoTime();
                for (Slot slot : slots) {
                    if (slot.subscription != null && slot.calling && now - slot.startedAt >= CALLBACK_NANOS)
                        detach(slot.subscription, DetachReason.CALLBACK_TIMEOUT);
                }
                for (Subscription subscription : new ArrayList<>(registry)) {
                    if (subscription.phase == Phase.FINAL_PENDING && now - subscription.finalAt >= DRAIN_NANOS)
                        detach(subscription, DetachReason.FINAL_TIMEOUT);
                }
                // 首个关闭者可能中断或提前返回；监督线程继续执行原截止，不延长预算。
                if (phase == OwnerPhase.DRAINING
                        && ((tickets.isEmpty() && inFlightCount() == 0) || now - closeDeadline >= 0)) {
                    for (Subscription subscription : new ArrayList<>(registry))
                        detach(subscription, DetachReason.OWNER_CLOSED);
                    phase = OwnerPhase.CLOSED;
                    deliveryLock.notifyAll();
                    break;
                }
                try { TimeUnit.NANOSECONDS.timedWait(deliveryLock, SCAN_NANOS); }
                catch (InterruptedException ignored) { }
            }
        }
    }

    private void detach(Subscription subscription, DetachReason reason) {
        if (subscription.phase == Phase.DETACHED || subscription.phase == Phase.DRAINED) return;
        subscription.phase = Phase.DETACHED;
        subscription.detachReason = reason;
        registry.remove(subscription);
        if (subscription.ticketQueued) {
            tickets.remove(subscription);
            subscription.ticketQueued = false;
        }
        while (!subscription.normal.isEmpty()) drop(subscription, subscription.normal.removeFirst(), DropReason.DETACHED);
        if (subscription.finalEvent != null) {
            drop(subscription, subscription.finalEvent, DropReason.DETACHED);
            subscription.finalEvent = null;
        }
        // 不清inFlight/slot、不interrupt、不补worker：逻辑脱离不等于物理释放。
        deliveryLock.notifyAll();
    }

    private static void drop(Subscription subscription, AgentRunEvent event, DropReason reason) {
        subscription.drops = increment(subscription.drops);
        subscription.firstDropped = subscription.firstDropped == 0 ? event.sequence()
                : Math.min(subscription.firstDropped, event.sequence());
        subscription.lastDropped = Math.max(subscription.lastDropped, event.sequence());
        subscription.lastDropReason = reason;
    }

    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }

    private int inFlightCount() {
        int count = 0;
        for (Slot slot : slots) if (slot.subscription != null) count++;
        return count;
    }

    private static boolean withinPayloadBudget(AgentRunEvent event) {
        long length = errorLength(event.error())
                + (event.runSnapshot() == null ? 0 : errorLength(event.runSnapshot().error()));
        AgentToolCall call = event.toolCall();
        if (call != null) {
            length += length(call.callId()) + length(call.providerCallId()) + length(call.toolName())
                    + length(call.argumentsJson());
            if (call.result() != null) length += length(call.result().payload()) + errorLength(call.result().error());
        }
        return length <= 8192;
    }

    private static long length(String value) { return value == null ? 0 : (long) value.length(); }
    private static long errorLength(AgentRuntimeError error) {
        return error == null ? 0 : length(error.code()) + length(error.safeMessage());
    }

    @Override
    public void close() { close(Duration.ofSeconds(1)); }

    public void close(Duration remaining) {
        if (processOwned) throw new IllegalStateException("默认观察资源只能由JVM关闭");
        Objects.requireNonNull(remaining);
        if (remaining.isNegative() || remaining.compareTo(Duration.ofSeconds(1)) > 0)
            throw new IllegalArgumentException("观察资源关闭预算必须为0—1000ms");
        closeInternal(remaining);
    }

    private void closeInternal(Duration remaining) {
        long callerDeadline = System.nanoTime() + remaining.toNanos();
        boolean interrupted = false;
        synchronized (deliveryLock) {
            if (phase == OwnerPhase.OPEN) {
                phase = OwnerPhase.DRAINING;
                closeDeadline = callerDeadline;
                deliveryLock.notifyAll();
            }
            while (phase != OwnerPhase.CLOSED) {
                boolean empty = tickets.isEmpty() && inFlightCount() == 0;
                if (empty || System.nanoTime() - closeDeadline >= 0) {
                    for (Subscription subscription : new ArrayList<>(registry))
                        detach(subscription, DetachReason.OWNER_CLOSED);
                    phase = OwnerPhase.CLOSED;
                    deliveryLock.notifyAll();
                    break;
                }
                long wait = Math.min(closeDeadline, callerDeadline) - System.nanoTime();
                if (wait <= 0) break;
                try { TimeUnit.NANOSECONDS.timedWait(deliveryLock, wait); }
                catch (InterruptedException exception) { interrupted = true; break; }
            }
        }
        // 总预算共享，不给每条线程再叠加1000ms；isAlive才是额度释放的依据。
        for (Thread thread : threads) {
            // 调用者被中断后不重新进入join；监督线程独立执行已固定的关闭截止。
            if (interrupted) break;
            long nanos = callerDeadline - System.nanoTime();
            if (thread == null || thread == Thread.currentThread() || nanos <= 0) continue;
            try { TimeUnit.NANOSECONDS.timedJoin(thread, nanos); }
            catch (InterruptedException exception) { interrupted = true; break; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    static final class Subscription {
        final UUID runId;
        final Consumer<AgentRunEvent> observer;
        final ArrayDeque<AgentRunEvent> normal = new ArrayDeque<>(32);
        Phase phase = Phase.NEW;
        DetachReason detachReason = DetachReason.NONE;
        boolean ticketQueued, inFlight;
        AgentRunEvent finalEvent;
        long finalAt, produced, started, returned, normalReturns, exceptions, drops, firstDropped, lastDropped;
        DropReason lastDropReason = DropReason.NONE;

        Subscription(UUID runId, Consumer<AgentRunEvent> observer) {
            this.runId = runId;
            this.observer = observer;
        }
    }

    private static final class Slot {
        Subscription subscription;
        AgentRunEvent event;
        long token, startedAt;
        boolean calling;
    }
}
