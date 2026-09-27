package com.alb.worker.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that intercepts worker requests to inject controlled latency
 * and/or HTTP 500 error bursts based on the active chaos configuration.
 *
 * Automatically bypasses administrative endpoints (/chaos/**), metrics and health probes (/actuator/**),
 * ensuring control plane and scraping integrity during active fault injection.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ChaosFilter extends OncePerRequestFilter {

    private final ChaosService chaosService;

    public ChaosFilter(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Control and observation paths must never be impeded by chaos
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Error burst injection (T5.3)
        if (chaosService.shouldInjectError()) {
            log.debug("ChaosFilter injecting HTTP 500 error on [{}]", path);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Chaos: Simulated 500 Error\"}");
            return;
        }

        // 2. Latency injection with jitter (T5.2)
        if (chaosService.shouldInjectLatency()) {
            int delayMs = chaosService.calculateLatencyDelay();
            if (delayMs > 0) {
                log.trace("ChaosFilter delaying request [{}] by {}ms", path, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Chaos delay interrupted");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExemptPath(String path) {
        return path.startsWith("/chaos")
                || path.startsWith("/actuator")
                || path.startsWith("/error")
                || path.equals("/health")
                || path.equals("/api/v1/health")
                || path.equals("/api/health");
    }
}
