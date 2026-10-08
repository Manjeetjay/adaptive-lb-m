import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 3: Single-Node Heterogeneous Degradation (T6.3)
 *
 * Workload: Constant 600 req/s steady-state traffic.
 * External Fault Coordination:
 *   - 0s - 120s: Baseline healthy cluster.
 *   - 120s - 480s: Worker 2 degraded (300ms latency, 85% CPU burn).
 *   - 480s - 600s: Fault cleared, observe dynamic recovery.
 * Target: /api/v1/compute
 * Objective: Measure T_adapt, tail latency containment, and traffic shedding from degraded instance.
 */
const duration = __ENV.DURATION || (IS_QUICK ? '15s' : '10m');
const rate = parseInt(__ENV.RATE || (IS_QUICK ? '100' : '600'), 10);

export const options = {
  scenarios: {
    heterogeneous_degradation: {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: duration,
      preAllocatedVUs: IS_QUICK ? 20 : 100,
      maxVUs: IS_QUICK ? 80 : 300,
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
