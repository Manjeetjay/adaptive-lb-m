import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 5: Catastrophic Worker Node Failure (T6.4)
 *
 * Workload: Constant 800 req/s load.
 * External Fault Coordination:
 *   - Container or service failure on Worker 2 injected mid-run.
 * Target: /api/v1/compute
 * Objective: Measure error rate containment, dropped requests, and gateway eviction speed.
 */
const duration = __ENV.DURATION || (IS_QUICK ? '15s' : '5m');
const rate = parseInt(__ENV.RATE || (IS_QUICK ? '80' : '800'), 10);

export const options = {
  scenarios: {
    node_failure: {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: duration,
      preAllocatedVUs: IS_QUICK ? 20 : 120,
      maxVUs: IS_QUICK ? 80 : 400,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<800'],
  },
};

export default function () {
  sendComputeRequest();
}
