// 인증·응답 파싱·업무 성공 검증을 한 곳에서 처리한다.
// HTTP 200 이어도 응답 내용이 기대와 다르면 실패로 센다. k6 기본 지표(http_req_failed)는 상태 코드만 본다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// API 이름(api 태그)별 업무 성공률. 시나리오에서 thresholds 에 걸어 결과 파일에 남긴다.
export const businessOk = new Rate('business_ok');
export const businessFailures = new Counter('business_failures');

// 서버 WebConfig.API_PREFIX. /version 은 접두사 없이 루트에 있어 이 클라이언트를 거치지 않는다.
const API_PREFIX = '/api/v1';
const MAX_FAILURE_LOGS = 20;
let failureLogs = 0;

export function createClient(baseUrl, { timeout = '10s' } = {}) {
  function request(method, name, path, { token, body, expectedStatus = 200, validate } = {}) {
    const params = {
      headers: {
        ...(token && { Authorization: `Bearer ${token}` }),
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
      },
      tags: { api: name },
      timeout,
      // 기대한 상태 코드만 성공으로 본다. 401·403 이 성공으로 섞이지 않는다.
      responseCallback: http.expectedStatuses(expectedStatus),
    };
    const res = http.request(method, baseUrl + API_PREFIX + path, body === undefined ? null : JSON.stringify(body), params);
    const parsed = parseJson(res);
    const data = parsed?.data;

    let reason = null;
    if (res.status !== expectedStatus) {
      reason = `status ${res.status}${parsed?.code ? ` ${parsed.code}` : ''}`;
    } else if (parsed === null) {
      reason = 'JSON 이 아닌 응답';
    } else if (validate) {
      reason = validate(data) || null;
    }

    const ok = reason === null;
    check(res, { [`${name} 업무 성공`]: () => ok }, { api: name });
    businessOk.add(ok, { api: name });
    if (!ok) {
      businessFailures.add(1, { api: name, reason: reason.split(' ').slice(0, 2).join(' ') });
      if (failureLogs++ < MAX_FAILURE_LOGS) {
        console.warn(`[${name}] ${method} ${path} 실패: ${reason}`);
      }
    }
    return { res, data, ok, reason };
  }

  return {
    get: (name, path, options) => request('GET', name, path, options),
    post: (name, path, options) => request('POST', name, path, options),
    put: (name, path, options) => request('PUT', name, path, options),
    patch: (name, path, options) => request('PATCH', name, path, options),
    del: (name, path, options) => request('DELETE', name, path, options),
  };
}

function parseJson(res) {
  if (!res.body) {
    return null;
  }
  try {
    return JSON.parse(res.body);
  } catch {
    return null;
  }
}
