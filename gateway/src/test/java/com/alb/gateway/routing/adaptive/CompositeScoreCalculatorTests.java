package com.alb.gateway.routing.adaptive;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeScoreCalculatorTests {

    @Test
    void calculatesWeightedCompositeScoreAndHealthTier() {
        CompositeScoreCalculator calculator = new CompositeScoreCalculator(new AdaptiveRoutingConfig());
        InstanceMetricsSnapshot snapshot = InstanceMetricsSnapshot.builder()
                .cpuUsage(0.0)
                .memoryUsageRatio(0.0)
                .latencyEmaMs(20.0)
                .activeConnections(0)
                .errorRate(0.0)
                .build();

        snapshot.setCompositeScore(calculator.calculateScore(snapshot));

        assertEquals(1.0, snapshot.getCompositeScore());
        assertEquals(InstanceMetricsSnapshot.InstanceStatus.HEALTHY, calculator.classifyHealthTier(snapshot));

        snapshot.setErrorRate(0.03);
        assertEquals(InstanceMetricsSnapshot.InstanceStatus.DEGRADED, calculator.classifyHealthTier(snapshot));
        snapshot.setErrorRate(0.05);
        assertEquals(InstanceMetricsSnapshot.InstanceStatus.UNHEALTHY, calculator.classifyHealthTier(snapshot));
    }
}
