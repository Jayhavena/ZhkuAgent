package com.zhku.knowledge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在CSRF解析参数前拒绝表单并限制正文，避免无长度或分块请求绕过上限。 */
final class PasswordRequestFilter extends OncePerRequestFilter {
    private final String path;
    PasswordRequestFilter(String path) { this.path=path; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"PUT".equals(request.getMethod()) || !path.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean json=false;
        try {
            var type=MediaType.parseMediaType(request.getContentType());
            json="application".equalsIgnoreCase(type.getType()) && "json".equalsIgnoreCase(type.getSubtype())
                    && (type.getCharset()==null || StandardCharsets.UTF_8.equals(type.getCharset()));
        } catch (RuntimeException ignored) { }
        if (!json) { response.setStatus(415); response.setHeader("Cache-Control","no-store"); return; }
        byte[] bytes=request.getInputStream().readNBytes(2049);
        if (bytes.length>2048) { response.setStatus(413); response.setHeader("Cache-Control","no-store"); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return bytes.length; }
            @Override public long getContentLengthLong() { return bytes.length; }
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new IllegalStateException("仅支持同步读取"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
        },response);
    }
}
