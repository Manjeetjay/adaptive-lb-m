package com.alb.gateway.routing.adaptive;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Computes the composite health fitness score for each service instance.
 *
 * Score(i) = w_lat * S_lat(i) + w_cpu * S_cpu(i) + w_err * S_err(i)
 *          + w_conn * S_conn(i) + w_mem * S_mem(i)
 *
 * All weights are sourced from {@link AdaptiveRoutingConfig} and must sum to 1.0.
 * The resulting score ∈ [0.0, 1.0] determines both the health tier classification
 * and Softmax routing probability.
 */
@Slf4j
@Component
public class CompositeScoreCalculator {

    private final AdaptiveRoutingConfig config;

    public CompositeScoreCalculator(AdaptiveRoutingConfig config) {
        this.config = config;
    }

    /**
     * Calculates the composite health score for a given instance snapshot.
     *
     * @param snapshot Current telemetry snapshot of the instance
     * @return Composite score in [0.0, 1.0]
     */
    public double calculateScore(InstanceMetricsSnapshot snapshot) {
        double sCpu = MetricNormalizer.normalizeCpu(snapshot.getCpuUsage());
        double sMem = MetricNormalizer.normalizeMemory(snapshot.getMemoryUsageRatio());
        double sLat = MetricNormalizer.normalizeLatency(
                snapshot.getLatencyEmaMs(),
                config.getLatencyBaseMs(),
                config.getLatencyMaxMs());
        double sConn = MetricNormalizer.normalizeConnections(
                snapshot.getActiveConnections(),
                config.getConnectionLimit());
        double sErr = MetricNormalizer.normalizeErrorRate(snapshot.getErrorRate());

        double score = config.getWeightLatency() * sLat
                + config.getWeightCpu() * sCpu
                + config.getWeightError() * sErr
                + config.getWeightConnections() * sConn
                + config.getWeightMemory() * sMem;

        // Clamp to [0.0, 1.0] for safety
        score = Math.max(0.0, Math.min(1.0, score));

        log.trace("Instance [{}] scores — cpu:{} mem:{} lat:{} conn:{} err:{} → composite:{}",
                snapshot.getInstanceId(),
                String.format("%.3f", sCpu),
                String.format("%.3f", sMem),
                String.format("%.3f", sLat),
                String.format("%.3f", sConn),
                String.format("%.3f", sErr),
                String.format("%.4f", score));

        return score;
    }

    /**
     * Classifies an instance into a health tier based on composite score.
     *
     * @param compositeScore The composite fitness score in [0.0, 1.0]
     * @return Health tier classification
     */
    public InstanceMetricsSnapshot.InstanceStatus classifyHealthTier(InstanceMetricsSnapshot snapshot) {
        double compositeScore = snapshot.getCompositeScore();
        if (compositeScore < config.getUnhealthyThreshold() || snapshot.getErrorRate() >= 0.05) {
            return InstanceMetricsSnapshot.InstanceStatus.UNHEALTHY;
        }
        if (compositeScore >= config.getHealthyThreshold() && snapshot.getErrorRate() < 0.02) {
            return InstanceMetricsSnapshot.InstanceStatus.HEALTHY;
        }
        return InstanceMetricsSnapshot.InstanceStatus.DEGRADED;
    }
}
