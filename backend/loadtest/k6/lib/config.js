// 실행 설정과 fixtures 를 읽고 검증한다. init 단계에서 실패시켜 잘못된 설정으로 요청이 나가지 않게 한다.
// fixtures 형식은 seed/lib/fixtures.mjs 가 정한다.

// v1 은 신규 입장용 계정(entrants)이 없는 형식이다. 읽을 수는 있고 entrants 만 비어 있다.
const SUPPORTED_SCHEMAS = new Set([1, 2]);

export function env(name, fallback) {
  const value = __ENV[name];
  return value === undefined || value === '' ? fallback : value;
}

export function intEnv(name, fallback) {
  const raw = env(name, String(fallback));
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`${name} 은 0 이상의 정수여야 합니다 (입력: ${raw})`);
  }
  return value;
}

export function durationEnv(name, fallback) {
  const value = env(name, fallback);
  if (!/^\d+(ms|s|m|h)$/.test(value)) {
    throw new Error(`${name} 은 30s·3m 같은 형식이어야 합니다 (입력: ${value})`);
  }
  return value;
}

// k6 의 open() 은 상대 경로를 스크립트 위치 기준으로 푼다. 사용자가 실행한 위치 기준으로 맞춘다.
export function fromCwd(path) {
  return path.startsWith('/') ? path : `${__ENV.PWD}/${path}`;
}

// k6 는 결과 파일을 시험이 끝난 뒤에 쓰고, 폴더가 없으면 그때 실패해 결과를 통째로 잃는다.
// open() 은 폴더에 쓰면 "can't be used with directories" 로, 없는 경로면 "no such file" 로 실패하므로
// 그 차이로 시작 전에 폴더가 있는지 확인한다.
export function resolveResultsDir() {
  const dir = env('RESULTS_DIR', 'results');
  try {
    open(fromCwd(dir));
  } catch (e) {
    if (String(e).includes('directories')) {
      return dir;
    }
    throw new Error(`RESULTS_DIR(${dir}) 폴더가 없습니다. 먼저 만든다: mkdir -p ${dir}`);
  }
  throw new Error(`RESULTS_DIR(${dir}) 이 폴더가 아니라 파일입니다`);
}

export function loadFixtures() {
  const path = env('FIXTURES');
  if (!path) {
    throw new Error('FIXTURES 가 필요합니다. 예: -e FIXTURES=fixtures/<runId>.json');
  }
  const fixtures = JSON.parse(open(fromCwd(path)));
  if (!SUPPORTED_SCHEMAS.has(fixtures.schemaVersion)) {
    throw new Error(`fixtures schemaVersion ${fixtures.schemaVersion} 은 지원하지 않습니다`);
  }
  if (fixtures.status !== 'COMPLETED') {
    throw new Error(`fixtures 상태가 ${fixtures.status} 입니다. 시드 검증을 통과한 fixtures 만 쓴다`);
  }
  const now = Date.now();
  for (const room of fixtures.rooms) {
    room.entrants ??= [];
    for (const user of [room.host, ...room.participants, ...room.entrants]) {
      if (Date.parse(user.expiresAt) <= now) {
        throw new Error(`userId=${user.userId} 토큰이 만료되었습니다. 시드를 다시 준비한다`);
      }
    }
  }
  return { path, fixtures };
}

export function resolveBaseUrl(fixtures) {
  const baseUrl = env('BASE_URL', fixtures.baseUrl).replace(/\/+$/, '');
  if (baseUrl !== fixtures.baseUrl) {
    throw new Error(`BASE_URL(${baseUrl})과 fixtures 의 대상(${fixtures.baseUrl})이 다릅니다. 다른 환경의 토큰으로 요청하지 않는다`);
  }
  return baseUrl;
}

// 목록에 보여야 하는 사진 수. 시드 검증을 통과한 fixtures 라 PROCESSING 은 없어야 하지만 seed 와 같은 기준으로 센다.
export function expectedPhotos(room) {
  return room.photos.filter((p) => p.status === 'READY' || p.status === 'PROCESSING').length;
}

// 상세·좋아요·폴더 담기에 쓸 사진. 썸네일까지 준비된 것만 쓴다.
export function readyPhotoIds(room) {
  return room.photos.filter((p) => p.status === 'READY').map((p) => p.mediaId);
}

// 가상 사용자마다 계정 하나를 고정한다. 방 순서대로 이어 붙이고, 가상 사용자 번호로 돌려 쓴다.
// 방이 여러 개면 VUS 를 방 수 × 방당 인원으로 맞춰야 모든 방에 같은 인원이 붙는다.
export function buildUsers(fixtures) {
  const users = [];
  for (const room of fixtures.rooms) {
    const photoIds = readyPhotoIds(room);
    for (const user of [room.host, ...room.participants]) {
      users.push({ userId: user.userId, token: user.accessToken, roomId: room.roomId, code: room.code, expectedPhotos: expectedPhotos(room), photoIds });
    }
  }
  return users;
}

// 신규 입장 시험용 미입장 계정. 방을 돌아가며 섞어서 입장 시간대마다 방별 부하가 고르게 한다.
// fixtures 의 joined 는 시드 시점 값이라, 실제로 아직 입장 전인지는 시나리오 setup 에서 서버에 다시 묻는다.
export function buildEntrants(fixtures) {
  const perRoom = fixtures.rooms.map((room) => room.entrants
    .filter((entrant) => !entrant.joined)
    .map((entrant) => ({ userId: entrant.userId, token: entrant.accessToken, roomId: room.roomId, code: room.code, expectedPhotos: expectedPhotos(room) })));
  const entrants = [];
  for (let i = 0; perRoom.some((list) => i < list.length); i++) {
    for (const list of perRoom) {
      if (i < list.length) entrants.push(list[i]);
    }
  }
  return entrants;
}

export function userForVu(users, vu) {
  return users[(vu - 1) % users.length];
}
