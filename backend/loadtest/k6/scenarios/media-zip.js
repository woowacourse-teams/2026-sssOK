// ZIP 다운로드 부하. 요청률이 아니라 "동시에 진행 중인 압축 작업 수"를 고정하고, 접수와 압축 완료를 따로 잰다.
//
//   가상 사용자 1명 = 압축 작업 1개씩 반복 (가상 사용자마다 다른 계정):
//     POST /downloads/zip          접수 — 202, jobId (zip_create 지연이 접수 시간)
//     GET  /downloads/zip/{jobId}  POLL_INTERVAL 마다 상태 조회 — READY 가 될 때까지 (zip_complete_ms)
//     GET  <downloadUrl>           완성된 zip 내려받기 (storage_get, ZIP_FETCH=0 이면 건너뜀)
//
//   zip_complete_ms 는 READY 를 처음 본 시각까지라 POLL_INTERVAL 단위로만 잡힌다(기본 2초면 최소 약 2초).
//   429(동시 작업 상한)·FAILED·ZIP_TIMEOUT 초과는 모두 실패다. 각각 zip_rejected·zip_failed·zip_timeout 으로 센다.
//   서버는 계정마다 동시 3개까지 받는다(download.max-concurrent-jobs-per-requester). 가상 사용자마다 계정을 따로 쓰므로
//   이 상한 때문에 429 가 나지는 않는다. 429 가 나면 서버 전체 처리 여력 문제다.
//   만든 zip 은 서버가 보관 기간(기본 1시간)이 지나면 정리한다.
//
//   PROFILE=smoke  1개 작업을 2번. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   ZIP_CONCURRENCY 개를 DURATION 동안 계속 돌린다. 1 → 3 → 5 로 늘려 본다.
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { businessFailures, businessOk, createClient } from '../lib/http.js';
import { checkTarget, pickMany, runInfo, stopConditions } from '../lib/run.js';
import { trackApis, trackMetrics, writeSummary } from '../lib/summary.js';

const SCENARIO = 'media-zip';
const PHASE = 'zip';
const APIS = ['zip_create', 'zip_status', 'storage_get'];

const zipCompleteMs = new Trend('zip_complete_ms', true);
const zipOk = new Rate('zip_ok');
const zipRejected = new Counter('zip_rejected');
const zipFailed = new Counter('zip_failed');
const zipTimeout = new Counter('zip_timeout');

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const ZIP_PHOTOS = intEnv('ZIP_PHOTOS', 30);
const ZIP_FETCH = env('ZIP_FETCH', '1') !== '0';
const POLL_INTERVAL = toSeconds(durationEnv('POLL_INTERVAL', '2s'));
const ZIP_TIMEOUT = toSeconds(durationEnv('ZIP_TIMEOUT', '5m'));
if (ZIP_PHOTOS < 1) throw new Error('ZIP_PHOTOS 는 1 이상');
if (USERS.some((user) => user.photoIds.length < ZIP_PHOTOS)) throw new Error(`준비된 사진이 ZIP_PHOTOS(${ZIP_PHOTOS})보다 적은 방이 있습니다`);

const CONFIG = buildConfig();
if (CONFIG.concurrency > USERS.length) throw new Error(`ZIP_CONCURRENCY(${CONFIG.concurrency})가 fixtures 계정 수(${USERS.length})보다 많습니다`);

export const options = {
  scenarios: {
    [PHASE]: PROFILE === 'smoke'
      ? { executor: 'per-vu-iterations', vus: 1, iterations: CONFIG.iterations, maxDuration: `${ZIP_TIMEOUT * 2 + 60}s`, tags: { phase: PHASE } }
      // 작업 하나가 끝나야 다음 작업을 낸다. 끝낼 때는 진행 중인 작업이 마무리되도록 ZIP_TIMEOUT 만큼 기다린다.
      : { executor: 'constant-vus', vus: CONFIG.concurrency, duration: CONFIG.duration, gracefulStop: `${ZIP_TIMEOUT + 30}s`, tags: { phase: PHASE } },
  },
  thresholds: {
    ...trackApis(APIS, [PHASE]),
    ...trackMetrics({ 'zip_complete_ms{phase:zip}': 'trend', 'zip_ok{phase:zip}': 'rate', 'zip_rejected{phase:zip}': 'count', 'zip_failed{phase:zip}': 'count', 'zip_timeout{phase:zip}': 'count' }),
    ...stopConditions(PROFILE, PHASE),
    ...(PROFILE === 'smoke' && { 'zip_ok{phase:zip}': ['rate==1'] }),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function buildConfig() {
  const common = { profile: PROFILE, zipPhotos: ZIP_PHOTOS, zipFetch: ZIP_FETCH, pollIntervalSec: POLL_INTERVAL, zipTimeoutSec: ZIP_TIMEOUT };
  if (PROFILE === 'smoke') {
    return { ...common, concurrency: 1, iterations: intEnv('ITERATIONS', 2), phaseDurations: { [PHASE]: null } };
  }
  const duration = durationEnv('DURATION', '5m');
  return { ...common, concurrency: intEnv('ZIP_CONCURRENCY', 1), duration, phaseDurations: { [PHASE]: duration } };
}

function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
  return Number(match[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
}

export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: CONFIG.concurrency });
}

const client = createClient(BASE_URL);

export default function () {
  // 가상 사용자마다 계정을 하나씩 고정한다. 같은 계정이 겹치면 계정별 동시 상한(3) 때문에 429 가 섞인다.
  const user = USERS[(exec.vu.idInTest - 1) % USERS.length];
  const mediaIds = pickMany(user.photoIds, ZIP_PHOTOS);
  const base = `/rooms/${user.roomId}/downloads/zip`;

  const created = client.post('zip_create', base, {
    token: user.token,
    body: { mediaIds },
    expectedStatus: 202,
    validate: (job) => {
      if (!job.jobId) return 'jobId 없음';
      if (job.mediaCount !== mediaIds.length) return `mediaCount ${job.mediaCount} ≠ ${mediaIds.length}`;
      return null;
    },
  });
  if (!created.ok) {
    finish(created.res.status === 429 ? 'rejected' : 'failed', `접수 실패 ${created.reason}`);
    return;
  }

  const accepted = Date.now();
  const deadline = accepted + ZIP_TIMEOUT * 1000;
  const jobId = created.data.jobId;
  while (Date.now() < deadline) {
    sleep(POLL_INTERVAL);
    const status = client.get('zip_status', `${base}/${jobId}`, {
      token: user.token,
      validate: (job) => (job.jobId === jobId ? null : `jobId ${job.jobId} ≠ ${jobId}`),
    });
    if (!status.ok) continue;
    const job = status.data;
    if (job.status === 'READY') {
      zipCompleteMs.add(Date.now() - accepted);
      if (!job.downloadUrl) {
        finish('failed', `jobId=${jobId} READY 인데 downloadUrl 없음`);
        return;
      }
      if (ZIP_FETCH && !fetchZip(job.downloadUrl, jobId)) {
        finish('failed', `jobId=${jobId} zip 내려받기 실패`);
        return;
      }
      finish('ok');
      return;
    }
    if (job.status === 'FAILED' || job.status === 'EXPIRED') {
      finish('failed', `jobId=${jobId} ${job.status} ${job.failureReason ?? ''}`);
      return;
    }
  }
  finish('timeout', `jobId=${jobId} ${ZIP_TIMEOUT}초 안에 READY 가 되지 않음`);
}

function fetchZip(url, jobId) {
  const res = http.get(url, { responseType: 'binary', tags: { api: 'storage_get' }, timeout: '120s', responseCallback: http.expectedStatuses(200) });
  const bytes = res.body ? new Uint8Array(res.body) : new Uint8Array(0);
  // zip 파일은 PK\x03\x04 로 시작한다. 빈 응답이나 오류 페이지를 성공으로 세지 않는다.
  const ok = res.status === 200 && bytes.length > 4 && bytes[0] === 0x50 && bytes[1] === 0x4b;
  businessOk.add(ok, { api: 'storage_get' });
  if (!ok) {
    businessFailures.add(1, { api: 'storage_get', reason: `status ${res.status}` });
    console.warn(`[storage_get] zip jobId=${jobId} 실패: status ${res.status}, ${bytes.length} bytes`);
  }
  return ok;
}

function finish(result, message) {
  zipOk.add(result === 'ok');
  if (result === 'rejected') zipRejected.add(1);
  if (result === 'failed') zipFailed.add(1);
  if (result === 'timeout') zipTimeout.add(1);
  if (result !== 'ok') console.warn(`[zip] ${message}`);
}

export function handleSummary(data) {
  return writeSummary(data, {
    scenario: SCENARIO, apis: APIS, phases: [PHASE], config: CONFIG, resultsDir: RESULTS_DIR,
    extras: ['zip_complete_ms{phase:zip}', 'zip_ok{phase:zip}', 'zip_rejected{phase:zip}', 'zip_failed{phase:zip}', 'zip_timeout{phase:zip}'],
  });
}
