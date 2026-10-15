import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { test } from 'node:test';

const SCRIPT = resolve('seed/upload-images.mjs');

function run(cwd, ...args) {
  return spawnSync(process.execPath, [SCRIPT, ...args], { cwd, encoding: 'utf8' });
}

test('업로드용 이미지와 k6 가 읽을 manifest 를 만든다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-upload-'));
  try {
    const result = run(dir, '--count', '3');
    assert.equal(result.status, 0, result.stderr);
    const out = join(dir, 'images', 'upload-set');
    const manifest = JSON.parse(readFileSync(join(out, 'manifest.json'), 'utf8'));
    assert.equal(manifest.files.length, 3);
    for (const file of manifest.files) {
      assert.equal(file.mimeType, 'image/png');
      assert.equal(statSync(join(out, file.name)).size, file.size);
    }
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('Git 에서 제외된 images/ 밖에는 쓰지 않는다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-upload-'));
  try {
    const result = run(dir, '--count', '1', '--out', 'elsewhere');
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /images\//);
  } finally {
    rmSync(dir, { recursive: true });
  }
});
