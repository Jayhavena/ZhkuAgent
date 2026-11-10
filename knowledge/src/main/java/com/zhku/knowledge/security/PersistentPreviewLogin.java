package com.zhku.knowledge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/** 长期凭据独立于内存HTTP会话，数据库只接收不可直接使用的摘要。 */
public final class PersistentPreviewLogin extends OncePerRequestFilter {
    public static final String NAME="ZHKU_RULE_CHECK_REMEMBER";
    static final String SESSION_DIGEST="ZHKU_RULE_CHECK_PERSISTENT_DIGEST";
    private static final String PATH=RuleCheckPreviewSecurityConfiguration.COOKIE_PATH;
    private static final SecureRandom RANDOM=new SecureRandom();
    private final PreviewIdentityRepository repository;
    private final boolean secure;
    public PersistentPreviewLogin(PreviewIdentityRepository repository, boolean secure) { this.repository=repository; this.secure=secure; }
    private static String token() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String cookie(HttpServletRequest request) {
        if (request.getCookies()!=null) for (var cookie:request.getCookies())
            if (NAME.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        return null;
    }
    private void set(HttpServletResponse response,String value,long age) {
        response.addHeader("Set-Cookie",ResponseCookie.from(NAME,value).path(PATH).httpOnly(true).secure(secure).sameSite("Strict").maxAge(age).build().toString());
    }
    public static void clear(HttpServletRequest request,HttpServletResponse response) {
        response.addHeader("Set-Cookie",ResponseCookie.from(NAME,"").path(PATH).httpOnly(true).secure(request.isSecure()).sameSite("Strict").maxAge(0).build().toString());
    }
    public void login(HttpServletRequest request,HttpServletResponse response,Authentication authentication) {
        String old=cookie(request);
        if(old!=null) repository.revokePersistent(digest(old));
        if ("true".equals(request.getParameter("remember-me")) && authentication.getPrincipal() instanceof PreviewPrincipal principal
                && principal.actor().role()==TrustedActor.Role.USER) {
            String value=token(); String hash=digest(value); repository.createPersistent(principal.actor(),hash);
            request.getSession(true).setAttribute(SESSION_DIGEST,hash); set(response,value,30L*24*3600);
        } else {
            var session=request.getSession(false); if (session!=null) session.removeAttribute(SESSION_DIGEST);
            set(response,"",0);
        }
    }
    void slide(HttpServletRequest request,HttpServletResponse response,String sessionDigest,TrustedActor actor) {
        String value=cookie(request);
        // 会话绑定、Cookie与数据库主体缺一不可；非记住登录或混入其他账号Cookie都不能续期。
        if (value!=null && MessageDigest.isEqual(digest(value).getBytes(StandardCharsets.US_ASCII),sessionDigest.getBytes(StandardCharsets.US_ASCII))
                && repository.slidePersistent(sessionDigest,actor)) set(response,value,30L*24*3600);
    }
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        // Cookie 可能在其他窗口被替换；仍须撤销当前会话实际绑定的长期凭据。
        var session=request.getSession(false);
        Object bound=session==null?null:session.getAttribute(SESSION_DIGEST);
        if(bound instanceof String hash) repository.revokePersistent(hash);
        String value=cookie(request);
        if(value!=null && !digest(value).equals(bound)) repository.revokePersistent(digest(value));
        set(response,"",0);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var existing=SecurityContextHolder.getContext().getAuthentication();
        String value=cookie(request);
        String path=request.getRequestURI().substring(request.getContextPath().length());
        // 只在安全GET恢复，避免隐式登录绕过CSRF；不对失败的并发旧Cookie发清除头。
        if (existing==null && value!=null && "GET".equals(request.getMethod()) && path.startsWith(RuleCheckPreviewSecurityConfiguration.PREFIX+"/")) {
            try {
                var principal=repository.renewPersistent(digest(value));
                if (principal.isPresent()) {
                    var session=request.getSession(false);
                    if (session!=null) { request.changeSessionId(); synchronized(session) { session.removeAttribute("ZHKU_RULE_AGENT_OWNER"); } }
                    request.getSession(true).setAttribute(SESSION_DIGEST,digest(value));
                    var auth=UsernamePasswordAuthenticationToken.authenticated(principal.get(),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context);
                    new HttpSessionSecurityContextRepository().saveContext(context,request,response);
                    set(response,value,30L*24*3600);
                }
            } catch (PreviewIdentityRepository.Failure failure) {
                response.setStatus(503); response.setHeader("Cache-Control","no-store"); return;
            }
        }
        chain.doFilter(request,response);
    }
}
