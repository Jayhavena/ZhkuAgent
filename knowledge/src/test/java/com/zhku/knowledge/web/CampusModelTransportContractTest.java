package com.zhku.knowledge.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机假供应商的真实 TCP 交换；EOF 和线程退出分别证明资源收敛，不依赖 Future.cancel。 */
class CampusModelTransportContractTest {
    private static final long READY_MS = 2_000;
    private static final long RETURN_MS = 1_000;
    private static final Duration PROFILE_TIMEOUT = Duration.ofMillis(500);
    private final Logger springAiLogger = (Logger) LoggerFactory.getLogger("org.springframework.ai");
    private Level previousLevel;

    @BeforeEach
    void silenceProviderDetails() {
        previousLevel = springAiLogger.getLevel();
        springAiLogger.setLevel(Level.OFF);
    }

    @AfterEach
    void restoreLogging() {
        springAiLogger.setLevel(previousLevel);
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"NO_HEADERS", "PARTIAL_BODY", "TRICKLE"})
    void stalledExchangeObeysProfileDeadlineAndClosesConnection(Mode mode) throws Exception {
        try (var peer = new ProtocolPeer(mode, 1);
                var transport = transport(peer, PROFILE_TIMEOUT)) {
            var call = new ActualCall(transport.model());
            try {
                call.start();
                assertThat(peer.entered.await(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(call.finished.await(remainingReturnMillis(call.started), TimeUnit.MILLISECONDS))
                        .as("实际 SDK 调用必须在冻结的 1000ms 内返回").isTrue();
                assertThat(call.failure.get()).isNotNull();
                assertThat(peer.closedByClient.await(RETURN_MS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(peer.requests.get()).isEqualTo(1);
                peer.assertHealthy();
            } finally {
                // RED 也要释放真实 socket 并 join；不让旧 SDK 的等待逃到下一项测试。
                call.thread.interrupt();
                peer.closeSockets();
                call.thread.join(READY_MS);
                assertThat(call.thread.isAlive()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"RATE_LIMIT", "UNAVAILABLE"})
    void transientStatusHasOneWireAttemptAndExplicitNextCallRecovers(Mode mode) throws Exception {
        try (var peer = new ProtocolPeer(mode, 1); var transport = transport(peer, PROFILE_TIMEOUT)) {
            var first = new ActualCall(transport.model());
            try {
                first.start();
                assertThat(first.finished.await(RETURN_MS, TimeUnit.MILLISECONDS)).isTrue();
                assertThat(first.failure.get()).isNotNull();
                assertThat(peer.requests.get()).isEqualTo(1);
                var next = new ActualCall(transport.model());
                try {
                    next.start();
                    assertThat(next.finished.await(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
                    assertThat(next.failure.get()).isNull();
                    assertThat(peer.requests.get()).isEqualTo(2);
                    peer.assertHealthy();
                } finally {
                    next.thread.interrupt();
                    next.thread.join(READY_MS);
                    assertThat(next.thread.isAlive()).isFalse();
                }
            } finally {
                first.thread.interrupt();
                first.thread.join(READY_MS);
                assertThat(first.thread.isAlive()).isFalse();
            }
        }
    }

    @Test
    void twoRealTimeoutsReturnWorkersAndThirdRunExecutes() throws Exception {
        var worker = (ThreadPoolExecutor) new CampusAgentPreviewConfiguration().zhkuCampusAgentWorker();
        var timer = Executors.newSingleThreadScheduledExecutor();
        try (var peer = new ProtocolPeer(Mode.NO_HEADERS, 2); var transport = transport(peer, PROFILE_TIMEOUT)) {
            var manager = manager(transport.model(), worker, timer);
            var first = manager.start(request());
            var second = manager.start(request());
            assertThat(peer.entered.await(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            await(() -> manager.find(first.runId()).terminated() && manager.find(second.runId()).terminated(), RETURN_MS);
            for (var run : List.of(first, second)) {
                var finished = manager.find(run.runId());
                assertThat(finished.state()).isEqualTo(AgentRunState.FAILED);
                assertThat(finished.failureCategory()).isEqualTo("MODEL_FAILURE");
                assertThat(finished.stopReason()).isEqualTo(AgentStopReason.NONE);
            }
            assertThat(peer.closedByClient.await(RETURN_MS, TimeUnit.MILLISECONDS)).isTrue();
            await(() -> worker.getActiveCount() == 0 && worker.getQueue().isEmpty(), READY_MS);
            var recovery = manager.start(request());
            await(() -> manager.find(recovery.runId()).terminated(), READY_MS);
            assertThat(manager.find(recovery.runId()).state()).isEqualTo(AgentRunState.INCOMPLETE);
            assertThat(manager.find(recovery.runId()).status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
            assertThat(manager.find(recovery.runId()).failureCategory()).isEqualTo("NONE");
            assertThat(peer.requests.get()).isEqualTo(3);
            peer.assertHealthy();
        } finally {
            worker.shutdownNow();
            timer.shutdownNow();
            assertThat(worker.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(timer.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    @Test
    void userCancelActuallyReturnsSdkAndClosesInFlightSocket() throws Exception {
        var worker = (ThreadPoolExecutor) new CampusAgentPreviewConfiguration().zhkuCampusAgentWorker();
        var timer = Executors.newSingleThreadScheduledExecutor();
        try (var peer = new ProtocolPeer(Mode.NO_HEADERS, 1); var transport = transport(peer, Duration.ofSeconds(5))) {
            var sdkExited = new CountDownLatch(1);
            var sdkInterrupted = new AtomicBoolean();
            ChatModel counted = prompt -> {
                try {
                    return transport.model().call(prompt);
                } finally {
                    sdkInterrupted.set(Thread.currentThread().isInterrupted());
                    sdkExited.countDown();
                }
            };
            var manager = manager(counted, worker, timer);
            var run = manager.start(request());
            assertThat(peer.entered.await(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            long started = System.nanoTime();
            manager.cancel(run.runId());
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThanOrEqualTo(200);
            assertThat(sdkExited.await(RETURN_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(sdkInterrupted.get()).isTrue();
            assertThat(peer.closedByClient.await(RETURN_MS, TimeUnit.MILLISECONDS)).isTrue();
            await(() -> manager.find(run.runId()).terminated(), READY_MS);
            assertThat(manager.find(run.runId()).state()).isEqualTo(AgentRunState.CANCELLED);
            assertThat(manager.find(run.runId()).stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
            assertThat(peer.requests.get()).isEqualTo(1);
            peer.assertHealthy();
        } finally {
            worker.shutdownNow();
            timer.shutdownNow();
            assertThat(worker.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(timer.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    private static CampusCreditAgentManager manager(ChatModel model, ThreadPoolExecutor worker,
            java.util.concurrent.ScheduledExecutorService timer) {
        var service = new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> {
                    throw new AssertionError("缺课程不得查询政策");
                }, 3);
        return new CampusCreditAgentManager(service, worker, timer, Duration.ofSeconds(30));
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("请汇总虚构课程", List.of());
    }

    private static void await(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static long remainingReturnMillis(long started) {
        return Math.max(0, RETURN_MS - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    private static CourseCreditModelTransport transport(ProtocolPeer peer, Duration timeout) {
        var profile = new RagProviderProfile("local-resource-contract",
                "http://127.0.0.1:" + peer.server.getLocalPort() + "/v1",
                "ZHKU_LOCAL_RESOURCE_CONTRACT_KEY", "stub-chat", "stub-embed", 1, timeout, "stub-index");
        return CourseCreditChatModelFactory.open(profile,
                new ApiKeyResolver(Map.of(profile.apiKeyEnvVar(), "sk-local-fake-only")));
    }

    private static final class ActualCall {
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final Thread thread;
        private long started;

        private ActualCall(ChatModel model) {
            thread = new Thread(() -> {
                try {
                    model.call(new Prompt("仅使用合成问题"));
                } catch (Throwable exception) {
                    failure.set(exception);
                } finally {
                    finished.countDown();
                }
            }, "r14-actual-sdk-call");
        }

        private void start() {
            started = System.nanoTime();
            thread.start();
        }
    }

    private enum Mode { NO_HEADERS, PARTIAL_BODY, TRICKLE, NORMAL, RATE_LIMIT, UNAVAILABLE }

    private static final class ProtocolPeer implements AutoCloseable {
        private static final String NORMAL = """
                {"id":"stub","object":"chat.completion","created":1,"model":"stub-chat",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"请补充课程"},"finish_reason":"stop"}]}
                """;
        private final ServerSocket server = new ServerSocket();
        private final ThreadPoolExecutor handlers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), Thread.ofPlatform().name("r14-peer-handler-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch entered;
        private final CountDownLatch closedByClient;
        private final Thread acceptor;
        private final int faultRequests;
        private final Mode mode;
        private volatile boolean closing;

        private ProtocolPeer(Mode mode, int faultRequests) throws IOException {
            this.mode = mode;
            this.faultRequests = faultRequests;
            entered = new CountDownLatch(faultRequests);
            closedByClient = new CountDownLatch(faultRequests);
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            acceptor = new Thread(() -> {
                while (!closing) {
                    try {
                        Socket socket = server.accept();
                        sockets.add(socket);
                        handlers.execute(() -> handle(socket));
                    } catch (IOException | RuntimeException exception) {
                        if (!closing) {
                            failure.compareAndSet(null, exception);
                        }
                    }
                }
            }, "r14-peer-acceptor");
            acceptor.start();
        }

        private void handle(Socket socket) {
            try (socket) {
                socket.setSoTimeout(5_000);
                InputStream input = new BufferedInputStream(socket.getInputStream());
                consumeRequest(input);
                int number = requests.incrementAndGet();
                entered.countDown();
                Mode response = number <= faultRequests ? mode : Mode.NORMAL;
                if (response == Mode.NORMAL || response == Mode.RATE_LIMIT || response == Mode.UNAVAILABLE) {
                    int status = response == Mode.RATE_LIMIT ? 429 : response == Mode.UNAVAILABLE ? 503 : 200;
                    byte[] body = (status == 200 ? NORMAL : "{\"error\":{\"message\":\"synthetic failure\"}}")
                            .getBytes(StandardCharsets.UTF_8);
                    send(socket, "HTTP/1.1 " + status + " Stub\r\nContent-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n");
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    return;
                }
                if (response != Mode.NO_HEADERS) {
                    send(socket, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                            + "Content-Length: 100000\r\nConnection: close\r\n\r\n");
                    send(socket, response == Mode.PARTIAL_BODY ? "{\"id\":\"" : " ");
                }
                if (response == Mode.TRICKLE) {
                    socket.setSoTimeout(50);
                }
                // 夹具超时本身不是客户端 EOF；必须观察实际 -1 或连接重置。
                while (!closing) {
                    try {
                        if (input.read() == -1) {
                            closedByClient.countDown();
                            return;
                        }
                    } catch (SocketTimeoutException exception) {
                        if (response != Mode.TRICKLE) {
                            throw exception;
                        }
                        send(socket, " ");
                    }
                }
            } catch (SocketException exception) {
                if (!closing && (exception.getMessage().contains("reset")
                        || exception.getMessage().contains("Broken pipe")
                        || exception.getMessage().contains("abort"))) {
                    closedByClient.countDown();
                } else if (!closing) {
                    failure.compareAndSet(null, exception);
                }
            } catch (Exception exception) {
                if (!closing) {
                    failure.compareAndSet(null, exception);
                }
            } finally {
                sockets.remove(socket);
            }
        }

        private static void send(Socket socket, String text) throws IOException {
            socket.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }

        private static void consumeRequest(InputStream input) throws IOException {
            String first = line(input);
            if (!first.startsWith("POST /v1/chat/completions ")) {
                throw new IOException("unexpected synthetic request path");
            }
            int length = 0;
            boolean chunked = false;
            int headers = first.length();
            String header;
            while (!(header = line(input)).isEmpty()) {
                headers += header.length();
                if (headers > 65_536) {
                    throw new IOException("synthetic headers too large");
                }
                String lower = header.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
                }
                chunked |= lower.startsWith("transfer-encoding:") && lower.contains("chunked");
            }
            if (chunked) {
                int size;
                while ((size = Integer.parseInt(line(input).split(";", 2)[0], 16)) != 0) {
                    discard(input, size);
                    if (!line(input).isEmpty()) {
                        throw new IOException("bad synthetic chunk");
                    }
                }
                while (!line(input).isEmpty()) {
                    // 消费尾部，绝不存储或回显请求内容/鉴权头。
                }
            } else {
                discard(input, length);
            }
        }

        private static void discard(InputStream input, int count) throws IOException {
            if (count < 0 || count > 1_048_576 || input.readNBytes(count).length != count) {
                throw new IOException("bad synthetic request size");
            }
        }

        private static String line(InputStream input) throws IOException {
            var result = new StringBuilder();
            int next;
            while ((next = input.read()) != -1 && next != '\n') {
                if (result.length() >= 65_536) {
                    throw new IOException("synthetic line too large");
                }
                if (next != '\r') {
                    result.append((char) next);
                }
            }
            if (next == -1) {
                throw new IOException("request ended early");
            }
            return result.toString();
        }

        private void assertHealthy() {
            assertThat(failure.get()).isNull();
        }

        private void closeSockets() throws IOException {
            closing = true;
            server.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }

        @Override
        public void close() throws Exception {
            closeSockets();
            handlers.shutdownNow();
            acceptor.join(READY_MS);
            assertThat(acceptor.isAlive()).isFalse();
            assertThat(handlers.awaitTermination(READY_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }
}
