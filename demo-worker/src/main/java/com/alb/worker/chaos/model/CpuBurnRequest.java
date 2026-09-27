package com.alb.worker.chaos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuBurnRequest {
    @Builder.Default
    private int threads = 4;

    @Builder.Default
    private int targetCpuPercent = 90;

    @Builder.Default
    private int durationSeconds = 45;
}
