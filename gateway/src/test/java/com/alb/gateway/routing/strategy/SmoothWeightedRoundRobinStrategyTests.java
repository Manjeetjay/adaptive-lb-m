package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmoothWeightedRoundRobinStrategyTests {

    private SmoothWeightedRoundRobinStrategy strategy;
    private ServiceInstance instanceA;
    private ServiceInstance instanceB;
    private ServiceInstance instanceC;

    @BeforeEach
    void setUp() {
        strategy = new SmoothWeightedRoundRobinStrategy();
        instanceA = new DefaultServiceInstance("inst-a", "DEMO-SERVICE", "10.0.0.1", 8081, false);
        instanceB = new DefaultServiceInstance("inst-b", "DEMO-SERVICE", "10.0.0.2", 8082, false);
        instanceC = new DefaultServiceInstance("inst-c", "DEMO-SERVICE", "10.0.0.3", 8083, false);
    }

    @Test
    void testProportionalDistribution() {
        List<ServiceInstance> instances = List.of(instanceA, instanceB, instanceC);

        Map<String, InstanceMetricsSnapshot> metrics = Map.of(
                "inst-a", InstanceMetricsSnapshot.builder().instanceId("inst-a").weight(5).build(),
                "inst-b", InstanceMetricsSnapshot.builder().instanceId("inst-b").weight(1).build(),
                "inst-c", InstanceMetricsSnapshot.builder().instanceId("inst-c").weight(1).build()
        );

        Map<String, Integer> counts = new HashMap<>();
        // In 7 requests, A should be picked 5 times, B once, C once
        for (int i = 0; i < 7; i++) {
            ServiceInstance selected = strategy.select(instances, metrics);
            assertNotNull(selected);
            counts.merge(selected.getInstanceId(), 1, Integer::sum);
        }

        assertEquals(5, counts.get("inst-a"));
        assertEquals(1, counts.get("inst-b"));
        assertEquals(1, counts.get("inst-c"));
    }

    @Test
    void testSmoothInterleaving() {
        // Test weights: A=4, B=2. Ratio 2:1.
        List<ServiceInstance> instances = List.of(instanceA, instanceB);

        Map<String, InstanceMetricsSnapshot> metrics = Map.of(
                "inst-a", InstanceMetricsSnapshot.builder().instanceId("inst-a").weight(4).build(),
                "inst-b", InstanceMetricsSnapshot.builder().instanceId("inst-b").weight(2).build()
        );

        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            ServiceInstance selected = strategy.select(instances, metrics);
            sequence.append(selected.getInstanceId().equals("inst-a") ? "A" : "B");
        }

        // Must smoothly interleave, e.g. ABAABA (4 A's, 2 B's evenly distributed)
        assertEquals("ABAABA", sequence.toString());
    }

    @Test
    void testStrategyType() {
        assertEquals(RoutingStrategyType.WEIGHTED_ROUND_ROBIN, strategy.getType());
    }
}
