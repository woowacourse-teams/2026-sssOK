#!/usr/bin/env node
// fixtures 에 기록된 실험 자원만 API 로 정리한다. 방을 뒤져 찾은 사진은 지우지 않는다.
//
// 순서가 중요하다. 방 삭제만으로는 스토리지 원본이 지워지지 않으므로 사진을 먼저 지운 뒤 방을 지운다.
// 계정·만료된 예약 행은 삭제 API 가 없어 남는다 — 끝에 출력하는 "API 로 정리하지 못한 범위" 를 참고한다.
import { resolve } from 'node:path';

import { ApiError, createApi } from './lib/api.mjs';
import { fail, guardTarget, log, parseCli } from './lib/cli.mjs';
import { loadFixtures, saveFixtures, Status, usersOf } from './lib/fixtures.mjs';

const USAGE = `사용법: node seed/cleanup.mjs [--fixtures <file>] [--yes] [--dry-run]

  --fixtures <file>   seed.mjs 가 만든 fixtures 경로. 생략하면 환경 변수 FIXTURES
  --dry-run           지울 대상만 출력한다
  --yes               로컬이 아닌 대상에 실행한다`;

// 서버 DeleteMediaService.MAX_MEDIA_COUNT 와 맞춘다.
const DELETE_CHUNK = 500;

const args = parseCli({
  fixtures: { type: 'string' },
  'dry-run': { type: 'boolean', default: false },
  yes: { type: 'boolean', default: false },
}, USAGE);

const fixturesArg = args.fixtures ?? process.env.FIXTURES;
if (!fixturesArg) {
  fail(`--fixtures 또는 환경 변수 FIXTURES 가 필요합니다\n\n${USAGE}`);
}
const path = resolve(fixturesArg);
const fixtures = loadFixtures(path);
if (fixtures.status === Status.CLEANED) {
  console.log(`이미 정리된 fixtures 입니다 (${fixtures.cleanup?.finishedAt}). 남은 범위는 fixtures.cleanup.leftovers 를 본다`);
  process.exit(0);
}
guardTarget(fixtures.baseUrl, args.yes);
const api = createApi(fixtures.baseUrl);

const report = { startedAt: new Date().toISOString(), rooms: [], leftovers: {} };

for (const room of fixtures.rooms) {
  report.rooms.push(await cleanRoom(room));
}

const accounts = fixtures.rooms.flatMap((room) => [...usersOf(room), ...(room.entrants ?? [])].map((user) => user.userId));
const reserved = fixtures.rooms.flatMap((room) =>
  room.photos.filter((photo) => photo.mediaId && photo.status === 'RESERVED').map((photo) => photo.mediaId));
report.leftovers = {
  accountUserIds: accounts,
  reservedMediaIds: reserved,
  note: '익명 계정은 삭제 API 가 없어 DB 에 남는다. 업로드를 끝내지 못한 예약 행은 목록에 보이지 않지만 행이 남을 수 있다. 정리 필요 시 이 ID 로 DB 를 직접 확인한다.',
};
report.finishedAt = new Date().toISOString();

const failedRooms = report.rooms.filter((room) => room.error);
if (!args['dry-run']) {
  fixtures.cleanup = report;
  if (failedRooms.length === 0) {
    fixtures.status = Status.CLEANED;
  }
  saveFixtures(path, fixtures);
}

console.log(`\n정리 ${args['dry-run'] ? '(dry-run) ' : ''}결과`);
for (const room of report.rooms) {
  console.log(`  roomId=${room.roomId ?? '-'} 사진 삭제 ${room.deletedMedia}장(이미 없음 ${room.notFoundMedia}) 방 ${room.roomDeleted}${room.error ? ` ✗ ${room.error}` : ''}`);
}
console.log(`  API 로 정리하지 못한 범위: 계정 ${accounts.length}개, 미완료 예약 ${reserved.length}건 (fixtures.cleanup.leftovers)`);
if (failedRooms.length > 0) {
  process.exit(1);
}

async function cleanRoom(room) {
  const result = { roomId: room.roomId ?? null, deletedMedia: 0, notFoundMedia: 0, roomDeleted: 'skip' };
  if (!room.roomId || !room.host) {
    result.roomDeleted = '생성 전';
    return result;
  }
  const token = room.host.accessToken;
  try {
    // fixtures 의 방이 지금도 fixtures 의 방장 것인지 확인한다. 아니면 아무것도 지우지 않는다.
    const { data: detail } = await api.getRoom(token, room.code);
    if (detail.roomId !== room.roomId || detail.hostId !== room.host.userId) {
      throw new Error(`방 정보가 fixtures 와 다릅니다 (roomId=${detail.roomId}, hostId=${detail.hostId})`);
    }
    const mediaIds = room.photos.map((photo) => photo.mediaId).filter(Boolean);
    if (detail.status === 'DELETED') {
      result.roomDeleted = '이미 삭제됨';
      if (mediaIds.length > 0) {
        result.error = '방이 먼저 삭제되어 사진을 API 로 지울 수 없습니다. 스토리지 원본 정리가 필요합니다';
      }
      return result;
    }
    if (args['dry-run']) {
      result.deletedMedia = mediaIds.length;
      result.roomDeleted = '삭제 예정';
      return result;
    }

    for (let i = 0; i < mediaIds.length; i += DELETE_CHUNK) {
      const { data } = await api.deleteMedia(token, room.roomId, mediaIds.slice(i, i + DELETE_CHUNK));
      result.deletedMedia += data.deletedCount;
      result.notFoundMedia += data.notFoundMediaIds?.length ?? 0;
    }
    log(`roomId=${room.roomId}: 사진 ${result.deletedMedia}장 삭제`);
    await api.deleteRoom(token, room.roomId);
    result.roomDeleted = '삭제';
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      result.roomDeleted = '찾을 수 없음';
      result.error = '방을 찾을 수 없어 사진 정리 여부를 확인하지 못했습니다. 스토리지 원본 정리가 필요할 수 있습니다';
      return result;
    }
    result.error = e instanceof ApiError && e.status === 401
      ? `방장 토큰이 만료되었거나 유효하지 않습니다 (${e.message})`
      : e.message;
  }
  return result;
}
