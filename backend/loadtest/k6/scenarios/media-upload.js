// 사진 업로드 부하. 10장 묶음을 정해진 장/초로 올리고, 썸네일이 준비되는 데 걸린 시간을 SSE 로 잰다.
// SSE 를 쓰므로 xk6-sse 가 든 bin/k6 로 실행한다.
//
//   업로드 (반복 하나 = 10장 묶음 하나, 가상 사용자마다 업로더 계정 하나):
//     POST /media/upload-urls  10장 발급 — 거절 0장이어야 한다
//     PUT  <서명 URL>           스토리지 직접 업로드. 사용자별 동시 3개
//     POST /media               완료 등록 — PUT 에 성공한 장이 모두 등록되어야 한다 (부분 실패는 실패)
//
//   관찰 (가상 사용자 1명):
//     GET /rooms/{roomId}/events  media.created(완료 등록)와 media.ready(썸네일 준비)를 mediaId 로 짝지어
//                                 지연(media_ready_ms)·누락(media_ready_missing)·시간 초과(media_ready_late)를 센다
//
//   업로드는 실행마다 새로 만든 전용 방에 한다. k6 는 fixtures 에 쓸 수 없어 올린 사진을 기록할 수 없으므로,
//   방 안의 사진은 모두 이 실행이 올린 것이 되게 하고 끝날 때(teardown) 방째 정리한다.
//   검색 분석이 켜진 서버는 올린 장마다 외부 AI 호출이 생긴다 — ALLOW_AI_COST=1 이 없으면 시작하지 않는다.
//
//   PROFILE=smoke  업로더 3명이 묶음 2개씩(60장). 업로드·ready 가 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   PHOTOS_PER_SEC(장/초)로 예열 뒤 본 측정. #517 기준 2 → 6 → 12 → 18.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

import {
  durationEnv, env, fromCwd, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { businessFailures, businessOk, createClient } from '../lib/http.js';
import { runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { holdRoomStream } from '../lib/sse.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';

const SCENARIO = 'media-upload';
// 스토리지 PUT 은 우리 서버가 아니라 R2 로 가는 요청이라 API 와 따로 본다(storage_put).
const APIS = ['upload_issue', 'storage_put', 'upload_complete'];
const BATCH_SIZE = 10;
const PUT_CONCURRENCY = 3;
const API_PREFIX = '/api/v1';
// 서버 RoomName.MAX_LENGTH
const MAX_ROOM_NAME = 12;

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

export const options = {
  scenarios: {
    ...buildUploadScenarios(),
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
    ...stopConditions(PROFILE, JUDGED),
    ...(PROFILE === 'smoke' && { media_ready_ok: ['rate==1'], 'sse_ok{phase:watch}': ['rate==1'] }),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function loadImages(dir) {
  let manifest;
  try {
    manifest = JSON.parse(open(fromCwd(`${dir}/manifest.json`)));
  } catch {
    throw new Error(`${dir}/manifest.json 이 없습니다. 먼저 준비한다: node seed/upload-images.mjs`);
  }
  // 가상 사용자마다 이미지를 메모리에 올린다. 장 수 × 업로더 수만큼 쓰므로 큰 실제 사진은 장 수를 줄인다.
  return manifest.files.map((file) => ({ ...file, bytes: open(fromCwd(`${dir}/${file.name}`), 'b') }));
}

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
  const phases = warmupMain({
    executor: 'constant-arrival-rate', rate: CONFIG.photosPerSec, timeUnit: `${BATCH_SIZE}s`,
    preAllocatedVUs: UPLOADERS.length, maxVUs: UPLOADERS.length, exec: 'upload',
  }, CONFIG.phaseDurations);
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

  // 검색 분석이 켜진 서버는 업로드마다 외부 AI 호출이 생긴다. 시드의 --allow-ai-cost 와 같은 확인이다.
  const probe = http.get(`${BASE_URL}${API_PREFIX}/rooms/${fixtures.rooms[0].roomId}/media/all?query=loadtest%20probe`, {
    headers: { Authorization: `Bearer ${HOST.accessToken}` }, tags: { api: 'setup' }, responseCallback: http.expectedStatuses(200, 503),
  });
  if (probe.status === 200 && env('ALLOW_AI_COST') !== '1') {
    exec.test.abort('대상 서버의 이미지 검색이 켜져 있어 올린 장마다 외부 AI 호출이 생깁니다. 비용 한도를 확인했으면 -e ALLOW_AI_COST=1');
  }

  const name = `${NAME_TAG}${Date.now().toString(36)}`.slice(0, MAX_ROOM_NAME);
  const created = client.post('setup', '/rooms', { token: HOST.accessToken, body: { name, uploadPolicy: 'everyone' }, expectedStatus: 201 });
  if (!created.ok) exec.test.abort(`업로드 전용 방을 만들지 못했습니다: ${created.reason}`);
  const room = created.data;
  for (const uploader of UPLOADERS) {
    const joined = client.post('setup', `/rooms/${room.roomId}/members`, { token: uploader.token, expectedStatus: 201 });
    if (!joined.ok) {
      exec.test.abort(`userId=${uploader.userId} 이 업로드 방에 입장하지 못했습니다: ${joined.reason}. 방 roomId=${room.roomId} 를 지운다`);
    }
  }
  console.log(`업로드 전용 방 roomId=${room.roomId} code=${room.code} (끝나면 사진과 함께 지운다)`);
  return {
    ...runInfo({ baseUrl: BASE_URL, appVersion: JSON.parse(versionRes.body), fixturesPath, fixtures, accounts: UPLOADERS.length }),
    roomId: room.roomId,
    code: room.code,
    searchEnabled: probe.status === 200,
    watchUntil: Date.now() + (UPLOAD_START_SEC + CONFIG.uploadSec + CONFIG.readyTimeoutSec) * 1000,
  };
}

const client = createClient(BASE_URL);

export function upload(data) {
  const uploader = UPLOADERS[(exec.vu.idInTest - 1) % UPLOADERS.length];
  const base = `/rooms/${data.roomId}/media`;
  const iteration = exec.scenario.iterationInTest;
  const files = Array.from({ length: BATCH_SIZE }, (_, k) => {
    const image = IMAGES[(iteration * BATCH_SIZE + k) % IMAGES.length];
    const ext = image.name.split('.').pop();
    return { image, request: { fileName: `${NAME_TAG}-${iteration}-${k}.${ext}`, mimeType: image.mimeType, size: image.size } };
  });

  const issued = client.post('upload_issue', `${base}/upload-urls`, {
    token: uploader.token,
    body: { files: files.map((f) => f.request) },
    validate: (body) => {
      if (body.rejected.length > 0) return `발급 거절 ${body.rejected.length}장 (${body.rejected[0].code})`;
      if (body.issued.length !== files.length) return `발급 ${body.issued.length} ≠ ${files.length}`;
      return null;
    },
  });
  if (!issued.ok) return;

  const uploaded = putAll(issued.data.issued, files);
  if (uploaded.length === 0) return;

  const completed = client.post('upload_complete', base, {
    token: uploader.token,
    body: { mediaIds: uploaded },
    expectedStatus: 201,
    validate: (body) => {
      if (body.failed.length > 0) return `완료 등록 부분 실패 ${body.failed.length}장 (${body.failed[0].code})`;
      if (body.registered.length !== uploaded.length) return `등록 ${body.registered.length} ≠ PUT 성공 ${uploaded.length}`;
      return null;
    },
  });
  if (completed.data && Array.isArray(completed.data.registered)) {
    mediaRegistered.add(completed.data.registered.length);
  }
}

// 사용자별 동시 3개씩 PUT 한다. 발급 응답의 method·headers 를 그대로 써야 서명이 맞는다.
function putAll(issuedList, files) {
  const uploaded = [];
  for (let i = 0; i < issuedList.length; i += PUT_CONCURRENCY) {
    const chunk = issuedList.slice(i, i + PUT_CONCURRENCY);
    const requests = chunk.map((item) => {
      const file = files.find((f) => f.request.fileName === item.fileName);
      return {
        method: item.method, url: item.uploadUrl, body: file.image.bytes,
        params: { headers: item.headers, tags: { api: 'storage_put' }, timeout: '60s', responseCallback: http.expectedStatuses(200) },
      };
    });
    const responses = http.batch(requests);
    responses.forEach((res, k) => {
      const ok = res.status === 200;
      businessOk.add(ok, { api: 'storage_put' });
      if (ok) {
        uploaded.push(chunk[k].mediaId);
      } else {
        businessFailures.add(1, { api: 'storage_put', reason: `status ${res.status}` });
        console.warn(`[storage_put] mediaId=${chunk[k].mediaId} 실패: status ${res.status}`);
      }
    });
  }
  return uploaded;
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

// 전용 방의 사진은 모두 이 실행이 올린 것이다. 사진을 먼저 지워 스토리지 원본까지 정리하고 방을 지운다.
export function teardown(data) {
  if (!data?.roomId) return;
  const res = client.get('teardown', `/rooms/${data.roomId}/media/all`, { token: HOST.accessToken });
  const ids = res.ok ? res.data.items.map((item) => item.mediaId) : [];
  let deleted = 0;
  for (let i = 0; i < ids.length; i += 500) {
    const del = client.del('teardown', `/rooms/${data.roomId}/media`, { token: HOST.accessToken, body: { mediaIds: ids.slice(i, i + 500) } });
    if (del.ok) deleted += del.data.deletedCount;
  }
  const room = client.del('teardown', `/rooms/${data.roomId}`, { token: HOST.accessToken });
  const message = `업로드 전용 방 roomId=${data.roomId} 정리: 사진 ${deleted}/${ids.length}장 삭제, 방 ${room.ok ? '삭제' : `삭제 실패(${room.reason})`}`;
  if (!res.ok || deleted !== ids.length || !room.ok) {
    console.error(`${message} — 남은 사진·방을 방장 토큰으로 직접 지운다`);
  } else {
    console.log(message);
  }
}

export function handleSummary(data) {
  return writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR,
    extras: ['media_registered', 'media_created_seen', 'media_ready_ms', 'media_ready_ok', 'media_ready_missing', 'media_ready_late',
      'media_ready_without_created', 'sse_ok{phase:watch}'],
  });
}
