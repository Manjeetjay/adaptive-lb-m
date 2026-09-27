package com.alb.worker.chaos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatencyRequest {
    @Builder.Default
    private int delayMs = 350;

    @Builder.Default
    private int jitterMs = 50;

    @Builder.Default
    private double probability = 1.0;

    @Builder.Default
    private int durationSeconds = 60;
}
