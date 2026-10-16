package com.zhku.knowledge.ruleagent.web;
import com.zhku.knowledge.controlled.ControlledRunRequestLimitFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
/** 身份/授权后有界读取；任务的所有POST保持同一JSON与字节约束。 */
public final class RuleAgentRequestLimitFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getRequestURI().substring(r.getContextPath().length());return !"POST".equals(r.getMethod())||!(path.equals("/zhku/controlled-preview/v2/tasks")||path.startsWith("/zhku/controlled-preview/v2/tasks/"));}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
        boolean json=false;try{var type=MediaType.parseMediaType(r.getContentType());json=type.isCompatibleWith(MediaType.APPLICATION_JSON)&&(type.getCharset()==null||java.nio.charset.StandardCharsets.UTF_8.equals(type.getCharset()));}catch(RuntimeException invalid){}
        if(!json){s.setStatus(415);s.setContentType("application/json");s.setHeader("Cache-Control","no-store");s.getWriter().write("{\"code\":\"JSON_REQUIRED\"}");return;}
        new ControlledRunRequestLimitFilter(r.getRequestURI().substring(r.getContextPath().length()),65536).doFilter(r,s,chain);
    }
}
