package com.alb.worker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false"
})
class DemoWorkerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies worker microservice Spring context initializes successfully
    }
}
