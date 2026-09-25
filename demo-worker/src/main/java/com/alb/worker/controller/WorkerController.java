package com.alb.worker.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker business workload controller providing CPU-bound and I/O-bound simulation endpoints.
 */
@RestController
@RequestMapping("/api/v1")
public class WorkerController {

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final String instanceId;
    private final int serverPort;

    public WorkerController(
            MeterRegistry meterRegistry,
            @Value("${eureka.instance.instance-id:${spring.application.name}:${server.port}}") String instanceId,
            @Value("${server.port:8081}") int serverPort) {
        this.instanceId = instanceId;
        this.serverPort = serverPort;

        // Register custom gauge in Prometheus meter registry
        Gauge.builder("alb_worker_active_requests", activeRequests, AtomicInteger::get)
                .description("Number of concurrent active HTTP requests in-flight on this worker instance")
                .tag("instance_id", instanceId)
                .register(meterRegistry);
    }

    /**
     * Executes CPU-intensive mathematical work (SHA-256 hashing iterations).
     */
    @GetMapping("/compute")
    public ResponseEntity<Map<String, Object>> compute(
            @RequestParam(defaultValue = "100000") int iterations) {
        activeRequests.incrementAndGet();
        long startTime = System.nanoTime();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = instanceId.getBytes();

            // Run hashing iterations to burn CPU predictably
            for (int i = 0; i < iterations; i++) {
                digest.update((byte) (i & 0xFF));
                hash = digest.digest(hash);
            }

            double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;
            String resultHash = HexFormat.of().formatHex(hash);

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "instanceId", instanceId,
                    "serverPort", serverPort,
                    "iterations", iterations,
                    "executionTimeMs", Math.round(elapsedMs * 100.0) / 100.0,
                    "resultHash", resultHash
            ));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    /**
     * Simulates downstream I/O latency (database, cache, or 3rd-party microservice call).
     */
    @GetMapping("/io-wait")
    public ResponseEntity<Map<String, Object>> ioWait(
            @RequestParam(defaultValue = "50") int delayMs) {
        activeRequests.incrementAndGet();
        long startTime = System.nanoTime();
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "instanceId", instanceId,
                    "serverPort", serverPort,
                    "simulatedDelayMs", delayMs,
                    "executionTimeMs", Math.round(elapsedMs * 100.0) / 100.0
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("error", "Interrupted during simulated IO"));
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    /**
     * Returns worker metadata and diagnostic information.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
                "instanceId", instanceId,
                "serverPort", serverPort,
                "hostname", host,
                "jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime(),
                "activeRequests", activeRequests.get()
        ));
    }

    /**
     * Fast health probe.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "instanceId", instanceId
        ));
    }
}
