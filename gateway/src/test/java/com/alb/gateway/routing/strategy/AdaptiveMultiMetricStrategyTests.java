package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.adaptive.AdaptiveRoutingConfig;
import com.alb.gateway.routing.adaptive.CompositeScoreCalculator;
import com.alb.gateway.routing.adaptive.MetricCache;
import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveMultiMetricStrategyTests {

    @Test
    void softmaxDispatchRemainsProbabilisticWhenHysteresisIsEnabled() {
        MetricCache cache = new MetricCache();
        AdaptiveRoutingConfig config = new AdaptiveRoutingConfig();
        AdaptiveMultiMetricStrategy strategy = new AdaptiveMultiMetricStrategy(
                cache, new CompositeScoreCalculator(config), config);
        ServiceInstance high = new DefaultServiceInstance("high", "DEMO-SERVICE", "127.0.0.1", 8081, false);
        ServiceInstance low = new DefaultServiceInstance("low", "DEMO-SERVICE", "127.0.0.2", 8082, false);
        cache.put("high", InstanceMetricsSnapshot.builder().instanceId("high").compositeScore(0.9).build());
        cache.put("low", InstanceMetricsSnapshot.builder().instanceId("low").compositeScore(0.4).build());

        int highSelections = 0;
        int lowSelections = 0;
        for (int attempt = 0; attempt < 500; attempt++) {
            ServiceInstance selected = strategy.select(List.of(high, low), Map.of());
            if (selected.getInstanceId().equals("high")) {
                highSelections++;
            } else {
                lowSelections++;
            }
        }

        assertTrue(highSelections > lowSelections);
        assertTrue(lowSelections > 0, "Softmax must not be overridden by sticky hysteresis routing");
    }

    @Test
    void excludesQuarantinedInstances() {
        MetricCache cache = new MetricCache();
        AdaptiveRoutingConfig config = new AdaptiveRoutingConfig();
        AdaptiveMultiMetricStrategy strategy = new AdaptiveMultiMetricStrategy(
                cache, new CompositeScoreCalculator(config), config);
        ServiceInstance healthy = new DefaultServiceInstance("healthy", "DEMO-SERVICE", "127.0.0.1", 8081, false);
        ServiceInstance unhealthy = new DefaultServiceInstance("unhealthy", "DEMO-SERVICE", "127.0.0.2", 8082, false);
        cache.quarantine("unhealthy", 10);

        ServiceInstance selected = strategy.select(List.of(healthy, unhealthy), Map.of());

        assertEquals("healthy", selected.getInstanceId());
    }
}
