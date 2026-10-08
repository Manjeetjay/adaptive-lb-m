import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 1: Baseline Steady-State Workload (T6.1)
 *
 * Workload: Constant 150 req/s for 10 minutes (or quick mode duration).
 * Target: /api/v1/compute
 * Objective: Quantify baseline routing algorithm overhead under homogeneous healthy node conditions.
 */
const duration = __ENV.DURATION || (IS_QUICK ? '10s' : '10m');
const rate = parseInt(__ENV.RATE || (IS_QUICK ? '50' : '150'), 10);

export const options = {
  scenarios: {
    baseline_steady_state: {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: duration,
      preAllocatedVUs: IS_QUICK ? 10 : 30,
      maxVUs: IS_QUICK ? 50 : 150,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<300'],
  },
};

export default function () {
  sendComputeRequest();
}
