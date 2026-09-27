package com.alb.gateway.routing.adaptive;

/**
 * Stateless metric normalization functions for the Multi-Metric Adaptive Routing engine.
 *
 * Each function maps a raw metric value into a dimensionless fitness score in [0.0, 1.0],
 * where 1.0 represents optimal health and 0.0 represents total saturation or failure.
 *
 * Mathematical specifications from: docs/02-routing-algorithms-spec.md §5.2
 */
public final class MetricNormalizer {

    private MetricNormalizer() {
        // Utility class — prevent instantiation
    }

    /**
     * CPU Saturation Score: S_cpu(i) = 1.0 - min(1.0, U_cpu(i))
     *
     * @param cpuUsage CPU utilization ratio in [0.0, 1.0]
     * @return Fitness score where 1.0 = idle CPU, 0.0 = fully saturated
     */
    public static double normalizeCpu(double cpuUsage) {
        return 1.0 - Math.min(1.0, Math.max(0.0, cpuUsage));
    }

    /**
     * Memory Utilization Score: S_mem(i) = 1.0 - min(1.0, M_used / M_max)
     *
     * @param memoryUsageRatio Heap usage ratio in [0.0, 1.0]
     * @return Fitness score where 1.0 = empty heap, 0.0 = heap exhausted
     */
    public static double normalizeMemory(double memoryUsageRatio) {
        return 1.0 - Math.min(1.0, Math.max(0.0, memoryUsageRatio));
    }

    /**
     * Latency Score with linear penalty:
     * S_lat(i) = max(0.0, 1.0 - (L - L_base) / (L_max - L_base))
     *
     * Latencies at or below L_base score 1.0. Latencies at or above L_max score 0.0.
     *
     * @param latencyMs   Observed latency EMA in milliseconds
     * @param latencyBase Baseline latency threshold (ms) — below this, score = 1.0
     * @param latencyMax  Maximum tolerated latency (ms) — above this, score = 0.0
     * @return Fitness score in [0.0, 1.0]
     */
    public static double normalizeLatency(double latencyMs, double latencyBase, double latencyMax) {
        if (latencyMs <= latencyBase) {
            return 1.0;
        }
        if (latencyMs >= latencyMax) {
            return 0.0;
        }
        double range = latencyMax - latencyBase;
        if (range <= 0) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (latencyMs - latencyBase) / range);
    }

    /**
     * Active Connections Score: S_conn(i) = 1.0 - min(1.0, C(i) / C_limit)
     *
     * @param activeConnections Current in-flight request count
     * @param connectionLimit   Maximum capacity threshold
     * @return Fitness score where 1.0 = no connections, 0.0 = at capacity
     */
    public static double normalizeConnections(int activeConnections, int connectionLimit) {
        if (connectionLimit <= 0) {
            return 0.0;
        }
        return 1.0 - Math.min(1.0, Math.max(0.0, (double) activeConnections / connectionLimit));
    }

    /**
     * Error Rate Score: S_err(i) = 1.0 - min(1.0, E(i))
     *
     * @param errorRate Ratio of 5xx responses to total requests in [0.0, 1.0]
     * @return Fitness score where 1.0 = zero errors, 0.0 = 100% error rate
     */
    public static double normalizeErrorRate(double errorRate) {
        return 1.0 - Math.min(1.0, Math.max(0.0, errorRate));
    }

    /**
     * Applies Exponential Moving Average smoothing to a raw metric value.
     * M_t = α * M_raw + (1 - α) * M_prev
     *
     * @param rawValue     Current raw metric observation
     * @param previousEma  Previous smoothed value
     * @param alpha        Smoothing factor α ∈ (0, 1]
     * @return Smoothed EMA value
     */
    public static double applyEma(double rawValue, double previousEma, double alpha) {
        if (Double.isNaN(previousEma) || previousEma < 0) {
            return rawValue;
        }
        return alpha * rawValue + (1.0 - alpha) * previousEma;
    }
}
