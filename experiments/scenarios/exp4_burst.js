import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 4: Sudden Traffic Burst (T6.4)
 *
 * Workload:
 *   - Baseline: 100 req/s (60s)
 *   - Instantaneous surge: 3500 req/s (30s)
 *   - Cooldown: 100 req/s (60s)
 * Target: /api/v1/compute
 * Objective: Verify rapid queue rebalancing and avoidance of cascading saturation.
 */
const baselineRate = parseInt(__ENV.BASELINE_RATE || (IS_QUICK ? '50' : '100'), 10);
const burstRate = parseInt(__ENV.BURST_RATE || (IS_QUICK ? '500' : '3500'), 10);
const baseDuration = __ENV.BASE_DURATION || (IS_QUICK ? '5s' : '60s');
const burstDuration = __ENV.BURST_DURATION || (IS_QUICK ? '5s' : '30s');

export const options = {
  scenarios: {
    traffic_burst: {
      executor: 'ramping-arrival-rate',
      startRate: baselineRate,
      timeUnit: '1s',
      preAllocatedVUs: IS_QUICK ? 20 : 150,
      maxVUs: IS_QUICK ? 100 : 700,
      stages: [
        { target: baselineRate, duration: baseDuration },
        { target: burstRate, duration: '2s' },
        { target: burstRate, duration: burstDuration },
        { target: baselineRate, duration: '2s' },
        { target: baselineRate, duration: baseDuration },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.15'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  sendComputeRequest();
}
