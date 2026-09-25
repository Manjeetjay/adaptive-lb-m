package com.alb.gateway.admin;

import com.alb.gateway.routing.engine.InstanceConnectionTracker;
import com.alb.gateway.routing.engine.RoutingStrategyRegistry;
import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Administrative and research management API for dynamic algorithm switching,
 * weight adjustments, and real-time state inspection.
 */
@RestController
@RequestMapping("/admin/routing")
public class RoutingAdminController {

    private final RoutingStrategyRegistry strategyRegistry;
    private final InstanceConnectionTracker connectionTracker;
    private final DiscoveryClient discoveryClient;

    public RoutingAdminController(
            RoutingStrategyRegistry strategyRegistry,
            InstanceConnectionTracker connectionTracker,
            DiscoveryClient discoveryClient) {
        this.strategyRegistry = strategyRegistry;
        this.connectionTracker = connectionTracker;
        this.discoveryClient = discoveryClient;
    }

    /**
     * Inspect current routing configuration, active algorithm, and instance telemetry.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getRoutingStatus() {
        return Mono.fromCallable(() -> {
            List<String> services = discoveryClient.getServices();
            List<String> allInstanceIds = new ArrayList<>();
            for (String svc : services) {
                discoveryClient.getInstances(svc).forEach(i -> {
                    String id = i.getInstanceId() != null ? i.getInstanceId() : i.getUri().toString();
                    allInstanceIds.add(id);
                });
            }

            Map<String, InstanceMetricsSnapshot> metricsMap = connectionTracker.buildMetricsMap(allInstanceIds);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("activeStrategy", strategyRegistry.getActiveStrategyType().name());
            response.put("availableStrategies", Arrays.stream(RoutingStrategyType.values())
                    .map(Enum::name)
                    .collect(Collectors.toList()));
            response.put("trackedInstances", metricsMap.values());
            response.put("timestamp", Instant.now().toString());

            return ResponseEntity.ok(response);
        });
    }

    /**
     * Dynamically switch active routing algorithm at runtime.
     */
    @PostMapping("/strategy")
    public Mono<ResponseEntity<Map<String, Object>>> setStrategy(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String strategyStr = request.get("strategy");
            if (strategyStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'strategy' field"));
            }

            RoutingStrategyType type;
            try {
                type = RoutingStrategyType.valueOf(strategyStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid strategy. Supported values: " + Arrays.toString(RoutingStrategyType.values())
                ));
            }

            RoutingStrategyType previous = strategyRegistry.getActiveStrategyType();
            boolean success = strategyRegistry.setActiveStrategy(type);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "previousStrategy", previous.name(),
                        "activeStrategy", type.name(),
                        "updatedAt", Instant.now().toString()
                ));
            } else {
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to activate strategy: " + type));
            }
        });
    }

    /**
     * Set weight for a specific instance (for Weighted Round Robin).
     */
    @PostMapping("/weights")
    public Mono<ResponseEntity<Map<String, Object>>> setWeights(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            if (request.containsKey("instanceId") && request.containsKey("weight")) {
                String id = (String) request.get("instanceId");
                int weight = ((Number) request.get("weight")).intValue();
                connectionTracker.setInstanceWeight(id, weight);
                return ResponseEntity.ok(Map.of(
                        "status", "UPDATED",
                        "instanceId", id,
                        "weight", weight
                ));
            } else {
                // Bulk update: {"instance-1": 5, "instance-2": 2}
                Map<String, Integer> updated = new HashMap<>();
                request.forEach((key, val) -> {
                    if (val instanceof Number) {
                        int w = ((Number) val).intValue();
                        connectionTracker.setInstanceWeight(key, w);
                        updated.put(key, w);
                    }
                });
                return ResponseEntity.ok(Map.of(
                        "status", "UPDATED",
                        "weights", updated
                ));
            }
        });
    }
}
