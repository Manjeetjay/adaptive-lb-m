package com.alb.worker.chaos;

import com.alb.worker.chaos.model.CpuBurnRequest;
import com.alb.worker.chaos.model.ErrorBurstRequest;
import com.alb.worker.chaos.model.LatencyRequest;
import com.alb.worker.metrics.WorkerMetricsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

class ChaosServiceTests {

    private WorkerMetricsService metricsService;
    private ChaosService chaosService;

    @BeforeEach
    void setUp() {
        metricsService = Mockito.mock(WorkerMetricsService.class);
        chaosService = new ChaosService(metricsService);
    }

    @AfterEach
    void tearDown() {
        chaosService.cleanup();
    }

    @Test
    void testCpuBurnLifecycle() {
        CpuBurnRequest request = CpuBurnRequest.builder()
                .threads(2)
                .targetCpuPercent(80)
                .durationSeconds(2)
                .build();

        chaosService.startCpuBurn(request);

        Map<String, Object> status = chaosService.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> cpuStatus = (Map<String, Object>) status.get("cpuBurn");
        assertTrue((Boolean) cpuStatus.get("active"));
        assertEquals(2, cpuStatus.get("threads"));
        assertEquals(80, cpuStatus.get("targetCpuPercent"));
        assertFalse((Boolean) status.get("healthy"));

        chaosService.stopCpuBurn();

        Map<String, Object> statusAfter = chaosService.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> cpuStatusAfter = (Map<String, Object>) statusAfter.get("cpuBurn");
        assertFalse((Boolean) cpuStatusAfter.get("active"));
        assertEquals(0, cpuStatusAfter.get("threads"));
        assertTrue((Boolean) statusAfter.get("healthy"));
    }

    @Test
    void testLatencyInjectionLifecycle() {
        LatencyRequest request = LatencyRequest.builder()
                .delayMs(200)
                .jitterMs(20)
                .probability(1.0)
                .durationSeconds(5)
                .build();

        chaosService.startLatency(request);
        verify(metricsService).setSimulatedDelayMs(200);

        assertTrue(chaosService.shouldInjectLatency());
        int delay = chaosService.calculateLatencyDelay();
        assertTrue(delay >= 100, "Delay with jitter should be near 200ms");

        Map<String, Object> status = chaosService.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> latencyStatus = (Map<String, Object>) status.get("latency");
        assertTrue((Boolean) latencyStatus.get("active"));
        assertEquals(200, latencyStatus.get("delayMs"));
        assertEquals(20, latencyStatus.get("jitterMs"));
        assertEquals(1.0, latencyStatus.get("probability"));

        chaosService.stopLatency();
        verify(metricsService).setSimulatedDelayMs(0);
        assertFalse(chaosService.shouldInjectLatency());
    }

    @Test
    void testErrorBurstLifecycle() {
        ErrorBurstRequest request = ErrorBurstRequest.builder()
                .errorRate(1.0)
                .durationSeconds(5)
                .build();

        chaosService.startErrorBurst(request);
        assertTrue(chaosService.shouldInjectError());

        Map<String, Object> status = chaosService.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, Object> errorStatus = (Map<String, Object>) status.get("errorBurst");
        assertTrue((Boolean) errorStatus.get("active"));
        assertEquals(1.0, errorStatus.get("errorRate"));

        chaosService.stopErrorBurst();
        assertFalse(chaosService.shouldInjectError());
    }

    @Test
    void testResetRestoresHealthyBaseline() {
        chaosService.startCpuBurn(CpuBurnRequest.builder().threads(1).durationSeconds(10).build());
        chaosService.startLatency(LatencyRequest.builder().delayMs(100).probability(1.0).durationSeconds(10).build());
        chaosService.startErrorBurst(ErrorBurstRequest.builder().errorRate(0.5).durationSeconds(10).build());

        assertFalse((Boolean) chaosService.getStatus().get("healthy"));

        Map<String, Object> status = chaosService.reset();

        assertTrue((Boolean) status.get("healthy"));
        assertFalse(chaosService.shouldInjectError());
        assertFalse(chaosService.shouldInjectLatency());
    }
}
