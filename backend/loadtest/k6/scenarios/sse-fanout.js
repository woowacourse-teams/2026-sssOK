// SSE 발행 병목 재현(#833). 한 방(방 A)에 구독자 여럿을 붙여 둔 채 업로더 여럿이 10장 묶음을 동시에 등록하고,
// 다른 방(방 B~D)의 갤러리 조회가 같이 느려지는지 본다. SSE 를 쓰므로 xk6-sse 가 든 bin/k6 로 실행한다.
//
//   방 A — 실행마다 새로 만든 전용 방. 끝날 때(teardown) 사진과 함께 지운다.
//     구독 (SUBSCRIBERS명, 시작부터 끝까지 연결 유지): GET /rooms/{roomId}/events
//       구독자마다 받은 이벤트의 중복(같은 이벤트 ID·같은 mediaId 의 media.created 두 번)·
//       ID 역전(앞서 받은 ID 보다 작음)·media.created 전에 온 media.ready 를 센다.
//       누락은 끝난 뒤 "서버에 등록된 장 수 × 구독자 수 − 구독자들이 받은 media.created 수"로 구한다.
//       클라이언트가 시간 초과로 실패로 센 등록도 서버에는 남을 수 있어, 등록 수는 teardown 에서 서버에 다시 묻는다.
//     업로드 (UPLOADERS명, 0 이면 업로드 없이 구독만): 10장 묶음을 쉬지 않고(UPLOAD_PAUSE) 반복 등록한다. lib/upload.js 참고
//   방 B~D — fixtures 의 방. 기존 사진 목록을 반복 조회한다 (READERS명, GET /rooms/{roomId}/media/all)
//
//   이벤트 전달 지연·방 락 대기/보유 시간은 서버 지표(/actuator/prometheus)로 본다. k6 가상 사용자끼리는
//   값을 주고받을 수 없어 "업로더가 등록한 시각"과 "구독자가 받은 시각"을 클라이언트에서 짝지을 수 없다.
//
//   fixtures 는 방 여러 개로 준비한다. 업로더는 참여자(방장 제외) 중 앞에서부터 쓰고, 구독자는 방장·참여자 계정을
//   돌려 쓴다(같은 계정이 연결을 여러 개 연다). 예: node seed/seed.mjs --rooms 3 --members 10 --photos 100
//   검색 분석이 켜진 서버는 올린 장마다 외부 AI 호출이 생긴다 — ALLOW_AI_COST=1 이 없으면 시작하지 않는다.
//
//   PROFILE=smoke  구독자 3·업로더 2(묶음 2개씩)·조회 2명. 중복·역전·누락·구독 실패가 하나라도 있으면 실패로 끝난다.
//   PROFILE=load   예열(WARMUP) 뒤 본 측정(DURATION). 업로드·조회는 구간을 나눠 집계하고, 구독은 전 구간을 한 번에 본다.
import exec from 'k6/execution';
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir, toSeconds,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, jitter, runInfo, spreadFirstIteration, stopConditions } from '../lib/run.js';
import { holdRoomStream } from '../lib/sse.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';
import {
  BATCH_SIZE, UPLOAD_APIS, createDedicatedRoom, deleteRoomWithMedia, guardAiCost, loadImages, uploadBatch,
} from '../lib/upload.js';

const SCENARIO = 'sse-fanout';
const READ_API = 'other_room_list';
const APIS = [...UPLOAD_APIS, READ_API];
const MAX_OTHER_ROOMS = 3;
// smoke 는 업로드가 정해진 횟수만 돌고 끝난다. 그 안에 끝나지 않으면 실패로 본다.
const SMOKE_LOAD_SEC = 60;

const uploadedPhotos = new Counter('fanout_registered');
const serverPhotos = new Counter('fanout_server_photos');
const createdReceived = new Counter('fanout_created_received');
const duplicates = new Counter('fanout_duplicate');
const idReversed = new Counter('fanout_id_reversed');
const readyBeforeCreated = new Counter('fanout_ready_before_created');
const perSubscriberCreated = new Trend('fanout_subscriber_created');

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const RESULTS_DIR = resolveResultsDir();
const NAME_TAG = `lt${fixtures.runId.slice(-4)}f`;

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);
const SMOKE = PROFILE === 'smoke';

const IMAGES = loadImages(env('UPLOAD_IMAGES', 'images/upload-set'));
const HOST = fixtures.rooms[0].host;
const PARTICIPANTS = fixtures.rooms.flatMap((room) => room.participants).map((u) => ({ userId: u.userId, token: u.accessToken }));
const UPLOADERS = pickUploaders(intEnv('UPLOADERS', SMOKE ? 2 : 5));
const SUBSCRIBER_ACCOUNTS = [{ userId: HOST.userId, token: HOST.accessToken }, ...PARTICIPANTS];
const SUBSCRIBERS = intEnv('SUBSCRIBERS', SMOKE ? 3 : 20);
const READER_USERS = pickReaders();
const READERS = intEnv('READERS', SMOKE ? 2 : 15);
// 구독이 서버에 등록된 뒤에 업로드·조회를 시작한다. 서버는 구독 등록 즉시 이벤트를 보내므로 몇 초면 충분하다.
// 느린 구독자(scripts/slow-subscriber.mjs)를 붙일 때는 그 스크립트가 방을 찾아 입장할 시간만큼 늘린다.
const LOAD_START_SEC = toSeconds(durationEnv('LOAD_START', '5s'));
const CONFIG = buildConfig();
const PHASES = SMOKE ? ['smoke'] : ['warmup', 'main'];
const JUDGED = SMOKE ? 'smoke' : 'main';

if (SUBSCRIBERS < 1) throw new Error('SUBSCRIBERS 는 1 이상');

const SCENARIOS = buildScenarios();

export const options = {
  scenarios: SCENARIOS,
  setupTimeout: '2m',
  teardownTimeout: '5m',
  thresholds: {
    ...trackApis(APIS, PHASES),
    ...trackMetrics({
      fanout_registered: 'count', fanout_server_photos: 'count', fanout_created_received: 'count', fanout_duplicate: 'count', fanout_id_reversed: 'count',
      fanout_ready_before_created: 'count', fanout_subscriber_created: 'trend', 'sse_ok{phase:subscribe}': 'rate',
      'sse_open_ms{phase:subscribe}': 'trend',
    }),
    // 본 측정의 판정 구간은 구독 연결 뒤 예열이 끝난 시점에 시작한다.
    ...stopConditions(PROFILE, JUDGED, SMOKE ? {} : { main: { startTime: `${LOAD_START_SEC + toSeconds(CONFIG.phaseDurations.warmup)}s` } }),
    ...(SMOKE && {
      'sse_ok{phase:subscribe}': ['rate==1'], fanout_duplicate: ['count==0'], fanout_id_reversed: ['count==0'], fanout_ready_before_created: ['count==0'],
    }),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function pickUploaders(count) {
  if (count > PARTICIPANTS.length) throw new Error(`UPLOADERS(${count})가 fixtures 참여자 수(${PARTICIPANTS.length})보다 많습니다`);
  return PARTICIPANTS.slice(0, count);
}

// 다른 방 조회는 fixtures 의 방(최대 3개)에서 각 방 사람이 한다. 방 A 는 새로 만든 방이라 fixtures 방과 겹치지 않는다.
function pickReaders() {
  const roomIds = new Set(fixtures.rooms.slice(0, MAX_OTHER_ROOMS).map((room) => room.roomId));
  return buildUsers(fixtures).filter((user) => roomIds.has(user.roomId));
}

function buildConfig() {
  const common = {
    profile: PROFILE, subscribers: SUBSCRIBERS, uploaders: UPLOADERS.length, readers: READERS, batchSize: BATCH_SIZE,
    loadStartSec: LOAD_START_SEC,
    otherRooms: [...new Set(READER_USERS.map((u) => u.roomId))], images: IMAGES.length,
  };
  if (SMOKE) {
    return { ...common, iterations: intEnv('ITERATIONS', 2), drainSec: toSeconds(durationEnv('DRAIN', '20s')), phaseDurations: { smoke: null } };
  }
  return {
    ...common,
    uploadPauseSec: Number(env('UPLOAD_PAUSE', '0')),
    readThinkTimeSec: Number(env('READ_THINK_TIME', '1')),
    drainSec: toSeconds(durationEnv('DRAIN', '20s')),
    phaseDurations: { warmup: durationEnv('WARMUP', '30s'), main: durationEnv('DURATION', '2m') },
  };
}

// 업로드는 예열·본 측정을 시나리오 하나로 돌리고 구간 태그만 바꾼다(phaseTag).
// 구간마다 시나리오를 나누면 예열 마지막 묶음이 끝날 때까지 본 측정 업로더와 겹쳐 업로더 수가 순간적으로 두 배가 된다.
function buildScenarios() {
  const start = `${LOAD_START_SEC}s`;
  const subscribe = {
    executor: 'per-vu-iterations', vus: SUBSCRIBERS, iterations: 1, exec: 'subscribe',
    maxDuration: `${loadSeconds() + CONFIG.drainSec + 60}s`, tags: { phase: 'subscribe' },
  };
  const scenarios = { subscribe };
  if (SMOKE) {
    if (UPLOADERS.length > 0) {
      scenarios.upload = {
        executor: 'per-vu-iterations', vus: UPLOADERS.length, iterations: CONFIG.iterations, maxDuration: `${SMOKE_LOAD_SEC}s`,
        startTime: start, exec: 'upload', tags: { phase: 'smoke' },
      };
    }
    if (READERS > 0) {
      scenarios.read = {
        executor: 'per-vu-iterations', vus: READERS, iterations: 3, maxDuration: `${SMOKE_LOAD_SEC}s`,
        startTime: start, exec: 'read', tags: { phase: 'smoke' },
      };
    }
    return scenarios;
  }
  const duration = `${loadSeconds()}s`;
  if (UPLOADERS.length > 0) {
    scenarios.upload = { executor: 'constant-vus', vus: UPLOADERS.length, duration, startTime: start, exec: 'upload', gracefulStop: '30s' };
  }
  if (READERS > 0) {
    scenarios.read = { executor: 'constant-vus', vus: READERS, duration, startTime: start, exec: 'read', gracefulStop: '10s' };
  }
  return scenarios;
}

function loadSeconds() {
  if (SMOKE) return SMOKE_LOAD_SEC;
  return toSeconds(CONFIG.phaseDurations.warmup) + toSeconds(CONFIG.phaseDurations.main);
}

// 반복을 시작한 시각으로 구간을 정한다. 이 반복에서 나간 요청은 모두 그 구간으로 집계된다.
function phaseTag() {
  if (SMOKE) return;
  const elapsedSec = (Date.now() - exec.scenario.startTime) / 1000;
  exec.vu.metrics.tags.phase = elapsedSec < toSeconds(CONFIG.phaseDurations.warmup) ? 'warmup' : 'main';
}

export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  const searchEnabled = UPLOADERS.length > 0 ? guardAiCost(BASE_URL, { roomId: fixtures.rooms[0].roomId, token: HOST.accessToken }) : null;

  // 방 A 에 들어갈 계정: 업로더 전원 + 구독에 쓰일 계정. 방장은 만든 사람이라 이미 들어가 있다.
  const used = new Map();
  for (const user of UPLOADERS) used.set(user.userId, user);
  for (let i = 0; i < Math.min(SUBSCRIBERS, SUBSCRIBER_ACCOUNTS.length); i++) used.set(SUBSCRIBER_ACCOUNTS[i].userId, SUBSCRIBER_ACCOUNTS[i]);
  used.delete(HOST.userId);
  const room = createDedicatedRoom(client, { host: HOST, members: [...used.values()], nameTag: NAME_TAG, label: '방 A(구독·업로드 전용)' });

  return {
    ...runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: used.size + 1 }),
    roomId: room.roomId,
    code: room.code,
    searchEnabled,
    // 구독은 업로드가 끝나고 graceful stop·전달까지 기다린 뒤 닫는다.
    watchUntil: Date.now() + (LOAD_START_SEC + loadSeconds() + CONFIG.drainSec) * 1000,
  };
}

const client = createClient(BASE_URL);

export function subscribe(data) {
  const account = SUBSCRIBER_ACCOUNTS[exec.scenario.iterationInTest % SUBSCRIBER_ACCOUNTS.length];
  const seenIds = new Set();
  const createdMedia = new Set();
  let lastId = -1;
  let created = 0;

  holdRoomStream(BASE_URL, {
    roomId: data.roomId, token: account.token, until: data.watchUntil, name: 'fanout_events', tags: { phase: 'subscribe' },
    onEvent: (name, payload, receivedAt, rawId) => {
      const id = Number(rawId);
      if (rawId && Number.isFinite(id)) {
        if (seenIds.has(id)) {
          duplicates.add(1, { kind: 'event_id' });
          return;
        }
        seenIds.add(id);
        if (id < lastId) idReversed.add(1);
        lastId = Math.max(lastId, id);
      }
      const mediaId = payload?.mediaId;
      if (!mediaId) return;
      if (name === 'media.created') {
        if (createdMedia.has(mediaId)) {
          duplicates.add(1, { kind: 'media_created' });
          return;
        }
        createdMedia.add(mediaId);
        created++;
        createdReceived.add(1);
      } else if (name === 'media.ready' && !createdMedia.has(mediaId)) {
        readyBeforeCreated.add(1);
      }
    },
  });
  perSubscriberCreated.add(created);
}

export function upload(data) {
  phaseTag();
  const uploader = UPLOADERS[(exec.vu.idInTest - 1) % UPLOADERS.length];
  const registered = uploadBatch(client, {
    roomId: data.roomId, token: uploader.token, images: IMAGES, key: exec.scenario.iterationInTest, nameTag: NAME_TAG,
  });
  if (registered > 0) {
    uploadedPhotos.add(registered);
  }
  if (!SMOKE && CONFIG.uploadPauseSec > 0) sleep(jitter(CONFIG.uploadPauseSec));
}

export function read() {
  phaseTag();
  spreadFirstIteration(SMOKE ? 0 : 2);
  const user = READER_USERS[(exec.vu.idInTest - 1) % READER_USERS.length];
  client.get(READ_API, `/rooms/${user.roomId}/media/all`, {
    token: user.token,
    validate: (body) => {
      if (!body || !Array.isArray(body.items)) return 'items 없음';
      if (body.items.length !== user.expectedPhotos) return `items ${body.items.length} ≠ ${user.expectedPhotos}`;
      return null;
    },
  });
  if (!SMOKE && CONFIG.readThinkTimeSec > 0) sleep(jitter(CONFIG.readThinkTimeSec));
}

export function teardown(data) {
  if (!data?.roomId) return;
  const photos = deleteRoomWithMedia(client, { roomId: data.roomId, hostToken: HOST.accessToken, label: '방 A(구독·업로드 전용)' });
  if (photos !== null) serverPhotos.add(photos);
}

// 구독자별 수신 집계를 결과 JSON 의 delivery 로 묶는다. 누락은 여기서 계산한다.
export function handleSummary(data) {
  const outputs = writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR,
    extras: ['fanout_registered', 'fanout_server_photos', 'fanout_created_received', 'fanout_duplicate', 'fanout_id_reversed', 'fanout_ready_before_created',
      'fanout_subscriber_created', 'sse_ok{phase:subscribe}', 'sse_open_ms{phase:subscribe}'],
  });
  const count = (name) => data.metrics[name]?.values?.count ?? 0;
  const clientRegistered = count('fanout_registered');
  // teardown 이 서버 사진 수를 못 읽었으면(목록 실패) 클라이언트가 센 값으로 대신한다.
  // 기준을 걸어 둔 지표라 값을 넣지 않아도 count 0 으로 나온다. 업로드가 없었으면 둘 다 0 이다.
  const serverKnown = count('fanout_server_photos') > 0 || clientRegistered === 0;
  const registered = serverKnown ? count('fanout_server_photos') : clientRegistered;
  const received = count('fanout_created_received');
  const perSub = data.metrics.fanout_subscriber_created?.values;
  const delivery = {
    subscribers: SUBSCRIBERS,
    registeredPhotos: registered,
    registeredSource: serverKnown ? 'server' : 'client',
    clientRegisteredPhotos: clientRegistered,
    expectedCreatedEvents: registered * SUBSCRIBERS,
    receivedCreatedEvents: received,
    missingCreatedEvents: registered * SUBSCRIBERS - received,
    duplicates: count('fanout_duplicate'),
    idReversed: count('fanout_id_reversed'),
    readyBeforeCreated: count('fanout_ready_before_created'),
    minCreatedPerSubscriber: perSub?.min ?? null,
    subscribeOkRate: data.metrics['sse_ok{phase:subscribe}']?.values?.rate ?? null,
  };

  const jsonPath = Object.keys(outputs).find((key) => key.endsWith(`-${SCENARIO}.json`));
  const report = JSON.parse(outputs[jsonPath]);
  report.delivery = delivery;
  if (SMOKE && delivery.missingCreatedEvents > 0) {
    report.thresholds.passed = false;
    report.thresholds.failed.push(`media.created 누락 ${delivery.missingCreatedEvents}건`);
  }
  outputs[jsonPath] = JSON.stringify(report, null, 2);
  outputs.stdout += [
    `-- 전달: 등록 ${registered}장 × 구독자 ${SUBSCRIBERS} = 기대 ${delivery.expectedCreatedEvents}, 받음 ${received}, 누락 ${delivery.missingCreatedEvents}`,
    `   중복 ${delivery.duplicates} · ID 역전 ${delivery.idReversed} · created 전 ready ${delivery.readyBeforeCreated} · 구독자 최소 수신 ${delivery.minCreatedPerSubscriber}`,
    '',
  ].join('\n');
  return outputs;
}
