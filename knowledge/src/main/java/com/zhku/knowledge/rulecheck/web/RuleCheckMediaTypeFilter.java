package com.zhku.knowledge.rulecheck.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;

/** 只检查规范路径及媒体声明，避免CSRF先解析表单；不声称已验证实际正文UTF-8编码。 */
public final class RuleCheckMediaTypeFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // MVC会解码合法普通字符，而原URI仍带百分号；新应用只接受固定规范入口，先拒绝编码变体。
        // 对新应用所有路径检查，不能只匹配原始v2前缀，否则编码前缀也会绕过。
        String path=request.getRequestURI().substring(request.getContextPath().length());
        // task表单也必须在CSRF解析参数前拒绝，否则正文限长会被提前解析绕过。
        boolean jsonPost="POST".equals(request.getMethod())&&(path.equals("/zhku/controlled-preview/v2/rule-check")||path.equals("/zhku/controlled-preview/v2/tasks")||path.startsWith("/zhku/controlled-preview/v2/tasks/"));
        return !request.getRequestURI().contains("%")&&!jsonPost;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().contains("%")) {
            response.setStatus(400);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"code\":\"NON_CANONICAL_PATH\",\"message\":\"请使用规范接口路径\"}");return;
        }
        boolean json=false;
        try {
            var type=MediaType.parseMediaType(request.getContentType());
            json="application".equalsIgnoreCase(type.getType())&&"json".equalsIgnoreCase(type.getSubtype())
                &&(type.getCharset()==null||StandardCharsets.UTF_8.equals(type.getCharset()));
        } catch(RuntimeException invalid) { }
        if(!json) {
            response.setStatus(415);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"code\":\"JSON_REQUIRED\",\"message\":\"查证请求须声明UTF-8 JSON类型\"}");return;
        }
        chain.doFilter(request,response);
    }
}
