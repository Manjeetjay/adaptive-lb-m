package com.alb.worker.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerMetricsServiceTests {

    private MeterRegistry meterRegistry;
    private WorkerMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new WorkerMetricsService(meterRegistry, "worker-test:8081");
    }

    @Test
    void testGaugesRegisteredInMeterRegistry() {
        Gauge activeGauge = meterRegistry.find("alb_worker_active_requests").gauge();
        assertNotNull(activeGauge, "alb_worker_active_requests gauge must be registered");
        assertEquals(0.0, activeGauge.value());

        Gauge cpuGauge = meterRegistry.find("alb_worker_cpu_usage").gauge();
        assertNotNull(cpuGauge, "alb_worker_cpu_usage gauge must be registered");
        assertTrue(cpuGauge.value() >= 0.0 && cpuGauge.value() <= 1.0);

        Gauge memGauge = meterRegistry.find("alb_worker_memory_ratio").gauge();
        assertNotNull(memGauge, "alb_worker_memory_ratio gauge must be registered");
        assertTrue(memGauge.value() >= 0.0 && memGauge.value() <= 1.0);

        Gauge delayGauge = meterRegistry.find("alb_worker_simulated_delay_ms").gauge();
        assertNotNull(delayGauge, "alb_worker_simulated_delay_ms gauge must be registered");
        assertEquals(0.0, delayGauge.value());
    }

    @Test
    void testActiveRequestsIncrementAndDecrement() {
        assertEquals(0, metricsService.getActiveRequests());

        metricsService.incrementActiveRequests();
        metricsService.incrementActiveRequests();
        assertEquals(2, metricsService.getActiveRequests());

        Gauge activeGauge = meterRegistry.find("alb_worker_active_requests").gauge();
        assertNotNull(activeGauge);
        assertEquals(2.0, activeGauge.value());

        metricsService.decrementActiveRequests();
        assertEquals(1, metricsService.getActiveRequests());
        assertEquals(1.0, activeGauge.value());
    }

    @Test
    void testSimulatedDelayMsTracking() {
        metricsService.setSimulatedDelayMs(250);
        assertEquals(250, metricsService.getSimulatedDelayMs());

        Gauge delayGauge = meterRegistry.find("alb_worker_simulated_delay_ms").gauge();
        assertNotNull(delayGauge);
        assertEquals(250.0, delayGauge.value());
    }

    @Test
    void testTelemetryValuesWithinBounds() {
        double cpu = metricsService.getCpuUsage();
        assertTrue(cpu >= 0.0 && cpu <= 1.0, "CPU usage must be between 0.0 and 1.0");

        double mem = metricsService.getMemoryRatio();
        assertTrue(mem >= 0.0 && mem <= 1.0, "Memory ratio must be between 0.0 and 1.0");

        assertEquals("worker-test:8081", metricsService.getInstanceId());
    }
}
