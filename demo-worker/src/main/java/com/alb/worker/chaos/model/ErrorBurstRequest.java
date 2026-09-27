package com.alb.worker.chaos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorBurstRequest {
    @Builder.Default
    private double errorRate = 0.50;

    @Builder.Default
    private int durationSeconds = 30;
}
