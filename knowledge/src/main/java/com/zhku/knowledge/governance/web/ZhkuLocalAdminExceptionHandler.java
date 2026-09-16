package com.zhku.knowledge.governance.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/** 将本机管理失败映射为可操作且不泄露内部文本、路径的响应。 */
@RestControllerAdvice(assignableTypes = ZhkuLocalAdminController.class)
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
public class ZhkuLocalAdminExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        if (exception.getMessage() != null
                && exception.getMessage().startsWith("PDF 内容无法读取")) {
            return response(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PDF_READ_FAILED",
                    "PDF 读取失败，请检查文件后重新上传或提取。"
            );
        }
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数不符合要求，请检查后重试。");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> invalidState(IllegalStateException exception) {
        if (exception.getMessage() != null && exception.getMessage().startsWith("无法")) {
            return response(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LOCAL_ADMIN_UNAVAILABLE",
                    "本机资料服务暂时不可用，请检查本地服务后重试。"
            );
        }
        return response(
                HttpStatus.CONFLICT,
                "SUBMISSION_NOT_READY",
                "投稿尚未满足发布条件，请完成全部候选片段审核。"
        );
    }

    @ExceptionHandler(IOException.class)
    ResponseEntity<ErrorResponse> unreadablePdf(IOException exception) {
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PDF_READ_FAILED",
                "PDF 读取失败，请检查文件后重新上传或提取。"
        );
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status)
                .contentType(ZhkuLocalAdminController.JSON_UTF8)
                .body(new ErrorResponse(code, message));
    }

    record ErrorResponse(String code, String message) {
    }
}
