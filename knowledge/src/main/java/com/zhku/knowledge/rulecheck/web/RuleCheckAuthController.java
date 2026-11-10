package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.controlled.ControlledAuthController;
import com.zhku.knowledge.security.PreviewPrincipal;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.zhku.knowledge.security.RuleCheckPreviewSecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** 只读会话信息；认证/登出交框架，不接受客户端自报身份。 */
@RestController
@Profile("zhku-rule-check-preview")
@RequestMapping(RuleCheckPreviewSecurityConfiguration.PREFIX+"/auth")
public final class RuleCheckAuthController {
    private final PreviewIdentityRepository repository;
    private long windowStart;
    private int attempts;
    public RuleCheckAuthController(PreviewIdentityRepository repository) { this.repository=repository; }
    // 有界全局预算避免匿名注册把BCrypt线程耗尽；多实例生产部署还需共享入口限流。
    private synchronized boolean admit() {
        long now=System.nanoTime();
        if (windowStart==0 || now-windowStart>=java.util.concurrent.TimeUnit.MINUTES.toNanos(1)) { windowStart=now; attempts=0; }
        if (attempts>=20) return false;
        attempts++; return true;
    }
    @PostMapping("/register") public ResponseEntity<?> register(@RequestBody Credentials input) {
        if (!admit()) return error(429,"RATE_LIMIT");
        if (input.password()==null || !input.password().equals(input.confirmPassword())) return error(400,"INVALID_INPUT");
        try {
            var account=repository.register(input.username(), input.password().toCharArray());
            return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control","no-store")
                    .body(java.util.Map.of("userId",account.userId(),"username",account.username(),"loginName",account.loginName(),"message","注册成功，请登录"));
        } catch (PreviewIdentityRepository.Failure failure) { return failure(failure); }
    }
    @GetMapping("/account") public ResponseEntity<?> account(@AuthenticationPrincipal PreviewPrincipal principal) {
        try { return ResponseEntity.ok(repository.account(principal.actor())); }
        catch (PreviewIdentityRepository.Failure failure) { return failure(failure); }
    }
    @PutMapping("/password") public ResponseEntity<?> password(@AuthenticationPrincipal PreviewPrincipal principal,
            @RequestBody PasswordChange input, HttpServletRequest request, HttpServletResponse response) {
        if (!admit()) return error(429,"RATE_LIMIT");
        if (input.newPassword()==null || !input.newPassword().equals(input.confirmPassword())) return error(400,"INVALID_INPUT");
        try {
            repository.changePassword(principal.actor(), input.currentPassword()==null?null:input.currentPassword().toCharArray(),
                    input.newPassword()==null?null:input.newPassword().toCharArray());
            var session=request.getSession(false); if (session!=null) session.invalidate();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            com.zhku.knowledge.security.PersistentPreviewLogin.clear(request,response);
            return ResponseEntity.noContent().build();
        } catch (PreviewIdentityRepository.Failure failure) { return failure(failure); }
    }
    private ResponseEntity<?> failure(PreviewIdentityRepository.Failure failure) {
        return switch(failure.code()) {
            case INVALID_INPUT -> error(400,"INVALID_INPUT");
            case LOGIN_EXISTS -> error(409,"LOGIN_EXISTS");
            case BAD_PASSWORD -> error(401,"AUTHENTICATION_FAILED");
            case AUTH_REVOKED, ROLE_DENIED -> error(401,"AUTH_REVOKED");
            default -> error(503,"IDENTITY_UNAVAILABLE");
        };
    }
    private static ResponseEntity<?> error(int status,String code) { return ResponseEntity.status(status).body(java.util.Map.of("code",code)); }
    public record Credentials(String username,String password,String confirmPassword) { }
    public record PasswordChange(String currentPassword,String newPassword,String confirmPassword) { }
    @GetMapping("/csrf") public ControlledAuthController.CsrfReply csrf(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return new ControlledAuthController.CsrfReply(token.getHeaderName(),token.getParameterName(),token.getToken());
    }
    public record MeReply(java.util.UUID userId, String username, String accountStatus, java.time.Instant createdAt,
            com.zhku.knowledge.security.TrustedActor.Role role, long authRevision) { }
    @GetMapping("/me") public ResponseEntity<?> me(@AuthenticationPrincipal PreviewPrincipal principal) {
        var actor=principal.actor();
        try {
            var account=repository.account(actor);
            return ResponseEntity.ok().header("Cache-Control","no-store").body(new MeReply(actor.userId(),account.username(),account.accountStatus(),account.createdAt(),actor.role(),actor.authRevision()));
        } catch (PreviewIdentityRepository.Failure failure) { return failure(failure); }
    }
}
