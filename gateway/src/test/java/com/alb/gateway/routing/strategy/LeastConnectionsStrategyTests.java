package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeastConnectionsStrategyTests {

    private LeastConnectionsStrategy strategy;
    private ServiceInstance instanceA;
    private ServiceInstance instanceB;
    private ServiceInstance instanceC;

    @BeforeEach
    void setUp() {
        strategy = new LeastConnectionsStrategy();
        instanceA = new DefaultServiceInstance("inst-a", "DEMO-SERVICE", "10.0.0.1", 8081, false);
        instanceB = new DefaultServiceInstance("inst-b", "DEMO-SERVICE", "10.0.0.2", 8082, false);
        instanceC = new DefaultServiceInstance("inst-c", "DEMO-SERVICE", "10.0.0.3", 8083, false);
    }

    @Test
    void testRoutesToLowestConnectionInstance() {
        List<ServiceInstance> instances = List.of(instanceA, instanceB, instanceC);

        Map<String, InstanceMetricsSnapshot> metrics = Map.of(
                "inst-a", InstanceMetricsSnapshot.builder().instanceId("inst-a").activeConnections(15).build(),
                "inst-b", InstanceMetricsSnapshot.builder().instanceId("inst-b").activeConnections(2).build(),
                "inst-c", InstanceMetricsSnapshot.builder().instanceId("inst-c").activeConnections(8).build()
        );

        ServiceInstance selected = strategy.select(instances, metrics);
        assertNotNull(selected);
        assertEquals("inst-b", selected.getInstanceId(), "Should pick inst-b which has only 2 connections");
    }

    @Test
    void testTieBreakerWhenEqualConnections() {
        List<ServiceInstance> instances = List.of(instanceA, instanceB);

        Map<String, InstanceMetricsSnapshot> metrics = Map.of(
                "inst-a", InstanceMetricsSnapshot.builder().instanceId("inst-a").activeConnections(5).build(),
                "inst-b", InstanceMetricsSnapshot.builder().instanceId("inst-b").activeConnections(5).build()
        );

        ServiceInstance s1 = strategy.select(instances, metrics);
        ServiceInstance s2 = strategy.select(instances, metrics);

        assertNotEquals(s1.getInstanceId(), s2.getInstanceId(), "Tie-breaker should alternate between equal candidates");
    }

    @Test
    void testStrategyType() {
        assertEquals(RoutingStrategyType.LEAST_CONNECTIONS, strategy.getType());
    }
}
