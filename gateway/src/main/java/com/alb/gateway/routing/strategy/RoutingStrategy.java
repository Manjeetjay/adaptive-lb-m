package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;
import java.util.Map;

/**
 * Common contract for all load balancing and routing strategies.
 */
public interface RoutingStrategy {

    /**
     * Selects an instance from the pool of candidates using strategy-specific logic.
     *
     * @param instances Candidate instances registered for the targeted service
     * @param metricsMap Latest snapshot of metrics and active connections keyed by instance ID
     * @return The chosen ServiceInstance, or null if candidates are empty
     */
    ServiceInstance select(List<ServiceInstance> instances, Map<String, InstanceMetricsSnapshot> metricsMap);

    /**
     * Returns the algorithm type identifier.
     */
    RoutingStrategyType getType();
}
