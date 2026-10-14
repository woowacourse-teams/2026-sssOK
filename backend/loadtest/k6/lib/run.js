// 시나리오 공통 실행 뼈대. 구간 나누기, 중단 기준, 시작 전 대상·데이터 확인을 한 곳에 둔다.
import http from 'k6/http';
import exec from 'k6/execution';

import { abortEvalDelay, env, expectedPhotos } from './config.js';

// 본 측정은 예열(warmup) 뒤 본 측정(main) 구간을 따로 집계한다. executor 설정만 시나리오가 정한다.
// 예열은 기본으로 끝나는 즉시 멈춘다. 진행 중인 반복을 기다리면 본 측정 사용자와 겹쳐 전환 순간 요청이 몰린다.
// 반복을 중간에 끊으면 안 되는 시나리오(업로드)만 warmupGracefulStop 으로 기다린다.
export function warmupMain(executor, { warmup, main }, { warmupGracefulStop = '0s' } = {}) {
  const phase = (name, startTime, duration, gracefulStop) => ({ ...executor, startTime, duration, tags: { phase: name }, gracefulStop });
  return {
    warmup: phase('warmup', '0s', warmup, warmupGracefulStop),
    main: phase('main', warmup, main, '10s'),
  };
}

// 사전 점검은 하나라도 실패하면 실패다. 본 측정은 구간 시작 1분 뒤부터 누적 실패율이 5% 를 넘는 즉시 멈춘다(#517 중단 기준).
// 판정 구간이 예열 뒤에 시작하면 scenarios 에서 그 시작 시각을 읽어 유예에 더한다.
// 공유 RDS CPU·운영 경보는 k6 가 보지 못하므로 사람이 Grafana 를 보고 중단한다.
export function stopConditions(profile, judgedPhase, scenarios = {}) {
  if (profile === 'smoke') {
    return { [`business_ok{phase:${judgedPhase}}`]: ['rate==1'] };
  }
  const delayAbortEval = abortEvalDelay(scenarios[judgedPhase]?.startTime);
  return {
    [`http_req_failed{phase:${judgedPhase}}`]: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval }],
    [`business_ok{phase:${judgedPhase}}`]: [{ threshold: 'rate>0.95', abortOnFail: true, delayAbortEval }],
  };
}

// 측정 전에 대상과 데이터가 fixtures 와 맞는지 확인한다. 어긋나면 부하를 걸지 않고 끝낸다.
// 그 방에 누가 사진을 올리거나 지웠으면 측정 조건이 달라지므로 사진 수를 서버에 다시 묻는다.
export function checkTarget({ baseUrl, client, fixtures }) {
  const versionRes = http.get(`${baseUrl}/version`, { tags: { api: 'setup' } });
  if (versionRes.status !== 200) {
    exec.test.abort(`대상 서버 응답 없음: GET /version → ${versionRes.status}`);
  }
  for (const room of fixtures.rooms) {
    const expected = expectedPhotos(room);
    const { ok, data, reason } = client.get('setup', `/rooms/${room.roomId}/media?size=1`, { token: room.host.accessToken });
    if (!ok || data.totalCount !== expected) {
      exec.test.abort(`roomId=${room.roomId} 사진 수가 fixtures(${expected})와 다릅니다: ${reason ?? data.totalCount}`);
    }
  }
  return JSON.parse(versionRes.body);
}

// handleSummary 가 결과 파일 머리에 남길 실행 정보.
export function runInfo({ baseUrl, appVersion, fixturesPath, fixtures, accounts }) {
  return {
    runId: env('RUN_ID', new Date().toISOString().replace(/[-:]/g, '').slice(0, 15) + 'Z'),
    startedAtUtc: new Date().toISOString(),
    baseUrl,
    appVersion,
    dataCondition: { fixtures: fixturesPath, seedRunId: fixtures.runId, ...fixtures.condition, accounts },
  };
}

// 0.5~1.5배로 흔들어 모든 가상 사용자가 같은 박자로 요청하지 않게 한다.
export function jitter(seconds) {
  return seconds * (0.5 + Math.random());
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 겹치지 않게 count 개를 고른다.
export function pickMany(list, count) {
  const copy = list.slice();
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy.slice(0, count);
}
