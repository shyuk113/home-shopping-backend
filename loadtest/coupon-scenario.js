import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

// 수량 100장 쿠폰에 1000명이 동시에 발급 요청 → 정확히 100명만 성공해야 함
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = 1000;
const COUPON_QUANTITY = 100;

const issued = new Counter('coupon_issued');         // 202 발급 성공
const soldOut = new Counter('coupon_sold_out');      // 409 수량 소진
const duplicated = new Counter('coupon_duplicated'); // 400 중복 발급
const unexpected = new Counter('coupon_unexpected'); // 그 외 (연결 거부 등)

export const options = {
    setupTimeout: '5m', // 로그인 1000번 (bcrypt라 시간이 걸림)
    scenarios: {
        burst_1000: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,     // 각 유저가 딱 1번 요청
            maxDuration: '2m',
        },
    },
    thresholds: {
        coupon_issued: [`count==${COUPON_QUANTITY}`],
    },
};

// 서버가 LocalDateTime(타임존 없음)을 받으므로 이 PC의 현지 시각 문자열로 만듦
function localDateTime(offsetMs) {
    const d = new Date(Date.now() + offsetMs);
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function setup() {
    // 1000명 로그인을 50명씩 묶어서 병렬 처리
    const tokens = [];
    const CHUNK = 50;
    for (let start = 1; start <= USER_COUNT; start += CHUNK) {
        const reqs = [];
        for (let i = start; i < start + CHUNK && i <= USER_COUNT; i++) {
            reqs.push(['POST', `${BASE_URL}/api/auth/login`,
                JSON.stringify({ email: `loadtest${i}@test.com`, password: 'test1234!' }),
                { headers: { 'Content-Type': 'application/json' } }]);
        }
        http.batch(reqs).forEach((res) => tokens.push(res.json('token')));
    }

    // 쿠폰은 반드시 API로 만들어야 함 (생성 시점에 Redis에 meta가 올라가기 때문)
    const couponRes = http.post(`${BASE_URL}/api/coupons`, JSON.stringify({
        name: '선착순 부하테스트 쿠폰',
        discountAmount: 1000,
        totalQuantity: COUPON_QUANTITY,
        startTime: localDateTime(-60 * 1000),      // 1분 전부터
        endTime: localDateTime(60 * 60 * 1000),   // 1시간 후까지
    }), { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[0]}` } });
    check(couponRes, { 'coupon created': (r) => r.status === 200 });

    return { tokens, couponId: couponRes.json() };
}

export default function (data) {
    const token = data.tokens[exec.vu.idInTest - 1]; // VU마다 서로 다른 유저
    const res = http.post(`${BASE_URL}/api/coupons/${data.couponId}/issue`, null,
        { headers: { Authorization: `Bearer ${token}` } });

    if (res.status === 202) issued.add(1);
    else if (res.status === 409) soldOut.add(1);
    else if (res.status === 400) duplicated.add(1);
    else unexpected.add(1);
}