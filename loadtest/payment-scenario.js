import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import exec from 'k6/execution';

// 결제 요청(ready) -> 승인(confirm) -> 조회(get) 흐름을 100 / 500 / 1000 동시 요청 단계로 재현한다.
// data-postgresql.sql 로 시드된 PENDING 주문 1600건(id 1~1600), 유저 20명을 그대로 소비한다.
// shared-iterations 실행기는 vus <= iterations 여야 하므로, 각 단계는 vus == iterations로
// "N명이 동시에 각 1건씩" 요청하는 것으로 구성한다. 주문은 confirm 시 1회만 결제 완료 처리되므로
// 세 시나리오가 서로 겹치지 않는 주문 구간을 나눠 쓴다.
//   100 동시 요청  -> 주문 id 1~100     (100 iterations)
//   500 동시 요청  -> 주문 id 101~600   (500 iterations)
//   1000 동시 요청 -> 주문 id 601~1600  (1000 iterations)

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = 20;

const flowSuccessRate = new Rate('payment_flow_success');

export const options = {
  scenarios: {
    concurrency_100: {
      executor: 'shared-iterations',
      exec: 'paymentFlow',
      vus: 100,
      iterations: 100,
      maxDuration: '2m',
      startTime: '0s',
      env: { ORDER_OFFSET: '0' },
    },
    concurrency_500: {
      executor: 'shared-iterations',
      exec: 'paymentFlow',
      vus: 500,
      iterations: 500,
      maxDuration: '2m',
      startTime: '2m',
      env: { ORDER_OFFSET: '100' },
    },
    concurrency_1000: {
      executor: 'shared-iterations',
      exec: 'paymentFlow',
      vus: 1000,
      iterations: 1000,
      maxDuration: '3m',
      startTime: '4m',
      env: { ORDER_OFFSET: '600' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
    payment_flow_success: ['rate>0.99'],
  },
};

export function setup() {
  const tokens = [];
  for (let i = 1; i <= USER_COUNT; i++) {
    const res = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `loadtest${i}@test.com`, password: 'test1234!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { [`login user ${i} 200`]: (r) => r.status === 200 });
    tokens.push(res.json('token'));
  }
  return { tokens };
}

export function paymentFlow(data) {
  const offset = Number(__ENV.ORDER_OFFSET);
  // shared-iterations 실행기에서 __ITER 는 VU별 카운터라 겹칠 수 있으므로,
  // 시나리오 전체에서 유일한 exec.scenario.iterationInTest 를 사용한다.
  const orderId = offset + exec.scenario.iterationInTest + 1;
  const memberIndex = (orderId - 1) % USER_COUNT;
  const token = data.tokens[memberIndex];
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  let ok = true;

  const readyRes = http.post(
    `${BASE_URL}/api/payments`,
    JSON.stringify({ orderId, method: 'CARD', pgProvider: 'MOCK_PG' }),
    { headers, tags: { step: 'ready' } }
  );
  ok = check(readyRes, { 'ready 200': (r) => r.status === 200 }) && ok;

  const paymentKey = readyRes.json('paymentKey');
  if (!paymentKey) {
    flowSuccessRate.add(false);
    return;
  }

  const confirmRes = http.post(
    `${BASE_URL}/api/payments/${paymentKey}/comfirm`,
    JSON.stringify({ amount: 10000 }),
    { headers, tags: { step: 'confirm' } }
  );
  ok = check(confirmRes, { 'confirm 200': (r) => r.status === 200 }) && ok;

  const getRes = http.get(`${BASE_URL}/api/payments/${paymentKey}`, {
    headers,
    tags: { step: 'get' },
  });
  ok = check(getRes, { 'get 200': (r) => r.status === 200 }) && ok;

  flowSuccessRate.add(ok);
}
