package com.zhku.knowledge.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public ZhkuLocalPreviewConversationController(LocalPreviewConversationFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<ConversationCreatedResponse> create() {
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(ConversationCreatedResponse.from(facade.createConversation()));
    }

    @PostMapping("/{sessionId}/messages")
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
}
