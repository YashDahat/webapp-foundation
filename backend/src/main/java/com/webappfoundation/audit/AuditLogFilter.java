package com.webappfoundation.audit;

import com.webappfoundation.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Stamps every request with a correlation id + client IP in the MDC (so {@link com.webappfoundation.service.AuditService}
 * events and all logs on the request thread carry them) and emits one structured access-log line per
 * API request (method, path, status, duration, actor). Runs after the security chain so the authenticated
 * user is resolvable. This is the baseline "log all activity" layer; domain audit events add the "who did
 * what" detail.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        MDC.put("ip", clientIp(request));
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/")) {
                long ms = System.currentTimeMillis() - start;
                accessLog.info("{} {} -> {} ({}ms)", request.getMethod(), path, response.getStatus(), ms,
                        kv("method", request.getMethod()), kv("path", path),
                        kv("status", response.getStatus()), kv("durationMs", ms),
                        kv("actorEmail", currentEmail()), kv("ip", MDC.get("ip")),
                        kv("requestId", requestId));
            }
            MDC.remove("requestId");
            MDC.remove("ip");
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getPrincipal() instanceof User user ? user.getEmail() : null;
    }
}
