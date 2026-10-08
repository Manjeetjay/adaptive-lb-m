import { sendComputeRequest, IS_QUICK } from './config.js';

/**
 * Experiment 6: Telemetry Scrape Frequency Trade-Off Analysis
 *
 * Workload: Constant 400 req/s load with mid-run latency fault injection.
 * Gateway Configuration: Orchestrator sets refreshIntervalMs across {100, 250, 500, 1000, 2000, 5000}.
 * Target: /api/v1/compute
 * Objective: Measure T_adapt vs worker scrape overhead as a function of telemetry frequency.
 */
const duration = __ENV.DURATION || (IS_QUICK ? '15s' : '5m');
const rate = parseInt(__ENV.RATE || (IS_QUICK ? '60' : '400'), 10);

export const options = {
  scenarios: {
    scrape_frequency_tradeoff: {
      executor: 'constant-arrival-rate',
      rate: rate,
      timeUnit: '1s',
      duration: duration,
      preAllocatedVUs: IS_QUICK ? 15 : 60,
      maxVUs: IS_QUICK ? 60 : 250,
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
