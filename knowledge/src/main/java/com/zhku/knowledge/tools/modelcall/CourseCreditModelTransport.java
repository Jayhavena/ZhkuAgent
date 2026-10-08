package com.zhku.knowledge.tools.modelcall;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** 校园同步模型的资源句柄；调用方拥有关闭责任，不能按每个 Run 创建发送池。 */
public final class CourseCreditModelTransport implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CourseCreditModelTransport.class);
    private static final Semaphore OWNER = new Semaphore(1);
    private final ThreadPoolExecutor senders;
    private final ThreadPoolExecutor io;
    private final HttpClient client;
    private final ChatModel model;
    private final Object admission = new Object();
    private final AtomicBoolean released = new AtomicBoolean();
    private boolean closing;
    private int active;

    CourseCreditModelTransport(Duration timeout, BiFunction<HttpClient, Executor, OpenAiChatModel> build) {
        if (!OWNER.tryAcquire()) {
            throw new IllegalStateException("校园模型资源尚未释放");
        }
        senders = pool(2, 16, "zhku-campus-model-body-");
        io = pool(4, 64, "zhku-campus-model-io-");
        HttpClient allocated = null;
        try {
            allocated = HttpClient.newBuilder().connectTimeout(timeout).executor(io).build();
            client = allocated;
            OpenAiChatModel delegate = build.apply(client, senders);
            model = prompt -> {
                synchronized (admission) {
                    if (closing || active >= 2) {
                        throw new RejectedExecutionException("校园模型传输当前不可受理");
                    }
                    active++;
                }
                try {
                    return delegate.call(prompt);
                } finally {
                    boolean finishClose;
                    synchronized (admission) {
                        active--;
                        finishClose = closing && active == 0;
                        admission.notifyAll();
                    }
                    if (finishClose) {
                        // 关闭预算耗尽后由最后一个实际返回者收尾；不用另起补偿线程或伪造宿主停止。
                        boolean interrupted = Thread.interrupted();
                        try {
                            close();
                        } finally {
                            if (interrupted) Thread.currentThread().interrupt();
                        }
                    }
                }
            };
        } catch (RuntimeException | Error failure) {
            if (allocated != null) allocated.shutdownNow();
            senders.shutdownNow();
            io.shutdownNow();
            // 构建尚未投递请求；只有实际资源收敛后才归还 owner 名额。
            boolean interrupted = Thread.interrupted();
            try {
                if (allocated == null || allocated.awaitTermination(Duration.ofSeconds(1))) {
                    if (senders.awaitTermination(1, TimeUnit.SECONDS) && io.awaitTermination(1, TimeUnit.SECONDS)) {
                        OWNER.release();
                    }
                }
            } catch (InterruptedException exception) {
                interrupted = true;
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
            throw failure;
        }
    }

    public ChatModel model() {
        return model;
    }

    /** 实际 SDK selector 和两个自有线程池均退出；close 返回或 Future 完成不代替此事实。 */
    public boolean isTerminated() {
        return client.isTerminated() && senders.isTerminated() && io.isTerminated();
    }

    @Override
    public void close() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        try {
            synchronized (admission) {
                closing = true;
                while (active != 0 && System.nanoTime() < deadline) {
                    TimeUnit.NANOSECONDS.timedWait(admission, Math.max(1, deadline - System.nanoTime()));
                }
                if (active != 0) {
                    LOG.warn("校园模型资源尚未收敛，保留 owner 名额");
                    return;
                }
            }
            // 不强制终止宿主任务；只有同步调用实际返回后才关闭 SDK 和发送资源。
            client.shutdown();
            if (!client.awaitTermination(remaining(deadline))) {
                LOG.warn("校园模型资源尚未收敛，保留 owner 名额");
                return;
            }
            senders.shutdown();
            io.shutdown();
            senders.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
            io.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
            if (isTerminated() && released.compareAndSet(false, true)) {
                OWNER.release();
            } else if (!isTerminated()) {
                LOG.warn("校园模型资源尚未收敛，保留 owner 名额");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("校园模型资源关闭被中断，保留 owner 名额");
        }
    }

    private static Duration remaining(long deadline) {
        return Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
    }

    private static ThreadPoolExecutor pool(int workers, int capacity, String name) {
        // 正文发送不能 CallerRuns：订阅发送链上直接写入会等待 demand 而阻塞 SDK。
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), Thread.ofPlatform().name(name, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
