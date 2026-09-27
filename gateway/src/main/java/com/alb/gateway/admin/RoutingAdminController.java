package com.alb.gateway.admin;

import com.alb.gateway.routing.adaptive.AdaptiveRoutingConfig;
import com.alb.gateway.routing.adaptive.MetricCache;
import com.alb.gateway.routing.engine.InstanceConnectionTracker;
import com.alb.gateway.routing.engine.RoutingStrategyRegistry;
import com.alb.gateway.routing.model.RoutingStrategyType;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/routing")
public class RoutingAdminController {

    private final RoutingStrategyRegistry strategyRegistry;
    private final InstanceConnectionTracker connectionTracker;
    private final DiscoveryClient discoveryClient;
    private final AdaptiveRoutingConfig adaptiveRoutingConfig;
    private final MetricCache metricCache;

    public RoutingAdminController(
            RoutingStrategyRegistry strategyRegistry,
            InstanceConnectionTracker connectionTracker,
            DiscoveryClient discoveryClient,
            AdaptiveRoutingConfig adaptiveRoutingConfig,
            MetricCache metricCache) {
        this.strategyRegistry = strategyRegistry;
        this.connectionTracker = connectionTracker;
        this.discoveryClient = discoveryClient;
        this.adaptiveRoutingConfig = adaptiveRoutingConfig;
        this.metricCache = metricCache;
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getRoutingStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("activeStrategy", strategyRegistry.getActiveStrategyType().name());
            status.put("refreshIntervalMs", adaptiveRoutingConfig.getPollerIntervalMs());
            status.put("hysteresisDelta", adaptiveRoutingConfig.getHysteresisThreshold());
            status.put("softmaxTemperature", adaptiveRoutingConfig.getSoftmaxTemperature());
            status.put("weights", Map.of(
                    "cpu", adaptiveRoutingConfig.getWeightCpu(),
                    "memory", adaptiveRoutingConfig.getWeightMemory(),
                    "latency", adaptiveRoutingConfig.getWeightLatency(),
                    "connections", adaptiveRoutingConfig.getWeightConnections(),
                    "errors", adaptiveRoutingConfig.getWeightError()
            ));
            status.put("availableStrategies", Arrays.stream(RoutingStrategyType.values())
                    .map(Enum::name)
                    .collect(Collectors.toList()));
            status.put("instances", metricCache.getAll().values());
            status.put("trackedInstances", metricCache.getAll().values());
            status.put("timestamp", Instant.now().toString());
            return ResponseEntity.ok(status);
        });
    }

    @PostMapping("/strategy")
    public Mono<ResponseEntity<Map<String, Object>>> setStrategy(@RequestBody Map<String, String> request) {
        return Mono.fromCallable(() -> {
            String strategyName = request.get("strategy");
            if (strategyName == null) {
                return badRequest("Missing 'strategy' field");
            }
            try {
                String normalized = strategyName.toUpperCase().trim();
                if ("SMOOTH_WEIGHTED_ROUND_ROBIN".equals(normalized)) {
                    normalized = "WEIGHTED_ROUND_ROBIN";
                }
                RoutingStrategyType requested = RoutingStrategyType.valueOf(normalized);
                RoutingStrategyType previous = strategyRegistry.getActiveStrategyType();
                if (!strategyRegistry.setActiveStrategy(requested)) {
                    return ResponseEntity.internalServerError().body(Map.of("error", "Failed to activate strategy"));
                }
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "previousStrategy", previous.name(),
                        "activeStrategy", requested.name(),
                        "updatedAt", Instant.now().toString()));
            } catch (IllegalArgumentException exception) {
                return badRequest("Invalid strategy. Supported values: " + Arrays.toString(RoutingStrategyType.values()));
            }
        });
    }

    @PostMapping("/weights")
    public Mono<ResponseEntity<Map<String, Object>>> setWeights(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            if (containsAdaptiveWeight(request)) {
                return updateAdaptiveConfigValues(request);
            }
            if (request.containsKey("instanceId") && request.containsKey("weight")) {
                Object instanceId = request.get("instanceId");
                Object weight = request.get("weight");
                if (!(instanceId instanceof String id) || !(weight instanceof Number number) || number.intValue() <= 0) {
                    return badRequest("instanceId must be a string and weight must be a positive number");
                }
                connectionTracker.setInstanceWeight(id, number.intValue());
                return ResponseEntity.ok(Map.of("status", "UPDATED", "instanceId", id, "weight", number.intValue()));
            }
            Map<String, Integer> updated = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : request.entrySet()) {
                if (entry.getValue() instanceof Number number && number.intValue() > 0) {
                    connectionTracker.setInstanceWeight(entry.getKey(), number.intValue());
                    updated.put(entry.getKey(), number.intValue());
                }
            }
            if (updated.isEmpty()) {
                return badRequest("Provide adaptive weights or one or more positive instance weights");
            }
            return ResponseEntity.ok(Map.of("status", "UPDATED", "weights", updated));
        });
    }

    @GetMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> getAdaptiveConfig() {
        return Mono.fromCallable(() -> ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "config", configurationMap(adaptiveRoutingConfig),
                "timestamp", Instant.now().toString())));
    }

    @PostMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> postAdaptiveConfig(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> updateAdaptiveConfigValues(request));
    }

    @PutMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> updateAdaptiveConfig(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> updateAdaptiveConfigValues(request));
    }

    private ResponseEntity<Map<String, Object>> updateAdaptiveConfigValues(Map<String, Object> request) {
        synchronized (adaptiveRoutingConfig) {
            try {
                AdaptiveRoutingConfig candidate = new AdaptiveRoutingConfig();
                copyConfiguration(adaptiveRoutingConfig, candidate);
                setDouble(request, "weightLatency", "latency", candidate::setWeightLatency);
                setDouble(request, "weightCpu", "cpu", candidate::setWeightCpu);
                setDouble(request, "weightError", "errors", candidate::setWeightError);
                setDouble(request, "weightConnections", "connections", candidate::setWeightConnections);
                setDouble(request, "weightMemory", "memory", candidate::setWeightMemory);
                setDouble(request, "softmaxTemperature", null, candidate::setSoftmaxTemperature);
                setDouble(request, "hysteresisThreshold", "hysteresisDelta", candidate::setHysteresisThreshold);
                setDouble(request, "latencyBaseMs", null, candidate::setLatencyBaseMs);
                setDouble(request, "latencyMaxMs", null, candidate::setLatencyMaxMs);
                setDouble(request, "emaSmoothingFactor", null, candidate::setEmaSmoothingFactor);
                setInt(request, "connectionLimit", null, candidate::setConnectionLimit);
                setDouble(request, "healthyThreshold", null, candidate::setHealthyThreshold);
                setDouble(request, "unhealthyThreshold", null, candidate::setUnhealthyThreshold);
                setLong(request, "quarantineDurationSeconds", null, candidate::setQuarantineDurationSeconds);
                setLong(request, "pollerIntervalMs", "refreshIntervalMs", candidate::setPollerIntervalMs);
                setLong(request, "pollerTimeoutMs", null, candidate::setPollerTimeoutMs);

                String error = validate(candidate);
                if (error != null) {
                    return badRequest(error);
                }
                copyConfiguration(candidate, adaptiveRoutingConfig);
                return ResponseEntity.ok(Map.of(
                        "status", "UPDATED",
                        "config", configurationMap(adaptiveRoutingConfig),
                        "timestamp", Instant.now().toString()));
            } catch (IllegalArgumentException exception) {
                return badRequest(exception.getMessage());
            }
        }
    }

    private boolean containsAdaptiveWeight(Map<String, Object> request) {
        return request.containsKey("cpu") || request.containsKey("memory") || request.containsKey("latency")
                || request.containsKey("connections") || request.containsKey("errors")
                || request.containsKey("weightCpu") || request.containsKey("weightMemory")
                || request.containsKey("weightLatency") || request.containsKey("weightConnections")
                || request.containsKey("weightError");
    }

    private void setDouble(Map<String, Object> request, String key, String alias, DoubleConsumer setter) {
        Object value = request.containsKey(key) ? request.get(key) : alias == null ? null : request.get(alias);
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be a finite numeric value");
        }
        setter.accept(number.doubleValue());
    }

    private void setInt(Map<String, Object> request, String key, String alias, java.util.function.IntConsumer setter) {
        Object value = request.containsKey(key) ? request.get(key) : alias == null ? null : request.get(alias);
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        setter.accept(number.intValue());
    }

    private void setLong(Map<String, Object> request, String key, String alias, java.util.function.LongConsumer setter) {
        Object value = request.containsKey(key) ? request.get(key) : alias == null ? null : request.get(alias);
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        setter.accept(number.longValue());
    }

    private String validate(AdaptiveRoutingConfig candidate) {
        double weightSum = candidate.getWeightLatency() + candidate.getWeightCpu() + candidate.getWeightError()
                + candidate.getWeightConnections() + candidate.getWeightMemory();
        if (candidate.getWeightLatency() < 0 || candidate.getWeightCpu() < 0 || candidate.getWeightError() < 0
                || candidate.getWeightConnections() < 0 || candidate.getWeightMemory() < 0
                || Math.abs(weightSum - 1.0) > 0.001) {
            return "Adaptive routing weights must be non-negative and sum to 1.0";
        }
        if (candidate.getSoftmaxTemperature() <= 0 || candidate.getHysteresisThreshold() < 0) {
            return "softmaxTemperature must be positive and hysteresisThreshold cannot be negative";
        }
        if (candidate.getLatencyBaseMs() < 0 || candidate.getLatencyMaxMs() <= candidate.getLatencyBaseMs()) {
            return "latencyMaxMs must be greater than non-negative latencyBaseMs";
        }
        if (candidate.getConnectionLimit() <= 0 || candidate.getEmaSmoothingFactor() <= 0
                || candidate.getEmaSmoothingFactor() > 1) {
            return "connectionLimit must be positive and emaSmoothingFactor must be in (0, 1]";
        }
        if (candidate.getUnhealthyThreshold() < 0 || candidate.getHealthyThreshold() > 1
                || candidate.getUnhealthyThreshold() >= candidate.getHealthyThreshold()) {
            return "unhealthyThreshold must be lower than healthyThreshold, both within [0, 1]";
        }
        if (candidate.getQuarantineDurationSeconds() < 0 || candidate.getPollerIntervalMs() <= 0
                || candidate.getPollerTimeoutMs() <= 0) {
            return "quarantineDurationSeconds cannot be negative and poller intervals must be positive";
        }
        return null;
    }

    private Map<String, Object> configurationMap(AdaptiveRoutingConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("weights", Map.of("cpu", config.getWeightCpu(), "memory", config.getWeightMemory(),
                "latency", config.getWeightLatency(), "connections", config.getWeightConnections(),
                "errors", config.getWeightError()));
        values.put("softmaxTemperature", config.getSoftmaxTemperature());
        values.put("hysteresisThreshold", config.getHysteresisThreshold());
        values.put("latencyBaseMs", config.getLatencyBaseMs());
        values.put("latencyMaxMs", config.getLatencyMaxMs());
        values.put("connectionLimit", config.getConnectionLimit());
        values.put("emaSmoothingFactor", config.getEmaSmoothingFactor());
        values.put("healthyThreshold", config.getHealthyThreshold());
        values.put("unhealthyThreshold", config.getUnhealthyThreshold());
        values.put("quarantineDurationSeconds", config.getQuarantineDurationSeconds());
        values.put("pollerIntervalMs", config.getPollerIntervalMs());
        values.put("pollerTimeoutMs", config.getPollerTimeoutMs());
        return values;
    }

    private void copyConfiguration(AdaptiveRoutingConfig source, AdaptiveRoutingConfig target) {
        target.setWeightLatency(source.getWeightLatency());
        target.setWeightCpu(source.getWeightCpu());
        target.setWeightError(source.getWeightError());
        target.setWeightConnections(source.getWeightConnections());
        target.setWeightMemory(source.getWeightMemory());
        target.setSoftmaxTemperature(source.getSoftmaxTemperature());
        target.setHysteresisThreshold(source.getHysteresisThreshold());
        target.setLatencyBaseMs(source.getLatencyBaseMs());
        target.setLatencyMaxMs(source.getLatencyMaxMs());
        target.setConnectionLimit(source.getConnectionLimit());
        target.setEmaSmoothingFactor(source.getEmaSmoothingFactor());
        target.setHealthyThreshold(source.getHealthyThreshold());
        target.setUnhealthyThreshold(source.getUnhealthyThreshold());
        target.setQuarantineDurationSeconds(source.getQuarantineDurationSeconds());
        target.setPollerIntervalMs(source.getPollerIntervalMs());
        target.setPollerTimeoutMs(source.getPollerTimeoutMs());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("status", "INVALID", "error", message));
    }
}
