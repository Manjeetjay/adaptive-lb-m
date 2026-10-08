/**
 * Common configuration and helper functions for k6 load scenarios.
 * Supports running via local k6 or Dockerized k6 (grafana/k6) with host mapping.
 */
import http from 'k6/http';
import { check } from 'k6';

export const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
export const COMPUTE_ITERATIONS = __ENV.ITERATIONS || '200000';
export const IS_QUICK = __ENV.QUICK_MODE === 'true';

/**
 * Execute standard /api/v1/compute request and validate 200 response.
 */
export function sendComputeRequest(customParams) {
  const url = `${GATEWAY_URL}/api/v1/compute?iterations=${COMPUTE_ITERATIONS}`;
  const params = customParams || {
    headers: {
      'Accept': 'application/json',
      'User-Agent': 'k6-alb-benchmark',
    },
    timeout: '10s',
  };

  const res = http.get(url, params);
  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
  });

  return { res, ok };
}
