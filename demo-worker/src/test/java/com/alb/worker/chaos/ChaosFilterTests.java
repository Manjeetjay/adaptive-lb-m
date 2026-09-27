package com.alb.worker.chaos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ChaosFilterTests {

    private ChaosService chaosService;
    private ChaosFilter chaosFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        chaosService = Mockito.mock(ChaosService.class);
        chaosFilter = new ChaosFilter(chaosService);
        filterChain = Mockito.mock(FilterChain.class);
    }

    @Test
    void exemptPathsBypassChaosEvenWhenFaultsActive() throws ServletException, IOException {
        when(chaosService.shouldInjectError()).thenReturn(true);
        when(chaosService.shouldInjectLatency()).thenReturn(true);

        // Check /actuator/prometheus
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        chaosFilter.doFilterInternal(req1, res1, filterChain);
        verify(filterChain, times(1)).doFilter(req1, res1);
        assertEquals(200, res1.getStatus());

        // Check /chaos/reset
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/chaos/reset");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        chaosFilter.doFilterInternal(req2, res2, filterChain);
        verify(filterChain, times(1)).doFilter(req2, res2);
        assertEquals(200, res2.getStatus());
    }

    @Test
    void injectsHttp500OnBusinessEndpoint() throws ServletException, IOException {
        when(chaosService.shouldInjectError()).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/compute");
        MockHttpServletResponse response = new MockHttpServletResponse();

        chaosFilter.doFilterInternal(request, response, filterChain);

        assertEquals(500, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void passesCleanTrafficWhenNoFaultsActive() throws ServletException, IOException {
        when(chaosService.shouldInjectError()).thenReturn(false);
        when(chaosService.shouldInjectLatency()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/compute");
        MockHttpServletResponse response = new MockHttpServletResponse();

        chaosFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
