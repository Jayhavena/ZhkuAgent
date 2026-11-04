package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmission;
import org.springframework.web.bind.annotation.*;

/** 只输出有限分类；驱动原因、请求文本和缓存正文不进入受控错误响应。 */
@RestControllerAdvice(basePackages="com.zhku.knowledge.controlled")
public class ControlledPreviewExceptionHandler {
    public record ErrorReply(String code,String message) { }
    public record AcceptedError(String code,String message,boolean accepted,UUID runId) { }
    public record SubmissionError(String code,String message,String acceptance,UUID submissionKey,UUID runId,Boolean accepted) { }
    @ExceptionHandler(CampusRunSubmission.Failure.class) ResponseEntity<?> submission(CampusRunSubmission.Failure failure) {
        var code=failure.code();
        if(code==CampusRunSubmission.Code.SUBMISSION_CONTENT_CONFLICT)return error(409,code.name(),"同一次提交的内容不一致");
        if(code==CampusRunSubmission.Code.SUBMISSION_NOT_FOUND)return error(404,code.name(),"当前未找到本人可见的提交，请勿据此自动重发");
        String acceptance=code==CampusRunSubmission.Code.SUBMISSION_UNCONFIRMED?"UNKNOWN"
                :code==CampusRunSubmission.Code.RESULT_READ_UNAVAILABLE?"ACCEPTED":"NOT_ACCEPTED";
        return ResponseEntity.status(code==CampusRunSubmission.Code.USER_IN_FLIGHT_LIMIT?429:503).header("Cache-Control","no-store")
                .body(new SubmissionError(code.name(),"请保留提交键确认原任务，不要自动重复发送",acceptance,failure.submissionKey(),
                        failure.runId(),acceptance.equals("ACCEPTED")?Boolean.TRUE:acceptance.equals("NOT_ACCEPTED")?Boolean.FALSE:null));
    }
    @ExceptionHandler(CampusRunAccessService.AcceptedResultUnavailable.class)
    ResponseEntity<?> accepted(CampusRunAccessService.AcceptedResultUnavailable failure) {
        return ResponseEntity.status(503).header("Cache-Control","no-store")
                .body(new AcceptedError("RESULT_READ_UNAVAILABLE","本次已受理，请查询该运行；不要自动重复提交",true,failure.runId()));
    }
    @ExceptionHandler(CampusRunArchive.Failure.class) ResponseEntity<?> archive(CampusRunArchive.Failure failure) {
        int status=switch(failure.code()) {case ARCHIVE_NOT_FOUND->404;case INVALID_REQUEST->400;default->503;};
        return error(status,failure.code().name(),failure.safeMessage());
    }
    @ExceptionHandler(PreviewIdentityRepository.Failure.class) ResponseEntity<?> identity(PreviewIdentityRepository.Failure failure) {
        int status=switch(failure.code()){case AUTH_REVOKED->401;case ROLE_DENIED->403;case IDENTITY_UNAVAILABLE->503;case INVALID_INPUT->400;case LOGIN_EXISTS->409;case BAD_PASSWORD->401;};
        return error(status,failure.code().name(),"当前身份许可不可用于本次操作");
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class,MissingRequestHeaderException.class})
    ResponseEntity<?> invalid(Exception failure) {return error(400,"INVALID_REQUEST","请求格式或字段无效");}
    @ExceptionHandler(RejectedExecutionException.class) ResponseEntity<?> busy(RejectedExecutionException failure) {
        return error(503,"EXECUTOR_REJECTED","当前无法受理新的运行");
    }
    @ExceptionHandler(RuntimeException.class) ResponseEntity<?> unexpected(RuntimeException failure) {
        return error(500,"INTERNAL_ERROR","本次请求无法完成");
    }
    private static ResponseEntity<?> error(int status,String code,String message){
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(new ErrorReply(code,message));
    }
}
