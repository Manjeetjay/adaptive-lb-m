package com.alb.gateway.routing.model;

/**
 * Enumeration of all supported routing algorithms in the load balancer.
 */
public enum RoutingStrategyType {
    ROUND_ROBIN,
    WEIGHTED_ROUND_ROBIN,
    LEAST_CONNECTIONS,
    ADAPTIVE_MULTI_METRIC
}
