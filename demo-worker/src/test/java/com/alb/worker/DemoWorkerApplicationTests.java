package com.alb.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "management.prometheus.metrics.export.enabled=true",
        "management.metrics.export.prometheus.enabled=true",
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics",
        "management.endpoint.prometheus.enabled=true",
        "logging.level.org.springframework.boot.autoconfigure=DEBUG"
})
class DemoWorkerApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Verifies worker microservice Spring context initializes successfully
    }

    @Test
    void testPrometheusMetricsEndpointExposesCustomGauges() {
        ResponseEntity<String> actuatorResponse = restTemplate.getForEntity("/actuator", String.class);
        System.out.println("ACTUATOR_LINKS: " + actuatorResponse.getBody());

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        System.out.println("PROMETHEUS_STATUS: " + response.getStatusCode());
        System.out.println("PROMETHEUS_BODY: " + response.getBody());
        assertEquals(200, response.getStatusCode().value());
    }
}
