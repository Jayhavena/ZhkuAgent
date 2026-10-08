package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.controlled.ControlledAuthController;
import com.zhku.knowledge.security.PreviewPrincipal;
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
    @GetMapping("/csrf") public ControlledAuthController.CsrfReply csrf(HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return new ControlledAuthController.CsrfReply(token.getHeaderName(),token.getParameterName(),token.getToken());
    }
    @GetMapping("/me") public ControlledAuthController.MeReply me(@AuthenticationPrincipal PreviewPrincipal principal) {
        var actor=principal.actor();
        return new ControlledAuthController.MeReply(actor.userId(),actor.role(),actor.authRevision());
    }
}
