package com.alb.worker.chaos;

import com.alb.worker.chaos.model.CpuBurnRequest;
import com.alb.worker.chaos.model.ErrorBurstRequest;
import com.alb.worker.chaos.model.LatencyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChaosControllerTests {

    private MockMvc mockMvc;
    private ChaosService chaosService;

    @BeforeEach
    void setUp() {
        chaosService = Mockito.mock(ChaosService.class);
        ChaosController controller = new ChaosController(chaosService, "DEMO-SERVICE:test:8081");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCpuBurnEndpointJsonBody() throws Exception {
        mockMvc.perform(post("/chaos/cpu-burn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threads\":2,\"targetCpuPercent\":85,\"durationSeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.fault").value("CPU_BURN"))
                .andExpect(jsonPath("$.threads").value(2))
                .andExpect(jsonPath("$.targetCpuPercent").value(85))
                .andExpect(jsonPath("$.durationSeconds").value(30));

        verify(chaosService).startCpuBurn(any(CpuBurnRequest.class));
    }

    @Test
    void testCpuBurnEndpointQueryParams() throws Exception {
        mockMvc.perform(post("/chaos/cpu-burn")
                        .param("threads", "4")
                        .param("targetCpuPercent", "95")
                        .param("durationSeconds", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.threads").value(4))
                .andExpect(jsonPath("$.targetCpuPercent").value(95));

        verify(chaosService).startCpuBurn(any(CpuBurnRequest.class));
    }

    @Test
    void testLatencyEndpoint() throws Exception {
        mockMvc.perform(post("/chaos/latency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayMs\":300,\"jitterMs\":40,\"probability\":0.8,\"durationSeconds\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.fault").value("LATENCY"))
                .andExpect(jsonPath("$.delayMs").value(300))
                .andExpect(jsonPath("$.jitterMs").value(40))
                .andExpect(jsonPath("$.probability").value(0.8));

        verify(chaosService).startLatency(any(LatencyRequest.class));
    }

    @Test
    void testErrorBurstEndpoint() throws Exception {
        mockMvc.perform(post("/chaos/error-burst")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"errorRate\":0.4,\"durationSeconds\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.fault").value("ERROR_BURST"))
                .andExpect(jsonPath("$.errorRate").value(0.4));

        verify(chaosService).startErrorBurst(any(ErrorBurstRequest.class));
    }

    @Test
    void testResetEndpoint() throws Exception {
        when(chaosService.reset()).thenReturn(Map.of("healthy", true));

        mockMvc.perform(post("/chaos/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET"))
                .andExpect(jsonPath("$.chaosStatus.healthy").value(true));

        verify(chaosService).reset();
    }

    @Test
    void testStatusEndpoint() throws Exception {
        when(chaosService.getStatus()).thenReturn(Map.of(
                "healthy", true,
                "cpuBurn", Map.of("active", false),
                "latency", Map.of("active", false),
                "errorBurst", Map.of("active", false)
        ));

        mockMvc.perform(get("/chaos/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.chaos.healthy").value(true));
    }
}
