// 폴더 정리 부하. 방마다 대표 사용자가 폴더를 만들고 사진을 정리하는 흐름이다(#517 "폴더").
//
//   방별 대표 사용자 반복:
//     POST   /folders              폴더 생성
//     PATCH  /folders/{id}         이름 변경
//     PUT    /media/folders        사진 FOLDER_PHOTOS 장 담기
//     GET    /media/all?folderId=  폴더 열기 — 담은 사진만 정확히 보여야 한다
//     DELETE /media/folders        담은 사진 꺼내기
//     DELETE /folders/{id}         폴더 삭제 (실행이 끝나도 방 상태가 시드 때와 같도록)
//     THINK_TIME 쉼
//
//   폴더 생성·이름 변경·담기는 방의 모든 구독자에게 SSE 이벤트로 퍼진다. 입장 시험과 함께 돌리면 그 부하도 같이 생긴다.
//   중간에 실패하면 만든 폴더를 지우려 한 번 더 시도한다. 그래도 남은 폴더는 정리 도구가 방을 지울 때 함께 사라진다.
//
//   PROFILE=smoke  방마다 한 명이 두 번씩 돈다. 하나라도 실패하면 실패로 끝난다.
//   PROFILE=load   방마다 USERS_PER_ROOM 명(기본 1)으로 예열 뒤 본 측정.
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';

import {
  durationEnv, env, intEnv, loadFixtures, readyPhotoIds, resolveBaseUrl, resolveResultsDir,
} from '../lib/config.js';
import { createClient } from '../lib/http.js';
import { checkTarget, jitter, pickMany, runInfo, stopConditions, warmupMain } from '../lib/run.js';
import { trackApis, writeSummary } from '../lib/summary.js';

const SCENARIO = 'folder-ops';
const APIS = ['folder_create', 'folder_rename', 'folder_add_media', 'media_all_folder', 'folder_remove_media', 'folder_delete'];
// 서버 FolderName.MAX_LENGTH
const MAX_FOLDER_NAME = 12;

const { path: fixturesPath, fixtures } = loadFixtures();
const BASE_URL = resolveBaseUrl(fixtures);
const RESULTS_DIR = resolveResultsDir();

const PROFILE = env('PROFILE', 'smoke');
if (!['smoke', 'load'].includes(PROFILE)) throw new Error(`PROFILE 은 smoke 또는 load (입력: ${PROFILE})`);

const USERS_PER_ROOM = intEnv('USERS_PER_ROOM', 1);
const FOLDER_PHOTOS = intEnv('FOLDER_PHOTOS', 10);
const THINK_TIME = Number(env('THINK_TIME', '3'));
if (USERS_PER_ROOM < 1) throw new Error('USERS_PER_ROOM 은 1 이상');
if (FOLDER_PHOTOS < 1) throw new Error('FOLDER_PHOTOS 는 1 이상');
if (!(THINK_TIME >= 0)) throw new Error('THINK_TIME 은 0 이상의 초');

const ACTORS = buildActors();
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

// 방마다 방장부터 USERS_PER_ROOM 명을 대표로 쓴다. 가상 사용자 번호 순서로 방을 돌아가며 배정한다.
function buildActors() {
  const actors = [];
  for (let slot = 0; slot < USERS_PER_ROOM; slot++) {
    for (const room of fixtures.rooms) {
      const user = [room.host, ...room.participants][slot];
      if (!user) throw new Error(`USERS_PER_ROOM(${USERS_PER_ROOM})이 방 인원보다 많습니다`);
      const photoIds = readyPhotoIds(room);
      if (photoIds.length < FOLDER_PHOTOS) throw new Error(`roomId=${room.roomId} 준비된 사진(${photoIds.length})이 FOLDER_PHOTOS(${FOLDER_PHOTOS})보다 적습니다`);
      actors.push({ userId: user.userId, token: user.accessToken, roomId: room.roomId, photoIds });
    }
  }
  return actors;
}

function buildConfig() {
  const common = { profile: PROFILE, vus: ACTORS.length, usersPerRoom: USERS_PER_ROOM, folderPhotos: FOLDER_PHOTOS };
  if (PROFILE === 'smoke') {
    return { ...common, iterations: intEnv('ITERATIONS', 2), thinkTimeSec: 0, phaseDurations: { smoke: null } };
  }
  return {
    ...common, thinkTimeSec: THINK_TIME,
    phaseDurations: { warmup: durationEnv('WARMUP', '1m'), main: durationEnv('DURATION', '5m') },
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
  return runInfo({ baseUrl: BASE_URL, appVersion, fixturesPath, fixtures, accounts: ACTORS.length });
}

const client = createClient(BASE_URL);

export default function () {
  const actor = ACTORS[(exec.vu.idInTest - 1) % ACTORS.length];
  const base = `/rooms/${actor.roomId}`;
  const opts = (extra) => ({ token: actor.token, ...extra });

  const created = client.post('folder_create', `${base}/folders`, opts({
    body: { name: folderName('c') },
    expectedStatus: 201,
    validate: (folder) => (folder.id && folder.photoCount === 0 ? null : `생성 응답 이상 (id=${folder.id}, photoCount=${folder.photoCount})`),
  }));
  if (!created.ok) {
    pause();
    return;
  }
  const folderId = created.data.id;
  let deleted = false;
  try {
    const renamed = folderName('r');
    client.patch('folder_rename', `${base}/folders/${folderId}`, opts({
      body: { name: renamed },
      validate: (folder) => (folder.id === folderId && folder.name === renamed ? null : `이름 ${folder.name} ≠ ${renamed}`),
    }));

    const mediaIds = pickMany(actor.photoIds, FOLDER_PHOTOS);
    const added = client.put('folder_add_media', `${base}/media/folders`, opts({
      body: { mediaIds, folderId },
      validate: (body) => {
        if (body.updatedCount !== mediaIds.length) return `updatedCount ${body.updatedCount} ≠ ${mediaIds.length}`;
        if (body.notFoundMediaIds.length > 0) return `notFound ${body.notFoundMediaIds.length}`;
        if (body.folder.id !== folderId || body.folder.photoCount !== mediaIds.length) return `폴더 사진 수 ${body.folder.photoCount}`;
        return null;
      },
    }));
    if (!added.ok) return;

    client.get('media_all_folder', `${base}/media/all?folderId=${folderId}`, opts({
      validate: (body) => {
        if (!body || !Array.isArray(body.items)) return 'items 없음';
        const ids = new Set(body.items.map((item) => item.mediaId));
        if (ids.size !== mediaIds.length || mediaIds.some((id) => !ids.has(id))) return `폴더 목록 ${ids.size}장이 담은 사진과 다름`;
        return null;
      },
    }));

    client.del('folder_remove_media', `${base}/media/folders`, opts({
      body: { mediaIds, folderIds: [folderId] },
      validate: (body) => (body.updatedCount === mediaIds.length && body.notFoundMediaIds.length === 0
        ? null : `updatedCount ${body.updatedCount} ≠ ${mediaIds.length}`),
    }));

    deleted = deleteFolder(base, folderId, opts).ok;
  } finally {
    // 중간에 실패해도 폴더가 쌓이지 않게 한 번 더 지운다. 이 재시도는 결과 집계에 넣지 않는다.
    if (!deleted) {
      http.del(`${BASE_URL}/api/v1${base}/folders/${folderId}`, null, {
        headers: { Authorization: `Bearer ${actor.token}` }, tags: { api: 'folder_cleanup' }, responseCallback: http.expectedStatuses(200, 404),
      });
    }
    pause();
  }
}

function deleteFolder(base, folderId, opts) {
  return client.del('folder_delete', `${base}/folders/${folderId}`, opts({
    validate: (body) => (body.deletedFolderId === folderId ? null : `deletedFolderId ${body.deletedFolderId} ≠ ${folderId}`),
  }));
}

// 방 안에서 이름이 겹치면 409 다. 가상 사용자·반복 번호와 시각으로 겹치지 않게 만든다(최대 12자).
function folderName(prefix) {
  const unique = `${exec.vu.idInTest.toString(36)}${exec.vu.iterationInScenario.toString(36)}${Date.now().toString(36).slice(-4)}`;
  return `${prefix}${unique}`.slice(0, MAX_FOLDER_NAME);
}

function pause() {
  if (CONFIG.thinkTimeSec > 0) {
    sleep(jitter(CONFIG.thinkTimeSec));
  }
}

export function handleSummary(data) {
  return writeSummary(data, { scenario: SCENARIO, apis: APIS, phases: PHASES, config: CONFIG, resultsDir: RESULTS_DIR });
}
