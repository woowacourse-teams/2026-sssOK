// 갤러리 열람 부하. 이미 방에 있는 사람들이 갤러리를 열고, 사진을 눌러 보고, 가끔 좋아요를 누르는 흐름이다.
// 목록만 재는 media-list.js 와 달리 실제 사용 흐름을 섞는다. 같은 /media/all 도 여기서는 media_all 로 따로 집계된다.
//
//   가상 사용자 1명 (= fixtures 계정 1개) 반복:
//     GET /media/all              갤러리 열기 (사진 전체)
//     DETAILS 번: GET /media/{id} 사진 상세. 썸네일까지 준비된 사진 중 무작위
//       LIKE_RATIO 확률로 PUT /likes → 잠시 뒤 DELETE /likes
//     THINK_TIME 쉼
//
//   좋아요는 눌렀다가 다시 취소한다. 실행이 끝나도 좋아요 수가 시드 때와 같아 같은 조건으로 반복 측정할 수 있다.
//   한 계정을 여러 가상 사용자가 함께 쓰면(VUS > 계정 수) 같은 사진 좋아요가 겹칠 수 있어, 응답의 liked 만 검증하고 좋아요 수는 검증하지 않는다.
//
//   PROFILE=smoke  5명이 두 번씩 돈다. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   VUS 명(기본: fixtures 계정 전부)으로 예열 뒤 본 측정. 30·90·150·300명은 방 1·3·5·10개 시드로 맞춘다.
import exec from 'k6/execution';
import { sleep } from 'k6';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir, userForVu,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, jitter, pick, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, writeSummary } from '../lib/summary.js';

const SCENARIO = 'gallery-browse';
const APIS = ['media_all', 'media_detail', 'media_like', 'media_unlike'];

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const DETAILS = intEnv('DETAILS', 3);
const LIKE_RATIO = Number(env('LIKE_RATIO', '0.3'));
const THINK_TIME = Number(env('THINK_TIME', '2'));
if (!(LIKE_RATIO >= 0 && LIKE_RATIO <= 1)) throw new Error('LIKE_RATIO 는 0~1');
if (!(THINK_TIME >= 0)) throw new Error('THINK_TIME 은 0 이상의 초');
if (USERS.some((user) => user.photoIds.length === 0)) throw new Error('썸네일이 준비된 사진이 없는 방이 있습니다. 시드를 확인한다');

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
  const common = { profile: PROFILE, details: DETAILS, likeRatio: LIKE_RATIO };
  if (PROFILE === 'smoke') {
    // 좋아요 경로도 꼭 한 번은 지나가도록 사전 점검에서는 상세마다 좋아요를 누른다.
    return { ...common, vus: intEnv('VUS', 5), iterations: intEnv('ITERATIONS', 2), likeRatio: 1, thinkTimeSec: 0, phaseDurations: { smoke: null } };
  }
  return {
    ...common, vus: intEnv('VUS', USERS.length), thinkTimeSec: THINK_TIME,
    phaseDurations: { warmup: durationEnv('WARMUP', '2m'), main: durationEnv('DURATION', '5m') },
  };
}

function buildScenarios() {
  if (PROFILE === 'smoke') {
    return {
      smoke: { executor: 'per-vu-iterations', vus: CONFIG.vus, iterations: CONFIG.iterations, maxDuration: '2m', tags: { phase: 'smoke' } },
    };
  }
  return warmupMain({ executor: 'constant-vus', vus: CONFIG.vus }, CONFIG.phaseDurations);
}

export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  if (CONFIG.vus > USERS.length) {
    console.warn(`VUS(${CONFIG.vus})가 fixtures 계정 수(${USERS.length})보다 많아 한 계정을 여러 가상 사용자가 함께 쓴다`);
  }
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: USERS.length });
}

const client = createClient(BASE_URL);

export default function () {
  const user = userForVu(USERS, exec.vu.idInTest);
  const list = client.get('media_all', `/rooms/${user.roomId}/media/all`, {
    token: user.token,
    validate: (body) => {
      if (!body || !Array.isArray(body.items)) return 'items 없음';
      if (body.items.length !== user.expectedPhotos) return `items ${body.items.length} ≠ ${user.expectedPhotos}`;
      if (body.items.some((item) => !item.mediaId || !item.thumbnailUrl)) return 'mediaId·thumbnailUrl 없는 항목';
      return null;
    },
  });
  if (!list.ok) {
    pause();
    return;
  }

  for (let i = 0; i < CONFIG.details; i++) {
    pause(0.5);
    const mediaId = pick(user.photoIds);
    const detail = client.get('media_detail', `/rooms/${user.roomId}/media/${mediaId}`, {
      token: user.token,
      validate: (media) => {
        if (media.mediaId !== mediaId) return `mediaId ${media.mediaId} ≠ ${mediaId}`;
        if (media.status !== 'READY') return `status ${media.status}`;
        if (!media.displayUrl) return 'displayUrl 없음';
        return null;
      },
    });
    if (detail.ok && Math.random() < CONFIG.likeRatio) {
      toggleLike(user, mediaId);
    }
  }
  pause();
}

function toggleLike(user, mediaId) {
  const path = `/rooms/${user.roomId}/media/${mediaId}/likes`;
  const validate = (expected) => (body) => {
    if (body.mediaId !== mediaId) return `mediaId ${body.mediaId} ≠ ${mediaId}`;
    if (body.liked !== expected) return `liked ${body.liked} ≠ ${expected}`;
    if (!(body.likeCount >= 0)) return `likeCount ${body.likeCount}`;
    return null;
  };
  const like = client.put('media_like', path, { token: user.token, validate: validate(true) });
  if (!like.ok) return;
  pause(0.5);
  client.del('media_unlike', path, { token: user.token, validate: validate(false) });
}

function pause(scale = 1) {
  if (CONFIG.thinkTimeSec > 0) {
    sleep(jitter(CONFIG.thinkTimeSec * scale));
  }
}

export function handleSummary(data) {
  return writeSummary(data, { scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR });
}
