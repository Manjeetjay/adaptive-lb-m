package com.alb.gateway.routing.adaptive;

import com.alb.gateway.routing.engine.InstanceConnectionTracker;
import com.alb.gateway.routing.model.InstanceMetricsSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous background poller that periodically scrapes worker Actuator/Prometheus
 * endpoints and populates the {@link MetricCache} with EMA-smoothed telemetry snapshots.
 *
 * Runs on a fixed schedule (default 500ms) completely decoupled from the data plane.
 * The routing filter reads cached snapshots without blocking or issuing HTTP calls.
 */
@Slf4j
@Service
public class MetricPollerService {

    private final DiscoveryClient discoveryClient;
    private final MetricCache metricCache;
    private final InstanceConnectionTracker connectionTracker;
    private final CompositeScoreCalculator scoreCalculator;
    private final AdaptiveRoutingConfig config;
    private final WebClient webClient;
    private final Timer pollCycleTimer;
    private final MeterRegistry meterRegistry;
    private final Set<String> registeredMetricInstances = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastPollStartedAtMs = new AtomicLong();

    // Regex patterns for parsing Prometheus text format
    private static final Pattern SYSTEM_CPU_PATTERN =
            Pattern.compile("^system_cpu_usage\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern PROCESS_CPU_PATTERN =
            Pattern.compile("^process_cpu_usage\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern JVM_MEMORY_USED_PATTERN =
            Pattern.compile("jvm_memory_used_bytes\\{[^}]*area=\"heap\"[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern JVM_MEMORY_MAX_PATTERN =
            Pattern.compile("jvm_memory_max_bytes\\{[^}]*area=\"heap\"[^}]*id=\"G1 Old Gen\"[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern ACTIVE_REQUESTS_PATTERN =
            Pattern.compile("^alb_worker_active_requests\\{[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern HTTP_REQUESTS_TOTAL_PATTERN =
            Pattern.compile("http_server_requests_seconds_count\\{[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern HTTP_5XX_TOTAL_PATTERN =
            Pattern.compile("http_server_requests_seconds_count\\{[^}]*status=\"5[0-9]{2}\"[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);
    private static final Pattern HTTP_REQUESTS_SUM_PATTERN =
            Pattern.compile("http_server_requests_seconds_sum\\{[^}]*\\}\\s+([\\d.E+-]+)", Pattern.MULTILINE);

    /** Tracks previous scrape values for rate calculation */
    private final Map<String, Double> previousRequestCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Double> previous5xxCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Double> previousRequestSums = new java.util.concurrent.ConcurrentHashMap<>();

    public MetricPollerService(
            DiscoveryClient discoveryClient,
            MetricCache metricCache,
            InstanceConnectionTracker connectionTracker,
            CompositeScoreCalculator scoreCalculator,
            AdaptiveRoutingConfig config,
            MeterRegistry meterRegistry) {
        this.discoveryClient = discoveryClient;
        this.metricCache = metricCache;
        this.connectionTracker = connectionTracker;
        this.scoreCalculator = scoreCalculator;
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.pollCycleTimer = Timer.builder("alb_gateway_metric_poll_duration_seconds")
                .description("Time taken for a complete metric poll cycle across all workers")
                .register(meterRegistry);
    }

    /**
     * Scheduled poll cycle that discovers all registered worker instances and
     * asynchronously scrapes their /actuator/prometheus endpoints.
     */
    @Scheduled(fixedDelay = 100)
    public void pollMetrics() {
        long now = System.currentTimeMillis();
        long previous = lastPollStartedAtMs.get();
        if (now - previous < config.getPollerIntervalMs()
                || !lastPollStartedAtMs.compareAndSet(previous, now)) {
            return;
        }
        long cycleStart = System.nanoTime();

        List<ServiceInstance> workers = discoveryClient.getInstances("DEMO-SERVICE");
        if (workers == null || workers.isEmpty()) {
            log.trace("No DEMO-SERVICE instances registered — skipping poll cycle");
            return;
        }

        Flux.fromIterable(workers)
                .flatMap(this::scrapeAndUpdate)
                .doOnComplete(() -> {
                    publishSoftmaxProbabilities();
                    pollCycleTimer.record(System.nanoTime() - cycleStart, TimeUnit.NANOSECONDS);
                    log.trace("Poll cycle completed for {} instances", workers.size());
                })
                .subscribe();
    }

    /**
     * Scrapes a single worker instance's Prometheus endpoint and updates the cache.
     */
    private Mono<Void> scrapeAndUpdate(ServiceInstance instance) {
        String instanceId = instance.getInstanceId() != null
                ? instance.getInstanceId()
                : instance.getUri().toString();
        String url = String.format("http://%s:%d/actuator/prometheus", instance.getHost(), instance.getPort());

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.getPollerTimeoutMs()))
                .map(body -> parsePrometheusMetrics(instanceId, body))
                .doOnNext(snapshot -> {
                    // Apply EMA smoothing against previous values
                    InstanceMetricsSnapshot smoothed = applySmoothing(instanceId, snapshot);

                    // Calculate composite score and classify health tier
                    double score = scoreCalculator.calculateScore(smoothed);
                    smoothed.setCompositeScore(score);
                    smoothed.setStatus(scoreCalculator.classifyHealthTier(smoothed));

                    // Manage quarantine
                    if (smoothed.getStatus() == InstanceMetricsSnapshot.InstanceStatus.UNHEALTHY
                            && !metricCache.isQuarantined(instanceId)) {
                        metricCache.quarantine(instanceId, config.getQuarantineDurationSeconds());
                    }

                    // Update cache
                    metricCache.put(instanceId, smoothed);
                    metricCache.recordSuccessfulPoll(instanceId);
                    registerInstanceGauges(instanceId);

                    // Also sync weight and connection info into the connection tracker
                    connectionTracker.updateTelemetry(instanceId, smoothed);

                    log.debug("Updated metrics for [{}] — score:{} status:{} cpu:{} mem:{} lat:{}ms conns:{} err:{}",
                            instanceId,
                            String.format("%.3f", smoothed.getCompositeScore()),
                            smoothed.getStatus(),
                            String.format("%.3f", smoothed.getCpuUsage()),
                            String.format("%.3f", smoothed.getMemoryUsageRatio()),
                            String.format("%.1f", smoothed.getLatencyEmaMs()),
                            smoothed.getActiveConnections(),
                            String.format("%.4f", smoothed.getErrorRate()));
                })
                .doOnError(err -> {
                    int failures = metricCache.recordPollFailure(instanceId);
                    if (failures >= 1 && !metricCache.isQuarantined(instanceId)) {
                        metricCache.quarantine(instanceId, config.getQuarantineDurationSeconds());
                    }
                    log.warn("Failed to scrape metrics from [{}] at {} (consecutive failures={}): {}",
                            instanceId, url, failures, err.getMessage());
                })
                .onErrorResume(err -> Mono.empty())
                .then();
    }

    /**
     * Parses raw Prometheus text exposition format into an InstanceMetricsSnapshot.
     */
    private InstanceMetricsSnapshot parsePrometheusMetrics(String instanceId, String body) {
        double cpuUsage = extractFirstDouble(SYSTEM_CPU_PATTERN, body);
        if (cpuUsage < 0) {
            cpuUsage = extractFirstDouble(PROCESS_CPU_PATTERN, body);
        }
        if (cpuUsage < 0) cpuUsage = 0.0;

        double memUsed = extractSumDoubles(JVM_MEMORY_USED_PATTERN, body);
        double memMax = extractFirstDouble(JVM_MEMORY_MAX_PATTERN, body);
        double memRatio = (memMax > 0) ? Math.min(1.0, memUsed / memMax) : 0.0;

        int activeRequests = (int) extractFirstDouble(ACTIVE_REQUESTS_PATTERN, body);
        if (activeRequests < 0) activeRequests = 0;

        // Calculate error rate from delta counters
        double totalRequests = extractSumDoubles(HTTP_REQUESTS_TOTAL_PATTERN, body);
        double total5xx = extractSumDoubles(HTTP_5XX_TOTAL_PATTERN, body);

        double prevTotal = previousRequestCounts.getOrDefault(instanceId, 0.0);
        double prev5xx = previous5xxCounts.getOrDefault(instanceId, 0.0);
        previousRequestCounts.put(instanceId, totalRequests);
        previous5xxCounts.put(instanceId, total5xx);

        double deltaTotal = totalRequests - prevTotal;
        double delta5xx = total5xx - prev5xx;
        double errorRate = (deltaTotal > 0) ? Math.max(0.0, delta5xx / deltaTotal) : 0.0;

        // Calculate average latency from delta counters
        double totalSum = extractSumDoubles(HTTP_REQUESTS_SUM_PATTERN, body);
        double prevSum = previousRequestSums.getOrDefault(instanceId, 0.0);
        previousRequestSums.put(instanceId, totalSum);

        double deltaSum = totalSum - prevSum;
        double avgLatencyMs = (deltaTotal > 0) ? Math.max(0.0, (deltaSum / deltaTotal) * 1000.0) : 0.0;

        // Also get the gateway-tracked connection count
        int gatewayConns = connectionTracker.getActiveConnections(instanceId);
        int effectiveConns = Math.max(activeRequests, gatewayConns);

        return InstanceMetricsSnapshot.builder()
                .instanceId(instanceId)
                .cpuUsage(cpuUsage)
                .memoryUsageRatio(memRatio)
                .latencyEmaMs(avgLatencyMs)
                .activeConnections(effectiveConns)
                .errorRate(errorRate)
                .weight(connectionTracker.getInstanceWeight(instanceId))
                .status(InstanceMetricsSnapshot.InstanceStatus.HEALTHY)
                .lastUpdatedTimestampMs(System.currentTimeMillis())
                .build();
    }

    /**
     * Applies EMA smoothing to a new snapshot against the previously cached values.
     */
    private InstanceMetricsSnapshot applySmoothing(String instanceId, InstanceMetricsSnapshot raw) {
        double alpha = config.getEmaSmoothingFactor();

        return metricCache.get(instanceId)
                .map(prev -> InstanceMetricsSnapshot.builder()
                        .instanceId(instanceId)
                        .uri(raw.getUri())
                        .weight(raw.getWeight())
                        .activeConnections(raw.getActiveConnections())
                        .cpuUsage(MetricNormalizer.applyEma(raw.getCpuUsage(), prev.getCpuUsage(), alpha))
                        .memoryUsageRatio(MetricNormalizer.applyEma(raw.getMemoryUsageRatio(), prev.getMemoryUsageRatio(), alpha))
                        .latencyEmaMs(MetricNormalizer.applyEma(raw.getLatencyEmaMs(), prev.getLatencyEmaMs(), alpha))
                        .errorRate(MetricNormalizer.applyEma(raw.getErrorRate(), prev.getErrorRate(), alpha))
                        .status(raw.getStatus())
                        .lastUpdatedTimestampMs(raw.getLastUpdatedTimestampMs())
                        .build())
                .orElse(raw);
    }

    private void publishSoftmaxProbabilities() {
        List<InstanceMetricsSnapshot> eligible = metricCache.getAll().entrySet().stream()
                .filter(entry -> !metricCache.isQuarantined(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (eligible.isEmpty()) {
            return;
        }

        double temperature = config.getSoftmaxTemperature();
        double maxScaledScore = eligible.stream()
                .mapToDouble(snapshot -> snapshot.getCompositeScore() / temperature)
                .max()
                .orElse(0.0);
        double denominator = eligible.stream()
                .mapToDouble(snapshot -> Math.exp(snapshot.getCompositeScore() / temperature - maxScaledScore))
                .sum();

        for (InstanceMetricsSnapshot snapshot : metricCache.getAll().values()) {
            snapshot.setRoutingProbability(0.0);
        }
        for (InstanceMetricsSnapshot snapshot : eligible) {
            double probability = Math.exp(snapshot.getCompositeScore() / temperature - maxScaledScore) / denominator;
            snapshot.setRoutingProbability(probability);
        }
    }

    private void registerInstanceGauges(String instanceId) {
        if (!registeredMetricInstances.add(instanceId)) {
            return;
        }
        Gauge.builder("alb_gateway_instance_health_score", metricCache,
                        cache -> cache.get(instanceId).map(InstanceMetricsSnapshot::getCompositeScore).orElse(0.0))
                .description("Computed composite health score for a worker instance")
                .tag("instance_id", instanceId)
                .register(meterRegistry);
        Gauge.builder("alb_gateway_instance_probability", metricCache,
                        cache -> cache.get(instanceId).map(InstanceMetricsSnapshot::getRoutingProbability).orElse(0.0))
                .description("Softmax routing probability for a worker instance")
                .tag("instance_id", instanceId)
                .register(meterRegistry);
        Gauge.builder("alb_gateway_cache_staleness_ms", metricCache,
                        cache -> (double) cache.getStalenessMs(instanceId))
                .description("Age of the telemetry snapshot used for routing")
                .tag("instance_id", instanceId)
                .register(meterRegistry);
    }

    /**
     * Extracts the first matching double value from Prometheus text format.
     */
    private double extractFirstDouble(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1.0;
            }
        }
        return -1.0;
    }

    /**
     * Extracts and sums all matching double values (for multi-label metrics).
     */
    private double extractSumDoubles(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        double sum = 0;
        boolean found = false;
        while (matcher.find()) {
            try {
                sum += Double.parseDouble(matcher.group(1));
                found = true;
            } catch (NumberFormatException ignored) {
            }
        }
        return found ? sum : -1.0;
    }
}
