1. Eureka Server - http://localhost:8761
2. Worker Prometheus metrics - http://localhost:<worker-port>/actuator/prometheus {worker-ports - 8081, 8082, 8083}
3. Gateway Routing Eval Metrics - http://localhost:<gateway-port>/actuator/prometheus {gateway-port - 8080}
4. Prometheus - http://localhost:9090/targets
5. Grafana - http://localhost:3000