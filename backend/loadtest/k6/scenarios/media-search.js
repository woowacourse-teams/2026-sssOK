// 자연어 사진 검색 부하. 정해진 검색/초로 GET /media/all?query= 를 보낸다.
// 검색 한 번마다 서버가 외부 AI 에 검색어 임베딩을 요청한다 — 비용이 들고 하루 한도(README "6. dev 에서 실행할 때")를 넘기지 않는다.
//
//   QUERY_MODE=repeat   같은 검색어(SEARCH_QUERY)만 반복한다 (search_repeat). 임베딩 캐시가 있으면 여기서 효과가 보인다
//   QUERY_MODE=diverse  아래 검색어 목록을 돌려 쓴다 (search_diverse). 매번 새 임베딩이 필요한 경우에 가깝다
//
//   검증: 200 이고, 결과가 모두 그 방의 시드 사진이어야 한다. repeat 은 결과가 1장 이상이어야 한다
//         (setup 에서 같은 검색어로 결과가 나오는지 먼저 확인한다). diverse 는 관련 없는 검색어가 섞여 0장도 정상이다.
//   검색이 꺼졌거나 분석된 사진이 없으면 setup 에서 요청 없이 멈춘다. 분석 완료 여부는 API 로 볼 수 없어 README 의 DB 조회로 확인한다.
//
//   PROFILE=smoke  검색 5번. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   RATE(검색/초)로 예열 뒤 본 측정. #517 기준 1 → 3 → 5, 10 은 선택.
import exec from 'k6/execution';

import {
  buildUsers, durationEnv, env, intEnv, loadFixtures, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, writeSummary } from '../lib/summary.js';

const SCENARIO = 'media-search';
// 서버 SearchImagesService.MAX_QUERY_LENGTH
const MAX_QUERY_LENGTH = 200;
const DIVERSE_QUERIES = [
  '바닷가에서 찍은 단체 사진', '케이크에 촛불이 켜진 생일 파티', '산 정상에서 본 풍경', '음식이 가득한 식탁',
  '밤에 찍은 도시 야경', '강아지와 함께 있는 사람', '눈 내리는 거리', '웃고 있는 사람들',
  '단풍이 든 공원', '무대 위 공연', '카페에서 마시는 커피', '자전거를 타는 모습',
  '해 질 녘 하늘', '실내에서 찍은 단체 사진', '꽃이 핀 정원', '운동장에서 뛰는 아이들',
  '캠핑장 텐트', '회의실 화이트보드', '기차 창밖 풍경', '빨간색 옷을 입은 사람',
];

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const USERS = buildUsers(fixtures);
const RESULTS_DIR = resolveResultsDir();
// 결과가 그 방의 시드 사진인지 확인한다. 검색은 READY 사진만 대상으로 한다.
const ROOM_PHOTOS = Object.fromEntries(USERS.map((user) => [user.roomId, new Set(user.photoIds)]));

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);
const QUERY_MODE = env('QUERY_MODE', 'repeat');
if (!['repeat', 'diverse'].includes(QUERY_MODE)) throw new Error(`QUERY_MODE 는 repeat 또는 diverse (입력: ${QUERY_MODE})`);
const SEARCH_QUERY = env('SEARCH_QUERY', '웃고 있는 사람들');
if (SEARCH_QUERY.length > MAX_QUERY_LENGTH) throw new Error(`SEARCH_QUERY 는 ${MAX_QUERY_LENGTH}자 이하`);

const API = QUERY_MODE === 'repeat' ? 'search_repeat' : 'search_diverse';
const APIS = [API];
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
};

function buildConfig() {
  const common = { profile: PROFILE, queryMode: QUERY_MODE, ...(QUERY_MODE === 'repeat' ? { query: SEARCH_QUERY } : { queries: DIVERSE_QUERIES.length }) };
  if (PROFILE === 'smoke') {
    return { ...common, vus: 1, iterations: intEnv('ITERATIONS', 5), phaseDurations: { smoke: null } };
  }
  const rate = intEnv('RATE', 1);
  if (rate < 1) throw new Error('RATE 는 1 이상');
  const warmup = durationEnv('WARMUP', '1m');
  const main = durationEnv('DURATION', '3m');
  return {
    ...common, rate, vus: intEnv('VUS', Math.max(10, rate * 10)),
    phaseDurations: { warmup, main },
    // 외부 AI 호출 수 추정. 실행 전에 하루 한도와 비교한다.
    estimatedSearches: rate * (toSeconds(warmup) + toSeconds(main)),
  };
}

function buildScenarios() {
  if (PROFILE === 'smoke') {
    return {
      smoke: { executor: 'per-vu-iterations', vus: CONFIG.vus, iterations: CONFIG.iterations, maxDuration: '2m', tags: { phase: 'smoke' } },
    };
  }
  // 검색은 외부 AI 를 거쳐 느릴 수 있다. 요청률을 고정하고, 가상 사용자가 모자라면 dropped iterations 로 남긴다.
  return warmupMain({
    executor: 'constant-arrival-rate', rate: CONFIG.rate, timeUnit: '1s', preAllocatedVUs: CONFIG.vus, maxVUs: CONFIG.vus,
  }, CONFIG.phaseDurations);
}

function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration);
  return Number(match[1]) * { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
}

// 검색이 켜져 있고 분석된 사진이 있는지 방마다 한 번씩 확인한다. 이 확인도 외부 AI 호출이 방 수만큼 생긴다.
export function setup() {
  const client = createClient(BASE_URL);
  const appVersion = checkTarget({ baseUrl: BASE_URL, client, fixtures });
  for (const room of fixtures.rooms) {
    const probe = client.get('setup', searchPath(room.roomId, SEARCH_QUERY), { token: room.host.accessToken });
    if (probe.res.status === 503) {
      exec.test.abort('검색이 꺼져 있습니다(503 IMAGE_SEARCH_UNAVAILABLE). 대상 서버의 IMAGE_SEARCH_ENABLED·임계값·OpenAI 키를 확인한다');
    }
    if (!probe.ok) {
      exec.test.abort(`roomId=${room.roomId} 검색 확인 실패: ${probe.reason}`);
    }
    if (probe.data.items.length === 0) {
      exec.test.abort(`roomId=${room.roomId} 에서 "${SEARCH_QUERY}" 결과가 없습니다. 분석이 끝난 사진이 있는지 README 의 DB 조회로 확인한다`);
    }
  }
  if (PROFILE === 'load') {
    console.log(`예상 검색 수 ${CONFIG.estimatedSearches}회 (= 외부 AI 임베딩 호출 수, setup 확인 ${fixtures.rooms.length}회 별도)`);
  }
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: USERS.length });
}

const client = createClient(BASE_URL);

export default function () {
  const iteration = exec.scenario.iterationInTest;
  const user = USERS[iteration % USERS.length];
  const query = QUERY_MODE === 'repeat' ? SEARCH_QUERY : DIVERSE_QUERIES[iteration % DIVERSE_QUERIES.length];
  const allowed = ROOM_PHOTOS[user.roomId];
  client.get(API, searchPath(user.roomId, query), {
    token: user.token,
    validate: (body) => {
      if (!body || !Array.isArray(body.items)) return 'items 없음';
      if (body.items.some((item) => !allowed.has(item.mediaId))) return '그 방의 시드 사진이 아닌 결과';
      if (QUERY_MODE === 'repeat' && body.items.length === 0) return '반복 검색어 결과 0장';
      return null;
    },
  });
}

function searchPath(roomId, query) {
  return `/rooms/${roomId}/media/all?query=${encodeURIComponent(query)}`;
}

export function handleSummary(data) {
  return writeSummary(data, { scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR });
}
