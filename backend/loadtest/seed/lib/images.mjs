import { readdirSync, readFileSync, statSync } from 'node:fs';
import { extname, join } from 'node:path';
import { deflateSync } from 'node:zlib';

// 서버 업로드 정책(application.yml upload.image-max-size)과 맞춘다.
export const IMAGE_MAX_BYTES = 20 * 1024 * 1024;

const MIME_BY_FORMAT = { jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif' };
const EXTENSION_BY_FORMAT = { jpeg: 'jpg', png: 'png', gif: 'gif' };

// 이미지 원본을 준비한다. 디렉터리를 주면 실제 사진을 검증해 돌려쓰고, 없으면 PNG 를 생성한다.
// 목록 시험은 썸네일만 내려받으므로 생성 이미지로 충분하다. 업로드·ZIP 시험은 실제 사진을 쓴다.
export function createImageSource({ imagesDir, width = 1280, height = 960 } = {}) {
  if (!imagesDir) {
    return {
      description: `생성 PNG ${width}x${height}`,
      summary: null,
      get: (index) => {
        const bytes = generatePng(width, height, index);
        return { bytes, format: 'png', mimeType: 'image/png', width, height, extension: 'png' };
      },
    };
  }
  const images = loadImageDir(imagesDir);
  return {
    description: `${imagesDir} 의 실제 이미지 ${images.length}장`,
    summary: summarizeImages(images),
    get: (index) => images[index % images.length],
  };
}

export function loadImageDir(dir) {
  const entries = readdirSync(dir)
    .filter((name) => !name.startsWith('.'))
    .sort();
  const images = [];
  const problems = [];
  for (const name of entries) {
    const path = join(dir, name);
    if (!statSync(path).isFile()) {
      continue;
    }
    const bytes = readFileSync(path);
    const info = inspectImage(bytes);
    if (!info) {
      problems.push(`${name}: JPEG·PNG·GIF 가 아님`);
      continue;
    }
    if (bytes.length > IMAGE_MAX_BYTES) {
      problems.push(`${name}: ${bytes.length} bytes 로 업로드 상한(20MB) 초과`);
      continue;
    }
    images.push({ name, bytes, ...info, extension: EXTENSION_BY_FORMAT[info.format] ?? extname(name).slice(1) });
  }
  if (problems.length > 0) {
    throw new Error(`이미지 디렉터리 검증 실패\n  ${problems.join('\n  ')}`);
  }
  if (images.length === 0) {
    throw new Error(`${dir} 에 업로드할 이미지가 없습니다`);
  }
  return images;
}

// 매직 바이트로 형식을 판별하고 헤더에서 해상도를 읽는다. 확장자는 믿지 않는다.
export function inspectImage(bytes) {
  if (bytes.length >= 24 && bytes.readUInt32BE(0) === 0x89504e47) {
    return { format: 'png', mimeType: MIME_BY_FORMAT.png, width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
  }
  if (bytes.length >= 10 && bytes.toString('ascii', 0, 3) === 'GIF') {
    return { format: 'gif', mimeType: MIME_BY_FORMAT.gif, width: bytes.readUInt16LE(6), height: bytes.readUInt16LE(8) };
  }
  if (bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8) {
    const size = readJpegSize(bytes);
    return size && { format: 'jpeg', mimeType: MIME_BY_FORMAT.jpeg, ...size };
  }
  return null;
}

function readJpegSize(bytes) {
  let offset = 2;
  while (offset + 9 < bytes.length) {
    if (bytes[offset] !== 0xff) {
      return null;
    }
    const marker = bytes[offset + 1];
    const length = bytes.readUInt16BE(offset + 2);
    // SOF0~SOF15 중 DHT(C4)·JPG(C8)·DAC(CC) 를 뺀 것이 프레임 헤더다.
    if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
      return { height: bytes.readUInt16BE(offset + 5), width: bytes.readUInt16BE(offset + 7) };
    }
    offset += 2 + length;
  }
  return null;
}

export function summarizeImages(images) {
  const sizes = images.map((image) => image.bytes.length);
  const formats = {};
  for (const image of images) {
    formats[image.format] = (formats[image.format] ?? 0) + 1;
  }
  const pixels = images.map((image) => image.width * image.height);
  const smallest = images[pixels.indexOf(Math.min(...pixels))];
  const largest = images[pixels.indexOf(Math.max(...pixels))];
  return {
    count: images.length,
    formats,
    bytes: { min: Math.min(...sizes), max: Math.max(...sizes), avg: Math.round(sizes.reduce((a, b) => a + b, 0) / sizes.length) },
    resolution: { min: `${smallest.width}x${smallest.height}`, max: `${largest.width}x${largest.height}` },
  };
}

// 장마다 내용이 다른 PNG 를 만든다. 같은 바이트를 반복 업로드하면 중복 제거·캐시가 측정을 왜곡할 수 있다.
export function generatePng(width, height, seed) {
  const rowLength = width * 3 + 1;
  const raw = Buffer.alloc(rowLength * height);
  const baseR = (seed * 67) % 256;
  const baseG = (seed * 131) % 256;
  const baseB = (seed * 197) % 256;
  const block = 32 + (seed % 5) * 16;
  for (let y = 0; y < height; y++) {
    const row = y * rowLength;
    raw[row] = 0;
    for (let x = 0; x < width; x++) {
      const checker = (Math.floor(x / block) + Math.floor(y / block) + seed) % 2 === 0 ? 40 : 0;
      const pixel = row + 1 + x * 3;
      raw[pixel] = (baseR + ((x * 255) / width) + checker) & 0xff;
      raw[pixel + 1] = (baseG + ((y * 255) / height) + checker) & 0xff;
      raw[pixel + 2] = (baseB + (((x + y) * 127) / (width + height))) & 0xff;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // truecolor RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', header),
    pngChunk('IDAT', deflateSync(raw)),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

function pngChunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndData));
  return Buffer.concat([length, typeAndData, crc]);
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  }
  return c >>> 0;
});

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
