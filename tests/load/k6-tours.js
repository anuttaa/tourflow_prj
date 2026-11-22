// Нагрузочный тест (ТЗ 11): 50 одновременных пользователей, p95 чтения не более 500 мс.
// Запуск: k6 run -e BASE=https://localhost:8443 -e EMAIL=manager@tourflow.local -e PASSWORD='Manager123!' tests/load/k6-tours.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: { read: { executor: 'constant-vus', vus: 50, duration: '1m' } },
  thresholds: { http_req_duration: ['p(95)<500'], http_req_failed: ['rate<0.01'] },
};

const BASE = __ENV.BASE || 'https://localhost:8443';

export function setup() {
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({ email: __ENV.EMAIL, password: __ENV.PASSWORD }), { headers: { 'Content-Type': 'application/json' } });
  return { token: res.json('accessToken') };
}

export default function (data) {
  const showcase = http.get(`${BASE}/api/v1/public/tours?size=9`);
  check(showcase, { 'витрина 200': (r) => r.status === 200 });
  const auth = { headers: { Authorization: `Bearer ${data.token}` } };
  const offers = http.get(`${BASE}/api/v1/catalog/offers?type=EXCURSION&size=20`, auth);
  check(offers, { 'каталог 200': (r) => r.status === 200 });
  const tours = http.get(`${BASE}/api/v1/tours`, auth);
  check(tours, { 'туры 200': (r) => r.status === 200 });
  sleep(1);
}
