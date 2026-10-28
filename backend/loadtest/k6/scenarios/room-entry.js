// 신규 입장 부하. 처음 초대 링크를 연 사람이 방에 들어와 갤러리를 보고 실시간 연결을 유지하는 흐름이다.
// SSE 를 쓰므로 xk6-sse 가 든 bin/k6 로 실행한다.
//
//   한 명(= 시드의 미입장 계정 하나)이 한 번씩만 들어온다:
//     GET /rooms/{code}            초대 링크로 방 정보 확인 (아직 미입장이어야 한다)
//     POST /rooms/{roomId}/members 입장. 201(새로 입장)만 성공이다 — 200(이미 입장)은 신규 입장으로 세지 않는다
//     GET /rooms/{roomId}/media/all 최초 갤러리 목록
//     GET /rooms/{roomId}/events   SSE 구독. 마지막 입장 뒤 OBSERVE 동안 끊기지 않아야 성공
//
//   PROFILE=smoke  5명이 10초에 걸쳐 들어오고 30초 관찰. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   ENTRANTS 명이 ENTRY_WINDOW(60초)에 고르게 들어오고, 마지막 입장 뒤 OBSERVE(5분) 관찰.
//
// 한 번 돌리면 쓴 계정은 입장 상태가 되어 다시 쓸 수 없다. 다시 돌리기 전에 시드로 채운다:
//   node seed/seed.mjs --base-url <URL> --resume $FIXTURES --entrants <방당 인원>
import exec from 'k6/execution';

import {
  buildEntrants, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, runInfo, stopConditions } from '../lib/run.js';
import { holdRoomStream } from '../lib/sse.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';

const SCENARIO = 'room-entry';
const PHASE = 'entry';
// SSE(room_events)는 연결 시간 전체가 요청 시간으로 잡혀 API 지연 통계에 넣지 않는다. sse_* 지표로 따로 본다.
const APIS = ['room_lookup', 'room_join', 'media_all_initial'];

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const ENTRANTS = buildEntrants(fixtures);
const RESULTS_DIR = resolveResultsDir();

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const CONFIG = buildConfig();
if (CONFIG.entrants < 1) {
  throw new Error('신규 입장용 미입장 계정이 없습니다. 시드를 --entrants <방당 인원> 으로 준비하거나 --resume 으로 채운다');
}
if (CONFIG.entrants > ENTRANTS.length) {
  throw new Error(`ENTRANTS(${CONFIG.entrants})가 fixtures 의 미입장 계정 수(${ENTRANTS.length})보다 많습니다. 시드 --resume --entrants 로 채운다`);
}

export const options = {
  scenarios: {
    [PHASE]: {
      // 반복 하나가 한 명의 입장이다. ENTRY_WINDOW 동안 ENTRANTS 번을 고르게 시작한다.
      executor: 'constant-arrival-rate',
      rate: CONFIG.entrants,
      timeUnit: CONFIG.entryWindow,
      duration: CONFIG.entryWindow,
      // 입장한 사람은 관찰이 끝날 때까지 SSE 에 묶여 있으므로 인원만큼 가상 사용자가 필요하다.
      preAllocatedVUs: CONFIG.entrants,
      maxVUs: CONFIG.entrants,
      // 마지막 입장 뒤 관찰 시간 + heartbeat 를 기다려 닫는 여유
      gracefulStop: `${toSeconds(CONFIG.observe) + 60}s`,
      tags: { phase: PHASE },
    },
  },
  setupTimeout: '5m',
  thresholds: {
    ...trackApis(APIS, [PHASE]),
    ...trackMetrics({ [`sse_ok{phase:${PHASE}}`]: 'rate', [`sse_open_ms{phase:${PHASE}}`]: 'trend', [`sse_events{phase:${PHASE}}`]: 'count' }),
    ...stopConditions(PROFILE, PHASE),
    ...(PROFILE === 'smoke' && { [`sse_ok{phase:${PHASE}}`]: ['rate==1'] }),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function buildConfig() {
  if (PROFILE === 'smoke') {
    return {
      profile: PROFILE, entrants: intEnv('ENTRANTS', Math.min(5, ENTRANTS.length)),
      entryWindow: durationEnv('ENTRY_WINDOW', '10s'), observe: durationEnv('OBSERVE', '30s'),
    };
  }
  return {
    profile: PROFILE, entrants: intEnv('ENTRANTS', ENTRANTS.length),
    entryWindow: durationEnv('ENTRY_WINDOW', '60s'), observe: durationEnv('OBSERVE', '5m'),
  };
}

function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
  return Number(match[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
}

// 쓸 계정이 정말 아직 입장 전인지 서버에 묻는다. 이전 실행에서 들어간 계정이 섞이면 신규 입장 수가 부풀려진다.
export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  const used = [];
  for (const entrant of ENTRANTS.slice(0, CONFIG.entrants)) {
    const { ok, data, reason } = client.get('setup', `/rooms/${entrant.code}`, { token: entrant.token });
    if (!ok) {
      exec.test.abort(`userId=${entrant.userId} 방 조회 실패: ${reason}`);
    }
    if (data.joined) {
      used.push(entrant.userId);
    }
  }
  if (used.length > 0) {
    exec.test.abort(`이미 입장한 계정 ${used.length}개가 섞여 있습니다 (userId ${used.slice(0, 5).join(',')}…). ` +
      `시드로 채운다: node seed/seed.mjs --base-url ${BASE_URL} --resume <fixtures> --entrants <방당 인원>`);
  }
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: CONFIG.entrants });
}

const client = createClient(BASE_URL);

export default function () {
  const index = exec.scenario.iterationInTest;
  // setup 에서 미입장을 확인한 앞쪽 ENTRANTS 명만 쓴다. 구간 경계에서 반복이 하나 더 잡혀도 확인 안 된 계정을 쓰지 않는다.
  if (index >= CONFIG.entrants) {
    return;
  }
  const entrant = ENTRANTS[index];
  // 관찰은 입장 구간이 끝난 시각부터 센다. 먼저 들어온 사람도 마지막 사람과 같은 시각까지 연결을 유지한다.
  const until = exec.scenario.startTime + (toSeconds(CONFIG.entryWindow) + toSeconds(CONFIG.observe)) * 1000;

  const lookup = client.get('room_lookup', `/rooms/${entrant.code}`, {
    token: entrant.token,
    validate: (room) => {
      if (room.roomId !== entrant.roomId) return `roomId ${room.roomId} ≠ ${entrant.roomId}`;
      if (room.joined) return '이미 입장한 계정';
      return null;
    },
  });
  if (!lookup.ok) return;

  const join = client.post('room_join', `/rooms/${entrant.roomId}/members`, {
    token: entrant.token,
    body: { code: entrant.code },
    expectedStatus: 201,
    validate: (member) => (member.roomId === entrant.roomId && member.userId === entrant.userId ? null : '입장 응답의 방·회원 불일치'),
  });
  if (!join.ok) return;

  client.get('media_all_initial', `/rooms/${entrant.roomId}/media/all`, {
    token: entrant.token,
    validate: (body) => {
      if (!body || !Array.isArray(body.items)) return 'items 없음';
      if (body.items.length !== entrant.expectedPhotos) return `items ${body.items.length} ≠ ${entrant.expectedPhotos}`;
      if (body.items.some((item) => !item.mediaId || !item.thumbnailUrl)) return 'mediaId·thumbnailUrl 없는 항목';
      return null;
    },
  });

  holdRoomStream(BASE_URL, { roomId: entrant.roomId, token: entrant.token, until, name: 'room_events' });
}

export function handleSummary(data) {
  return writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: [PHASE], config: { ...CONFIG, phaseDurations: { [PHASE]: CONFIG.entryWindow } }, resultsDir: RESULTS_DIR,
    extras: [`sse_ok{phase:${PHASE}}`, `sse_open_ms{phase:${PHASE}}`, `sse_events{phase:${PHASE}}`],
  });
}
