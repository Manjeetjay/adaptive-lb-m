package com.alb.gateway.routing.model;

import lombok.Builder;
import lombok.Data;

/**
 * Real-time operational snapshot of a downstream service instance.
 */
@Data
@Builder
public class InstanceMetricsSnapshot {
    private String instanceId;
    private String uri;
    private int weight;
    private int activeConnections;
    private double cpuUsage;
    private double memoryUsageRatio;
    private double latencyEmaMs;
    private double errorRate;
    private double compositeScore;
    private double routingProbability;
    @Builder.Default
    private InstanceStatus status = InstanceStatus.HEALTHY;
    private long lastUpdatedTimestampMs;

    public enum InstanceStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
}
