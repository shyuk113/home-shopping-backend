import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 20);              // 초당 요청 수
const SELLER_MAX = Number(__ENV.SELLER_MAX || 1000);
const SOURCE = __ENV.SOURCE || 'unknown';           // 결과 구분용 태그 (raw / summary / cache)

export const options = {
    scenarios: {
        dashboard: {
            executor: 'constant-arrival-rate',   // 앞선 k6 실험의 교훈: 도착률 기반으로 측정
            rate: RATE,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 300,
        },
    },
    thresholds: { http_req_failed: ['rate<0.01'] },
    summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    if (!__ENV.ADMIN_EMAIL || !__ENV.ADMIN_PASSWORD) {
        exec.test.abort('ADMIN_EMAIL, ADMIN_PASSWORD 환경 변수가 필요합니다 (-e 옵션)');
    }
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: __ENV.ADMIN_EMAIL, password: __ENV.ADMIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } });
    if (res.status !== 200) {
        exec.test.abort(`관리자 로그인 실패: status=${res.status} body=${res.body}`);
    }
    return { token: res.json('token') };
}

export default function (data) {
    const sellerId = Math.floor(Math.random() * SELLER_MAX) + 1;
    const res = http.get(
        `${BASE_URL}/api/admin/sellers/${sellerId}/dashboard?from=2026-09-01&to=2026-09-30`,
        { headers: { Authorization: `Bearer ${data.token}` }, tags: { source: SOURCE } });
    check(res, {
        'status 200': (r) => r.status === 200,
        '30 days': (r) => r.status === 200 && r.json('days').length === 30,
    });
}