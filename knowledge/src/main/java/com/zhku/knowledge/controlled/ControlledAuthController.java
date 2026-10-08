package com.zhku.knowledge.controlled;

import com.zhku.knowledge.security.PreviewPrincipal;
import com.zhku.knowledge.security.TrustedActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** 登录/注销交框架；这里不手工保存SecurityContext或接受客户端身份。 */
@RestController
@RequestMapping("/zhku/controlled-preview/v1/auth")
public final class ControlledAuthController {
    @GetMapping("/csrf")
    public CsrfReply csrf(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfReply(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
    @GetMapping("/me")
    public MeReply me(@AuthenticationPrincipal PreviewPrincipal principal) {
        var actor = principal.actor();
        return new MeReply(actor.userId(), actor.role(), actor.authRevision());
    }
    public record CsrfReply(String headerName, String parameterName, String token) { }
    public record MeReply(UUID userId, TrustedActor.Role role, long authRevision) { }
}
