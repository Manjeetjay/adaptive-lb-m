package com.alb.gateway.routing.filter;

import com.alb.gateway.routing.adaptive.MetricCache;
import com.alb.gateway.routing.engine.InstanceConnectionTracker;
import com.alb.gateway.routing.engine.RoutingStrategyRegistry;
import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.strategy.RoutingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Global reactive filter that intercepts requests to microservices and resolves the destination
 * instance using the currently active RoutingStrategy.
 */
@Slf4j
@Component
public class AdaptiveRoutingFilter implements GlobalFilter, Ordered {

    public static final int FILTER_ORDER = ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER - 50;

    private final DiscoveryClient discoveryClient;
    private final RoutingStrategyRegistry strategyRegistry;
    private final InstanceConnectionTracker connectionTracker;
    private final Timer routingEvalTimer;
    private final MetricCache metricCache;
    private final MeterRegistry meterRegistry;

    public AdaptiveRoutingFilter(
            DiscoveryClient discoveryClient,
            RoutingStrategyRegistry strategyRegistry,
            InstanceConnectionTracker connectionTracker,
            MeterRegistry meterRegistry,
            MetricCache metricCache) {
        this.discoveryClient = discoveryClient;
        this.strategyRegistry = strategyRegistry;
        this.connectionTracker = connectionTracker;
        this.metricCache = metricCache;
        this.meterRegistry = meterRegistry;
        this.routingEvalTimer = Timer.builder("alb_gateway_routing_eval_duration_seconds")
                .description("Processing time taken by RoutingStrategy.select()")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (url == null) {
            return chain.filter(exchange);
        }

        String scheme = url.getScheme();
        // Intercept alb:// and lb:// schemes
        if (!"alb".equalsIgnoreCase(scheme) && !"lb".equalsIgnoreCase(scheme)) {
            return chain.filter(exchange);
        }

        String serviceId = url.getHost();
        if (serviceId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing service ID in target URL"));
        }

        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (instances == null || instances.isEmpty()) {
            log.warn("No instances available for service: {}", serviceId);
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No instances available for service: " + serviceId));
        }

        Map<String, InstanceMetricsSnapshot> metricsMap = metricCache.buildMetricsMap();

        RoutingStrategy strategy = strategyRegistry.getActiveStrategy();

        long startEval = System.nanoTime();
        ServiceInstance chosen = strategy.select(instances, metricsMap);
        routingEvalTimer.record(System.nanoTime() - startEval, TimeUnit.NANOSECONDS);

        if (chosen == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Routing strategy failed to select instance"));
        }

        String chosenId = chosen.getInstanceId() != null ? chosen.getInstanceId() : chosen.getUri().toString();

        // Increment active connection count
        connectionTracker.incrementConnection(chosenId);
        meterRegistry.counter("alb_gateway_route_decisions_total",
                        "strategy", strategy.getType().name(),
                        "target_instance", chosenId)
                .increment();

        // Reconstruct destination URL: http://chosenHost:chosenPort/originalPath
        URI originalUri = exchange.getRequest().getURI();
        URI destinationUri = UriComponentsBuilder.fromUri(originalUri)
                .scheme(chosen.isSecure() ? "https" : "http")
                .host(chosen.getHost())
                .port(chosen.getPort())
                .build(true)
                .toUri();

        log.debug("Strategy [{}] routed request {} to instance {} ({})",
                strategy.getType(), originalUri.getPath(), chosenId, destinationUri);

        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, destinationUri);

        // Decrement connection count when response completes or errors
        return chain.filter(exchange)
                .doFinally(signalType -> connectionTracker.decrementConnection(chosenId));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

}
