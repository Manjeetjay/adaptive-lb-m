package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standard Round Robin load balancing strategy.
 * Cycles through available instances sequentially without state inspection.
 */
@Component
public class RoundRobinStrategy implements RoutingStrategy {

    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public ServiceInstance select(List<ServiceInstance> instances, Map<String, InstanceMetricsSnapshot> metricsMap) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.get(0);
        }
        int pos = Math.abs(position.getAndIncrement() % instances.size());
        return instances.get(pos);
    }

    @Override
    public RoutingStrategyType getType() {
        return RoutingStrategyType.ROUND_ROBIN;
    }
}
