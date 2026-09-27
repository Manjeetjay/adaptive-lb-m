package com.alb.worker.chaos;

import com.alb.worker.chaos.model.CpuBurnRequest;
import com.alb.worker.chaos.model.ErrorBurstRequest;
import com.alb.worker.chaos.model.LatencyRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing programmable chaos engineering and fault injection hooks.
 * Supports both JSON request bodies and URL query parameters for script/curl compatibility.
 *
 * Implements Sprint 5 API contracts: docs/04-service-contracts-and-apis.md §5
 */
@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private final ChaosService chaosService;
    private final String instanceId;

    public ChaosController(
            ChaosService chaosService,
            @Value("${eureka.instance.instance-id:${spring.application.name}:${server.port:8081}}") String instanceId) {
        this.chaosService = chaosService;
        this.instanceId = instanceId;
    }

    /**
     * T5.1: Saturates worker CPU utilizing multi-threaded busy-loops.
     * POST /chaos/cpu-burn
     */
    @PostMapping("/cpu-burn")
    public ResponseEntity<Map<String, Object>> cpuBurn(
        @RequestBody(required = false) CpuBurnRequest body,
        @RequestParam(name = "threads", required = false) Integer threads,
        @RequestParam(name = "targetCpuPercent", required = false) Integer targetCpuPercent,
        @RequestParam(name = "durationSeconds", required = false) Integer durationSeconds) {

    CpuBurnRequest request = body != null ? body : new CpuBurnRequest();
    if (threads != null) request.setThreads(threads);
    if (targetCpuPercent != null) request.setTargetCpuPercent(targetCpuPercent);
    if (durationSeconds != null) request.setDurationSeconds(durationSeconds);

    chaosService.startCpuBurn(request);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "APPLIED");
    response.put("fault", "CPU_BURN");
    response.put("instanceId", instanceId);
    response.put("threads", request.getThreads());
    response.put("targetCpuPercent", request.getTargetCpuPercent());
    response.put("durationSeconds", request.getDurationSeconds());

    return ResponseEntity.ok(response);
}

    /**
     * T5.2: Injects artificial delay with Gaussian jitter.
     * POST /chaos/latency
     */
    @PostMapping("/latency")
    public ResponseEntity<Map<String, Object>> latency(
        @RequestBody(required = false) LatencyRequest body,
        @RequestParam(name = "delayMs", required = false) Integer delayMs,
        @RequestParam(name = "jitterMs", required = false) Integer jitterMs,
        @RequestParam(name = "probability", required = false) Double probability,
        @RequestParam(name = "durationSeconds", required = false) Integer durationSeconds) {

    LatencyRequest request = body != null ? body : new LatencyRequest();
    if (delayMs != null) request.setDelayMs(delayMs);
    if (jitterMs != null) request.setJitterMs(jitterMs);
    if (probability != null) request.setProbability(probability);
    if (durationSeconds != null) request.setDurationSeconds(durationSeconds);

    chaosService.startLatency(request);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "APPLIED");
    response.put("fault", "LATENCY");
    response.put("instanceId", instanceId);
    response.put("delayMs", request.getDelayMs());
    response.put("jitterMs", request.getJitterMs());
    response.put("probability", request.getProbability());
    response.put("durationSeconds", request.getDurationSeconds());

    return ResponseEntity.ok(response);
}

    /**
     * T5.3: Simulates transient internal server errors (HTTP 500).
     * POST /chaos/error-burst
     */
    @PostMapping("/error-burst")
    public ResponseEntity<Map<String, Object>> errorBurst(
        @RequestBody(required = false) ErrorBurstRequest body,
        @RequestParam(name = "errorRate", required = false) Double errorRate,
        @RequestParam(name = "rate", required = false) Double rate,
        @RequestParam(name = "durationSeconds", required = false) Integer durationSeconds) {

    ErrorBurstRequest request = body != null ? body : new ErrorBurstRequest();
    if (errorRate != null) request.setErrorRate(errorRate);
    else if (rate != null) request.setErrorRate(rate);
    if (durationSeconds != null) request.setDurationSeconds(durationSeconds);

    chaosService.startErrorBurst(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "APPLIED");
        response.put("fault", "ERROR_BURST");
        response.put("instanceId", instanceId);
        response.put("errorRate", request.getErrorRate());
        response.put("durationSeconds", request.getDurationSeconds());

        return ResponseEntity.ok(response);
    }

    /**
     * T5.4: Instantly clears all active chaos simulations.
     * POST /chaos/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        Map<String, Object> status = chaosService.reset();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "RESET");
        response.put("instanceId", instanceId);
        response.put("chaosStatus", status);

        return ResponseEntity.ok(response);
    }

    /**
     * Status check endpoint reporting active chaos state and remaining durations.
     * GET /chaos/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = chaosService.getStatus();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("instanceId", instanceId);
        response.put("chaos", status);

        return ResponseEntity.ok(response);
    }
}
