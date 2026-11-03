#!/usr/bin/env node
// fixtures 계정의 access 토큰을 refresh 토큰으로 새로 받는다. 서버 access 토큰은 기본 30분이라 측정 직전에 돌린다.
// refresh 토큰도 함께 바뀔 수 있어 응답 값으로 덮어쓴다. 실패한 계정이 있으면 파일은 저장하되 0 이 아닌 코드로 끝난다.
import { resolve } from 'node:path';

import { createApi, mapWithConcurrency } from './lib/api.mjs';
import { fail, guardTarget, log, parseCli } from './lib/cli.mjs';
import { loadFixtures, saveFixtures, usersOf } from './lib/fixtures.mjs';

const USAGE = `사용법: node seed/refresh-tokens.mjs [--fixtures <file>] [--yes]

  --fixtures <file>   seed.mjs 가 만든 fixtures 경로. 생략하면 환경 변수 FIXTURES
  --yes               로컬이 아닌 대상에 실행한다`;

const CONCURRENCY = 5;

const args = parseCli({
  fixtures: { type: 'string' },
  yes: { type: 'boolean', default: false },
}, USAGE);

const fixturesArg = args.fixtures ?? process.env.FIXTURES;
if (!fixturesArg) {
  fail(`--fixtures 또는 환경 변수 FIXTURES 가 필요합니다\n\n${USAGE}`);
}
const path = resolve(fixturesArg);
const fixtures = loadFixtures(path);
guardTarget(fixtures.baseUrl, args.yes);
const api = createApi(fixtures.baseUrl);

const accounts = fixtures.rooms.flatMap((room) => [...usersOf(room), ...(room.entrants ?? [])]);
const missing = accounts.filter((account) => !account.refreshToken);
if (missing.length > 0) {
  fail(`refresh 토큰이 없는 계정이 ${missing.length}개 있습니다. 이 도구 이전에 만든 fixtures 라 시드를 새로 준비한다`);
}

const failures = [];
await mapWithConcurrency(accounts, CONCURRENCY, async (account) => {
  try {
    const { data } = await api.refresh(account.refreshToken);
    account.accessToken = data.accessToken;
    account.expiresAt = data.expiresAt;
    account.refreshToken = data.refreshToken ?? account.refreshToken;
    account.refreshExpiresAt = data.refreshExpiresAt ?? account.refreshExpiresAt;
  } catch (error) {
    failures.push({ userId: account.userId, message: error.message });
  }
});
saveFixtures(path, fixtures);

const earliest = accounts.map((account) => account.expiresAt).filter(Boolean).sort()[0];
log(`토큰 갱신 ${accounts.length - failures.length}/${accounts.length}개, 가장 이른 만료 ${earliest}`);
if (failures.length > 0) {
  for (const failure of failures.slice(0, 5)) {
    console.error(`  userId=${failure.userId}: ${failure.message}`);
  }
  process.exit(1);
}
