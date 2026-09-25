package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.RoutingStrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTests {

    private RoundRobinStrategy strategy;
    private ServiceInstance instanceA;
    private ServiceInstance instanceB;
    private ServiceInstance instanceC;

    @BeforeEach
    void setUp() {
        strategy = new RoundRobinStrategy();
        instanceA = new DefaultServiceInstance("inst-a", "DEMO-SERVICE", "10.0.0.1", 8081, false);
        instanceB = new DefaultServiceInstance("inst-b", "DEMO-SERVICE", "10.0.0.2", 8082, false);
        instanceC = new DefaultServiceInstance("inst-c", "DEMO-SERVICE", "10.0.0.3", 8083, false);
    }

    @Test
    void testEmptyListReturnsNull() {
        assertNull(strategy.select(List.of(), Map.of()));
        assertNull(strategy.select(null, Map.of()));
    }

    @Test
    void testSingleInstanceReturnsSame() {
        ServiceInstance selected = strategy.select(List.of(instanceA), Map.of());
        assertEquals("inst-a", selected.getInstanceId());
    }

    @Test
    void testCyclicDistribution() {
        List<ServiceInstance> instances = List.of(instanceA, instanceB, instanceC);

        ServiceInstance s1 = strategy.select(instances, Map.of());
        ServiceInstance s2 = strategy.select(instances, Map.of());
        ServiceInstance s3 = strategy.select(instances, Map.of());
        ServiceInstance s4 = strategy.select(instances, Map.of());
        ServiceInstance s5 = strategy.select(instances, Map.of());
        ServiceInstance s6 = strategy.select(instances, Map.of());

        assertEquals("inst-a", s1.getInstanceId());
        assertEquals("inst-b", s2.getInstanceId());
        assertEquals("inst-c", s3.getInstanceId());
        assertEquals("inst-a", s4.getInstanceId());
        assertEquals("inst-b", s5.getInstanceId());
        assertEquals("inst-c", s6.getInstanceId());
    }

    @Test
    void testStrategyType() {
        assertEquals(RoutingStrategyType.ROUND_ROBIN, strategy.getType());
    }
}
