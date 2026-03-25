package com.manager.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs every inbound request and outbound response.
 * Populates MDC with requestId and userId so every log line in the chain
 * automatically includes them (see logback-spring.xml pattern).
 *
 * Sample output:
 *   → GET  /api/tasks  [uid=abc123]
 *   ← 200  GET  /api/tasks  45ms
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long start = System.currentTimeMillis();

        // Populate MDC – cleared in finally so it never leaks across threads
        MDC.put("requestId", requestId);

        // userId may be set later by FirebaseAuthFilter; we refresh it after the chain
        try {
            log.debug("→ {} {}", request.getMethod(), request.getRequestURI());

            chain.doFilter(request, response);

            // After FirebaseAuthFilter runs, uid is available as a request attribute
            Object uid = request.getAttribute("uid");
            if (uid != null) {
                MDC.put("userId", uid.toString());
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("← {} {} {} {}ms",
                    response.getStatus(),
                    request.getMethod(),
                    request.getRequestURI(),
                    elapsed);

        } finally {
            MDC.remove("requestId");
            MDC.remove("userId");
        }
    }
}
