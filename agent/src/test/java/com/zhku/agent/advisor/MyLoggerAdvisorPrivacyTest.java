package com.zhku.agent.advisor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.BaseSubscriber;
import org.reactivestreams.Subscription;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

/** 仅使用开发期合成标记验证日志、分块和控制信号，不调用模型。 */
class MyLoggerAdvisorPrivacyTest {
    static final String RAW = "DEVELOPMENT_SYNTHETIC_PROMPT_REPLY_TOOLS_HEADERS_COOKIE_AUTHORIZATION_SECRET_HASH";
    final MyLoggerAdvisor advisor = new MyLoggerAdvisor();
    final ChatClientRequest request = new ChatClientRequest(new Prompt(RAW), Map.of("Authorization", RAW, "tools", RAW));
    final Logger logger = (Logger) LoggerFactory.getLogger(MyLoggerAdvisor.class);
    final ListAppender<ILoggingEvent> capture = new ListAppender<>();
    final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void attach() { capture.start(); logger.addAppender(capture); }
    @AfterEach void detach() { logger.detachAppender(capture); capture.stop(); }

    ChatClientResponse response(String text) {
        return new ChatClientResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text, Map.of("tools", RAW))))), Map.of("headers", RAW));
    }
    CallAdvisorChain call(Function<ChatClientRequest, ChatClientResponse> next) {
        return new CallAdvisorChain() {
            public ChatClientResponse nextCall(ChatClientRequest actual) { calls.incrementAndGet(); assertThat(actual).isSameAs(request); return next.apply(actual); }
            public List<CallAdvisor> getCallAdvisors() { return List.of(); }
        };
    }
    StreamAdvisorChain stream(Function<ChatClientRequest, Flux<ChatClientResponse>> next) {
        return new StreamAdvisorChain() {
            public Flux<ChatClientResponse> nextStream(ChatClientRequest actual) { calls.incrementAndGet(); assertThat(actual).isSameAs(request); return next.apply(actual); }
            public List<StreamAdvisor> getStreamAdvisors() { return List.of(); }
        };
    }
    void safeLogs(String... events) {
        assertThat(capture.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(events);
        for (var event : capture.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(RAW);
            assertThat(event.getArgumentArray()).isNullOrEmpty(); assertThat(event.getThrowableProxy()).isNull();
        }
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void synchronousResponseIdentityAndContentStayUnchanged() {
        var expected = response(RAW);
        assertThat(advisor.adviseCall(request, call(r -> expected))).isSameAs(expected);
        safeLogs("AI_CALL_STARTED", "AI_CALL_COMPLETED");
    }
    @Test void nullSynchronousResponseDoesNotReadBody() {
        assertThat(advisor.adviseCall(request, call(r -> null))).isNull();
        safeLogs("AI_CALL_STARTED", "AI_CALL_COMPLETED");
    }
    @Test void emptySynchronousResponseDoesNotReadBody() {
        var expected = new ChatClientResponse(new ChatResponse(List.of()), Map.of());
        assertThat(advisor.adviseCall(request, call(r -> expected))).isSameAs(expected);
        safeLogs("AI_CALL_STARTED", "AI_CALL_COMPLETED");
    }
    @Test void synchronousErrorPropagatesSameExceptionWithoutTextOrStack() {
        var failure = new IllegalStateException(RAW, new RuntimeException(RAW));
        assertThatThrownBy(() -> advisor.adviseCall(request, call(r -> { throw failure; }))).isSameAs(failure);
        safeLogs("AI_CALL_STARTED", "AI_CALL_FAILED");
    }
    @Test void streamPreservesChunksWithOneSubscription() {
        var first = response(RAW); var second = response(RAW + "_2"); var subscriptions = new AtomicInteger();
        var output = advisor.adviseStream(request, stream(r -> Flux.defer(() -> {
            subscriptions.incrementAndGet(); return Flux.just(first, second);
        })));
        assertThat(subscriptions.get()).isZero();
        var chunks = output.collectList().block();
        assertThat(chunks).hasSize(2); assertThat(chunks.get(0)).isSameAs(first); assertThat(chunks.get(1)).isSameAs(second);
        assertThat(subscriptions.get()).isEqualTo(1);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_COMPLETED");
    }
    @Test void emptyStreamCompletesWithoutReadingResponse() {
        assertThat(advisor.adviseStream(request, stream(r -> Flux.empty())).collectList().block()).isEmpty();
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_COMPLETED");
    }
    @Test void streamErrorPropagatesSameExceptionWithoutTextOrStack() {
        var failure = new IllegalStateException(RAW);
        assertThatThrownBy(() -> advisor.adviseStream(request, stream(r -> Flux.error(failure))).blockLast()).isSameAs(failure);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_FAILED");
    }
    @Test void immediateStreamChainErrorPropagatesOnce() {
        var failure = new IllegalStateException(RAW);
        assertThatThrownBy(() -> advisor.adviseStream(request, stream(r -> { throw failure; }))).isSameAs(failure);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_FAILED");
    }
    @Test void streamCancellationCancelsUpstreamExactlyOnce() {
        var subscriptions = new AtomicInteger(); var cancellations = new AtomicInteger(); var expected = response(RAW);
        var output = advisor.adviseStream(request, stream(r -> Flux.defer(() -> {
            subscriptions.incrementAndGet(); return Flux.concat(Flux.just(expected), Flux.never());
        }).doOnCancel(cancellations::incrementAndGet)));
        assertThat(output.take(1).blockLast()).isSameAs(expected);
        assertThat(subscriptions.get()).isEqualTo(1); assertThat(cancellations.get()).isEqualTo(1);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_CANCELLED");
    }

    @Test void streamDeliversChunkBeforePropagatingTheSameFailure() {
        var first = response(RAW);
        var failure = new IllegalStateException(RAW);
        var chunks = new ArrayList<ChatClientResponse>();
        var subscriptions = new AtomicInteger();
        var output = advisor.adviseStream(request, stream(r -> Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.concat(Flux.just(first), Flux.error(failure));
        })));

        assertThatThrownBy(() -> output.doOnNext(chunks::add).blockLast(Duration.ofSeconds(2)))
                .isSameAs(failure);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isSameAs(first);
        assertThat(subscriptions.get()).isEqualTo(1);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_FAILED");
    }

    @Test void streamPreservesIncrementalDemandWithoutPrefetchingTheBody() {
        var first = response(RAW);
        var second = response(RAW + "_2");
        var chunks = new ArrayList<ChatClientResponse>();
        var requests = new ArrayList<Long>();
        var output = advisor.adviseStream(request, stream(r -> Flux.just(first, second)
                .doOnRequest(requests::add)));
        var subscriber = new BaseSubscriber<ChatClientResponse>() {
            @Override protected void hookOnSubscribe(Subscription subscription) { request(1); }
            @Override protected void hookOnNext(ChatClientResponse response) { chunks.add(response); }
        };

        output.subscribe(subscriber);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isSameAs(first);
        assertThat(requests).containsExactly(1L);
        safeLogs("AI_STREAM_STARTED");

        subscriber.request(1);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.getLast()).isSameAs(second);
        assertThat(requests).containsExactly(1L, 1L);
        safeLogs("AI_STREAM_STARTED", "AI_STREAM_COMPLETED");
    }
}
