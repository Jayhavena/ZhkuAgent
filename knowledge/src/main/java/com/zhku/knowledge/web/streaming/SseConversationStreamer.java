package com.zhku.knowledge.web.streaming;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Spring MVC SSE 适配器；耗时作答放到受限执行器，避免占用 Servlet 请求线程。 */
public final class SseConversationStreamer {

    private final ConversationStreamProcessor processor;
    private final Executor executor;
    private final long timeoutMillis;

    public SseConversationStreamer(
            ConversationStreamProcessor processor,
            Executor executor,
            long timeoutMillis
    ) {
        this.processor = Objects.requireNonNull(processor, "会话流处理器不能为空");
        this.executor = Objects.requireNonNull(executor, "流式执行器不能为空");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("流式超时必须为正数");
        }
        this.timeoutMillis = timeoutMillis;
    }

    public SseEmitter stream(UUID sessionId, String message) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        executor.execute(() -> writeStream(emitter, sessionId, message));
        return emitter;
    }

    private void writeStream(SseEmitter emitter, UUID sessionId, String message) {
        try {
            processor.process(sessionId, message, (name, payload) -> emitter.send(
                    SseEmitter.event().name(name).data(payload)
            ));
            emitter.complete();
        } catch (IOException | RuntimeException exception) {
            // 浏览器断连与业务故障都必须结束 emitter，避免有界工作线程被悬挂任务耗尽。
            emitter.completeWithError(exception);
        }
    }
}
