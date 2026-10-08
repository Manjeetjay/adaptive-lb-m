import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 2: Escalating Scalability & Saturation Curve (T6.2)
 *
 * Workload: Stepped rate progression from 100 req/s up to 3000 req/s.
 * Target: /api/v1/compute
 * Objective: Identify saturation ceiling and queue breakdown point for each algorithm.
 */
const stageTime = __ENV.STAGE_DURATION || (IS_QUICK ? '3s' : '2m');

export const options = {
  scenarios: {
    scalability_ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: IS_QUICK ? 20 : 100,
      maxVUs: IS_QUICK ? 100 : 500,
      stages: [
        { target: 100, duration: stageTime },
        { target: 500, duration: stageTime },
        { target: 1000, duration: stageTime },
        { target: 2000, duration: stageTime },
        { target: 3000, duration: stageTime },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  sendComputeRequest();
}
