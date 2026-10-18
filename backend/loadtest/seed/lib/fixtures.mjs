import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

// fixtures 는 시드 진행 상태이자 정리 대상 목록이다. 토큰이 들어 있어 Git 에서 제외하고 본인만 읽게 둔다.
// 형식을 바꾸면 k6/lib/config.js 와 README 의 "fixtures 형식" 도 함께 고친다.
// v2: 방마다 신규 입장 시험용 미입장 계정(entrants)을 둔다. v1 은 읽을 때 entrants 없이 v2 로 올린다.
export const SCHEMA_VERSION = 2;
const UPGRADABLE = new Set([1]);

export const Status = Object.freeze({
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CLEANED: 'CLEANED',
});

export function createFixtures({ runId, baseUrl, appVersion, condition }) {
  const now = new Date().toISOString();
  return {
    schemaVersion: SCHEMA_VERSION,
    runId,
    baseUrl,
    appVersion,
    status: Status.IN_PROGRESS,
    createdAt: now,
    updatedAt: now,
    condition,
    lastError: null,
    verification: null,
    rooms: [],
    cleanup: null,
  };
}

export function loadFixtures(path) {
  const fixtures = JSON.parse(readFileSync(path, 'utf8'));
  if (UPGRADABLE.has(fixtures.schemaVersion)) {
    return upgrade(fixtures);
  }
  if (fixtures.schemaVersion !== SCHEMA_VERSION) {
    throw new Error(`${path} 의 schemaVersion(${fixtures.schemaVersion})이 도구(${SCHEMA_VERSION})와 다릅니다`);
  }
  return fixtures;
}

function upgrade(fixtures) {
  fixtures.schemaVersion = SCHEMA_VERSION;
  fixtures.condition.entrantsPerRoom ??= 0;
  for (const room of fixtures.rooms) {
    room.entrants ??= [];
  }
  return fixtures;
}

// 중간에 프로세스가 죽어도 반쯤 쓴 파일이 남지 않도록 임시 파일에 쓰고 바꿔치기한다.
export function saveFixtures(path, fixtures) {
  fixtures.updatedAt = new Date().toISOString();
  mkdirSync(dirname(path), { recursive: true });
  const temp = `${path}.tmp`;
  writeFileSync(temp, `${JSON.stringify(fixtures, null, 2)}\n`, { mode: 0o600 });
  renameSync(temp, path);
  chmodSync(path, 0o600);
}

export function fixturesExists(path) {
  return existsSync(path);
}

// 방의 모든 계정. 방장이 맨 앞이다.
export function usersOf(room) {
  return [room.host, ...room.participants].filter(Boolean);
}

// 신규 입장 시험에 아직 쓰지 않은 계정. 한 번 입장한 계정은 다시 신규 입장으로 세지 않는다.
export function unusedEntrants(room) {
  return (room.entrants ?? []).filter((entrant) => !entrant.joined);
}

export function registeredPhotos(room) {
  return room.photos.filter((photo) => photo.status === 'PROCESSING' || photo.status === 'READY');
}
