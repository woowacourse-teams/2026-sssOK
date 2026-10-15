#!/usr/bin/env node
// 부하 시험용 계정·방·사진을 API 로만 만든다. DB 에 직접 넣지 않으므로 dev 와 로컬에서 같은 방식으로 돌아간다.
// 만든 자원은 생기는 즉시 fixtures 에 기록한다. 중간에 실패해도 그 파일로 이어서 만들거나(--resume) 정리한다.
import { randomBytes } from 'node:crypto';
import { relative as relativePath, resolve } from 'node:path';

import { createApi, mapWithConcurrency, putToStorage, sleep } from './lib/api.mjs';
import { fail, guardTarget, log, parseCli, requireNonNegativeInt, requirePositiveInt } from './lib/cli.mjs';
import {
  createFixtures, fixturesExists, loadFixtures, registeredPhotos, saveFixtures, Status, unusedEntrants, usersOf,
} from './lib/fixtures.mjs';
import { createImageSource } from './lib/images.mjs';

const USAGE = `사용법: node seed/seed.mjs --base-url <URL> [옵션]

  --base-url <URL>        대상 API 주소 (필수, 예: http://localhost:8080)
  --rooms <n>             만들 방 수 (기본 1)
  --members <n>           방당 계정 수, 방장 포함 (기본 30)
  --photos <n>            방당 사진 수 (기본 300)
  --entrants <n>          방당 신규 입장 시험용 미입장 계정 수 (기본 0).
                          --resume 과 함께 쓰면 이미 입장한 계정을 빼고 n 개가 되도록 채운다
  --images-dir <dir>      실제 사진 디렉터리. 생략하면 PNG 를 생성한다
  --out <file>            fixtures 경로 (기본 fixtures/<runId>.json)
  --resume <file>         실패한 fixtures 를 이어서 준비한다
  --concurrency <n>       동시에 처리할 10장 묶음 수 (기본 3)
  --ready-timeout <sec>   썸네일 준비 완료 대기 시간 (기본 600, 0 이면 기다리지 않음)
  --allow-ai-cost         검색 분석이 켜진 대상에도 업로드한다 (사진마다 외부 AI 호출)
  --yes                   로컬이 아닌 대상에 실행한다`;

const UPLOAD_BATCH_SIZE = 10;
const PUT_CONCURRENCY_PER_BATCH = 3;
const ACCOUNT_CONCURRENCY = 5;
const MAX_PER_NAME_SLOT = 99;

const args = parseCli({
  'base-url': { type: 'string' },
  rooms: { type: 'string', default: '1' },
  members: { type: 'string', default: '30' },
  photos: { type: 'string', default: '300' },
  entrants: { type: 'string' },
  'images-dir': { type: 'string' },
  out: { type: 'string' },
  resume: { type: 'string' },
  concurrency: { type: 'string', default: '3' },
  'ready-timeout': { type: 'string', default: '600' },
  'allow-ai-cost': { type: 'boolean', default: false },
  yes: { type: 'boolean', default: false },
}, USAGE);

if (!args['base-url']) {
  fail(`--base-url 이 필요합니다\n\n${USAGE}`);
}
const baseUrl = guardTarget(args['base-url'], args.yes);
const concurrency = requirePositiveInt('concurrency', args.concurrency);
const readyTimeoutSec = Number(args['ready-timeout']);
const api = createApi(baseUrl);
const images = createImageSource({ imagesDir: args['images-dir'] });

const { fixtures, path } = await prepareFixtures();
log(`fixtures: ${path}`);
log(`이미지: ${images.description}`);

try {
  for (let index = 0; index < fixtures.condition.rooms; index++) {
    await seedRoom(index);
  }
  if (readyTimeoutSec > 0) {
    await waitUntilReady();
  }
  fixtures.verification = await verify();
  fixtures.status = fixtures.verification.passed ? Status.COMPLETED : Status.FAILED;
  fixtures.lastError = fixtures.verification.passed ? null : '검증 실패 — verification 항목 확인';
  save();
} catch (e) {
  fixtures.status = Status.FAILED;
  fixtures.lastError = e.message;
  save();
  fail(`${e.message}\n생성된 자원은 ${path} 에 기록되어 있습니다. 이어서 준비: --resume ${path} / 정리: node seed/cleanup.mjs --fixtures ${path}`);
}

printSummary();
if (fixtures.status !== Status.COMPLETED) {
  process.exit(1);
}

async function prepareFixtures() {
  if (args.resume) {
    const resumePath = resolve(args.resume);
    const loaded = loadFixtures(resumePath);
    if (loaded.baseUrl !== baseUrl) {
      fail(`fixtures 의 대상(${loaded.baseUrl})과 --base-url(${baseUrl})이 다릅니다`);
    }
    if (loaded.status === Status.CLEANED) {
      fail('이미 정리된 fixtures 는 이어서 준비할 수 없습니다');
    }
    if (args.entrants !== undefined) {
      loaded.condition.entrantsPerRoom = requireNonNegativeInt('entrants', args.entrants);
    }
    loaded.status = Status.IN_PROGRESS;
    loaded.lastError = null;
    return { fixtures: loaded, path: resumePath };
  }

  const rooms = requirePositiveInt('rooms', args.rooms);
  const members = requirePositiveInt('members', args.members);
  const photos = requirePositiveInt('photos', args.photos);
  const entrants = requireNonNegativeInt('entrants', args.entrants ?? '0');
  if (rooms > MAX_PER_NAME_SLOT || members > MAX_PER_NAME_SLOT || entrants > MAX_PER_NAME_SLOT) {
    fail(`--rooms·--members·--entrants 는 ${MAX_PER_NAME_SLOT} 이하여야 합니다 (닉네임 12자 제한)`);
  }

  const { data: version } = await api.version().catch((e) => fail(`대상 서버에 연결하지 못했습니다: ${e.message}`));
  const runId = `${new Date().toISOString().replace(/[-:]/g, '').slice(0, 13)}Z-${randomBytes(2).toString('hex')}`;
  const outPath = resolve(args.out ?? `fixtures/${runId}.json`);
  if (fixturesExists(outPath)) {
    fail(`${outPath} 가 이미 있습니다. 덮어쓰면 정리 대상 기록이 사라집니다`);
  }
  const created = createFixtures({
    runId,
    baseUrl,
    appVersion: version,
    condition: {
      rooms,
      membersPerRoom: members,
      photosPerRoom: photos,
      entrantsPerRoom: entrants,
      image: images.summary ?? { description: images.description },
    },
  });
  saveFixtures(outPath, created);
  return { fixtures: created, path: outPath };
}

function save() {
  saveFixtures(path, fixtures);
}

function nameTag() {
  return `lt${fixtures.runId.slice(-4)}`;
}

async function seedRoom(index) {
  const room = fixtures.rooms[index] ?? { index, name: `${nameTag()}-r${index + 1}`, host: null, participants: [], entrants: [], photos: [] };
  fixtures.rooms[index] = room;
  const label = `방 ${index + 1}/${fixtures.condition.rooms}`;

  if (!room.host) {
    room.host = await createAccount(index, 0);
    save();
  }
  if (!room.roomId) {
    const { data } = await api.createRoom(room.host.accessToken, room.name);
    Object.assign(room, { roomId: data.roomId, code: data.code });
    room.host.joined = true;
    save();
    log(`${label}: 방 생성 roomId=${room.roomId} code=${room.code}`);
  }

  const participantCount = fixtures.condition.membersPerRoom - 1;
  const missing = Array.from({ length: participantCount }, (_, i) => i + 1)
    .filter((userNo) => !room.participants[userNo - 1]);
  await mapWithConcurrency(missing, ACCOUNT_CONCURRENCY, async (userNo) => {
    room.participants[userNo - 1] = await createAccount(index, userNo);
    save();
  });

  const notJoined = room.participants.filter((user) => !user.joined);
  await mapWithConcurrency(notJoined, ACCOUNT_CONCURRENCY, async (user) => {
    await api.joinRoom(user.accessToken, room.roomId);
    user.joined = true;
    save();
  });
  log(`${label}: 계정 ${usersOf(room).length}개 준비·입장 완료`);

  await prepareEntrants(room, index, label);
  await guardAiCost(room);
  await uploadPhotos(room, label);
}

// 신규 입장 시험용 계정은 만들기만 하고 방에 넣지 않는다. 입장은 k6 가 측정하면서 한다.
// 시험에서 한 번 입장한 계정은 서버에 joined 로 남으므로, 이어서 준비할 때 서버 상태로 표시하고 새 계정을 채운다.
async function prepareEntrants(room, index, label) {
  await refreshEntrantJoined(room);
  const target = fixtures.condition.entrantsPerRoom;
  const missing = target - unusedEntrants(room).length;
  if (missing <= 0) {
    return;
  }
  if (room.entrants.length + missing > MAX_PER_NAME_SLOT) {
    throw new Error(`${label}: 미입장 계정을 ${MAX_PER_NAME_SLOT}개 넘게 만들 수 없습니다 (닉네임 12자 제한). 시드를 새로 준비한다`);
  }
  const numbers = Array.from({ length: missing }, (_, i) => room.entrants.length + i + 1);
  await mapWithConcurrency(numbers, ACCOUNT_CONCURRENCY, async (entrantNo) => {
    const { data } = await api.createAnonymous(`${nameTag()}r${index + 1}e${entrantNo}`);
    room.entrants[entrantNo - 1] = { userId: data.userId, nickname: data.nickname, accessToken: data.accessToken, expiresAt: data.expiresAt, joined: false };
    save();
  });
  log(`${label}: 신규 입장용 미입장 계정 ${unusedEntrants(room).length}개 준비`);
}

async function refreshEntrantJoined(room) {
  const unknown = unusedEntrants(room);
  await mapWithConcurrency(unknown, ACCOUNT_CONCURRENCY, async (entrant) => {
    const { data } = await api.getRoom(entrant.accessToken, room.code);
    entrant.joined = data.joined;
  });
  if (unknown.length > 0) {
    save();
  }
}

// 검색 분석이 켜진 서버는 등록된 사진마다 설명 생성·임베딩을 외부 AI 에 요청한다.
// 목록 시험에는 분석 결과가 필요 없으므로, 켜져 있으면 명시적으로 허용했을 때만 올린다.
// 검색 API 가 200 이면 켜진 것이다. 503 은 꺼졌거나 검색 임계값만 비어 있는 경우라 분석 워커는 돌 수도 있다.
async function guardAiCost(room) {
  if (!fixtures.searchProbe) {
    const { status } = await api.searchMedia(room.host.accessToken, room.roomId, 'loadtest probe');
    fixtures.searchProbe = { status, searchEnabled: status === 200, checkedAt: new Date().toISOString() };
    save();
  }
  const { status } = fixtures.searchProbe;
  if (status === 200 && !args['allow-ai-cost']) {
    throw new Error(
      `대상 서버의 이미지 검색이 켜져 있어 사진 ${fixtures.condition.rooms * fixtures.condition.photosPerRoom}장마다 외부 AI 호출이 생깁니다. ` +
        '비용 한도를 확인했으면 --allow-ai-cost 를 붙여 --resume 하세요',
    );
  }
  log(status === 200 ? '검색 분석 켜짐 — --allow-ai-cost 로 진행' : '검색 API 503 — 분석 꺼짐으로 판단');
}

async function createAccount(roomIndex, userNo) {
  const nickname = `${nameTag()}r${roomIndex + 1}u${userNo}`;
  const { data } = await api.createAnonymous(nickname);
  return { userId: data.userId, nickname: data.nickname, accessToken: data.accessToken, expiresAt: data.expiresAt, joined: false };
}

async function uploadPhotos(room, label) {
  await reconcileReserved(room);
  const target = fixtures.condition.photosPerRoom;
  const remaining = target - registeredPhotos(room).length;
  if (remaining <= 0) {
    log(`${label}: 사진 ${target}장 이미 등록됨`);
    return;
  }

  // 업로더를 방 안의 모든 계정에 돌려 배정해 내 사진/다른 사람 사진 필터가 의미 있게 한다.
  const users = usersOf(room);
  const offset = room.photos.length;
  const batches = [];
  for (let i = 0; i < remaining; i += UPLOAD_BATCH_SIZE) {
    const count = Math.min(UPLOAD_BATCH_SIZE, remaining - i);
    const user = users[(batches.length + offset) % users.length];
    batches.push({ user, seeds: Array.from({ length: count }, (_, k) => offset + i + k) });
  }

  let done = 0;
  await mapWithConcurrency(batches, concurrency, async (batch) => {
    await uploadBatch(room, batch);
    done += batch.seeds.length;
    if (done % 50 < UPLOAD_BATCH_SIZE || done === remaining) {
      log(`${label}: 업로드 ${done}/${remaining}`);
    }
  });

  const failed = room.photos.filter((photo) => photo.status === 'FAILED');
  if (registeredPhotos(room).length < target) {
    throw new Error(`${label}: 사진 ${registeredPhotos(room).length}/${target}장만 등록됨 (실패 ${failed.length}장)`);
  }
}

async function uploadBatch(room, { user, seeds }) {
  const files = seeds.map((seed) => {
    const image = images.get(seed);
    const fileName = `${fixtures.runId}-${String(seed + 1).padStart(5, '0')}.${image.extension}`;
    return { image, request: { fileName, mimeType: image.mimeType, size: image.bytes.length } };
  });

  const { data: issued } = await api.issueUploadUrls(user.accessToken, room.roomId, files.map((file) => file.request));
  // 발급되는 순간 서버에 예약 행이 생기므로, PUT 전에 먼저 기록해 둔다.
  const photos = issued.issued.map((item) => {
    const file = files.find((f) => f.request.fileName === item.fileName);
    const photo = {
      mediaId: item.mediaId,
      fileName: item.fileName,
      uploaderUserId: user.userId,
      mimeType: file.request.mimeType,
      size: file.request.size,
      width: file.image.width,
      height: file.image.height,
      status: 'RESERVED',
    };
    room.photos.push(photo);
    return { photo, issued: item, bytes: file.image.bytes };
  });
  for (const rejected of issued.rejected) {
    room.photos.push({ mediaId: null, fileName: rejected.fileName, uploaderUserId: user.userId, status: 'FAILED', failure: `발급 거부 ${rejected.code}` });
  }
  save();

  const uploaded = [];
  await mapWithConcurrency(photos, PUT_CONCURRENCY_PER_BATCH, async (entry) => {
    try {
      await putToStorage(entry.issued, entry.bytes);
      uploaded.push(entry.photo);
    } catch (e) {
      entry.photo.status = 'FAILED';
      entry.photo.failure = e.message;
    }
  });
  if (uploaded.length === 0) {
    save();
    return;
  }

  const { data: completed } = await api.completeUpload(user.accessToken, room.roomId, uploaded.map((photo) => photo.mediaId));
  for (const media of completed.registered) {
    const photo = uploaded.find((p) => p.mediaId === media.mediaId);
    photo.status = media.status;
  }
  for (const failedMedia of completed.failed) {
    const photo = uploaded.find((p) => p.mediaId === failedMedia.mediaId);
    photo.status = 'FAILED';
    photo.failure = `완료 등록 실패 ${failedMedia.code}`;
  }
  save();
}

// 완료 등록 응답을 받기 전에 끊기면 서버에는 등록됐는데 fixtures 에는 RESERVED 로 남는다.
// 이어서 준비할 때 서버 목록과 맞춰 두지 않으면 사진 수가 목표보다 많아진다.
async function reconcileReserved(room) {
  const reserved = room.photos.filter((photo) => photo.status === 'RESERVED');
  if (reserved.length === 0) {
    return;
  }
  const statuses = await fetchStatuses(room);
  let recovered = 0;
  for (const photo of reserved) {
    const status = statuses.get(photo.mediaId);
    if (status) {
      photo.status = status;
      recovered++;
    }
  }
  save();
  if (recovered > 0) {
    log(`방 ${room.index + 1}: 기록이 누락된 등록 사진 ${recovered}장을 서버 목록에서 복구`);
  }
}

// 목록 시험은 썸네일이 준비된 상태를 기준으로 한다. PROCESSING 이 섞이면 응답 크기와 처리가 달라진다.
async function waitUntilReady() {
  const deadline = Date.now() + readyTimeoutSec * 1000;
  for (const room of fixtures.rooms) {
    while (true) {
      const statuses = await fetchStatuses(room);
      let processing = 0;
      for (const photo of registeredPhotos(room)) {
        const status = statuses.get(photo.mediaId);
        // 썸네일 처리에 실패하면 FAILED 가 되어 목록에서 빠진다.
        photo.status = status ?? 'FAILED';
        if (!status) {
          photo.failure = '등록 후 목록에서 사라짐 (썸네일 처리 실패 가능)';
        }
        if (photo.status === 'PROCESSING') {
          processing++;
        }
      }
      save();
      if (processing === 0) {
        log(`방 ${room.index + 1}: 썸네일 준비 완료`);
        break;
      }
      if (Date.now() > deadline) {
        throw new Error(`방 ${room.index + 1}: ${readyTimeoutSec}초 안에 ${processing}장이 준비되지 않았습니다`);
      }
      log(`방 ${room.index + 1}: 썸네일 준비 대기 (${processing}장 남음)`);
      await sleep(5000);
    }
  }
}

async function fetchStatuses(room) {
  const statuses = new Map();
  let cursor;
  do {
    const { data } = await api.listMedia(room.host.accessToken, room.roomId, { cursor });
    for (const item of data.items) {
      statuses.set(item.mediaId, item.status);
    }
    cursor = data.nextCursor;
  } while (cursor);
  return statuses;
}

// 다시 조회해서 서버 상태가 fixtures 와 같은지 확인한다. 응답 코드만으로 준비 완료를 판단하지 않는다.
async function verify() {
  const problems = [];
  const rooms = [];
  for (const room of fixtures.rooms) {
    const expected = registeredPhotos(room).length;
    const { data: detail } = await api.getRoom(room.host.accessToken, room.code);
    const { data: page } = await api.listMedia(room.host.accessToken, room.roomId, { size: 1 });
    const statuses = await fetchStatuses(room);
    const notReady = [...statuses.values()].filter((status) => status !== 'READY').length;

    let notJoined = 0;
    await mapWithConcurrency(usersOf(room), ACCOUNT_CONCURRENCY, async (user) => {
      const { data } = await api.getRoom(user.accessToken, room.code);
      if (!data.joined) {
        notJoined++;
      }
    });

    await refreshEntrantJoined(room);
    const entrants = unusedEntrants(room).length;

    const label = `방 ${room.index + 1}`;
    if (entrants !== fixtures.condition.entrantsPerRoom) problems.push(`${label}: 미입장 계정 ${entrants}/${fixtures.condition.entrantsPerRoom}`);
    if (detail.hostId !== room.host.userId) problems.push(`${label}: 방장이 fixtures 와 다름`);
    if (usersOf(room).length !== fixtures.condition.membersPerRoom) problems.push(`${label}: 계정 수 불일치`);
    if (notJoined > 0) problems.push(`${label}: 입장하지 않은 계정 ${notJoined}개`);
    if (expected !== fixtures.condition.photosPerRoom) problems.push(`${label}: 등록 사진 ${expected}/${fixtures.condition.photosPerRoom}`);
    if (detail.photoCount !== expected) problems.push(`${label}: 방 photoCount ${detail.photoCount} ≠ 등록 ${expected}`);
    if (page.totalCount !== expected) problems.push(`${label}: 목록 totalCount ${page.totalCount} ≠ 등록 ${expected}`);
    if (readyTimeoutSec > 0 && notReady > 0) problems.push(`${label}: READY 가 아닌 사진 ${notReady}장`);

    rooms.push({ roomId: room.roomId, users: usersOf(room).length, notJoined, entrants, photoCount: detail.photoCount, listTotalCount: page.totalCount, notReady });
  }
  return { passed: problems.length === 0, checkedAt: new Date().toISOString(), problems, rooms };
}

function printSummary() {
  const v = fixtures.verification;
  console.log(`\n시드 ${fixtures.status} (runId=${fixtures.runId})`);
  for (const room of v?.rooms ?? []) {
    console.log(`  roomId=${room.roomId} 계정 ${room.users}개(미입장 ${room.notJoined}) 신규 입장용 ${room.entrants ?? 0}개 사진 ${room.photoCount}장(목록 ${room.listTotalCount}, 미준비 ${room.notReady})`);
  }
  for (const problem of v?.problems ?? []) {
    console.log(`  ✗ ${problem}`);
  }
  console.log(`  fixtures: ${path}`);
  if (fixtures.status === Status.COMPLETED) {
    const relative = relativePath(process.cwd(), path);
    console.log(`
다음 단계 — 아래 줄을 그대로 복사해 실행한다
  export FIXTURES=${relative}
  k6 run -e PROFILE=smoke k6/scenarios/media-list.js${fixtures.condition.entrantsPerRoom > 0 ? '\n  ./bin/k6 run -e PROFILE=smoke k6/scenarios/room-entry.js' : ''}
  node seed/cleanup.mjs --dry-run`);
  }
}
