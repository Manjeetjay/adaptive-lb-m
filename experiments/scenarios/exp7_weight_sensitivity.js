import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 7: Scoring Weight Sensitivity Analysis
 *
 * Workload: Constant 500 req/s load with mixed CPU and latency stress.
 * Gateway Configuration: Orchestrator sets weight profiles:
 *   - Latency-Heavy (0.60 lat)
 *   - CPU-Heavy (0.60 cpu)
 *   - Balanced (Default: 0.35 lat, 0.25 cpu, 0.20 err, 0.10 conn, 0.10 mem)
 *   - Error-Focused (0.60 err)
 * Target: /api/v1/compute
 * Objective: Evaluate robustness and performance shifts under distinct telemetry objective priorities.
 */
const duration = __ENV.DURATION || (IS_QUICK ? '15s' : '5m');
const rate = parseInt(__ENV.RATE || (IS_QUICK ? '60' : '500'), 10);

export const options = {
  scenarios: {
    weight_sensitivity: {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: duration,
      preAllocatedVUs: IS_QUICK ? 15 : 80,
      maxVUs: IS_QUICK ? 60 : 300,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<600'],
  },
};

export default function () {
  sendComputeRequest();
}
