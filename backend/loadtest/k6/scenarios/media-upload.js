// 사진 업로드 부하. 10장 묶음을 정해진 장/초로 올리고, 썸네일이 준비되는 데 걸린 시간을 SSE 로 잰다.
// SSE 를 쓰므로 xk6-sse 가 든 bin/k6 로 실행한다.
//
//   업로드 (반복 하나 = 10장 묶음 하나, 가상 사용자마다 업로더 계정 하나): lib/upload.js 참고
//
//   관찰 (가상 사용자 1명):
//     GET /rooms/{roomId}/events  media.created(완료 등록)와 media.ready(썸네일 준비)를 mediaId 로 짝지어
//                                 지연(media_ready_ms)·누락(media_ready_missing)·시간 초과(media_ready_late)를 센다
//
//   업로드는 실행마다 새로 만든 전용 방에 하고 끝날 때(teardown) 방째 정리한다.
//   검색 분석이 켜진 서버는 올린 장마다 외부 AI 호출이 생긴다 — ALLOW_AI_COST=1 이 없으면 시작하지 않는다.
//
//   PROFILE=smoke  업로더 3명이 묶음 2개씩(60장). 업로드·ready 가 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   PHOTOS_PER_SEC(장/초)로 예열 뒤 본 측정. #517 기준 2 → 6 → 12 → 18.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

import {
  durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { holdRoomStream } from '../lib/sse.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';
import {
  BATCH_SIZE, PUT_CONCURRENCY, UPLOAD_APIS, createDedicatedRoom, deleteRoomWithMedia, guardAiCost, loadImages, uploadBatch,
} from '../lib/upload.js';

const SCENARIO = 'media-upload';
const APIS = UPLOAD_APIS;

const mediaRegistered = new Counter('media_registered');
const createdSeen = new Counter('media_created_seen');
const readyMs = new Trend('media_ready_ms', true);
const readyOk = new Rate('media_ready_ok');
const readyMissing = new Counter('media_ready_missing');
const readyLate = new Counter('media_ready_late');
const readyWithoutCreated = new Counter('media_ready_without_created');

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const RESULTS_DIR = resolveResultsDir();
const NAME_TAG = `lt${fixtures.runId.slice(-4)}u`;

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const IMAGES = loadImages(env('UPLOAD_IMAGES', 'images/upload-set'));
const HOST = fixtures.rooms[0].host;
const UPLOADERS = buildUploaders(intEnv('UPLOADERS', PROFILE === 'smoke' ? 3 : 10));
const CONFIG = buildConfig();
const PHASES = Object.keys(CONFIG.phaseDurations);
const JUDGED = PROFILE === 'smoke' ? 'smoke' : 'main';
// 업로드는 관찰자가 구독을 연 뒤에 시작한다. 서버는 구독 등록 즉시 이벤트를 쌓아 두므로 몇 초면 충분하다.
const UPLOAD_START_SEC = 3;

const UPLOAD_SCENARIOS = buildUploadScenarios();

export const options = {
  scenarios: {
    ...UPLOAD_SCENARIOS,
    watch: {
      executor: 'per-vu-iterations', vus: 1, iterations: 1, exec: 'watch',
      maxDuration: `${UPLOAD_START_SEC + CONFIG.uploadSec + CONFIG.readyTimeoutSec + 60}s`, tags: { phase: 'watch' },
    },
  },
  setupTimeout: '2m',
  teardownTimeout: '5m',
  thresholds: {
    ...trackApis(APIS, PHASES),
    ...trackMetrics({
      media_registered: 'count', media_created_seen: 'count', media_ready_ms: 'trend', media_ready_ok: 'rate',
      media_ready_missing: 'count', media_ready_late: 'count', media_ready_without_created: 'count', 'sse_ok{phase:watch}': 'rate',
    }),
    ...stopConditions(PROFILE, JUDGED, UPLOAD_SCENARIOS),
    ...(PROFILE === 'smoke' && { media_ready_ok: ['rate==1'], 'sse_ok{phase:watch}': ['rate==1'] }),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// 업로더는 fixtures 의 기존 계정(방장 제외)을 쓴다. setup 에서 업로드 전용 방에 입장시킨다.
function buildUploaders(count) {
  const candidates = fixtures.rooms.flatMap((room) => room.participants);
  if (count < 1) throw new Error('UPLOADERS 는 1 이상');
  if (count > candidates.length) throw new Error(`UPLOADERS(${count})가 fixtures 참여자 수(${candidates.length})보다 많습니다`);
  return candidates.slice(0, count).map((user) => ({ userId: user.userId, token: user.accessToken }));
}

function buildConfig() {
  const readyTimeoutSec = toSeconds(durationEnv('READY_TIMEOUT', '60s'));
  if (PROFILE === 'smoke') {
    const iterations = intEnv('ITERATIONS', 2);
    return {
      profile: PROFILE, uploaders: UPLOADERS.length, iterations, batchSize: BATCH_SIZE, putConcurrency: PUT_CONCURRENCY,
      readyTimeoutSec, uploadSec: 30, images: IMAGES.length, phaseDurations: { smoke: null },
    };
  }
  const photosPerSec = intEnv('PHOTOS_PER_SEC', 2);
  if (photosPerSec < 1) throw new Error('PHOTOS_PER_SEC 는 1 이상');
  const warmup = durationEnv('WARMUP', '1m');
  const main = durationEnv('DURATION', '3m');
  return {
    profile: PROFILE, photosPerSec, uploaders: UPLOADERS.length, batchSize: BATCH_SIZE, putConcurrency: PUT_CONCURRENCY,
    readyTimeoutSec, uploadSec: toSeconds(warmup) + toSeconds(main), images: IMAGES.length,
    phaseDurations: { warmup, main },
  };
}

function buildUploadScenarios() {
  const start = `${UPLOAD_START_SEC}s`;
  if (PROFILE === 'smoke') {
    return {
      smoke: {
        executor: 'per-vu-iterations', vus: UPLOADERS.length, iterations: CONFIG.iterations, maxDuration: `${CONFIG.uploadSec}s`,
        startTime: start, exec: 'upload', tags: { phase: 'smoke' },
      },
    };
  }
  // 장/초를 10장 묶음 수로 바꾼다: 초당 N장 = 10초에 묶음 N개.
  // 한 업로더는 한 번에 한 묶음만 올린다(사용자별 PUT 동시 3개). 업로더가 모자라면 dropped iterations 로 남는다.
  // 예약만 남고 완료되지 않은 업로드가 생기지 않도록 예열 마지막 묶음은 끝까지 올린다. 요청률 고정이라 이 동안 새 반복은 늘지 않는다.
  const phases = warmupMain({
    executor: 'constant-arrival-rate', rate: CONFIG.photosPerSec, timeUnit: `${BATCH_SIZE}s`,
    preAllocatedVUs: UPLOADERS.length, maxVUs: UPLOADERS.length, exec: 'upload',
  }, CONFIG.phaseDurations, { warmupGracefulStop: '10s' });
  phases.warmup.startTime = start;
  phases.main.startTime = `${UPLOAD_START_SEC + toSeconds(CONFIG.phaseDurations.warmup)}s`;
  return phases;
}

function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
  return Number(match[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
}

export function setup() {
  const client = createClient(BASE_URL);
  const versionRes = http.get(`${BASE_URL}/version`, { tags: { api: 'setup' } });
  if (versionRes.status !== 200) exec.test.abort(`대상 서버 응답 없음: GET /version → ${versionRes.status}`);

  const searchEnabled = guardAiCost(BASE_URL, { roomId: fixtures.rooms[0].roomId, token: HOST.accessToken });
  const room = createDedicatedRoom(client, { host: HOST, members: UPLOADERS, nameTag: NAME_TAG, label: '업로드 전용 방' });
  return {
    ...runInfo({ baseUrl: BASE_URL, appVersion: JSON.parse(versionRes.body), fixturesPath, fixtures, accounts: UPLOADERS.length }),
    roomId: room.roomId,
    code: room.code,
    searchEnabled,
    watchUntil: Date.now() + (UPLOAD_START_SEC + CONFIG.uploadSec + CONFIG.readyTimeoutSec) * 1000,
  };
}

const client = createClient(BASE_URL);

export function upload(data) {
  const uploader = UPLOADERS[(exec.vu.idInTest - 1) % UPLOADERS.length];
  const registered = uploadBatch(client, {
    roomId: data.roomId, token: uploader.token, images: IMAGES, key: exec.scenario.iterationInTest, nameTag: NAME_TAG,
  });
  if (registered > 0) {
    mediaRegistered.add(registered);
  }
}

// media.created 를 받은 mediaId 마다 media.ready 가 READY_TIMEOUT 안에 오는지 본다.
export function watch(data) {
  const createdAt = new Map();
  const timeoutMs = CONFIG.readyTimeoutSec * 1000;
  holdRoomStream(BASE_URL, {
    roomId: data.roomId, token: HOST.accessToken, until: data.watchUntil, name: 'upload_events',
    onEvent: (name, payload, receivedAt) => {
      const mediaId = payload?.mediaId;
      if (!mediaId) return;
      if (name === 'media.created') {
        createdSeen.add(1);
        createdAt.set(mediaId, receivedAt);
      } else if (name === 'media.ready') {
        const started = createdAt.get(mediaId);
        if (started === undefined) {
          readyWithoutCreated.add(1);
          return;
        }
        createdAt.delete(mediaId);
        const elapsed = receivedAt - started;
        readyMs.add(elapsed);
        const inTime = elapsed <= timeoutMs;
        readyOk.add(inTime);
        if (!inTime) readyLate.add(1);
      }
    },
  });
  // 관찰이 끝날 때까지 ready 가 오지 않은 장은 누락이다.
  for (const mediaId of createdAt.keys()) {
    readyOk.add(false);
    readyMissing.add(1);
    console.warn(`[media.ready] mediaId=${mediaId} 관찰 종료까지 ready 없음`);
  }
}

export function teardown(data) {
  if (!data?.roomId) return;
  deleteRoomWithMedia(client, { roomId: data.roomId, hostToken: HOST.accessToken, label: '업로드 전용 방' });
}

export function handleSummary(data) {
  return writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR,
    extras: ['media_registered', 'media_created_seen', 'media_ready_ms', 'media_ready_ok', 'media_ready_missing', 'media_ready_late',
      'media_ready_without_created', 'sse_ok{phase:watch}'],
  });
}
