package com.publicfund.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PublicAuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long starts, int count) {}
    private final Map<String, Window> windows=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path=request.getRequestURI();
        if (!"POST".equals(request.getMethod()) || !Set.of("/api/auth/register","/api/auth/resend-verification","/api/auth/verify-email",
                "/api/auth/google/challenge","/api/auth/google/login","/api/auth/forgot-password","/api/auth/reset-password").contains(path)) {
            chain.doFilter(request,response); return;
        }
        boolean shortWindow=path.endsWith("verify-email") || path.contains("/google/") || path.endsWith("/reset-password");
        long now=System.currentTimeMillis(), duration=shortWindow ? 60_000L : 3_600_000L;
        int limit=shortWindow ? 30 : 15;
        String key=request.getRemoteAddr()+":"+path;
        synchronized (windows) {
            windows.entrySet().removeIf(e -> now-e.getValue().starts()>3_600_000L);
            Window old=windows.get(key);
            if (old==null || now-old.starts()>=duration) old=new Window(now,0);
            if (old.count()>=limit || (!windows.containsKey(key) && windows.size()>=10_000)) {
                response.setStatus(429); response.setContentType("application/json");
                response.setHeader("Retry-After",Long.toString(duration/1000));
                response.getWriter().write("{\"message\":\"Too many requests. Please wait before trying again.\"}");
                return;
            }
            windows.put(key,new Window(old.starts(),old.count()+1));
        }
        chain.doFilter(request,response);
    }
}
