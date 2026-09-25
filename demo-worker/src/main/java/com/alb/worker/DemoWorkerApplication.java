package com.alb.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Worker microservice instance that registers with Eureka as DEMO-SERVICE.
 * Provides computational and I/O simulated endpoints alongside Actuator telemetry.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DemoWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoWorkerApplication.class, args);
    }
}
