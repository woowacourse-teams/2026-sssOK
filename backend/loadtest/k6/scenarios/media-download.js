// 다운로드 URL 발급 부하. 정해진 요청/초로 다운로드 URL 을 받고, 그 URL 로 실제 파일을 내려받아 확인한다.
//
//   DOWNLOAD_MODE=single  GET  /downloads/media/{id}  302 리다이렉트 → Location 으로 원본 GET
//   DOWNLOAD_MODE=batch   POST /downloads/batch       BATCH_SIZE 장의 서명 URL 발급 → BATCH_FETCH 만큼 원본 GET
//
//   원본 GET 은 우리 서버가 아니라 스토리지(R2)로 가는 요청이라 storage_get 으로 따로 집계한다.
//   받은 파일 크기가 시드 때 올린 크기와 같아야 성공이다. 빈 응답·다른 파일을 성공으로 세지 않는다.
//
//   PROFILE=smoke  고른 방식으로 5번. 다건은 받은 URL 을 모두 내려받는다. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   RATE(요청/초)로 예열 뒤 본 측정. #517 기준 1 → 3 → 5 → 10.
import http from 'k6/http';
import exec from 'k6/execution';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { businessFailures, businessOk, createClient } from '../lib/http.js';
import { checkTarget, pick, pickMany, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, writeSummary } from '../lib/summary.js';

const SCENARIO = 'media-download';
const API_PREFIX = '/api/v1';
// 서버 CreateBatchDownloadService 의 대상 상한
const MAX_BATCH = 1000;

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();
// 원본 크기 확인용. 다운로드는 원본 업로드가 끝난 사진이면 되지만 시드 검증을 통과한 READY 만 쓴다.
const SIZES = Object.fromEntries(fixtures.rooms.flatMap((room) => room.photos.filter((p) => p.status === 'READY').map((p) => [p.mediaId, p.size])));

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);
const MODE = env('DOWNLOAD_MODE', 'single');
if (!['single', 'batch'].includes(MODE)) throw new Error(`DOWNLOAD_MODE 는 single 또는 batch (입력: ${MODE})`);

const BATCH_SIZE = intEnv('BATCH_SIZE', 10);
const BATCH_FETCH = env('BATCH_FETCH', PROFILE === 'smoke' ? 'all' : 'one');
if (BATCH_SIZE < 1 || BATCH_SIZE > MAX_BATCH) throw new Error(`BATCH_SIZE 는 1~${MAX_BATCH}`);
if (!['all', 'one', 'none'].includes(BATCH_FETCH)) throw new Error(`BATCH_FETCH 는 all·one·none (입력: ${BATCH_FETCH})`);
if (USERS.some((user) => user.photoIds.length < (MODE === 'batch' ? BATCH_SIZE : 1))) {
  throw new Error(`준비된 사진이 BATCH_SIZE(${BATCH_SIZE})보다 적은 방이 있습니다`);
}

const APIS = [MODE === 'single' ? 'download_single' : 'download_batch', 'storage_get'];
const CONFIG = buildConfig();
const PHASES = Object.keys(CONFIG.phaseDurations);

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    ...trackApis(APIS, PHASES),
    ...stopConditions(PROFILE, PROFILE === 'smoke' ? 'smoke' : 'main'),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function buildConfig() {
  const common = { profile: PROFILE, mode: MODE, ...(MODE === 'batch' && { batchSize: BATCH_SIZE, batchFetch: BATCH_FETCH }) };
  if (PROFILE === 'smoke') {
    return { ...common, vus: 1, iterations: intEnv('ITERATIONS', 5), phaseDurations: { smoke: null } };
  }
  const rate = intEnv('RATE', 1);
  if (rate < 1) throw new Error('RATE 는 1 이상');
  return {
    ...common, rate, vus: intEnv('VUS', Math.max(10, rate * 5)),
    phaseDurations: { warmup: durationEnv('WARMUP', '1m'), main: durationEnv('DURATION', '3m') },
  };
}

function buildScenarios() {
  if (PROFILE === 'smoke') {
    return {
      smoke: { executor: 'per-vu-iterations', vus: CONFIG.vus, iterations: CONFIG.iterations, maxDuration: '2m', tags: { phase: 'smoke' } },
    };
  }
  // 요청률을 고정한다. 원본 내려받기가 느려져 가상 사용자가 모자라면 dropped iterations 로 남는다.
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
  const user = USERS[exec.scenario.iterationInTest % USERS.length];
  if (MODE === 'single') {
    downloadSingle(user);
  } else {
    downloadBatch(user);
  }
}

// 단건은 서버가 서명 URL 로 302 를 돌려준다. 리다이렉트를 따라가지 않고 응답을 직접 확인한 뒤 원본을 따로 받는다.
function downloadSingle(user) {
  const mediaId = pick(user.photoIds);
  const path = `/rooms/${user.roomId}/downloads/media/${mediaId}`;
  const res = http.get(`${BASE_URL}${API_PREFIX}${path}`, {
    headers: { Authorization: `Bearer ${user.token}` }, redirects: 0, tags: { api: 'download_single' },
    responseCallback: http.expectedStatuses(302),
  });
  const location = res.headers.Location;
  const reason = res.status !== 302 ? `status ${res.status}` : (location ? null : 'Location 없음');
  record('download_single', reason, `GET ${path}`);
  if (reason === null) {
    fetchOriginal(location, mediaId);
  }
}

function downloadBatch(user) {
  const mediaIds = pickMany(user.photoIds, BATCH_SIZE);
  const batch = client.post('download_batch', `/rooms/${user.roomId}/downloads/batch`, {
    token: user.token,
    body: { mediaIds },
    validate: (body) => {
      if (!body || !Array.isArray(body.files)) return 'files 없음';
      if (body.files.length !== mediaIds.length) return `files ${body.files.length} ≠ ${mediaIds.length}`;
      const asked = new Set(mediaIds);
      if (body.files.some((file) => !asked.has(file.mediaId) || !file.downloadUrl)) return '요청하지 않은 사진이나 downloadUrl 없는 항목';
      return null;
    },
  });
  if (!batch.ok || BATCH_FETCH === 'none') return;
  const targets = BATCH_FETCH === 'all' ? batch.data.files : [pick(batch.data.files)];
  for (const file of targets) {
    fetchOriginal(file.downloadUrl, file.mediaId);
  }
}

// 서명 URL 로 원본을 받는다. 크기가 시드 때 올린 크기와 같아야 한다.
function fetchOriginal(url, mediaId) {
  const res = http.get(url, { responseType: 'binary', tags: { api: 'storage_get' }, timeout: '60s', responseCallback: http.expectedStatuses(200) });
  const expected = SIZES[mediaId];
  const received = res.body ? res.body.byteLength : 0;
  let reason = null;
  if (res.status !== 200) reason = `status ${res.status}`;
  else if (expected !== undefined && received !== expected) reason = `크기 ${received} ≠ ${expected}`;
  record('storage_get', reason, `mediaId=${mediaId}`);
}

function record(api, reason, label) {
  const ok = reason === null;
  businessOk.add(ok, { api });
  if (!ok) {
    businessFailures.add(1, { api, reason: reason.split(' ').slice(0, 2).join(' ') });
    console.warn(`[${api}] ${label} 실패: ${reason}`);
  }
}

export function handleSummary(data) {
  return writeSummary(data, { scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR });
}
