#!/usr/bin/env node
// 업로드 시나리오(k6/scenarios/media-upload.js)가 쓸 이미지 묶음을 준비한다.
// k6 는 디렉터리를 훑을 수 없어서, 파일 목록·형식·크기를 manifest.json 에 적어 둔다.
// --images-dir 를 주면 실제 사진을 검증해 복사하고, 없으면 시드와 같은 PNG 를 장마다 다르게 만든다.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';

import { fail, log, parseCli, requirePositiveInt } from './lib/cli.mjs';
import { createImageSource } from './lib/images.mjs';

const USAGE = `사용법: node seed/upload-images.mjs [옵션]

  --count <n>          준비할 장 수 (기본 10). 업로드는 이 묶음을 돌려 쓴다. 가상 사용자마다 메모리에 올리므로 크게 잡지 않는다
  --images-dir <dir>   실제 사진 디렉터리. 생략하면 1280x960 PNG 를 만든다
  --out <dir>          저장할 곳 (기본 images/upload-set). 있으면 지우고 새로 만든다`;

const args = parseCli({
  count: { type: 'string', default: '10' },
  'images-dir': { type: 'string' },
  out: { type: 'string', default: 'images/upload-set' },
}, USAGE);

const count = requirePositiveInt('count', args.count);
const outDir = resolve(args.out);
if (!outDir.startsWith(resolve('images') + sep)) {
  fail(`--out 은 images/ 아래여야 합니다 (Git 제외 폴더): ${outDir}`);
}
const source = createImageSource({ imagesDir: args['images-dir'] });

rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });
const files = [];
for (let i = 0; i < count; i++) {
  const image = source.get(i);
  const name = `${String(i + 1).padStart(4, '0')}.${image.extension}`;
  writeFileSync(join(outDir, name), image.bytes);
  files.push({ name, mimeType: image.mimeType, size: image.bytes.length, width: image.width, height: image.height });
}
const manifest = { createdAt: new Date().toISOString(), source: source.description, files };
writeFileSync(join(outDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
const totalMb = files.reduce((sum, f) => sum + f.size, 0) / 1024 / 1024;
log(`${outDir} 에 ${count}장 준비 (${source.description}, 평균 ${(totalMb / count).toFixed(2)}MB)`);
