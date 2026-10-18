// 방 생성 부하. 방장이 방을 새로 만들어 공유 링크를 확인하고 빈 갤러리를 여는 흐름이다.
//
//   반복 하나 = 방 하나:
//     POST   /rooms                  방 생성 — 201, 방장이 나이고 참여자로 등록되어 있어야 한다
//     GET    /rooms/{code}           공유 코드로 방 조회 — 같은 방이 나와야 한다
//     GET    /rooms/{roomId}/media/all 빈 갤러리 — 사진 0장
//     DELETE /rooms/{roomId}         방 삭제 (실행 뒤 방이 쌓이지 않도록)
//     GET    /rooms/{code}           삭제 확인 — status DELETED
//
//   방장 계정은 시드 계정(방장·참여자)을 돌려 쓴다. 새 계정을 만들지 않으므로 계정이 쌓이지 않는다.
//   삭제는 soft delete 라 방 행은 DB 에 DELETED 로 남는다. 방 이름이 <시드 태그>c... 라 DB 에서 골라낼 수 있다.
//   삭제에 실패한 방은 한 번 더 지우고, 그래도 남으면 roomId 를 콘솔에 남기고 rooms_left 로 센다.
//
//   PROFILE=smoke  5개를 만든다. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   RATE(초당 방 수, 기본 1)로 예열 뒤 본 측정.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';

const SCENARIO = 'room-create';
const APIS = ['room_create', 'room_lookup_created', 'media_all_empty', 'room_delete', 'room_lookup_deleted'];
// 서버 RoomName.MAX_LENGTH
const MAX_ROOM_NAME = 12;

const roomsLeft = new Counter('rooms_left');

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();
// 시드와 같은 태그를 붙여 이 실험이 만든 방임을 알아볼 수 있게 한다 (seed.mjs nameTag).
const NAME_TAG = `lt${fixtures.runId.slice(-4)}c`;

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const CONFIG = buildConfig();
const PHASES = Object.keys(CONFIG.phaseDurations);
const JUDGED = PROFILE === 'smoke' ? 'smoke' : 'main';

const SCENARIOS = buildScenarios();

export const options = {
  scenarios: SCENARIOS,
  thresholds: {
    ...trackApis(APIS, PHASES),
    ...trackMetrics(Object.fromEntries(PHASES.map((phase) => [`rooms_left{phase:${phase}}`, 'count']))),
    ...stopConditions(PROFILE, JUDGED, SCENARIOS),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function buildConfig() {
  if (PROFILE === 'smoke') {
    return { profile: PROFILE, vus: 1, iterations: intEnv('ITERATIONS', 5), phaseDurations: { smoke: null } };
  }
  const rate = intEnv('RATE', 1);
  if (rate < 1) throw new Error('RATE 는 1 이상');
  return {
    profile: PROFILE, rate, vus: intEnv('VUS', Math.max(5, rate * 5)),
    phaseDurations: { warmup: durationEnv('WARMUP', '1m'), main: durationEnv('DURATION', '3m') },
  };
}

function buildScenarios() {
  if (PROFILE === 'smoke') {
    return {
      smoke: { executor: 'per-vu-iterations', vus: CONFIG.vus, iterations: CONFIG.iterations, maxDuration: '2m', tags: { phase: 'smoke' } },
    };
  }
  // 요청률을 고정한다. 한 바퀴가 느려지면 가상 사용자를 더 쓰고, 그래도 모자라면 dropped iterations 로 남는다.
  return warmupMain({
    executor: 'constant-arrival-rate', rate: CONFIG.rate, timeUnit: '1s', preAllocatedVUs: CONFIG.vus, maxVUs: CONFIG.vus,
  }, CONFIG.phaseDurations);
}

export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: USERS.length });
}

const client = createClient(BASE_URL);

export default function () {
  const host = USERS[exec.scenario.iterationInTest % USERS.length];
  const name = roomName();

  const created = client.post('room_create', '/rooms', {
    token: host.token,
    body: { name, uploadPolicy: 'everyone' },
    expectedStatus: 201,
    validate: (room) => {
      if (!room.roomId || !room.code) return 'roomId·code 없음';
      if (room.name !== name) return `이름 ${room.name} ≠ ${name}`;
      if (room.hostId !== host.userId) return `hostId ${room.hostId} ≠ ${host.userId}`;
      if (room.status !== 'ACTIVE') return `status ${room.status}`;
      if (!room.joined) return '방장이 참여자로 등록되지 않음';
      if (room.photoCount !== 0) return `photoCount ${room.photoCount}`;
      return null;
    },
  });
  if (!created.ok) return;
  const { roomId, code } = created.data;

  let deleted = false;
  try {
    client.get('room_lookup_created', `/rooms/${code}`, {
      token: host.token,
      validate: (room) => (room.roomId === roomId && room.joined ? null : `조회한 방 ${room.roomId} 이 만든 방 ${roomId} 와 다름`),
    });

    client.get('media_all_empty', `/rooms/${roomId}/media/all`, {
      token: host.token,
      validate: (body) => (body && Array.isArray(body.items) && body.items.length === 0 ? null : '새 방 갤러리가 비어 있지 않음'),
    });

    deleted = client.del('room_delete', `/rooms/${roomId}`, {
      token: host.token,
      validate: (body) => (body.deletedAt ? null : 'deletedAt 없음'),
    }).ok;
    if (!deleted) return;

    client.get('room_lookup_deleted', `/rooms/${code}`, {
      token: host.token,
      validate: (room) => (room.status === 'DELETED' ? null : `삭제 후 status ${room.status}`),
    });
  } finally {
    // 중간에 실패해도 방이 남지 않게 한 번 더 지운다. 이 재시도는 결과 집계에 넣지 않는다.
    if (!deleted && !retryDelete(roomId, host.token)) {
      roomsLeft.add(1);
      console.error(`[room_create] 지우지 못한 방 roomId=${roomId} code=${code} (방장 userId=${host.userId})`);
    }
  }
}

function retryDelete(roomId, token) {
  const res = http.del(`${BASE_URL}/api/v1/rooms/${roomId}`, null, {
    headers: { Authorization: `Bearer ${token}` }, tags: { api: 'room_cleanup' }, responseCallback: http.expectedStatuses(200, 410),
  });
  // 410(ROOM_ALREADY_DELETED)은 앞선 삭제 요청이 응답만 놓친 경우라 지워진 것으로 본다.
  return res.status === 200 || res.status === 410;
}

// 방 이름은 12자까지다. 시드 태그 뒤에 가상 사용자·반복 번호와 시각을 붙여 실행마다 겹치지 않게 한다.
function roomName() {
  const unique = `${exec.vu.idInTest.toString(36)}${exec.vu.iterationInScenario.toString(36)}${Date.now().toString(36).slice(-3)}`;
  return `${NAME_TAG}${unique}`.slice(0, MAX_ROOM_NAME);
}

export function handleSummary(data) {
  return writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR,
    extras: PHASES.map((phase) => `rooms_left{phase:${phase}}`),
  });
}
