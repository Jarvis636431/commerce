package com.jarvis.commerce.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        long startedAt = System.nanoTime();
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().equals("/actuator/prometheus")) {
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                log.info("HTTP request completed method={} uri={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            }
            MDC.remove("traceId");
        }
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) return candidate;
        return UUID.randomUUID().toString().replace("-", "");
    }
}
