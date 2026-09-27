package com.alb.gateway.routing.adaptive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration for the Multi-Metric Adaptive Routing (MM-AR) engine.
 * All tuning parameters are hot-reloadable via Spring Boot ConfigurationProperties.
 *
 * Configuration prefix: alb.adaptive
 */
@Data
@Component
@ConfigurationProperties(prefix = "alb.adaptive")
public class AdaptiveRoutingConfig {

    // ── Composite Score Weights (must sum to 1.0) ──
    /** Weight for latency score in composite calculation. Direct proxy for SLA violation. */
    private double weightLatency = 0.35;

    /** Weight for CPU usage score. Leading indicator of queuing delay. */
    private double weightCpu = 0.25;

    /** Weight for error rate score. Immediate safety breaker. */
    private double weightError = 0.20;

    /** Weight for active connections score. Damps queuing disparities. */
    private double weightConnections = 0.10;

    /** Weight for memory usage score. Protection against GC pause saturation. */
    private double weightMemory = 0.10;

    // ── Softmax Temperature ──
    /** Boltzmann temperature τ. Lower = more deterministic; Higher = more uniform. Default: 0.25 */
    private double softmaxTemperature = 0.25;

    // ── Hysteresis ──
    /** Minimum score superiority (%) required to switch preferred instance. Default: 10% */
    private double hysteresisThreshold = 0.10;

    // ── Latency Normalizer Bounds ──
    /** Baseline latency (ms) below which score is 1.0 */
    private double latencyBaseMs = 20.0;

    /** Maximum tolerated latency (ms) at which score is 0.0 */
    private double latencyMaxMs = 500.0;

    // ── Connection Normalizer ──
    /** Maximum connection count at which connection score becomes 0.0 */
    private int connectionLimit = 200;

    // ── EMA Smoothing ──
    /** Exponential moving average smoothing factor α. Higher = more reactive. */
    private double emaSmoothingFactor = 0.3;

    // ── Health Tier Thresholds ──
    /** Score threshold above which an instance is classified as HEALTHY */
    private double healthyThreshold = 0.50;

    /** Score threshold below which an instance is classified as UNHEALTHY (quarantined) */
    private double unhealthyThreshold = 0.20;

    /** Quarantine duration in seconds for UNHEALTHY instances */
    private long quarantineDurationSeconds = 15;

    // ── Metric Poller ──
    /** Interval (ms) between metric polling cycles from worker actuator endpoints */
    private long pollerIntervalMs = 500;

    /** HTTP timeout (ms) for individual worker actuator scrape requests */
    private long pollerTimeoutMs = 300;
}
