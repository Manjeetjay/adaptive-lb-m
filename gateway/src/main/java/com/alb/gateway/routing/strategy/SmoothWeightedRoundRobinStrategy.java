package com.alb.gateway.routing.strategy;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nginx-style Smooth Weighted Round Robin strategy.
 * Ensures traffic is smoothly interleaved according to assigned weights,
 * avoiding burst clumping on higher-weighted instances.
 */
@Component
public class SmoothWeightedRoundRobinStrategy implements RoutingStrategy {

    private final Map<String, AtomicInteger> currentWeights = new ConcurrentHashMap<>();

    @Override
    public synchronized ServiceInstance select(List<ServiceInstance> instances, Map<String, InstanceMetricsSnapshot> metricsMap) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        if (instances.size() == 1) {
            return instances.get(0);
        }

        int totalWeight = 0;
        ServiceInstance bestInstance = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (ServiceInstance instance : instances) {
            String instanceId = instance.getInstanceId() != null ? instance.getInstanceId() : instance.getUri().toString();
            int weight = resolveWeight(instance, metricsMap);
            totalWeight += weight;

            AtomicInteger currentWeight = currentWeights.computeIfAbsent(instanceId, k -> new AtomicInteger(0));
            int newWeight = currentWeight.addAndGet(weight);

            if (bestInstance == null || newWeight > maxCurrentWeight) {
                maxCurrentWeight = newWeight;
                bestInstance = instance;
            }
        }

        if (bestInstance != null) {
            String bestId = bestInstance.getInstanceId() != null ? bestInstance.getInstanceId() : bestInstance.getUri().toString();
            currentWeights.get(bestId).addAndGet(-totalWeight);
        }

        return bestInstance;
    }

    private int resolveWeight(ServiceInstance instance, Map<String, InstanceMetricsSnapshot> metricsMap) {
        String instanceId = instance.getInstanceId();
        if (instanceId != null && metricsMap != null && metricsMap.containsKey(instanceId)) {
            int w = metricsMap.get(instanceId).getWeight();
            if (w > 0) return w;
        }

        // Check Eureka metadata
        if (instance.getMetadata() != null && instance.getMetadata().containsKey("weight")) {
            try {
                int w = Integer.parseInt(instance.getMetadata().get("weight"));
                if (w > 0) return w;
            } catch (NumberFormatException ignored) {}
        }

        return 1; // Default fallback weight
    }

    @Override
    public RoutingStrategyType getType() {
        return RoutingStrategyType.WEIGHTED_ROUND_ROBIN;
    }
}
