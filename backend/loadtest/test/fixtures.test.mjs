import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { createFixtures, loadFixtures, registeredPhotos, saveFixtures, unusedEntrants, usersOf } from '../seed/lib/fixtures.mjs';

test('fixtures 는 본인만 읽을 수 있게 저장한다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-fixtures-'));
  try {
    const path = join(dir, 'run.json');
    saveFixtures(path, createFixtures({ runId: 'r', baseUrl: 'http://localhost:8080', appVersion: {}, condition: {} }));
    assert.equal(statSync(path).mode & 0o777, 0o600);
    assert.equal(loadFixtures(path).runId, 'r');
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('형식 버전이 다른 fixtures 는 읽지 않는다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-fixtures-'));
  try {
    const path = join(dir, 'run.json');
    saveFixtures(path, { schemaVersion: 999 });
    assert.throws(() => loadFixtures(path), /schemaVersion/);
    assert.ok(readFileSync(path, 'utf8').includes('999'));
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('등록 완료로 세는 사진은 목록에 보이는 상태뿐이다', () => {
  const room = {
    host: { userId: 1 },
    participants: [{ userId: 2 }],
    photos: ['RESERVED', 'PROCESSING', 'READY', 'FAILED'].map((status, i) => ({ mediaId: i + 1, status })),
  };
  assert.deepEqual(registeredPhotos(room).map((p) => p.mediaId), [2, 3]);
  assert.deepEqual(usersOf(room).map((u) => u.userId), [1, 2]);
});

test('v1 fixtures 는 신규 입장 계정 없이 v2 로 읽는다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-fixtures-'));
  try {
    const path = join(dir, 'run.json');
    saveFixtures(path, { schemaVersion: 1, condition: { rooms: 1 }, rooms: [{ host: { userId: 1 }, participants: [], photos: [] }] });
    const fixtures = loadFixtures(path);
    assert.equal(fixtures.schemaVersion, 2);
    assert.equal(fixtures.condition.entrantsPerRoom, 0);
    assert.deepEqual(fixtures.rooms[0].entrants, []);
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('신규 입장 계정은 방 인원에 넣지 않고, 입장한 계정은 다시 쓰지 않는다', () => {
  const room = {
    host: { userId: 1 },
    participants: [{ userId: 2 }],
    entrants: [{ userId: 3, joined: true }, { userId: 4, joined: false }],
    photos: [],
  };
  assert.deepEqual(usersOf(room).map((u) => u.userId), [1, 2]);
  assert.deepEqual(unusedEntrants(room).map((u) => u.userId), [4]);
});
