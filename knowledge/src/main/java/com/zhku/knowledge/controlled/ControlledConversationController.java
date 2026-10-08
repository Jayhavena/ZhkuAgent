package com.zhku.knowledge.controlled;

import com.zhku.knowledge.conversation.*;
import com.zhku.knowledge.security.*;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@Profile("zhku-controlled-preview")
@ConditionalOnProperty(name="zhku.controlled-preview.conversations.enabled",havingValue="true",matchIfMissing=false)
@RequestMapping(value="/zhku/controlled-preview/v1/conversations",produces="application/json")
public final class ControlledConversationController {
    private final ControlledConversationFacade facade;
    public ControlledConversationController(ControlledConversationFacade facade) { this.facade=facade; }
    @PostMapping(consumes="application/json") public ResponseEntity<?> create(@AuthenticationPrincipal PreviewPrincipal principal,
            @RequestBody(required=false) Map<String,Object> body) {
        if(body!=null&&!body.isEmpty()) throw new IllegalArgumentException("创建不接受身份、状态或TTL字段");
        return response(201,facade.createConversation(principal.actor()));
    }
    @GetMapping("/{sessionId}") public ResponseEntity<?> find(@AuthenticationPrincipal PreviewPrincipal principal,@PathVariable UUID sessionId) {
        return response(200,facade.findConversation(principal.actor(),sessionId));
    }
    @PostMapping(value="/{sessionId}/messages",consumes="application/json") public ResponseEntity<?> message(
            @AuthenticationPrincipal PreviewPrincipal principal,@PathVariable UUID sessionId,@RequestBody Message request) {
        requireRevision(request.expectedRevision());
        if(request.message()==null||request.message().isBlank()||request.message().strip().length()>500)
            throw new IllegalArgumentException("消息须为1至500字");
        return response(200,facade.sendMessage(principal.actor(),sessionId,request.expectedRevision(),request.message()));
    }
    @PostMapping(value="/{sessionId}/cancel",consumes="application/json") public ResponseEntity<?> cancel(
            @AuthenticationPrincipal PreviewPrincipal principal,@PathVariable UUID sessionId,@RequestBody Cancel request) {
        requireRevision(request.expectedRevision());
        return response(200,facade.cancelConversation(principal.actor(),sessionId,request.expectedRevision()));
    }
    public record Message(String message,Long expectedRevision) { }
    public record Cancel(Long expectedRevision) { }
    private record Error(String code,String message) { }
    private static void requireRevision(Long value) { if(value==null||value<0) throw new IllegalArgumentException("revision须明确且非负"); }
    private static ResponseEntity<?> response(int status,Object body) {
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(body);
    }
    // 仅映射本Controller的有限异常，不改4A全局handler，不把任意IllegalStateException解释为未提交。
    @ExceptionHandler(ControlledConversationAccessStore.Failure.class) ResponseEntity<?> stateFailure(ControlledConversationAccessStore.Failure failure) {
        int status=switch(failure.code()) { case SESSION_NOT_FOUND -> 404; case SESSION_EXPIRED -> 410; default -> 409; };
        return response(status,new Error(failure.code().name(),"会话当前状态不允许本次操作"));
    }
    @ExceptionHandler(ConversationStoreFailure.class) ResponseEntity<?> storeFailure(ConversationStoreFailure failure,
            HttpServletRequest request,HttpServletResponse response) {
        if(failure.code()==ConversationStoreFailure.Code.AUTH_REVOKED) clearRevokedLogin(request,response);
        int status=switch(failure.code()) { case AUTH_REVOKED -> 401; case ROLE_DENIED -> 403; default -> 503; };
        String code=switch(failure.code()) {
            case AUTH_REVOKED,ROLE_DENIED -> failure.code().name();
            case STORE_UNAVAILABLE -> "SESSION_STORE_UNAVAILABLE";
            case CREATION_UNCONFIRMED -> "SESSION_CREATION_UNCONFIRMED";
            case WRITE_UNCONFIRMED -> "SESSION_WRITE_UNCONFIRMED";
        };
        return response(status,new Error(code,"会话操作未获确认"));
    }
    @ExceptionHandler(PreviewIdentityRepository.Failure.class) ResponseEntity<?> identityFailure(PreviewIdentityRepository.Failure failure,
            HttpServletRequest request,HttpServletResponse response) {
        if(failure.code()==PreviewIdentityRepository.Code.AUTH_REVOKED) clearRevokedLogin(request,response);
        int status=switch(failure.code()) { case AUTH_REVOKED -> 401; case ROLE_DENIED -> 403; default -> 503; };
        return response(status,new Error(failure.code().name(),"身份许可当前不可用"));
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception ignored) { return response(400,new Error("INVALID_REQUEST","请求字段不符合会话约定")); }
    private static void clearRevokedLogin(HttpServletRequest request,HttpServletResponse response) {
        // 4A过滤链已核验入口，但SQL/作答期间仍可能撤销许可；本次401也要立即失效当前登录。
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var session=request.getSession(false); if(session!=null) session.invalidate();
        response.addHeader("Set-Cookie",org.springframework.http.ResponseCookie.from("ZHKU_CONTROLLED_SESSION","")
                .path("/api/zhku/controlled-preview/v1").httpOnly(true).sameSite("Strict").secure(false).maxAge(0).build().toString());
    }
}

