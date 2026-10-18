import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { inflateSync } from 'node:zlib';

import { createImageSource, generatePng, inspectImage, loadImageDir } from '../seed/lib/images.mjs';

test('생성한 PNG 는 헤더의 해상도와 실제 픽셀 수가 맞는다', () => {
  const png = generatePng(64, 48, 7);
  assert.deepEqual(inspectImage(png), { format: 'png', mimeType: 'image/png', width: 64, height: 48 });

  const idatLength = png.readUInt32BE(33);
  const pixels = inflateSync(png.subarray(41, 41 + idatLength));
  assert.equal(pixels.length, (64 * 3 + 1) * 48);
});

test('장마다 다른 바이트를 만든다', () => {
  assert.notDeepEqual(generatePng(32, 32, 1), generatePng(32, 32, 2));
});

test('JPEG 는 SOF 헤더에서 해상도를 읽는다', () => {
  // SOI, APP0(길이 4), SOF0(높이 300, 너비 400)
  const jpeg = Buffer.from([
    0xff, 0xd8,
    0xff, 0xe0, 0x00, 0x04, 0x00, 0x00,
    0xff, 0xc0, 0x00, 0x11, 0x08, 0x01, 0x2c, 0x01, 0x90, 0x03, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  ]);
  assert.deepEqual(inspectImage(jpeg), { format: 'jpeg', mimeType: 'image/jpeg', width: 400, height: 300 });
});

test('이미지가 아닌 파일이 섞이면 디렉터리 전체를 거부한다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-images-'));
  try {
    writeFileSync(join(dir, 'a.png'), generatePng(8, 8, 1));
    writeFileSync(join(dir, 'b.jpg'), 'not an image');
    assert.throws(() => loadImageDir(dir), /b\.jpg: JPEG·PNG·GIF 가 아님/);
  } finally {
    rmSync(dir, { recursive: true });
  }
});

test('실제 이미지가 장수보다 적으면 돌려쓴다', () => {
  const dir = mkdtempSync(join(tmpdir(), 'loadtest-images-'));
  try {
    writeFileSync(join(dir, 'a.png'), generatePng(8, 8, 1));
    writeFileSync(join(dir, 'b.png'), generatePng(16, 8, 2));
    const source = createImageSource({ imagesDir: dir });
    assert.equal(source.get(0).name, 'a.png');
    assert.equal(source.get(3).name, 'b.png');
    assert.deepEqual(source.summary.resolution, { min: '8x8', max: '16x8' });
  } finally {
    rmSync(dir, { recursive: true });
  }
});
