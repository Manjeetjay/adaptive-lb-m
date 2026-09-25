package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least Connections load balancing strategy.
 * Forwards requests to the instance with the fewest in-flight active requests.
 * Breaks ties using Round Robin.
 */
@Component
public class LeastConnectionsStrategy implements RoutingStrategy {

    private final AtomicInteger tieBreaker = new AtomicInteger(0);

    @Override
    public ServiceInstance select(List<ServiceInstance> instances, Map<String, InstanceMetricsSnapshot> metricsMap) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.get(0);
        }

        int minConnections = Integer.MAX_VALUE;
        List<ServiceInstance> bestCandidates = new ArrayList<>();

        for (ServiceInstance instance : instances) {
            String instanceId = instance.getInstanceId() != null ? instance.getInstanceId() : instance.getUri().toString();
            int connections = 0;
            if (metricsMap != null && metricsMap.containsKey(instanceId)) {
                connections = metricsMap.get(instanceId).getActiveConnections();
            }

            if (connections < minConnections) {
                minConnections = connections;
                bestCandidates.clear();
                bestCandidates.add(instance);
            } else if (connections == minConnections) {
                bestCandidates.add(instance);
            }
        }

        if (bestCandidates.size() == 1) {
            return bestCandidates.get(0);
        }

        // Tie-breaker via round-robin among instances with identical lowest connection count
        int idx = Math.abs(tieBreaker.getAndIncrement() % bestCandidates.size());
        return bestCandidates.get(idx);
    }

    @Override
    public RoutingStrategyType getType() {
        return RoutingStrategyType.LEAST_CONNECTIONS;
    }
}
