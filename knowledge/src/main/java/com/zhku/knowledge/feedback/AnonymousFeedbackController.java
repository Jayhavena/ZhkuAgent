package com.zhku.knowledge.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * 匿名反馈 HTTP 边界。
 *
 * <p>这里手工白名单 JSON 字段，而不是依赖框架的“忽略未知属性”默认值；否则文本或
 * 身份字段可能被静默接受，造成未受治理的数据收集。</p>
 */
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/feedback")
public final class AnonymousFeedbackController {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "guideIdentifier", "guideVersion", "reason", "anonymousSessionHash"
    );

    private final AnonymousFeedbackService service;

    public AnonymousFeedbackController(AnonymousFeedbackService service) {
        this.service = Objects.requireNonNull(service, "匿名反馈服务不能为空");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submit(@RequestBody JsonNode payload) {
        try {
            AnonymousFeedbackReceipt receipt = service.submit(parse(payload));
            return ResponseEntity.status(receipt.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(receipt);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    new FeedbackErrorResponse("INVALID_ANONYMOUS_FEEDBACK", exception.getMessage())
            );
        }
    }

    private static AnonymousFeedbackSubmission parse(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("反馈请求必须是 JSON 对象");
        }
        Iterator<String> names = payload.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                throw new IllegalArgumentException("反馈请求包含不允许的字段");
            }
        }
        return new AnonymousFeedbackSubmission(
                requiredText(payload, "guideIdentifier"),
                requiredText(payload, "guideVersion"),
                parseReason(requiredText(payload, "reason")),
                requiredText(payload, "anonymousSessionHash")
        );
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("反馈请求缺少有效字段");
        }
        return value.textValue();
    }

    private static FeedbackReason parseReason(String value) {
        try {
            return FeedbackReason.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("反馈原因不受支持");
        }
    }

    private record FeedbackErrorResponse(String code, String detail) {
    }
}
