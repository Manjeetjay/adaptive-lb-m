package com.alb.worker;

import com.alb.worker.controller.WorkerController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkerControllerTests {

    private WorkerController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkerController(new SimpleMeterRegistry(), "test-worker:8081", 8081);
    }

    @Test
    void testComputeEndpoint() {
        ResponseEntity<Map<String, Object>> response = controller.compute(1000);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("COMPLETED", body.get("status"));
        assertEquals("test-worker:8081", body.get("instanceId"));
        assertEquals(8081, body.get("serverPort"));
        assertTrue((Double) body.get("executionTimeMs") >= 0);
        assertNotNull(body.get("resultHash"));
    }

    @Test
    void testIoWaitEndpoint() {
        ResponseEntity<Map<String, Object>> response = controller.ioWait(10);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("COMPLETED", body.get("status"));
        assertEquals(10, body.get("simulatedDelayMs"));
    }

    @Test
    void testHealthEndpoint() {
        ResponseEntity<Map<String, String>> response = controller.health();
        assertNotNull(response);
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testInfoEndpoint() {
        ResponseEntity<Map<String, Object>> response = controller.info();
        assertNotNull(response);
        assertEquals("test-worker:8081", response.getBody().get("instanceId"));
    }
}
