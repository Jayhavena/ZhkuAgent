package com.zhku.knowledge.web;

import com.zhku.knowledge.web.streaming.SseConversationStreamer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** 仅供本机预览页面使用的阶段 5 会话接口。 */
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-preview/conversations")
public class ZhkuLocalPreviewConversationController {

    private static final MediaType JSON_UTF8 = new MediaType(
            MediaType.APPLICATION_JSON,
            StandardCharsets.UTF_8
    );

    private final LocalPreviewConversationFacade facade;
    private final ObjectProvider<SseConversationStreamer> streamerProvider;

    public ZhkuLocalPreviewConversationController(
            LocalPreviewConversationFacade facade,
            ObjectProvider<SseConversationStreamer> streamerProvider
    ) {
        this.facade = facade;
        this.streamerProvider = streamerProvider;
    }

    @PostMapping
    public ResponseEntity<ConversationCreatedResponse> create() {
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(ConversationCreatedResponse.from(facade.createConversation()));
    }

    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sendMessage(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) ConversationMessageRequest request
    ) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return badRequest("INVALID_MESSAGE", "消息不能为空");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(JSON_UTF8)
                    .body(facade.sendMessage(sessionId, request.message()));
        } catch (IllegalArgumentException exception) {
            return badRequest("INVALID_MESSAGE", exception.getMessage());
        } catch (IllegalStateException exception) {
            return serviceUnavailable();
        }
    }

    /**
     * 与同步消息共用同一资源地址，只由 Accept 协商传输形式。
     *
     * <p>未注册流能力时显式失败而非回落 JSON，避免 SSE 客户端把一次性 JSON 误当成长连接。</p>
     */
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /**
     * 返回类型必须保留 {@link SseEmitter}，不能擦除为通配泛型。
     *
     * <p>Spring MVC 依据声明类型选择异步 emitter 处理器；若写成 {@code ResponseEntity<?>}，
     * 会退回普通消息转换器并在浏览器协商 SSE 时稳定 500。</p>
     */
    public ResponseEntity<SseEmitter> streamMessage(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) ConversationMessageRequest request
    ) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new InvalidSseMessageException("消息不能为空");
        }
        SseConversationStreamer streamer = streamerProvider.getIfAvailable();
        if (streamer == null) {
            throw new SseServiceUnavailableException();
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamer.stream(sessionId, request.message()));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new SseServiceUnavailableException();
        }
    }

    /** 流式成功路径使用具体 emitter 类型，错误路径仍返回原有 JSON 契约。 */
    @ExceptionHandler(InvalidSseMessageException.class)
    public ResponseEntity<LocalPreviewErrorResponse> invalidSseMessage(
            InvalidSseMessageException exception
    ) {
        return badRequest("INVALID_MESSAGE", exception.getMessage());
    }

    @ExceptionHandler(SseServiceUnavailableException.class)
    public ResponseEntity<LocalPreviewErrorResponse> unavailableSseService() {
        return serviceUnavailable();
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID sessionId) {
        try {
            return ResponseEntity.ok()
                    .contentType(JSON_UTF8)
                    .body(facade.cancelConversation(sessionId));
        } catch (IllegalStateException exception) {
            return serviceUnavailable();
        }
    }

    private static ResponseEntity<LocalPreviewErrorResponse> badRequest(
            String code,
            String message
    ) {
        return ResponseEntity.badRequest()
                .contentType(JSON_UTF8)
                .body(new LocalPreviewErrorResponse(code, message));
    }

    private static ResponseEntity<LocalPreviewErrorResponse> serviceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(JSON_UTF8)
                .body(new LocalPreviewErrorResponse(
                        "KNOWLEDGE_SERVICE_UNAVAILABLE",
                        "本机知识库暂时不可用，请检查本地服务后重试。"
                ));
    }

    public record ConversationMessageRequest(String message) {
    }

    /** HTTP 时间使用 ISO-8601 文本，避免客户端依赖 Jackson 对 Instant 的全局序列化配置。 */
    public record ConversationCreatedResponse(UUID sessionId, String expiresAt) {

        private static ConversationCreatedResponse from(
                LocalPreviewConversationFacade.ConversationCreated created
        ) {
            Objects.requireNonNull(created, "新建会话不能为空");
            return new ConversationCreatedResponse(
                    created.sessionId(),
                    created.expiresAt().toString()
            );
        }
    }

    public record LocalPreviewErrorResponse(String code, String message) {
    }

    private static final class InvalidSseMessageException extends RuntimeException {

        private InvalidSseMessageException(String message) {
            super(message);
        }
    }

    private static final class SseServiceUnavailableException extends RuntimeException {
    }
}
