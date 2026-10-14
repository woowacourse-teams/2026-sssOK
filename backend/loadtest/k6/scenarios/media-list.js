// 갤러리 목록 조회 부하.
//
// 실제 앱은 방에 들어가면 GET /rooms/{roomId}/media/all 로 사진 전체를 한 번에 받는다
// (frontend/src/entities/media/api/getPhotos.ts). 기본값은 이 동작을 그대로 흉내 낸다.
// 커서 기반 GET /rooms/{roomId}/media 는 지금 앱이 쓰지 않지만 비교용으로 LIST_API=paged 로 남겨 둔다.
//
//   PROFILE=smoke  사전 점검. 5명이 몇 번씩 돌고, 업무 실패가 하나라도 있으면 실패로 끝난다. 성능 판정에 쓰지 않는다.
//   PROFILE=load   본 측정. 예열(warmup) 뒤 본 측정(main) 구간을 따로 집계한다.
//   MODE=vus       사용자 수 고정 (기본). 사용자마다 THINK_TIME 만큼 쉬며 목록을 다시 연다.
//   MODE=rate      요청률 고정. 목록 크기 비교처럼 데이터만 바꾸고 입력량을 같게 둘 때 쓴다.
import exec from 'k6/execution';
import { sleep } from 'k6';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir, userForVu,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, jitter, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, writeSummary } from '../lib/summary.js';

const SCENARIO = 'media-list';
const MAX_PAGE_SIZE = 100;

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();

const PROFILE = env('PROFILE', 'smoke');
const MODE = env('MODE', 'vus');
const LIST_API = env('LIST_API', 'all');
const PAGE_SIZE = intEnv('PAGE_SIZE', 30);
const PAGES = intEnv('PAGES', 3);
const THINK_TIME = Number(env('THINK_TIME', '2'));

if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);
if (!['vus', 'rate'].includes(MODE)) throw new Error(`MODE 는 vus 또는 rate (입력: ${MODE})`);
if (!['all', 'paged'].includes(LIST_API)) throw new Error(`LIST_API 는 all 또는 paged (입력: ${LIST_API})`);
if (PAGE_SIZE < 1 || PAGE_SIZE > MAX_PAGE_SIZE) throw new Error(`PAGE_SIZE 는 1~${MAX_PAGE_SIZE}`);
if (PAGES < 1) throw new Error('PAGES 는 1 이상');
if (!(THINK_TIME >= 0)) throw new Error('THINK_TIME 은 0 이상의 초');

const APIS = LIST_API === 'all' ? ['media_all'] : ['media_list_first', 'media_list_next'];
const CONFIG = buildConfig();
const PHASES = Object.keys(CONFIG.phaseDurations);

const SCENARIOS = buildScenarios();

export const options = {
  scenarios: SCENARIOS,
  thresholds: {
    ...trackApis(APIS, PHASES),
    ...stopConditions(PROFILE, PROFILE === 'smoke' ? 'smoke' : 'main', SCENARIOS),
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  // 응답 본문은 검증에 필요하다. discardResponseBodies 를 켜지 않는다.
};

function buildConfig() {
  const paging = LIST_API === 'paged' ? { pageSize: PAGE_SIZE } : {};
  if (PROFILE === 'smoke') {
    return {
      profile: PROFILE, listApi: LIST_API, mode: 'vus', vus: intEnv('VUS', 5), iterations: intEnv('ITERATIONS', 3),
      ...paging, pages: LIST_API === 'paged' ? PAGES : 1, thinkTimeSec: 0, phaseDurations: { smoke: null },
    };
  }
  const base = {
    profile: PROFILE, listApi: LIST_API, mode: MODE, vus: intEnv('VUS', 30), ...paging,
    phaseDurations: { warmup: durationEnv('WARMUP', '2m'), main: durationEnv('DURATION', '3m') },
  };
  if (MODE === 'rate') {
    return { ...base, rate: intEnv('RATE', 10), pages: 1, thinkTimeSec: 0 };
  }
  return { ...base, pages: LIST_API === 'paged' ? PAGES : 1, thinkTimeSec: THINK_TIME };
}

function buildScenarios() {
  if (PROFILE === 'smoke') {
    return {
      smoke: { executor: 'per-vu-iterations', vus: CONFIG.vus, iterations: CONFIG.iterations, maxDuration: '2m', tags: { phase: 'smoke' } },
    };
  }
  const executor = MODE === 'rate'
    ? { executor: 'constant-arrival-rate', rate: CONFIG.rate, timeUnit: '1s', preAllocatedVUs: CONFIG.vus, maxVUs: CONFIG.vus }
    : { executor: 'constant-vus', vus: CONFIG.vus };
  return warmupMain(executor, CONFIG.phaseDurations);
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
  if (LIST_API === 'all') {
    openGallery(user);
  } else {
    scrollPages(user);
  }
  if (CONFIG.thinkTimeSec > 0) {
    sleep(jitter(CONFIG.thinkTimeSec));
  }
}

// 앱과 같은 방식: 방 전체 목록을 한 번에 받는다.
function openGallery(user) {
  client.get('media_all', `/rooms/${user.roomId}/media/all`, {
    token: user.token,
    validate: (body) => validateAll(body, user.expectedPhotos),
  });
}

// 커서 페이지 방식: 첫 페이지를 받고 PAGES 까지 다음 페이지를 이어 받는다.
function scrollPages(user) {
  let cursor = null;
  for (let page = 0; page < CONFIG.pages; page++) {
    const name = page === 0 ? 'media_list_first' : 'media_list_next';
    const query = `size=${PAGE_SIZE}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`;
    const { ok, data } = client.get(name, `/rooms/${user.roomId}/media?${query}`, {
      token: user.token,
      validate: (body) => validatePage(body, user.expectedPhotos, PAGE_SIZE, page),
    });
    if (!ok || !data.hasNext) {
      return;
    }
    cursor = data.nextCursor;
    if (CONFIG.thinkTimeSec > 0) {
      sleep(jitter(CONFIG.thinkTimeSec));
    }
  }
}

// 시드한 사진이 빠짐없이 와야 성공이다. 빈 목록이나 다른 방 응답을 성공으로 세지 않는다.
function validateAll(body, expectedTotal) {
  if (!body || !Array.isArray(body.items)) return 'items 없음';
  if (body.items.length !== expectedTotal) return `items ${body.items.length} ≠ ${expectedTotal}`;
  return validateItems(body.items);
}

function validatePage(body, expectedTotal, pageSize, page) {
  if (!body || !Array.isArray(body.items)) return 'items 없음';
  if (body.totalCount !== expectedTotal) return `totalCount ${body.totalCount} ≠ ${expectedTotal}`;
  const remaining = expectedTotal - page * pageSize;
  const expectedItems = Math.max(0, Math.min(pageSize, remaining));
  if (body.items.length !== expectedItems) return `items ${body.items.length} ≠ ${expectedItems}`;
  if (body.hasNext !== remaining > pageSize) return `hasNext ${body.hasNext} 불일치`;
  if (body.hasNext && !body.nextCursor) return 'hasNext 인데 nextCursor 없음';
  return validateItems(body.items);
}

function validateItems(items) {
  if (items.some((item) => !item.mediaId || !item.thumbnailUrl)) return 'mediaId·thumbnailUrl 없는 항목';
  return null;
}

export function handleSummary(data) {
  return writeSummary(data, { scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR });
}
