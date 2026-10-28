package com.surf.tracker.userauth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Bounded per-instance edge limit. Deployments must also rate-limit at their shared ingress. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new HashMap<>();
    private record Window(long expiresAt, int count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Never trust client-supplied X-Forwarded-For. Configure a trusted proxy at the container/ingress.
        if (!allow(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String address, long now) {
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        Window window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10000) return false;
            window = new Window(now + 60000, 0);
        }
        if (window.count() >= 30) return false;
        windows.put(address, new Window(window.expiresAt(), window.count() + 1));
        return true;
    }
}
