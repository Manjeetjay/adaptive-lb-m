package com.alb.gateway.admin;

import com.alb.gateway.routing.engine.InstanceConnectionTracker;
import com.alb.gateway.routing.engine.RoutingStrategyRegistry;
import com.alb.gateway.routing.model.RoutingStrategyType;
import com.alb.gateway.routing.strategy.LeastConnectionsStrategy;
import com.alb.gateway.routing.strategy.RoundRobinStrategy;
import com.alb.gateway.routing.strategy.SmoothWeightedRoundRobinStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RoutingAdminControllerTests {

    private RoutingAdminController controller;
    private RoutingStrategyRegistry registry;
    private InstanceConnectionTracker tracker;
    private DiscoveryClient discoveryClient;

    @BeforeEach
    void setUp() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        SmoothWeightedRoundRobinStrategy wrr = new SmoothWeightedRoundRobinStrategy();
        LeastConnectionsStrategy lc = new LeastConnectionsStrategy();

        registry = new RoutingStrategyRegistry(List.of(rr, wrr, lc));
        tracker = new InstanceConnectionTracker();
        discoveryClient = Mockito.mock(DiscoveryClient.class);
        when(discoveryClient.getServices()).thenReturn(List.of("DEMO-SERVICE"));

        controller = new RoutingAdminController(registry, tracker, discoveryClient);
    }

    @Test
    void testGetRoutingStatus() {
        ResponseEntity<Map<String, Object>> response = controller.getRoutingStatus().block();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ROUND_ROBIN", body.get("activeStrategy"));
        assertTrue(body.containsKey("availableStrategies"));
    }

    @Test
    void testSwitchStrategyValid() {
        ResponseEntity<Map<String, Object>> response = controller.setStrategy(Map.of("strategy", "LEAST_CONNECTIONS")).block();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("SUCCESS", body.get("status"));
        assertEquals("LEAST_CONNECTIONS", body.get("activeStrategy"));
        assertEquals("ROUND_ROBIN", body.get("previousStrategy"));
        assertEquals(RoutingStrategyType.LEAST_CONNECTIONS, registry.getActiveStrategyType());
    }

    @Test
    void testSwitchStrategyInvalid() {
        ResponseEntity<Map<String, Object>> response = controller.setStrategy(Map.of("strategy", "NON_EXISTENT_ALGO")).block();
        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void testSetWeights() {
        ResponseEntity<Map<String, Object>> response = controller.setWeights(Map.of("instanceId", "worker-1", "weight", 5)).block();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(5, tracker.getInstanceWeight("worker-1"));
    }
}
