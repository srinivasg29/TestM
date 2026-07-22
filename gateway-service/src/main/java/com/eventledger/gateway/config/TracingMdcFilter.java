package com.eventledger.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Micrometer's OTel bridge (unlike its Brave bridge) doesn't auto-populate SLF4J MDC with the
 * current trace/span IDs, so structured logs wouldn't otherwise carry them. This filter runs inside
 * Spring Boot's own tracing observation filter (default ordering), so the current span is already
 * active by the time it executes.
 */
@Component
public class TracingMdcFilter extends HttpFilter {

    private final Tracer tracer;

    public TracingMdcFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Span currentSpan = tracer.currentSpan();
        try {
            if (currentSpan != null) {
                MDC.put("traceId", currentSpan.context().traceId());
                MDC.put("spanId", currentSpan.context().spanId());
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}
