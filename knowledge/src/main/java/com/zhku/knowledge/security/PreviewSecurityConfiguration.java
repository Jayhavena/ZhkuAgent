package com.zhku.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.zhku.knowledge.controlled.ControlledRunRequestLimitFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Profile("zhku-controlled-preview")
public class PreviewSecurityConfiguration {
    static final String PREFIX = "/zhku/controlled-preview/v1";
    static final String COOKIE_PATH = "/api" + PREFIX;

    @Bean AuthenticationProvider previewAuthenticationProvider(PreviewIdentityRepository repository, Clock previewIdentityClock) {
        return new Provider(repository, previewIdentityClock);
    }

    @Bean SecurityFilterChain previewSecurityFilterChain(HttpSecurity http, AuthenticationProvider previewAuthenticationProvider,
            PreviewIdentityRepository repository, ObjectMapper mapper, Environment environment) throws Exception {
        boolean conversations="true".equals(environment.getProperty("zhku.controlled-preview.conversations.enabled","false"));
        return buildChain(http,previewAuthenticationProvider,repository,mapper,PREFIX,"ZHKU_CONTROLLED_SESSION",conversations,false);
    }

    /** 只由独立受控配置调用；入口参数来自服务端常量，不能由请求覆盖cookie或路由。 */
    static SecurityFilterChain buildChain(HttpSecurity http,AuthenticationProvider provider,
            PreviewIdentityRepository repository,ObjectMapper mapper,String prefix,String cookieName,
            boolean conversations,boolean ruleCheck) throws Exception {
        String cookiePath="/api"+prefix;
        // 显式无parent，避免同一Provider在本地和父Manager各执行一次、提前耗尽登录预算。
        http.authenticationManager(new ProviderManager(List.of(provider)))
                .httpBasic(basic -> basic.disable()).requestCache(cache -> cache.disable())
                .formLogin(login -> login.loginProcessingUrl(prefix + "/auth/login")
                        .successHandler((request, response, authentication) -> {
                            var actor = ((PreviewPrincipal)authentication.getPrincipal()).actor();
                            if(ruleCheck){var session=request.getSession(false);if(session!=null)synchronized(session){session.removeAttribute("ZHKU_RULE_AGENT_OWNER");}}
                            json(mapper, response, 200, new LoginReply(actor.userId(), actor.role(), actor.authRevision()));
                        }).failureHandler((request, response, exception) -> {
                            String code = "LOGIN_RATE_LIMIT".equals(exception.getMessage()) ? "LOGIN_RATE_LIMIT"
                                    : "IDENTITY_UNAVAILABLE".equals(exception.getMessage()) ? "IDENTITY_UNAVAILABLE"
                                    : "AUTHENTICATION_FAILED";
                            json(mapper, response, code.equals("LOGIN_RATE_LIMIT") ? 429
                                    : code.equals("IDENTITY_UNAVAILABLE") ? 503 : 401, new ErrorReply(code, "登录未获确认"));
                        }))
                .logout(logout -> logout.logoutRequestMatcher(request -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    return "POST".equals(request.getMethod()) && (prefix + "/auth/logout").equals(path)
                            && authentication != null && authentication.isAuthenticated()
                            && authentication.getPrincipal() instanceof PreviewPrincipal;
                }).addLogoutHandler((request, response, authentication) -> clearCookie(response,cookieName,cookiePath))
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> json(mapper, response, 401,
                                new ErrorReply("AUTHENTICATION_REQUIRED", "需要有效登录")))
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication=SecurityContextHolder.getContext().getAuthentication();
                            String path=request.getRequestURI().substring(request.getContextPath().length());
                            boolean conversationRoleDenied=conversations && path.startsWith(prefix+"/conversations")
                                    && authentication!=null && authentication.getPrincipal() instanceof PreviewPrincipal principal
                                    && principal.actor().role()!=TrustedActor.Role.USER;
                            json(mapper,response,403,new ErrorReply(conversationRoleDenied?"ROLE_DENIED":"ACCESS_DENIED","请求未获许可"));
                        }))
                .authorizeHttpRequests(access -> {
                    access
                        .requestMatchers(HttpMethod.GET, prefix + "/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, prefix + "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, prefix + "/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, prefix + "/auth/logout").authenticated();
                    if (ruleCheck) {
                        // v2只开放查证和异步任务合同，身份不能继承v1历史、实验或管理权限。
                        access.requestMatchers(HttpMethod.POST,prefix+"/rule-check").hasRole("USER")
                            .requestMatchers(HttpMethod.GET,prefix+"/rule-check/originals/*").hasRole("USER")
                            .requestMatchers(HttpMethod.POST,prefix+"/tasks",prefix+"/tasks/*/messages",prefix+"/tasks/*/cancel").hasRole("USER")
                            .requestMatchers(HttpMethod.GET,prefix+"/tasks/capabilities",prefix+"/tasks/*",prefix+"/tasks/requests/*/*",prefix+"/tasks/*/originals/*").hasRole("USER");
                    } else {
                        access.requestMatchers(HttpMethod.POST, prefix + "/runs", prefix + "/runs/*/cancel").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, prefix + "/runs/*", prefix + "/history", prefix + "/history/*",
                                prefix + "/submissions/*").hasRole("USER");
                    // 仅明确JSON四路径；默认关闭时不放行列表、SSE或匿名旧入口。
                    if(conversations) {
                        access.requestMatchers(HttpMethod.POST,prefix+"/conversations",prefix+"/conversations/*/messages",
                                prefix+"/conversations/*/cancel").hasRole("USER");
                        access.requestMatchers(HttpMethod.GET,prefix+"/conversations/*").hasRole("USER");
                    }
                    }
                    access.anyRequest().denyAll();
                });
        // 不安全请求先检查CSRF，再查当前身份；数据库故障不能覆盖本该先发生的403。
        http.addFilterAfter(new CurrentIdentityFilter(repository, mapper,cookieName,cookiePath,ruleCheck,prefix), CsrfFilter.class);
        // 直接创建而非Servlet Bean；先身份/CSRF/授权，再读取和限制业务body。
        if (ruleCheck) {
            http.addFilterBefore(new com.zhku.knowledge.rulecheck.web.RuleCheckMediaTypeFilter(),CsrfFilter.class);
            http.addFilterAfter(new ControlledRunRequestLimitFilter(prefix+"/rule-check",32768),AuthorizationFilter.class);
            http.addFilterAfter(new com.zhku.knowledge.ruleagent.web.RuleAgentRequestLimitFilter(),AuthorizationFilter.class);
        } else http.addFilterAfter(new ControlledRunRequestLimitFilter(), AuthorizationFilter.class);
        return http.build();
    }

    static final class Provider implements AuthenticationProvider {
        private final PreviewIdentityRepository repository;
        private final Clock clock;
        private Instant window;
        private int attempts;
        Provider(PreviewIdentityRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
        private synchronized boolean admit() {
            Instant now = clock.instant();
            if (window == null || !now.isBefore(window.plusSeconds(60))) { window = now; attempts = 0; }
            if (attempts == 20) return false;
            attempts++;
            return true;
        }
        @Override public Authentication authenticate(Authentication request) {
            try {
                if (!admit()) throw new InternalAuthenticationServiceException("LOGIN_RATE_LIMIT");
                if (!(request.getPrincipal() instanceof String login) || !(request.getCredentials() instanceof String password))
                    throw new BadCredentialsException("AUTHENTICATION_FAILED");
                PreviewPrincipal principal;
                try {
                    principal = repository.authenticate(login, password.toCharArray())
                            .orElseThrow(() -> new BadCredentialsException("AUTHENTICATION_FAILED"));
                    repository.requireCurrent(principal.actor());
                } catch (PreviewIdentityRepository.Failure failure) {
                    if (failure.code() == PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE)
                        throw new InternalAuthenticationServiceException("IDENTITY_UNAVAILABLE");
                    throw new BadCredentialsException("AUTHENTICATION_FAILED");
                }
                // 只有一个明确角色；维护者不继承USER，也不获取校园治理权限。
                return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.actor().role().name())));
            } finally {
                if (request instanceof CredentialsContainer credentials) credentials.eraseCredentials();
            }
        }
        @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
    }

    private static final class CurrentIdentityFilter extends OncePerRequestFilter {
        private final PreviewIdentityRepository repository;
        private final ObjectMapper mapper;
        private final String cookieName,cookiePath,prefix;
        private final boolean ruleCheck;
        CurrentIdentityFilter(PreviewIdentityRepository repository,ObjectMapper mapper,String cookieName,String cookiePath,boolean ruleCheck,String prefix) {
            this.repository=repository;this.mapper=mapper;this.cookieName=cookieName;this.cookiePath=cookiePath;this.ruleCheck=ruleCheck;this.prefix=prefix;
        }
        @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
        @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path=request.getRequestURI().substring(request.getContextPath().length());
            // 旧许可失效仍须允许CSRF已核对后的重新认证/退出；新登录由Provider核验新主体。
            boolean identityAction="POST".equals(request.getMethod()) && (path.equals(prefix+"/auth/login") || path.equals(prefix+"/auth/logout"));
            boolean csrfRead="GET".equals(request.getMethod()) && path.equals(prefix+"/auth/csrf");
            if (ruleCheck && (identityAction || csrfRead)) {
                chain.doFilter(request,response);return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && !(authentication.getPrincipal() instanceof PreviewPrincipal)) {
                // 其他认证源的同名USER权限不是本入口的可信主体。
                rejectIdentity(request,response);
                json(mapper, response, 401, new ErrorReply("AUTH_REVOKED", "登录主体不受支持"));
                return;
            }
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof PreviewPrincipal principal) {
                try { repository.requireCurrent(principal.actor()); }
                catch (PreviewIdentityRepository.Failure failure) {
                    if (failure.code() == PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE) {
                        json(mapper, response, 503, new ErrorReply("IDENTITY_UNAVAILABLE", "身份核验暂不可用"));
                    } else {
                        rejectIdentity(request,response);
                        json(mapper, response, 401, new ErrorReply("AUTH_REVOKED", "登录许可已失效"));
                    }
                    return;
                }
            }
            chain.doFilter(request, response);
        }
        private void rejectIdentity(HttpServletRequest request,HttpServletResponse response) {
            SecurityContextHolder.clearContext();
            // v2旧请求的异常可能晚于新登录；Session锁也不能控制Cookie的网络交付顺序。
            // 保留记录不保留许可：每次仍fresh核验并拒绝，显式退出或自然TTL负责结束会话。
            if (ruleCheck) return;
            var session=request.getSession(false);
            if (session!=null) session.invalidate();
            clearCookie(response,cookieName,cookiePath);
        }
    }

    static void clearCookie(HttpServletResponse response) {
        clearCookie(response,"ZHKU_CONTROLLED_SESSION",COOKIE_PATH);
    }
    private static void clearCookie(HttpServletResponse response,String cookieName,String cookiePath) {
        response.addHeader("Set-Cookie", ResponseCookie.from(cookieName, "").path(cookiePath)
                .httpOnly(true).sameSite("Strict").secure(false).maxAge(0).build().toString());
    }
    private static void json(ObjectMapper mapper, HttpServletResponse response, int status, Object payload) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store"); mapper.writeValue(response.getOutputStream(), payload);
    }
    private record ErrorReply(String code, String message) { }
    private record LoginReply(java.util.UUID userId, TrustedActor.Role role, long authRevision) { }
}
