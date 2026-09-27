package com.alb.gateway.routing.adaptive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricNormalizerTests {

    @Test
    void normalizesAllMetricsWithinTheirExpectedBounds() {
        assertEquals(1.0, MetricNormalizer.normalizeCpu(0.0));
        assertEquals(0.0, MetricNormalizer.normalizeCpu(2.0));
        assertEquals(0.75, MetricNormalizer.normalizeMemory(0.25));
        assertEquals(1.0, MetricNormalizer.normalizeLatency(20.0, 20.0, 500.0));
        assertEquals(0.5, MetricNormalizer.normalizeLatency(260.0, 20.0, 500.0));
        assertEquals(0.0, MetricNormalizer.normalizeLatency(600.0, 20.0, 500.0));
        assertEquals(0.5, MetricNormalizer.normalizeConnections(100, 200));
        assertEquals(0.0, MetricNormalizer.normalizeErrorRate(1.0));
    }

    @Test
    void appliesExpectedEma() {
        assertEquals(70.0, MetricNormalizer.applyEma(100.0, 50.0, 0.4));
        assertEquals(100.0, MetricNormalizer.applyEma(100.0, -1.0, 0.4));
    }
}
