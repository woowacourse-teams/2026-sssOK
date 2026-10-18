import { parseArgs } from 'node:util';

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

export function parseCli(options, usage) {
  try {
    const { values } = parseArgs({ options: { ...options, help: { type: 'boolean' } }, strict: true });
    if (values.help) {
      console.log(usage);
      process.exit(0);
    }
    return values;
  } catch (e) {
    fail(`${e.message}\n\n${usage}`);
  }
}

export function requirePositiveInt(name, raw) {
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1) {
    fail(`--${name} 은 1 이상의 정수여야 합니다 (입력: ${raw})`);
  }
  return value;
}

export function requireNonNegativeInt(name, raw) {
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 0) {
    fail(`--${name} 은 0 이상의 정수여야 합니다 (입력: ${raw})`);
  }
  return value;
}

// 로컬이 아닌 대상은 데이터·스토리지·AI 비용이 실제로 발생하므로 --yes 로 의도를 확인받는다.
export function guardTarget(baseUrl, confirmed) {
  let url;
  try {
    url = new URL(baseUrl);
  } catch {
    fail(`--base-url 이 올바른 URL 이 아닙니다: ${baseUrl}`);
  }
  if (!LOCAL_HOSTS.has(url.hostname) && !confirmed) {
    fail(
      `${url.origin} 은 로컬이 아닙니다. 실행 시간·AI 비용 한도를 팀과 확인했다면 --yes 를 붙여 다시 실행하세요.\n` +
        '검색 분석이 켜진 환경에서는 업로드한 사진마다 외부 AI 호출이 발생합니다.',
    );
  }
  return url.origin + url.pathname.replace(/\/+$/, '');
}

export function fail(message) {
  console.error(`\n[실패] ${message}`);
  process.exit(1);
}

export function log(message) {
  console.log(`[${new Date().toISOString().slice(11, 19)}] ${message}`);
}
