// 방 이벤트(SSE) 구독. xk6-sse 확장이 든 k6(bin/k6)에서만 돈다 — README "2. 준비" 참고.
//
// sse.open 은 연결이 끝날 때까지 그 가상 사용자를 붙잡는다. 일반 API 요청과 같은 가상 사용자에서 돌리지 않는다.
//
// 서버 동작에서 나온 제약 (2026-10 로컬 확인)
// - 서버는 첫 데이터를 보낼 때까지 응답 헤더를 내보내지 않는다. 연결 확인(open)은 첫 이벤트나
//   heartbeat(15초 주기)가 올 때라, sse_open_ms 는 연결 수립 시간이 아니라 "첫 데이터까지 걸린 시간"이고 최대 약 15초다.
// - 끊는 시점도 데이터가 올 때만 판단할 수 있다. 관찰 종료 뒤 첫 heartbeat 에서 닫으므로 최대 15초 더 열려 있다.
// - 응답 본문을 읽다가 오류가 나도 xk6-sse 는 스스로 닫지 않는다. 오류 처리기에서 직접 닫는다.
// - 토큰은 ?token= 대신 헤더로 보낸다. 연결 오류 메시지에 URL 이 그대로 찍힌다.
import sse from 'k6/x/sse';
import { Counter, Rate, Trend } from 'k6/metrics';

import { businessFailures, businessOk } from './http.js';

export const sseOpenMs = new Trend('sse_open_ms', true);
export const sseOk = new Rate('sse_ok');
export const sseEvents = new Counter('sse_events');

const API_PREFIX = '/api/v1';
const HEARTBEAT_SEC = 15;
// heartbeat 가 두 번 연속 오지 않으면 연결이 죽은 것으로 본다.
const DEAD_AFTER_SEC = HEARTBEAT_SEC * 2;

// until(epoch ms)까지 연결을 유지하고 결과를 돌려준다. 끝까지 유지해야 성공이다.
// 첫 데이터가 openLimitMs 안에 오지 않았거나, 중간에 끊겼거나, 403 등으로 열리지 않으면 실패로 센다.
export function holdRoomStream(baseUrl, { roomId, token, until, openLimitMs = (HEARTBEAT_SEC + 5) * 1000, name = 'room_events', tags = {} }) {
  const started = Date.now();
  const timeoutSec = Math.max(1, Math.ceil((until - started) / 1000) + DEAD_AFTER_SEC);
  const result = { opened: false, openMs: null, closedByUs: false, events: 0, error: null, status: 0 };

  const res = sse.open(`${baseUrl}${API_PREFIX}/rooms/${roomId}/events`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: name, ...tags },
    timeout: `${timeoutSec}s`,
  }, (client) => {
    client.on('open', () => {
      result.opened = true;
      result.openMs = Date.now() - started;
    });
    client.on('event', (event) => {
      if (event.name) {
        result.events++;
        sseEvents.add(1, { event: event.name, ...tags });
      }
      if (Date.now() >= until) {
        result.closedByUs = true;
        client.close();
      }
    });
    client.on('error', (e) => {
      result.error = String(e.error());
      client.close();
    });
  });
  result.status = res.status;

  let reason = null;
  if (res.status !== 200) {
    reason = `status ${res.status}${res.error ? ` (${redact(res.error).slice(0, 80)})` : ''}`;
  } else if (!result.opened) {
    reason = '연결 확인 전 종료';
  } else if (result.openMs > openLimitMs) {
    reason = `첫 데이터 ${result.openMs}ms > ${openLimitMs}ms`;
  } else if (!result.closedByUs) {
    reason = `관찰 종료 전 끊김${result.error ? ` (${redact(result.error).slice(0, 80)})` : ''}`;
  }
  if (result.openMs !== null) {
    sseOpenMs.add(result.openMs, tags);
  }
  const ok = reason === null;
  sseOk.add(ok, tags);
  businessOk.add(ok, { api: name, ...tags });
  if (!ok) {
    businessFailures.add(1, { api: name, reason: reason.split(' ').slice(0, 2).join(' ') });
    console.warn(`[${name}] roomId=${roomId} 실패: ${reason}`);
  }
  return { ...result, ok, reason };
}

// 연결 오류 메시지에는 요청 URL 이 들어간다. 헤더로 인증해 토큰은 없지만 쿼리가 붙어도 남지 않게 지운다.
function redact(message) {
  return String(message).replace(/\?[^\s"]*/g, '');
}
