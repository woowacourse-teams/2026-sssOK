// 시드·정리 도구가 쓰는 API 클라이언트. 부하 측정용이 아니라 준비용이라 재시도와 상세한 오류 메시지를 우선한다.
// k6 쪽 공통 모듈(k6/lib/http.js)과 코드를 공유하지 않는다 — 런타임(Node / k6)이 다르다.

const RETRYABLE_STATUSES = new Set([429, 502, 503, 504]);
// 서버 WebConfig.API_PREFIX. /version·/health 만 접두사 없이 루트에 있다.
export const API_PREFIX = '/api/v1';

export class ApiError extends Error {
  constructor(method, path, status, body) {
    super(`${method} ${path} → ${status} ${summarize(body)}`);
    this.status = status;
    this.body = body;
  }
}

function summarize(body) {
  if (body && typeof body === 'object') {
    return body.code ? `${body.code}: ${body.message}` : JSON.stringify(body).slice(0, 200);
  }
  return String(body ?? '').slice(0, 200);
}

export function createApi(baseUrl, { retries = 3, timeoutMs = 15_000 } = {}) {
  const root = baseUrl.replace(/\/+$/, '');

  async function request(method, path, { token, body, expect = [200], root: atRoot = false, retries: maxRetries = retries } = {}) {
    const url = root + (atRoot ? '' : API_PREFIX) + path;
    let lastError;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      if (attempt > 0) {
        await sleep(500 * 2 ** (attempt - 1));
      }
      let response;
      try {
        response = await fetch(url, {
          method,
          headers: {
            ...(body !== undefined && { 'Content-Type': 'application/json' }),
            ...(token && { Authorization: `Bearer ${token}` }),
          },
          body: body === undefined ? undefined : JSON.stringify(body),
          signal: AbortSignal.timeout(timeoutMs),
        });
      } catch (e) {
        lastError = new Error(`${method} ${path} 요청 실패: ${e.message}`);
        continue;
      }
      const parsed = await parseBody(response);
      if (expect.includes(response.status)) {
        return { status: response.status, data: parsed?.data ?? parsed };
      }
      lastError = new ApiError(method, path, response.status, parsed);
      if (!RETRYABLE_STATUSES.has(response.status)) {
        throw lastError;
      }
    }
    throw lastError;
  }

  return {
    version: () => request('GET', '/version', { root: true }),
    createAnonymous: (nickname) =>
      request('POST', '/auth/anonymous', { body: { nickname }, expect: [201] }),
    createRoom: (token, name) =>
      request('POST', '/rooms', { token, body: { name, uploadPolicy: 'everyone' }, expect: [201] }),
    getRoom: (token, code) => request('GET', `/rooms/${encodeURIComponent(code)}`, { token }),
    joinRoom: (token, roomId) =>
      request('POST', `/rooms/${roomId}/members`, { token, expect: [200, 201] }),
    deleteRoom: (token, roomId) => request('DELETE', `/rooms/${roomId}`, { token }),
    issueUploadUrls: (token, roomId, files) =>
      request('POST', `/rooms/${roomId}/media/upload-urls`, { token, body: { files } }),
    completeUpload: (token, roomId, mediaIds) =>
      request('POST', `/rooms/${roomId}/media`, { token, body: { mediaIds }, expect: [201] }),
    listMedia: (token, roomId, { cursor, size = 100 } = {}) => {
      const query = new URLSearchParams({ size: String(size), ...(cursor && { cursor }) });
      return request('GET', `/rooms/${roomId}/media?${query}`, { token });
    },
    // 검색이 꺼져 있으면 503 IMAGE_SEARCH_UNAVAILABLE 이다. 재시도하지 않도록 503 도 기대값에 넣는다.
    searchMedia: (token, roomId, query) =>
      request('GET', `/rooms/${roomId}/media/all?${new URLSearchParams({ query })}`, { token, expect: [200, 503], retries: 0 }),
    deleteMedia: (token, roomId, mediaIds) =>
      request('DELETE', `/rooms/${roomId}/media`, { token, body: { mediaIds } }),
  };
}

// 서명 URL 로 스토리지에 직접 올린다. 발급 응답의 헤더를 그대로 실어야 서명이 맞는다.
export async function putToStorage(issued, bytes, { retries = 2, timeoutMs = 60_000 } = {}) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    if (attempt > 0) {
      await sleep(1000 * attempt);
    }
    try {
      const response = await fetch(issued.uploadUrl, {
        method: issued.method,
        headers: issued.headers,
        body: bytes,
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (response.ok) {
        return;
      }
      lastError = new Error(`스토리지 PUT ${response.status}`);
      // 서명 불일치(403)는 다시 보내도 같다.
      if (response.status === 403) {
        break;
      }
    } catch (e) {
      lastError = new Error(`스토리지 PUT 실패: ${e.message}`);
    }
  }
  throw lastError;
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// 동시에 limit 개까지만 실행한다.
export async function mapWithConcurrency(items, limit, task) {
  const results = new Array(items.length);
  let next = 0;
  async function worker() {
    while (next < items.length) {
      const index = next++;
      results[index] = await task(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}
