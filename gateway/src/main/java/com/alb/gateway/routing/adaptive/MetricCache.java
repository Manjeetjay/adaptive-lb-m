package com.alb.gateway.routing.adaptive;

import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache of instance telemetry snapshots.
 *
 * The data plane (AdaptiveRoutingFilter / Strategy.select) reads from this cache
 * with zero blocking — all Prometheus/Actuator queries happen asynchronously
 * in the {@link MetricPollerService} background task.
 *
 * Cache entries include EMA-smoothed metrics, composite health scores,
 * Softmax routing probabilities, and quarantine timestamps.
 */
@Slf4j
@Component
public class MetricCache {

    private final Map<String, InstanceMetricsSnapshot> cache = new ConcurrentHashMap<>();

    /** Quarantine expiry timestamps — instance ID → epoch millis when quarantine ends */
    private final Map<String, Long> quarantineExpiry = new ConcurrentHashMap<>();

    /** Consecutive background scrape failures used as a timeout fail-safe. */
    private final Map<String, Integer> consecutivePollFailures = new ConcurrentHashMap<>();

    /**
     * Updates or inserts a telemetry snapshot for the given instance.
     *
     * @param instanceId Unique identifier of the worker instance
     * @param snapshot   Fresh telemetry snapshot
     */
    public void put(String instanceId, InstanceMetricsSnapshot snapshot) {
        cache.put(instanceId, snapshot);
    }

    /**
     * Retrieves the latest cached snapshot for a specific instance.
     *
     * @param instanceId Unique identifier of the worker instance
     * @return Optional containing the snapshot if present
     */
    public Optional<InstanceMetricsSnapshot> get(String instanceId) {
        return Optional.ofNullable(cache.get(instanceId));
    }

    /**
     * Returns an unmodifiable view of the entire cache.
     *
     * @return Read-only map of instance ID → snapshot
     */
    public Map<String, InstanceMetricsSnapshot> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Builds a mutable copy of the current cache suitable for routing decisions.
     *
     * @return New mutable map containing all cached snapshots
     */
    public Map<String, InstanceMetricsSnapshot> buildMetricsMap() {
        return new ConcurrentHashMap<>(cache);
    }

    /**
     * Removes an instance from the cache (e.g., when deregistered from Eureka).
     *
     * @param instanceId Unique identifier of the worker instance
     */
    public void evict(String instanceId) {
        cache.remove(instanceId);
        quarantineExpiry.remove(instanceId);
        consecutivePollFailures.remove(instanceId);
    }

    /**
     * Places an instance in quarantine for the specified duration.
     * Quarantined instances are excluded from the routing pool.
     *
     * @param instanceId         Unique identifier of the worker instance
     * @param durationSeconds    Quarantine duration in seconds
     */
    public void quarantine(String instanceId, long durationSeconds) {
        long expiryMs = System.currentTimeMillis() + (durationSeconds * 1000);
        quarantineExpiry.put(instanceId, expiryMs);
        log.warn("Instance [{}] quarantined until {} ({} seconds)", instanceId, expiryMs, durationSeconds);
    }

    /**
     * Checks if an instance is currently quarantined.
     *
     * @param instanceId Unique identifier of the worker instance
     * @return true if still in quarantine
     */
    public boolean isQuarantined(String instanceId) {
        Long expiry = quarantineExpiry.get(instanceId);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            quarantineExpiry.remove(instanceId);
            log.info("Instance [{}] quarantine expired — re-entering routing pool", instanceId);
            return false;
        }
        return true;
    }

    /**
     * Returns the number of cached instances.
     */
    public int size() {
        return cache.size();
    }

    public void recordSuccessfulPoll(String instanceId) {
        consecutivePollFailures.remove(instanceId);
    }

    public int recordPollFailure(String instanceId) {
        return consecutivePollFailures.merge(instanceId, 1, Integer::sum);
    }

    public long getStalenessMs(String instanceId) {
        return get(instanceId)
                .map(snapshot -> Math.max(0, System.currentTimeMillis() - snapshot.getLastUpdatedTimestampMs()))
                .orElse(-1L);
    }
}
