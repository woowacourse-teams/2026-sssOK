// 결과 파일을 만든다. API 이름·구간(phase)별 지표는 thresholds 에 걸어야 k6 요약에 남으므로,
// 판정이 필요 없는 지표에도 항상 참인 조건을 걸어 하위 지표를 만든다.
import { env, fromCwd } from './config.js';

const ALWAYS = { count: 'count>=0', rate: 'rate>=0', trend: 'max>=0' };

export function trackApis(apis, phases) {
  const thresholds = {};
  for (const phase of phases) {
    for (const api of apis) {
      const tags = `{api:${api},phase:${phase}}`;
      thresholds[`http_reqs${tags}`] = [ALWAYS.count];
      thresholds[`http_req_duration${tags}`] = [ALWAYS.trend];
      thresholds[`http_req_failed${tags}`] = [ALWAYS.rate];
      thresholds[`business_ok${tags}`] = [ALWAYS.rate];
    }
    thresholds[`iterations{phase:${phase}}`] = [ALWAYS.count];
    thresholds[`dropped_iterations{phase:${phase}}`] = [ALWAYS.count];
  }
  return thresholds;
}

// SSE 처럼 API 요청이 아닌 지표를 결과에 남긴다. 태그를 붙인 이름은 threshold 가 있어야 요약에 나온다.
export function trackMetrics(names) {
  const thresholds = {};
  for (const [name, type] of Object.entries(names)) {
    thresholds[name] = [ALWAYS[type]];
  }
  return thresholds;
}

// 실행 메타데이터와 API 별 요청 수·p95·실패율·실제 요청률을 하나의 JSON 으로 묶는다.
export function writeSummary(data, { scenario, apis, phases, config, resultsDir, extras = [] }) {
  const setup = data.setup_data ?? {};
  const runId = env('RUN_ID', setup.runId ?? 'unknown');
  const base = `${resultsDir}/${runId}-${scenario}`;
  const durationSec = (data.state?.testRunDurationMs ?? 0) / 1000;

  const report = {
    runId,
    scenario,
    startedAtUtc: setup.startedAtUtc ?? null,
    finishedAtUtc: new Date().toISOString(),
    testRunDurationSec: round(durationSec),
    appVersion: setup.appVersion ?? null,
    scriptVersion: env('SCRIPT_VERSION', 'unknown'),
    target: setup.baseUrl ?? null,
    data: setup.dataCondition ?? null,
    config,
    phases: {},
    thresholds: failedThresholds(data),
    extras: {},
  };
  for (const name of extras) {
    report.extras[name] = roundValues(metric(data, name));
  }

  for (const phase of phases) {
    const apisReport = {};
    for (const api of apis) {
      const tags = `{api:${api},phase:${phase}}`;
      const reqs = metric(data, `http_reqs${tags}`);
      const duration = metric(data, `http_req_duration${tags}`);
      apisReport[api] = {
        requests: reqs?.count ?? 0,
        // 구간 길이가 아니라 전체 실행 시간으로 나눈 값이라, 구간별 실제 요청률은 phaseRate 를 본다.
        phaseRate: round((reqs?.count ?? 0) / phaseSeconds(config, phase, durationSec)),
        p95Ms: round(duration?.['p(95)']),
        p99Ms: round(duration?.['p(99)']),
        avgMs: round(duration?.avg),
        maxMs: round(duration?.max),
        httpFailRate: round(metric(data, `http_req_failed${tags}`)?.rate, 4),
        businessFailRate: round(1 - (metric(data, `business_ok${tags}`)?.rate ?? 1), 4),
      };
    }
    report.phases[phase] = {
      iterations: metric(data, `iterations{phase:${phase}}`)?.count ?? 0,
      droppedIterations: metric(data, `dropped_iterations{phase:${phase}}`)?.count ?? 0,
      apis: apisReport,
    };
  }

  return {
    [fromCwd(`${base}.json`)]: JSON.stringify(report, null, 2),
    [fromCwd(`${base}.k6.json`)]: JSON.stringify(data, null, 2),
    stdout: renderText(report, `${base}.json`),
  };
}

function phaseSeconds(config, phase, fallback) {
  const seconds = toSeconds(config.phaseDurations?.[phase]);
  return seconds > 0 ? seconds : Math.max(fallback, 1);
}

function toSeconds(duration) {
  const match = /^(\d+)(ms|s|m|h)$/.exec(duration ?? '');
  if (!match) {
    return 0;
  }
  const unit = { ms: 0.001, s: 1, m: 60, h: 3600 }[match[2]];
  return Number(match[1]) * unit;
}

function metric(data, name) {
  return data.metrics[name]?.values;
}

function failedThresholds(data) {
  const failed = [];
  for (const [name, m] of Object.entries(data.metrics)) {
    for (const [condition, result] of Object.entries(m.thresholds ?? {})) {
      if (!result.ok) {
        failed.push(`${name} ${condition}`);
      }
    }
  }
  return { passed: failed.length === 0, failed };
}

function roundValues(values) {
  if (!values) {
    return null;
  }
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, round(value, 4)]));
}

function round(value, digits = 2) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return null;
  }
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function renderText(report, path) {
  const lines = [
    '',
    `== ${report.scenario} (runId=${report.runId}) ==`,
    `대상 ${report.target} / 앱 ${JSON.stringify(report.appVersion)} / 스크립트 ${report.scriptVersion}`,
    `시작 ${report.startedAtUtc} · 종료 ${report.finishedAtUtc} (UTC)`,
  ];
  for (const [phase, p] of Object.entries(report.phases)) {
    lines.push(`-- ${phase}: iterations ${p.iterations}, dropped ${p.droppedIterations}`);
    for (const [api, a] of Object.entries(p.apis)) {
      lines.push(`   ${api.padEnd(20)} 요청 ${String(a.requests).padStart(6)}  ${String(a.phaseRate).padStart(7)} req/s  p95 ${String(a.p95Ms).padStart(8)}ms  HTTP 실패 ${pct(a.httpFailRate)}  업무 실패 ${pct(a.businessFailRate)}`);
    }
  }
  for (const [name, values] of Object.entries(report.extras)) {
    lines.push(`   ${name.padEnd(30)} ${values ? Object.entries(values).map(([k, v]) => `${k}=${v}`).join(' ') : '없음'}`);
  }
  lines.push(report.thresholds.passed ? '판정 기준 통과' : `판정 기준 미달: ${report.thresholds.failed.join(', ')}`);
  lines.push(`결과 파일: ${path}`, '');
  return lines.join('\n');
}

function pct(rate) {
  return rate === null ? '-' : `${(rate * 100).toFixed(2)}%`;
}
