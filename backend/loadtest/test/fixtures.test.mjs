import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { createFixtures, loadFixtures, registeredPhotos, saveFixtures, usersOf } from '../seed/lib/fixtures.mjs';

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
