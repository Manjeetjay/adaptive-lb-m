package com.alb.worker.chaos;

import com.alb.worker.chaos.model.CpuBurnRequest;
import com.alb.worker.chaos.model.ErrorBurstRequest;
import com.alb.worker.chaos.model.LatencyRequest;
import com.alb.worker.metrics.WorkerMetricsService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core engine for application-level chaos and fault injection.
 * Manages CPU busy-loop burning, artificial latency with Gaussian jitter,
 * and controlled HTTP 500 error bursts.
 *
 * Implements Sprint 5 specifications: docs/06-chaos-and-fault-injection.md §3
 */
@Slf4j
@Service
public class ChaosService {

    private final WorkerMetricsService metricsService;

    // CPU Burn state
    private final AtomicBoolean cpuBurnActive = new AtomicBoolean(false);
    private final List<Thread> burnThreads = new CopyOnWriteArrayList<>();
    private volatile int activeBurnThreads = 0;
    private volatile int targetCpuPercent = 0;
    private volatile long cpuBurnExpiresAtMs = 0;
    private final AtomicReference<ScheduledFuture<?>> cpuBurnFuture = new AtomicReference<>();

    // Latency injection state
    private volatile int latencyDelayMs = 0;
    private volatile int latencyJitterMs = 0;
    private volatile double latencyProbability = 0.0;
    private volatile long latencyExpiresAtMs = 0;
    private final AtomicReference<ScheduledFuture<?>> latencyFuture = new AtomicReference<>();

    // HTTP 500 Error burst state
    private volatile double errorRate = 0.0;
    private volatile long errorBurstExpiresAtMs = 0;
    private final AtomicReference<ScheduledFuture<?>> errorBurstFuture = new AtomicReference<>();

    // Scheduler for fault auto-expiration
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "alb-chaos-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public ChaosService(WorkerMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. CPU Burn Hook (T5.1)
    // ──────────────────────────────────────────────────────────────────────────

    public synchronized void startCpuBurn(CpuBurnRequest request) {
        stopCpuBurn();

        int threads = request.getThreads() > 0
                ? request.getThreads()
                : Math.max(2, Runtime.getRuntime().availableProcessors());
        int targetPercent = Math.min(100, Math.max(10, request.getTargetCpuPercent()));
        int durationSeconds = Math.max(1, request.getDurationSeconds());

        this.activeBurnThreads = threads;
        this.targetCpuPercent = targetPercent;
        this.cpuBurnExpiresAtMs = System.currentTimeMillis() + (durationSeconds * 1000L);
        this.cpuBurnActive.set(true);

        log.info("Starting CPU Burn: {} threads, target {}% load, duration {}s",
                threads, targetPercent, durationSeconds);

        for (int i = 0; i < threads; i++) {
            final int threadIdx = i;
            Thread thread = new Thread(() -> runBurnLoop(threadIdx, targetPercent), "chaos-cpu-burner-" + threadIdx);
            thread.setDaemon(true);
            burnThreads.add(thread);
            thread.start();
        }

        ScheduledFuture<?> future = scheduler.schedule(this::stopCpuBurn, durationSeconds, TimeUnit.SECONDS);
        ScheduledFuture<?> old = cpuBurnFuture.getAndSet(future);
        if (old != null) {
            old.cancel(false);
        }
    }

    private void runBurnLoop(int threadIdx, int targetPercent) {
        final long cycleMs = 100;
        final long busyMs = Math.round(cycleMs * (targetPercent / 100.0));
        final long sleepMs = cycleMs - busyMs;

        while (cpuBurnActive.get() && System.currentTimeMillis() < cpuBurnExpiresAtMs) {
            long cycleStart = System.currentTimeMillis();
            // Busy loop to consume ALU cycles
            while (System.currentTimeMillis() - cycleStart < busyMs && cpuBurnActive.get()) {
                // Non-optimizable arithmetic calculation
                Math.sin(Math.random() * Math.PI);
            }
            if (sleepMs > 0 && cpuBurnActive.get()) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.debug("CPU Burn thread [{}] exited", threadIdx);
    }

    public synchronized void stopCpuBurn() {
        if (!cpuBurnActive.getAndSet(false) && burnThreads.isEmpty()) {
            return;
        }
        for (Thread thread : burnThreads) {
            try {
                thread.interrupt();
            } catch (Exception ignored) {}
        }
        burnThreads.clear();
        this.activeBurnThreads = 0;
        this.targetCpuPercent = 0;
        this.cpuBurnExpiresAtMs = 0;

        ScheduledFuture<?> future = cpuBurnFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        log.info("CPU Burn stopped");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Latency Injection (T5.2)
    // ──────────────────────────────────────────────────────────────────────────

    public synchronized void startLatency(LatencyRequest request) {
        int delay = Math.max(0, request.getDelayMs());
        int jitter = Math.max(0, request.getJitterMs());
        double probability = Math.min(1.0, Math.max(0.0, request.getProbability()));
        int durationSeconds = Math.max(1, request.getDurationSeconds());

        this.latencyDelayMs = delay;
        this.latencyJitterMs = jitter;
        this.latencyProbability = probability;
        this.latencyExpiresAtMs = System.currentTimeMillis() + (durationSeconds * 1000L);

        metricsService.setSimulatedDelayMs(delay);

        log.info("Starting Latency Injection: baseDelay={}ms, jitter={}ms, prob={}, duration={}s",
                delay, jitter, probability, durationSeconds);

        ScheduledFuture<?> future = scheduler.schedule(this::stopLatency, durationSeconds, TimeUnit.SECONDS);
        ScheduledFuture<?> old = latencyFuture.getAndSet(future);
        if (old != null) {
            old.cancel(false);
        }
    }

    public synchronized void stopLatency() {
        this.latencyDelayMs = 0;
        this.latencyJitterMs = 0;
        this.latencyProbability = 0.0;
        this.latencyExpiresAtMs = 0;
        metricsService.setSimulatedDelayMs(0);

        ScheduledFuture<?> future = latencyFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        log.info("Latency injection stopped");
    }

    public boolean shouldInjectLatency() {
        if (latencyProbability <= 0.0 || System.currentTimeMillis() >= latencyExpiresAtMs) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < latencyProbability;
    }

    public int calculateLatencyDelay() {
        int base = latencyDelayMs;
        if (base <= 0) {
            return 0;
        }
        if (latencyJitterMs > 0) {
            // Gaussian jitter around 0 with standard deviation = jitterMs
            double gaussian = ThreadLocalRandom.current().nextGaussian();
            int jitter = (int) Math.round(gaussian * latencyJitterMs);
            return Math.max(0, base + jitter);
        }
        return base;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Error Burst (HTTP 500) (T5.3)
    // ──────────────────────────────────────────────────────────────────────────

    public synchronized void startErrorBurst(ErrorBurstRequest request) {
        double rate = Math.min(1.0, Math.max(0.0, request.getErrorRate()));
        int durationSeconds = Math.max(1, request.getDurationSeconds());

        this.errorRate = rate;
        this.errorBurstExpiresAtMs = System.currentTimeMillis() + (durationSeconds * 1000L);

        log.info("Starting Error Burst: errorRate={}, duration={}s", rate, durationSeconds);

        ScheduledFuture<?> future = scheduler.schedule(this::stopErrorBurst, durationSeconds, TimeUnit.SECONDS);
        ScheduledFuture<?> old = errorBurstFuture.getAndSet(future);
        if (old != null) {
            old.cancel(false);
        }
    }

    public synchronized void stopErrorBurst() {
        this.errorRate = 0.0;
        this.errorBurstExpiresAtMs = 0;

        ScheduledFuture<?> future = errorBurstFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
        log.info("Error burst stopped");
    }

    public boolean shouldInjectError() {
        if (errorRate <= 0.0 || System.currentTimeMillis() >= errorBurstExpiresAtMs) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < errorRate;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Reset & Status (T5.4)
    // ──────────────────────────────────────────────────────────────────────────

    public synchronized Map<String, Object> reset() {
        stopCpuBurn();
        stopLatency();
        stopErrorBurst();
        log.info("All chaos faults reset to healthy baseline");
        return getStatus();
    }

    public Map<String, Object> getStatus() {
        long now = System.currentTimeMillis();

        Map<String, Object> cpuStatus = new LinkedHashMap<>();
        boolean isCpuActive = cpuBurnActive.get() && now < cpuBurnExpiresAtMs;
        cpuStatus.put("active", isCpuActive);
        cpuStatus.put("threads", isCpuActive ? activeBurnThreads : 0);
        cpuStatus.put("targetCpuPercent", isCpuActive ? targetCpuPercent : 0);
        cpuStatus.put("remainingSeconds", isCpuActive ? Math.max(0, (cpuBurnExpiresAtMs - now) / 1000) : 0);

        Map<String, Object> latencyStatus = new LinkedHashMap<>();
        boolean isLatencyActive = latencyProbability > 0.0 && now < latencyExpiresAtMs;
        latencyStatus.put("active", isLatencyActive);
        latencyStatus.put("delayMs", isLatencyActive ? latencyDelayMs : 0);
        latencyStatus.put("jitterMs", isLatencyActive ? latencyJitterMs : 0);
        latencyStatus.put("probability", isLatencyActive ? latencyProbability : 0.0);
        latencyStatus.put("remainingSeconds", isLatencyActive ? Math.max(0, (latencyExpiresAtMs - now) / 1000) : 0);

        Map<String, Object> errorStatus = new LinkedHashMap<>();
        boolean isErrorActive = errorRate > 0.0 && now < errorBurstExpiresAtMs;
        errorStatus.put("active", isErrorActive);
        errorStatus.put("errorRate", isErrorActive ? errorRate : 0.0);
        errorStatus.put("remainingSeconds", isErrorActive ? Math.max(0, (errorBurstExpiresAtMs - now) / 1000) : 0);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("cpuBurn", cpuStatus);
        status.put("latency", latencyStatus);
        status.put("errorBurst", errorStatus);
        status.put("healthy", !isCpuActive && !isLatencyActive && !isErrorActive);

        return status;
    }

    @PreDestroy
    public void cleanup() {
        reset();
        scheduler.shutdownNow();
    }
}
