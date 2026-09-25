package com.alb.registry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRegistryApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Eureka server Spring context initializes with no bean creation issues
    }
}
