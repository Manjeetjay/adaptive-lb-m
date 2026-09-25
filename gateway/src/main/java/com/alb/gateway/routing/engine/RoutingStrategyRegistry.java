package com.alb.gateway.routing.engine;

import com.alb.gateway.routing.model.RoutingStrategyType;
import com.alb.gateway.routing.strategy.RoutingStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central registry managing all registered routing strategies and tracking the active strategy.
 */
@Component
public class RoutingStrategyRegistry {

    private final Map<RoutingStrategyType, RoutingStrategy> strategyMap = new EnumMap<>(RoutingStrategyType.class);
    private final AtomicReference<RoutingStrategy> activeStrategy = new AtomicReference<>();

    public RoutingStrategyRegistry(List<RoutingStrategy> strategies) {
        for (RoutingStrategy strategy : strategies) {
            strategyMap.put(strategy.getType(), strategy);
        }
        // Set default strategy to ROUND_ROBIN
        RoutingStrategy defaultStrategy = strategyMap.get(RoutingStrategyType.ROUND_ROBIN);
        if (defaultStrategy != null) {
            activeStrategy.set(defaultStrategy);
        } else if (!strategies.isEmpty()) {
            activeStrategy.set(strategies.get(0));
        }
    }

    public RoutingStrategy getActiveStrategy() {
        return activeStrategy.get();
    }

    public RoutingStrategyType getActiveStrategyType() {
        RoutingStrategy current = activeStrategy.get();
        return current != null ? current.getType() : RoutingStrategyType.ROUND_ROBIN;
    }

    public synchronized boolean setActiveStrategy(RoutingStrategyType type) {
        RoutingStrategy strategy = strategyMap.get(type);
        if (strategy != null) {
            activeStrategy.set(strategy);
            return true;
        }
        return false;
    }

    public RoutingStrategy getStrategy(RoutingStrategyType type) {
        return strategyMap.get(type);
    }

    public Map<RoutingStrategyType, RoutingStrategy> getAllStrategies() {
        return Map.copyOf(strategyMap);
    }
}
