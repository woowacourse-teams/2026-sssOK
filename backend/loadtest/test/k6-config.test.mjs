import assert from 'node:assert/strict';
import { test } from 'node:test';

// k6 전용 API(open·__ENV)는 함수 안에서만 쓰므로 순수 함수는 Node 에서 바로 검증할 수 있다.
import { abortEvalDelay, buildEntrants, buildUsers, readyPhotoIds, toSeconds } from '../k6/lib/config.js';

function room(roomId, { entrants = [], photos = [] } = {}) {
  return {
    roomId,
    code: `C${roomId}`,
    host: { userId: roomId * 100, accessToken: 'h' },
    participants: [{ userId: roomId * 100 + 1, accessToken: 'p' }],
    entrants,
    photos,
  };
}

test('신규 입장 계정은 방을 번갈아 섞고 이미 입장한 계정은 뺀다', () => {
  const fixtures = {
    rooms: [
      room(1, { entrants: [{ userId: 11, joined: false }, { userId: 12, joined: false }, { userId: 13, joined: false }] }),
      room(2, { entrants: [{ userId: 21, joined: true }, { userId: 22, joined: false }] }),
    ],
  };
  assert.deepEqual(buildEntrants(fixtures).map((e) => e.userId), [11, 22, 12, 13]);
  assert.equal(buildEntrants(fixtures)[1].roomId, 2);
});

test('상세·좋아요·폴더에는 썸네일이 준비된 사진만 쓰고, 목록 기대 수는 목록에 보이는 사진 기준이다', () => {
  const photos = ['READY', 'PROCESSING', 'FAILED', 'RESERVED', 'READY'].map((status, i) => ({ mediaId: i + 1, status }));
  const fixtures = { rooms: [room(1, { photos })] };
  assert.deepEqual(readyPhotoIds(fixtures.rooms[0]), [1, 5]);
  const users = buildUsers(fixtures);
  assert.equal(users.length, 2);
  assert.equal(users[0].expectedPhotos, 3);
  assert.deepEqual(users[0].photoIds, [1, 5]);
});

test('중단 판정 유예는 판정 구간 시작 시각부터 센다', () => {
  assert.equal(abortEvalDelay(), '60s');
  assert.equal(abortEvalDelay('2m'), '180s');
  assert.equal(abortEvalDelay('123s', '30s'), '153s');
  assert.throws(() => toSeconds('2분'));
});
