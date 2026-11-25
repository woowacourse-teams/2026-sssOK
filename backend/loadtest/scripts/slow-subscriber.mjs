// 느린 구독자(읽기를 멈춘 SSE 연결)를 만든다(#833 H5). k6 는 받은 데이터를 항상 읽어서 이 상태를 만들 수 없다.
//
// sse-fanout.js 가 만드는 방 A 를 방장 계정의 "내 방 목록"에서 찾아 입장한 뒤, 구독을 열고 응답을 한 바이트도 읽지 않는다.
// 클라이언트 수신 버퍼와 서버 송신 버퍼가 차면 서버의 send 가 그 연결에서 멈추고, 같은 방 발행이 그 뒤에 줄 선다.
// Nginx 는 응답을 버퍼링해 이 상태를 흡수하므로 앱 포트에 직접 붙는다(--base-url 이 fixtures 와 달라도 된다).
//
// 순서: 이 스크립트를 먼저 띄운다 → 다른 터미널에서 sse-fanout.js 를 LOAD_START 를 넉넉히(예: 20s) 주고 실행한다.
//   node scripts/slow-subscriber.mjs --fixtures fixtures/<runId>.json --count 5
//   ./bin/k6 run -e PROFILE=load -e LOAD_START=20s ... k6/scenarios/sse-fanout.js
import http from 'node:http';
import https from 'node:https';

import { API_PREFIX, createApi } from '../seed/lib/api.mjs';
import { fail, guardTarget, log, parseCli, requirePositiveInt } from '../seed/lib/cli.mjs';
import { loadFixtures } from '../seed/lib/fixtures.mjs';

const USAGE = `사용법: node scripts/slow-subscriber.mjs --fixtures <file> [옵션]
  --fixtures <file>     sse-fanout.js 에 넘기는 것과 같은 fixtures (필수)
  --count <n>           읽기를 멈출 연결 수 (기본 1). 참여자 계정을 뒤에서부터 쓴다
  --base-url <URL>      구독을 열 주소. 기본은 fixtures 의 대상. dev 는 Nginx 를 거치지 않는 앱 포트를 준다
  --wait <sec>          방 A 가 생길 때까지 기다리는 시간 (기본 300)
  --hold <sec>          읽기를 멈춘 채 유지하는 시간 (기본 600). Ctrl+C 로 먼저 끝낼 수 있다
  --yes                 로컬이 아닌 대상에 실행한다`;

const args = parseCli({
  fixtures: { type: 'string' },
  count: { type: 'string', default: '1' },
  'base-url': { type: 'string' },
  wait: { type: 'string', default: '300' },
  hold: { type: 'string', default: '600' },
  yes: { type: 'boolean', default: false },
}, USAGE);

if (!args.fixtures) fail(`--fixtures 가 필요합니다\n\n${USAGE}`);
const fixtures = loadFixtures(args.fixtures);
const count = requirePositiveInt('count', args.count);
const waitSec = requirePositiveInt('wait', args.wait);
const holdSec = requirePositiveInt('hold', args.hold);
const apiBase = guardTarget(fixtures.baseUrl, args.yes);
const streamBase = guardTarget(args['base-url'] ?? fixtures.baseUrl, args.yes);

// sse-fanout.js 의 방 이름 접두사(NAME_TAG)와 같아야 한다.
const ROOM_TAG = `lt${fixtures.runId.slice(-4)}f`;
const host = fixtures.rooms[0].host;
const participants = fixtures.rooms.flatMap((room) => room.participants);
if (count > participants.length) fail(`--count(${count})가 fixtures 참여자 수(${participants.length})보다 많습니다`);
// sse-fanout.js 는 업로더를 앞에서부터 쓰므로 겹치지 않게 뒤에서부터 쓴다.
const accounts = participants.slice(-count);

const api = createApi(apiBase);
const connections = [];

const room = await waitForRoom();
log(`방 A roomId=${room.roomId} 발견 — 계정 ${count}개 입장 후 구독을 열고 읽기를 멈춘다`);
for (const account of accounts) {
  await api.joinRoom(account.accessToken, room.roomId, room.code);
}
for (const account of accounts) {
  connections.push(openPaused(room.roomId, account));
}

process.on('SIGINT', () => finish('Ctrl+C'));
setTimeout(() => finish(`${holdSec}초 유지`), holdSec * 1000);

// 실행 전부터 있던 같은 접두사의 방은 지난 실행이 남긴 것이라 건너뛴다.
async function waitForRoom() {
  const existing = new Set((await myTaggedRooms()).map((r) => r.roomId));
  log(`방 A(이름 ${ROOM_TAG}…)가 생기기를 기다린다. 이제 sse-fanout.js 를 실행한다 (최대 ${waitSec}초)`);
  const deadline = Date.now() + waitSec * 1000;
  while (Date.now() < deadline) {
    const created = (await myTaggedRooms()).find((r) => !existing.has(r.roomId));
    if (created) return created;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  fail(`${waitSec}초 안에 방 A 가 생기지 않았습니다`);
}

async function myTaggedRooms() {
  const { data } = await api.myRooms(host.accessToken);
  return data.rooms.filter((r) => r.isHost && r.name.startsWith(ROOM_TAG));
}

// 응답 헤더를 받으면 바로 멈추고 본문을 읽지 않는다. 서버는 첫 데이터를 보낼 때 헤더를 내보낸다.
function openPaused(roomId, account) {
  const url = new URL(`${streamBase}${API_PREFIX}/rooms/${roomId}/events`);
  const client = url.protocol === 'https:' ? https : http;
  const state = { userId: account.userId, status: null, openedAt: null, closedAt: null, reason: null };
  const startedAt = Date.now();
  const req = client.get(url, { headers: { Authorization: `Bearer ${account.accessToken}`, Accept: 'text/event-stream' } }, (res) => {
    state.status = res.statusCode;
    state.openedAt = Date.now();
    res.pause();
    if (res.statusCode !== 200) {
      state.reason = `status ${res.statusCode}`;
      log(`userId=${account.userId} 구독 실패: ${state.reason}`);
      res.destroy();
      return;
    }
    log(`userId=${account.userId} 구독 열림 (첫 데이터까지 ${state.openedAt - startedAt}ms) — 읽기 멈춤`);
    res.on('close', () => {
      if (state.closedAt === null) {
        state.closedAt = Date.now();
        state.reason ??= '서버가 연결을 닫음';
        log(`userId=${account.userId} 연결 끝남: ${state.reason}`);
      }
    });
  });
  req.on('error', (e) => {
    state.closedAt ??= Date.now();
    state.reason ??= `오류 ${e.message}`;
  });
  return { req, state };
}

function finish(why) {
  log(`종료(${why}) — 연결을 닫는다`);
  for (const { req, state } of connections) {
    if (state.closedAt === null) state.reason = '스크립트가 닫음';
    req.destroy();
  }
  for (const { state } of connections) {
    const held = state.openedAt ? Math.round(((state.closedAt ?? Date.now()) - state.openedAt) / 1000) : 0;
    console.log(`  userId=${state.userId} status=${state.status} 유지 ${held}초 · ${state.reason}`);
  }
  process.exit(0);
}
