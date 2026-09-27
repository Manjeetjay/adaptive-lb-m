package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.adaptive.AdaptiveRoutingConfig;
import com.alb.gateway.routing.adaptive.CompositeScoreCalculator;
import com.alb.gateway.routing.adaptive.MetricCache;
import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-Metric Adaptive Routing Strategy (MM-AR).
 *
 * Implements a closed-loop feedback routing algorithm that:
 * 1. Reads pre-computed composite scores from the {@link MetricCache}
 * 2. Filters out quarantined (UNHEALTHY) instances
 * 3. Applies Boltzmann/Softmax probability distribution: P(i) = exp(Score(i)/τ) / Σ exp(Score(j)/τ)
 * 4. Selects an instance via weighted random sampling using cumulative probabilities
 * 5. Applies hysteresis dampening (δ) to prevent traffic flapping
 *
 * Mathematical specification: docs/02-routing-algorithms-spec.md §5
 */
@Slf4j
@Component
public class AdaptiveMultiMetricStrategy implements RoutingStrategy {

    private final MetricCache metricCache;
    private final CompositeScoreCalculator scoreCalculator;
    private final AdaptiveRoutingConfig config;

    /** Tracks the last preferred instance for hysteresis dampening */
    private volatile String lastPreferredInstanceId;

    public AdaptiveMultiMetricStrategy(
            MetricCache metricCache,
            CompositeScoreCalculator scoreCalculator,
            AdaptiveRoutingConfig config) {
        this.metricCache = metricCache;
        this.scoreCalculator = scoreCalculator;
        this.config = config;
    }

    @Override
    public ServiceInstance select(List<ServiceInstance> instances, Map<String, InstanceMetricsSnapshot> metricsMap) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.get(0);
        }

        // Step 1: Filter out quarantined instances
        List<ServiceInstance> eligible = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (ServiceInstance instance : instances) {
            String instanceId = resolveInstanceId(instance);

            if (metricCache.isQuarantined(instanceId)) {
                log.debug("Skipping quarantined instance [{}]", instanceId);
                continue;
            }

            // Read pre-computed score from cache, or compute on-the-fly from metricsMap
            double score = getCompositeScore(instanceId, metricsMap);
            eligible.add(instance);
            scores.add(score);
        }

        if (eligible.isEmpty()) {
            // All instances quarantined — fallback: allow all with minimum scores
            log.warn("All instances quarantined — falling back to full pool");
            for (ServiceInstance instance : instances) {
                eligible.add(instance);
                scores.add(0.1); // Minimal uniform score
            }
        }

        if (eligible.size() == 1) {
            return eligible.get(0);
        }

        // Step 2: Compute Softmax probabilities
        double[] probabilities = computeSoftmaxProbabilities(scores);

        // Step 3: Hysteresis stabilizes the preferred-node designation only. It must
        // not bypass Softmax dispatch, or every request would become sticky.
        updatePreferredInstance(eligible, scores);

        // Step 4: Weighted random selection using cumulative probabilities
        ServiceInstance selected = weightedRandomSelect(eligible, probabilities);

        return selected;
    }

    /**
     * Computes Boltzmann/Softmax probability distribution.
     * P(i) = exp(Score(i) / τ) / Σ exp(Score(j) / τ)
     *
     * Uses log-sum-exp trick for numerical stability.
     */
    private double[] computeSoftmaxProbabilities(List<Double> scores) {
        double temperature = config.getSoftmaxTemperature();
        int n = scores.size();
        double[] scaledScores = new double[n];

        // Find max for numerical stability (log-sum-exp trick)
        double maxScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            scaledScores[i] = scores.get(i) / temperature;
            maxScore = Math.max(maxScore, scaledScores[i]);
        }

        // Compute exp(score - maxScore) for numerical stability
        double sumExp = 0.0;
        double[] expValues = new double[n];
        for (int i = 0; i < n; i++) {
            expValues[i] = Math.exp(scaledScores[i] - maxScore);
            sumExp += expValues[i];
        }

        // Normalize to probabilities
        double[] probabilities = new double[n];
        for (int i = 0; i < n; i++) {
            probabilities[i] = (sumExp > 0) ? expValues[i] / sumExp : 1.0 / n;
        }

        if (log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("Softmax probabilities (τ=").append(temperature).append("): ");
            for (int i = 0; i < n; i++) {
                sb.append(String.format("[%.3f→%.2f%%] ", scores.get(i), probabilities[i] * 100));
            }
            log.trace(sb.toString());
        }

        return probabilities;
    }

    /**
     * Applies hysteresis dampening to prevent routing flapping.
     * The preferred instance is not changed unless a candidate exceeds its score by (1 + δ).
     *
     */
    private void updatePreferredInstance(List<ServiceInstance> eligible, List<Double> scores) {
        int bestIdx = 0;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > scores.get(bestIdx)) {
                bestIdx = i;
            }
        }

        if (lastPreferredInstanceId == null) {
            lastPreferredInstanceId = resolveInstanceId(eligible.get(bestIdx));
            return;
        }

        double delta = config.getHysteresisThreshold();
        int preferredIdx = -1;
        double preferredScore = 0;

        for (int i = 0; i < eligible.size(); i++) {
            if (resolveInstanceId(eligible.get(i)).equals(lastPreferredInstanceId)) {
                preferredIdx = i;
                preferredScore = scores.get(i);
                break;
            }
        }

        if (preferredIdx < 0) {
            lastPreferredInstanceId = resolveInstanceId(eligible.get(bestIdx));
            return;
        }

        double threshold = preferredScore * (1.0 + delta);
        if (scores.get(bestIdx) >= threshold) {
            lastPreferredInstanceId = resolveInstanceId(eligible.get(bestIdx));
        }
    }

    /**
     * Selects an instance via weighted random using cumulative probability distribution.
     */
    private ServiceInstance weightedRandomSelect(List<ServiceInstance> eligible, double[] probabilities) {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < eligible.size(); i++) {
            cumulative += probabilities[i];
            if (random <= cumulative) {
                return eligible.get(i);
            }
        }

        // Floating-point edge case — return last
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Gets the composite score from the MetricCache, or computes it on-the-fly from the metricsMap.
     */
    private double getCompositeScore(String instanceId, Map<String, InstanceMetricsSnapshot> metricsMap) {
        // Prefer cached score from MetricPollerService
        return metricCache.get(instanceId)
                .map(InstanceMetricsSnapshot::getCompositeScore)
                .orElseGet(() -> {
                    // Fallback: compute from metricsMap (e.g., if poller hasn't populated yet)
                    if (metricsMap != null && metricsMap.containsKey(instanceId)) {
                        return scoreCalculator.calculateScore(metricsMap.get(instanceId));
                    }
                    return 0.5; // Default neutral score
                });
    }

    private String resolveInstanceId(ServiceInstance instance) {
        return instance.getInstanceId() != null ? instance.getInstanceId() : instance.getUri().toString();
    }

    @Override
    public RoutingStrategyType getType() {
        return RoutingStrategyType.ADAPTIVE_MULTI_METRIC;
    }
}
