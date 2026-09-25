package com.alb.gateway.routing.engine;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks real-time active in-flight requests and configured weights per microservice instance.
 */
@Component
public class InstanceConnectionTracker {

    private final Map<String, AtomicInteger> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Integer> instanceWeights = new ConcurrentHashMap<>();
    private final Map<String, InstanceMetricsSnapshot> telemetryOverrides = new ConcurrentHashMap<>();

    public int incrementConnection(String instanceId) {
        return activeConnections.computeIfAbsent(instanceId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int decrementConnection(String instanceId) {
        AtomicInteger counter = activeConnections.get(instanceId);
        if (counter != null) {
            int val = counter.decrementAndGet();
            if (val < 0) {
                counter.set(0);
                return 0;
            }
            return val;
        }
        return 0;
    }

    public int getActiveConnections(String instanceId) {
        AtomicInteger counter = activeConnections.get(instanceId);
        return counter != null ? Math.max(0, counter.get()) : 0;
    }

    public void setInstanceWeight(String instanceId, int weight) {
        if (weight > 0) {
            instanceWeights.put(instanceId, weight);
        }
    }

    public int getInstanceWeight(String instanceId) {
        return instanceWeights.getOrDefault(instanceId, 1);
    }

    public void updateTelemetry(String instanceId, InstanceMetricsSnapshot snapshot) {
        telemetryOverrides.put(instanceId, snapshot);
    }

    /**
     * Builds the current snapshot map combining connection counts, weights, and telemetry.
     */
    public Map<String, InstanceMetricsSnapshot> buildMetricsMap(Iterable<String> instanceIds) {
        Map<String, InstanceMetricsSnapshot> map = new ConcurrentHashMap<>();
        for (String id : instanceIds) {
            int conns = getActiveConnections(id);
            int weight = getInstanceWeight(id);
            InstanceMetricsSnapshot existing = telemetryOverrides.get(id);

            if (existing != null) {
                existing.setActiveConnections(conns);
                existing.setWeight(weight);
                map.put(id, existing);
            } else {
                map.put(id, InstanceMetricsSnapshot.builder()
                        .instanceId(id)
                        .activeConnections(conns)
                        .weight(weight)
                        .status(InstanceMetricsSnapshot.InstanceStatus.HEALTHY)
                        .lastUpdatedTimestampMs(System.currentTimeMillis())
                        .build());
            }
        }
        return map;
    }
}
