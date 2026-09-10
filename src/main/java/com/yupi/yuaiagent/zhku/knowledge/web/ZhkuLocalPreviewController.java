package com.yupi.yuaiagent.zhku.knowledge.web;

import com.yupi.yuaiagent.zhku.knowledge.answer.GroundedAnswer;
import com.yupi.yuaiagent.zhku.knowledge.answer.GroundedAnswerService;
import com.yupi.yuaiagent.zhku.knowledge.answer.GroundedCitation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仅供开发者本机验证的校园问答接口。
 *
 * <p>双重启用条件避免把内部资料检索器误注册到普通运行环境；它不是学生端接口，也不能据此推断资料已获公开授权。</p>
 */
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-preview/answers")
public class ZhkuLocalPreviewController {

    private static final String LOCAL_PREVIEW_NOTICE =
            "仅供本机开发验证，不构成面向学生的正式校务依据。";

    private final GroundedAnswerService groundedAnswerService;

    public ZhkuLocalPreviewController(GroundedAnswerService groundedAnswerService) {
        this.groundedAnswerService = groundedAnswerService;
    }

    @PostMapping
    public ResponseEntity<?> answer(
            @RequestBody(required = false) LocalPreviewQuestionRequest request
    ) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return badRequest("问题不能为空");
        }

        try {
            GroundedAnswer answer = groundedAnswerService.answer(request.question());
            return ResponseEntity.ok(LocalPreviewAnswerResponse.from(answer));
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        } catch (IllegalStateException exception) {
            // 数据库连接细节不能出现在 HTTP 响应中，以免泄露本机部署信息。
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new LocalPreviewErrorResponse(
                            "KNOWLEDGE_SERVICE_UNAVAILABLE",
                            "本机知识库暂时不可用，请检查本地服务后重试。"
                    ));
        }
    }

    private static ResponseEntity<LocalPreviewErrorResponse> badRequest(
            String message
    ) {
        return ResponseEntity.badRequest().body(
                new LocalPreviewErrorResponse("INVALID_QUESTION", message)
        );
    }

    public record LocalPreviewQuestionRequest(String question) {
    }

    public record LocalPreviewAnswerResponse(
            String mode,
            String answer,
            List<LocalPreviewCitationResponse> citations,
            String notice
    ) {

        private static LocalPreviewAnswerResponse from(GroundedAnswer answer) {
            return new LocalPreviewAnswerResponse(
                    answer.mode().name(),
                    answer.answer(),
                    answer.citations().stream()
                            .map(LocalPreviewCitationResponse::from)
                            .toList(),
                    LOCAL_PREVIEW_NOTICE
            );
        }
    }

    public record LocalPreviewCitationResponse(
            String sourceTitle,
            String sourceLocator,
            int pageNumber
    ) {

        private static LocalPreviewCitationResponse from(
                GroundedCitation citation
        ) {
            return new LocalPreviewCitationResponse(
                    citation.sourceTitle(),
                    citation.sourceLocator(),
                    citation.pageNumber()
            );
        }
    }

    public record LocalPreviewErrorResponse(String code, String message) {
    }
}
