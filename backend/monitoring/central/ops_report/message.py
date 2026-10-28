"""Discord 보고서. 숫자와 규칙으로 확인한 사실만 쓰고 원인은 추론하지 않는다."""
from __future__ import annotations

from datetime import datetime
import json
from urllib.parse import urlencode

from . import ENVIRONMENT
from .aggregate import USABLE, Data
from .periods import KST, Period

GRAFANA = 'https://monitor.ssssok.com'
DASHBOARDS = (('앱', 'sssok-central-backend'), ('호스트', 'sssok-host-overview'),
              ('미디어', 'sssok-central-media'), ('로그 전송', 'sssok-log-transfer'))
# 월요일에는 embed 두 개가 메시지 전체 embed 6000자 제한을 나눠 쓴다.
DESCRIPTION_LIMIT = 2800
TOP = 3
ALERT_NAMES = {
    'BackendDown': '앱 지표 수집 실패', 'BackendHigh5xxErrorRate': '5xx 비율 기준 초과',
    'BackendHighLatency': '응답 지연 기준 초과', 'MetricsEndpointDown': '공통 지표 수집 실패',
    'NodeMetricsDown': '호스트 지표 수집 실패', 'HostHighCPU': 'CPU 기준 초과', 'HostMemoryLow': '메모리 여유 부족',
    'HostDiskSpaceLow': '디스크 여유 부족', 'HostDiskSpaceCritical': '디스크 고갈 임박',
    'HostDiskWillFillIn24h': '디스크 24시간 내 고갈 예상', 'LogPipelineDown': 'Alloy 수집 실패',
    'LogShippingFailing': '로그 전송 실패 지속', 'LogEntriesDropped': '로그 폐기',
}
STATE = {'no_data': '데이터 없음', 'no_requests': '요청 없음', 'error': '조회 실패',
         'incomparable': '비교 불가', 'partial': '일부 기간'}


def links(period: Period) -> list:
    window = {'from': int(period.start.timestamp() * 1000), 'to': int(period.end.timestamp() * 1000) - 1}
    return [(name, f'{GRAFANA}/d/{uid}?' + urlencode({'var-environment': ENVIRONMENT, **window}))
            for name, uid in DASHBOARDS]


def count(value) -> str:
    return f'{value:,.0f}'


def counted(value) -> str:
    return f'{value:,.0f}건'


def percent(value, digits=2) -> str:
    return f'{value * 100:.{digits}f}%'


def duration(seconds) -> str:
    return f'{seconds * 1000:.0f}ms' if seconds < 1 else f'{seconds:.2f}s'


def size(value) -> str:
    return f'{value / 1024 ** 3:.1f}GiB'


def minutes(seconds) -> str:
    return f'{seconds / 60:.0f}분'


def show(data: Data, metric: str, fmt, dimension: str = '') -> str:
    obs = data.get(metric, dimension)
    if obs is None:
        return '데이터 없음'
    if obs.status not in USABLE or obs.value is None:
        return STATE.get(obs.status, obs.status)
    text = fmt(obs.value)
    return text + (' (일부 기간)' if obs.status == 'partial' else '')


def change(comparisons: dict, metric: str, fmt, label: str) -> str:
    row = comparisons.get(metric)
    if row is None:
        return ''
    state = row['status']
    if state == 'ok':
        sign = '+' if row['delta'] >= 0 else '-'
        if metric.endswith('ratio') or metric == 'error_rate':
            return f' ({label} {sign}{abs(row["delta"]) * 100:.2f}%p)'
        return f' ({label} {sign}{abs(row["ratio"]) * 100:.1f}%, {fmt(row["previous_value"])})'
    return {'new': f' ({label} 0 → 신규 발생)', 'both_zero': '',
            'no_baseline': f' ({label} 비교 데이터 부족)', 'partial_data': f' ({label} 일부 기간이라 비교 생략)',
            'incomparable': f' ({label} 비교 불가)'}.get(state, '')


def top(data: Data, metric: str, limit: int = TOP) -> list:
    rows = [(dim, obs) for dim, obs in data.dims(metric).items() if obs.value is not None]
    return sorted(rows, key=lambda item: -item[1].value)[:limit]


def _when(timestamp: float, period: Period) -> str:
    moment = datetime.fromtimestamp(timestamp, KST)
    return f'{moment:%H:%M}' if period.kind == 'daily' else f'{moment:%m-%d %H:%M}'


def _intervals(data: Data, period: Period) -> list:
    lines = []
    grouped = {}
    for item in data.intervals:
        grouped.setdefault(item.alertname, []).append(item)
    for name, items in sorted(grouped.items(), key=lambda pair: -sum(i.observed_seconds for i in pair[1])):
        total = sum(i.observed_seconds for i in items)
        first = min(items, key=lambda i: i.start)
        span = f'{_when(first.start, period)}~{_when(first.end, period)}'
        target = json.loads(first.labels)
        where = target.get('host') or target.get('instance') or target.get('server', '')
        lines.append(f'- {ALERT_NAMES.get(name, name)} `{name}` {len(items)}회 · 관측 {minutes(total)}'
                     f' · 첫 구간 {span}' + (f' · {where}' if where else ''))
    return lines


def _numbers(data: Data, comparisons: dict, label: str) -> list:
    requests = show(data, 'requests_total', counted)
    lines = [f'- 요청 {requests}' + change(comparisons, 'requests_total', counted, label)]
    busy = top(data, 'requests_by_api')
    if busy:
        lines.append('  주요 API: ' + ' · '.join(f'`{dim}` {count(o.value)}' for dim, o in busy))
    lines.append(f'- 5xx {show(data, "errors_5xx_total", counted)} · 오류율 {show(data, "error_rate", percent)}'
                 f' (요청 {requests})' + change(comparisons, 'error_rate', percent, label))
    window = data.get('latency_p95_5m_max')
    peak = show(data, 'latency_p95_5m_max', duration)
    at = [part[3:] for part in window.detail.split('; ') if part.startswith('at=')] if window else []
    if at and window.value is not None:
        peak += f' ({at[0]})'
    lines.append(f'- 응답 p95 {show(data, "latency_p95", duration)}' + change(comparisons, 'latency_p95', duration, label)
                 + f' · 5분 구간 최대 {peak}')
    slow = top(data, 'latency_p95_by_api')
    if slow:
        lines.append('  느린 API: ' + ' · '.join(
            f'`{dim}` {duration(o.value)} ({o.detail.replace("requests=", "")}건)' for dim, o in slow))
    lines.append(f'- 미디어: 업로드 거절 {show(data, "upload_files_rejected", count)}'
                 f' (수락 {show(data, "upload_files_accepted", count)})'
                 f' · 썸네일 제출 거절 {show(data, "media_submissions_rejected", count)}'
                 f' · 썸네일 실패 {show(data, "thumbnail_failed", count)}'
                 f' · 재시도 {show(data, "thumbnail_retried", count)}'
                 f' · 영구 실패 {show(data, "media_permanent_failures", count)}'
                 f' · 다운로드 실패 {show(data, "download_failures", count)}'
                 f' · 다운로드 성공 {show(data, "download_successes", count)}')
    disk = show(data, 'disk_available_min_ratio', lambda v: percent(v, 0))
    if data.get('disk_available_min_bytes') and data.get('disk_available_min_bytes').value is not None:
        disk += f' ({size(data.get("disk_available_min_bytes").value)})'
    lines.append(f'- 서버: CPU 최고 {show(data, "cpu_peak_ratio", lambda v: percent(v, 0))} (5분 평균 기준)'
                 f' · 메모리 최소 여유 {show(data, "memory_available_min_ratio", lambda v: percent(v, 0))}'
                 f' · 디스크 최소 여유 {disk}')
    return lines


def _checks(data: Data, period: Period) -> list:
    lines = _intervals(data, period)
    errors = top(data, 'errors_5xx_by_api')
    if errors:
        lines.append('- 5xx 발생 API: ' + ' · '.join(
            f'`{dim}` {count(o.value)}건' + (f' (요청 {show(data, "requests_by_api", counted, dim)})'
                                             if data.get('requests_by_api', dim) else '')
            for dim, o in errors if o.value))
    for metric, text in (('thumbnail_failed', '썸네일 실패'), ('media_permanent_failures', '미디어 영구 실패'),
                         ('download_failures', '다운로드 처리 실패'), ('log_entries_dropped', '로그 폐기'),
                         ('log_send_failures', '로그 전송 실패 요청')):
        obs = data.get(metric)
        if obs and obs.value:
            lines.append(f'- {text} {count(obs.value)}건')
    return lines or ['- 기준 초과 구간·처리 실패 기록 없음']


def _data_state(data: Data, comparisons: dict) -> list:
    coverage = [(job, data.get('scrape_coverage', job)) for job in ('sssok-backend', 'node', 'alloy')]
    parts = [f'{job} {percent(o.value, 1)}' if o and o.value is not None else f'{job} 없음' for job, o in coverage]
    failed = data.get('scrape_failed_samples')
    lines = ['- 수집 성공 ' + ' · '.join(parts)
             + (f' · up=0 {count(failed.value)}회' if failed and failed.value else '')]
    low = [job for job, o in coverage if not o or o.value is None or o.value < 0.99]
    if low:
        lines.append('- 수집 누락·실패 구간이 있어 해당 구간은 정상으로 계산하지 않았습니다(서비스 중단 확정 아님)')
    problems = sorted({f'{metric}: {STATE.get(o.status, o.status)}' for (metric, dim), o in data.observations.items()
                       if not dim and not o.derived and o.status not in ('ok', 'no_requests')})
    if problems:
        lines.append('- ' + ' · '.join(problems[:6]) + (f' 외 {len(problems) - 6}건' if len(problems) > 6 else ''))
    if any(row['status'] in ('no_baseline', 'partial_data') for row in comparisons.values()):
        lines.append('- 일부 지표는 비교 데이터가 부족해 현재 값만 표시했습니다')
    if any(row['status'] == 'incomparable' for row in comparisons.values()):
        lines.append('- 버킷 경계가 달라 비교할 수 없는 지표가 있습니다')
    return lines if len(lines) > 1 else lines + ['- 조회 실패·누락 없음']


def _fit(lines: list) -> str:
    text = '\n'.join(lines)
    if len(text) <= DESCRIPTION_LIMIT:
        return text
    kept = []
    for line in lines:
        if len('\n'.join(kept + [line])) > DESCRIPTION_LIMIT - 40:
            break
        kept.append(line)
    return '\n'.join(kept + ['… 길이 제한으로 생략. 상세는 Grafana에서 확인하세요.'])


def section(period: Period, data: Data, status: str, comparisons: dict, report_links: list,
            repeated: list = ()) -> dict:
    kind = '일간' if period.kind == 'daily' else '주간'
    label = '전일' if period.kind == 'daily' else '전주'
    end = f'{period.end:%m-%d %H:%M}'
    lines = [f'**대상** 운영(`{ENVIRONMENT}`) · {kind} · {period.start:%m-%d %H:%M} ~ {end} KST (끝 시각 미포함)',
             '', '**주요 수치**', *_numbers(data, comparisons, label)]
    if period.kind == 'weekly':
        days = data.dims('daily_requests')
        if days:
            lines.append('  일별 요청: ' + ' · '.join(f'{dim} {count(o.value)}' for dim, o in sorted(days.items())))
    lines += ['', '**확인할 항목**', *_checks(data, period)]
    if repeated:
        lines.append('- 2일 이상 반복: ' + ', '.join(f'`{name}` {days}일' for name, days in repeated))
    lines += ['', '**데이터 상태**' + (' · 일부 지표 누락' if status == 'partial' else ''),
              *_data_state(data, comparisons), '', '**근거 링크** ' + ' · '.join(
                  f'[{name}]({url})' for name, url in report_links)]
    return {'title': f'[운영] {kind} 보고 · {period.label()}', 'description': _fit(lines),
            'color': 0x2F80ED if period.kind == 'daily' else 0x6C5CE7}


def payload(embeds: list) -> dict:
    return {'username': 'sssOK 운영 보고', 'embeds': embeds, 'allowed_mentions': {'parse': []}}


def failure(period: Period, errors: list, report_links: list, weekly_skipped: bool = False) -> dict:
    shown = sorted(set(errors))[:5]
    lines = [f'**대상** 운영(`{ENVIRONMENT}`) · 일간 · {period.label()}', '',
             '모든 지표 조회가 실패해 정상 보고서를 만들지 못했습니다. 수치를 추정해 채우지 않습니다.',
             *(['이번 실행의 주간 보고도 만들지 않았습니다.'] if weekly_skipped else []),
             '중앙 Prometheus 상태와 `journalctl -u sssok-ops-report.service`를 확인하세요.', '',
             '**조회 오류**', *[f'- {error}' for error in shown], '',
             '**근거 링크** ' + ' · '.join(f'[{name}]({url})' for name, url in report_links)]
    return {'title': f'[운영] 보고서 생성 실패 · {period.label()}', 'description': _fit(lines), 'color': 0xE74C3C}


def preview(body: dict) -> str:
    return '\n\n'.join(f"## {embed['title']}\n{embed['description']}" for embed in body['embeds'])
