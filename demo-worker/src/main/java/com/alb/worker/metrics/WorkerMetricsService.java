package com.alb.worker.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for managing and exposing real-time worker microservice telemetry
 * to Micrometer and Prometheus scrape endpoints.
 */
@Service
public class WorkerMetricsService {

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong simulatedDelayMs = new AtomicLong(0);

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final String instanceId;

    public WorkerMetricsService(
            MeterRegistry meterRegistry,
            @Value("${eureka.instance.instance-id:${spring.application.name}:${server.port:8081}}") String instanceId) {
        this.instanceId = instanceId;
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();

        // 1. Active in-flight requests gauge
        Gauge.builder("alb_worker_active_requests", this, WorkerMetricsService::getActiveRequests)
                .description("Number of concurrent active HTTP requests in-flight on this worker instance")
                .tag("instance_id", instanceId)
                .register(meterRegistry);

        // 2. Worker CPU usage ratio gauge [0.0, 1.0]
        Gauge.builder("alb_worker_cpu_usage", this, WorkerMetricsService::getCpuUsage)
                .description("Current CPU utilization ratio of this worker process [0.0, 1.0]")
                .tag("instance_id", instanceId)
                .register(meterRegistry);

        // 3. Worker JVM Heap memory usage ratio gauge [0.0, 1.0]
        Gauge.builder("alb_worker_memory_ratio", this, WorkerMetricsService::getMemoryRatio)
                .description("Current JVM heap memory utilization ratio [0.0, 1.0]")
                .tag("instance_id", instanceId)
                .register(meterRegistry);

        // 4. Simulated artificial delay gauge (ms)
        Gauge.builder("alb_worker_simulated_delay_ms", this, WorkerMetricsService::getSimulatedDelayMs)
                .description("Active artificial latency injected via chaos endpoints in milliseconds")
                .tag("instance_id", instanceId)
                .register(meterRegistry);
    }

    public int incrementActiveRequests() {
        return activeRequests.incrementAndGet();
    }

    public int decrementActiveRequests() {
        return activeRequests.updateAndGet(current -> Math.max(0, current - 1));
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public void setSimulatedDelayMs(long delayMs) {
        simulatedDelayMs.set(Math.max(0, delayMs));
    }

    public long getSimulatedDelayMs() {
        return simulatedDelayMs.get();
    }

    /**
     * Returns process/system CPU usage ratio in range [0.0, 1.0].
     */
    public double getCpuUsage() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double load = sunOsBean.getProcessCpuLoad();
                if (!Double.isNaN(load) && load >= 0.0) {
                    return Math.min(1.0, Math.max(0.0, Math.round(load * 1000.0) / 1000.0));
                }
                double systemLoad = sunOsBean.getCpuLoad();
                if (!Double.isNaN(systemLoad) && systemLoad >= 0.0) {
                    return Math.min(1.0, Math.max(0.0, Math.round(systemLoad * 1000.0) / 1000.0));
                }
            }
            double systemLoadAvg = osBean.getSystemLoadAverage();
            int availableProcessors = osBean.getAvailableProcessors();
            if (!Double.isNaN(systemLoadAvg) && systemLoadAvg >= 0.0 && availableProcessors > 0) {
                return Math.min(1.0, Math.max(0.0, Math.round((systemLoadAvg / availableProcessors) * 1000.0) / 1000.0));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    /**
     * Returns current JVM heap memory utilization ratio in range [0.0, 1.0].
     */
    public double getMemoryRatio() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long max = heapUsage.getMax();
            if (max <= 0) {
                max = heapUsage.getCommitted();
            }
            if (max > 0) {
                double ratio = (double) heapUsage.getUsed() / max;
                if (!Double.isNaN(ratio)) {
                    return Math.min(1.0, Math.max(0.0, Math.round(ratio * 1000.0) / 1000.0));
                }
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
