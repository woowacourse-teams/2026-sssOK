"""파생 지표, 일간 → 주간 재집계, 이전 기간 비교.

주간 오류율·p95는 일별 값의 평균이 아니라 합산한 원시 값(카운터·버킷 증가량)으로 다시 계산한다.
"""
from __future__ import annotations

from dataclasses import dataclass, field, replace
import math

from .queries import SCRAPE_INTERVAL, SPECS, TARGET_JOBS

USABLE = ('ok', 'partial')
# 요청이 적은 API의 p95는 몇 건에 좌우되므로 순위에서 뺀다.
MIN_API_REQUESTS = 20
COMPARED = ('requests_total', 'errors_5xx_total', 'error_rate', 'latency_p95', 'latency_p95_5m_max',
            'upload_files_rejected', 'media_submissions_rejected', 'media_permanent_failures',
            'thumbnail_failed', 'thumbnail_retried', 'download_failures',
            'cpu_peak_ratio', 'memory_available_min_ratio', 'disk_available_min_ratio',
            'log_entries_dropped', 'log_send_failures')


@dataclass(frozen=True)
class Obs:
    value: float | None
    unit: str
    method: str
    status: str  # ok | partial | no_data | no_requests | error | incomparable
    detail: str = ''
    derived: bool = False


@dataclass(frozen=True)
class Interval:
    alertname: str
    severity: str
    labels: str
    start: float
    end: float
    observed_seconds: int


@dataclass
class Data:
    observations: dict = field(default_factory=dict)
    distributions: dict = field(default_factory=dict)
    intervals: list = field(default_factory=list)

    def get(self, metric: str, dimension: str = '') -> Obs | None:
        return self.observations.get((metric, dimension))

    def dims(self, metric: str) -> dict:
        return {dim: obs for (name, dim), obs in self.observations.items() if name == metric and dim}


def _le(value: str) -> float:
    return math.inf if value == '+Inf' else float(value)


def quantile(q: float, buckets: dict) -> float | None:
    """Prometheus histogram_quantile과 같은 선형 보간."""
    ordered = sorted(((_le(le), count) for le, count in buckets.items()), key=lambda item: item[0])
    if not ordered or ordered[-1][0] != math.inf:
        return None
    total = ordered[-1][1]
    if total <= 0:
        return None
    rank = q * total
    lower_bound, lower_count = 0.0, 0.0
    for bound, count in ordered:
        if count >= rank:
            if bound == math.inf:
                return ordered[-2][0] if len(ordered) > 1 else None
            if count == lower_count:
                return bound
            return lower_bound + (bound - lower_bound) * (rank - lower_count) / (count - lower_count)
        lower_bound, lower_count = bound, count
    return None


def _ratio(numerator: Obs | None, denominator: Obs | None, unit: str, method: str) -> Obs:
    if numerator is None or denominator is None:
        return Obs(None, unit, method, 'no_data', derived=True)
    for obs in (numerator, denominator):
        if obs.status not in USABLE:
            return Obs(None, unit, method, obs.status, obs.detail, derived=True)
    if not denominator.value:
        return Obs(None, unit, method, 'no_requests', '분모 0', derived=True)
    status = 'partial' if 'partial' in (numerator.status, denominator.status) else 'ok'
    return Obs(numerator.value / denominator.value, unit, method, status,
               numerator.detail or denominator.detail, derived=True)


def _aligned_ratio(days: list, numerator: str, denominator: str, dim: str, method: str) -> Obs:
    """분자·분모가 모두 정상 조회된 날만 합산한다. 지표별 실패 일자가 달라도 기간이 어긋나지 않는다."""
    common = [day for day in days if day and all(
        day.get(name) and day.get(name).status == 'ok' for name in (numerator, denominator))]
    if not common:
        return Obs(None, 'ratio', method, 'no_data', f'공통 관측일 0/{len(days)}일', derived=True)

    def total(name):
        values = [day.get(name, dim) for day in common]
        return sum(o.value for o in values if o and o.value is not None)
    num, den = total(numerator), total(denominator)
    if not den:
        return Obs(None, 'ratio', method, 'no_requests', '분모 0', derived=True)
    state = 'ok' if len(common) == len(days) else 'partial'
    detail = '' if state == 'ok' else f'공통 관측일 {len(common)}/{len(days)}일'
    return Obs(num / den, 'ratio', method, state, detail, derived=True)


def derive(data: Data, seconds: int, incompatible: frozenset = frozenset(), days: list | None = None) -> None:
    """days(일간 Data 목록)를 주면 비율은 분자·분모를 함께 관측한 날만 묶어 계산한다."""
    obs = data.observations
    if days is None:
        obs[('error_rate', '')] = _ratio(data.get('errors_5xx_total'), data.get('requests_total'), 'ratio',
                                         '5xx 합계 / 요청 합계')
    else:
        obs[('error_rate', '')] = _aligned_ratio(days, 'errors_5xx_total', 'requests_total', '',
                                                 '공통 관측일의 5xx 합계 / 요청 합계')
    base = data.get('latency_buckets')
    method = '버킷 증가량 합산 후 histogram_quantile(0.95) 보간'
    if base is None or base.status not in USABLE:
        obs[('latency_p95', '')] = Obs(None, 'seconds', method, base.status if base else 'no_data',
                                       base.detail if base else '', derived=True)
    elif ('latency_buckets', '') in incompatible:
        obs[('latency_p95', '')] = Obs(None, 'seconds', method, 'incomparable', '버킷 경계가 달라 합산 불가', derived=True)
    else:
        buckets = data.distributions.get(('latency_buckets', ''), {})
        value = quantile(0.95, buckets)
        status = base.status if value is not None else 'no_requests'
        obs[('latency_p95', '')] = Obs(value, 'seconds', method, status, base.detail, derived=True)
    for (name, dim), buckets in data.distributions.items():
        if name != 'latency_buckets_by_api' or not dim:
            continue
        count = buckets.get('+Inf', 0)
        if (name, dim) in incompatible:
            obs[('latency_p95_by_api', dim)] = Obs(None, 'seconds', method, 'incomparable',
                                                   '버킷 경계가 달라 합산 불가', derived=True)
        elif count >= MIN_API_REQUESTS:
            obs[('latency_p95_by_api', dim)] = Obs(quantile(0.95, buckets), 'seconds', method, 'ok',
                                                   f'requests={count:.0f}', derived=True)
    requests = data.dims('requests_by_api')
    for dim, errors in data.dims('errors_5xx_by_api').items():
        if not errors.value:
            continue
        if days is None:
            obs[('error_rate_by_api', dim)] = _ratio(errors, requests.get(dim), 'ratio', 'API별 5xx / 요청')
        else:
            obs[('error_rate_by_api', dim)] = _aligned_ratio(days, 'errors_5xx_by_api', 'requests_by_api', dim,
                                                             '공통 관측일의 API별 5xx / 요청')
    expected = seconds / SCRAPE_INTERVAL
    samples = data.get('scrape_success_samples')
    for job in TARGET_JOBS:
        method = f'수집 성공 수 / 기대 수({expected:.0f})'
        if samples is None or samples.status not in USABLE:
            obs[('scrape_coverage', job)] = Obs(None, 'ratio', method, samples.status if samples else 'no_data',
                                                derived=True)
            continue
        found = obs.get(('scrape_success_samples', job))
        value = min((found.value if found else 0) / expected, 1.0)
        obs[('scrape_coverage', job)] = Obs(value, 'ratio', method, 'ok' if found else 'no_data', derived=True)


def status(data: Data) -> str:
    raw = [obs.status for (metric, dim), obs in data.observations.items() if not obs.derived and not dim]
    if not raw or all(state == 'error' for state in raw):
        return 'failed'
    return 'complete' if all(state in ('ok', 'no_requests') for state in raw) else 'partial'


def combine(days: list) -> tuple[Data, frozenset]:
    """일간 Data 목록(없는 날은 None)을 합친다. 파생 값은 derive()로 다시 계산한다."""
    total = len(days)
    week = Data()
    incompatible = set()
    names = [spec.name for spec in SPECS] + ['latency_p95_5m_max', 'alert_intervals']
    aggregate = {spec.name: spec.aggregate for spec in SPECS}
    aggregate.update(latency_p95_5m_max='max', alert_intervals='sum')
    for name in names:
        rows = [day.get(name) if day else None for day in days]
        usable = [i for i, row in enumerate(rows) if row and row.status in ('ok', 'no_requests')]
        template = next((row for row in rows if row), None)
        if template is None:
            week.observations[(name, '')] = Obs(None, '', '', 'no_data', f'0/{total}일')
            continue
        if not usable:
            states = {row.status for row in rows if row}
            state = 'error' if 'error' in states else 'no_data'
            week.observations[(name, '')] = Obs(None, template.unit, template.method, state, f'0/{total}일')
            continue
        state = 'ok' if len(usable) == total else 'partial'
        detail = '' if state == 'ok' else f'{len(usable)}/{total}일'
        keys = {dim for i in usable for (metric, dim) in days[i].observations if metric == name}
        for dim in keys:
            values = [(days[i].get(name, dim), i) for i in usable if days[i].get(name, dim)]
            numbers = [(o.value, i) for o, i in values if o.value is not None]
            if not numbers:
                empty = 'no_requests' if all(o.status == 'no_requests' for o, _ in values) else state
                week.observations[(name, dim)] = replace(values[0][0], value=None, status=empty, detail=detail)
                continue
            how = aggregate[name]
            if how == 'sum':
                value, note = sum(v for v, _ in numbers), detail
            else:
                value, index = (max if how == 'max' else min)(numbers, key=lambda item: item[0])
                at = days[index].get(name, dim).detail
                note = '; '.join(part for part in (detail, at) if part)
            week.observations[(name, dim)] = Obs(value, template.unit, template.method,
                                                 state if dim == '' else 'ok', note)
        for dim in {d for i in usable for (metric, d) in days[i].distributions if metric == name}:
            parts = [days[i].distributions[(name, dim)] for i in usable if (name, dim) in days[i].distributions]
            if len({frozenset(part) for part in parts}) > 1:
                incompatible.add((name, dim))
                continue
            week.distributions[(name, dim)] = {le: sum(part[le] for part in parts) for le in parts[0]}
        if (name, '') in incompatible:
            week.observations[(name, '')] = replace(week.observations[(name, '')], status='incomparable',
                                                    detail='일별 버킷 경계가 달라 합산 불가')
    for day in days:
        if day:
            week.intervals.extend(day.intervals)
    week.intervals = merge_intervals(week.intervals)
    return week, frozenset(incompatible)


def merge_intervals(intervals: list) -> list:
    """자정에서 나뉜 같은 대상의 연속 구간을 합친다."""
    merged = []
    for item in sorted(intervals, key=lambda i: (i.alertname, i.labels, i.start)):
        last = merged[-1] if merged else None
        if last and last.alertname == item.alertname and last.labels == item.labels and item.start <= last.end:
            merged[-1] = replace(last, end=max(last.end, item.end),
                                 observed_seconds=last.observed_seconds + item.observed_seconds)
        else:
            merged.append(item)
    return merged


@dataclass(frozen=True)
class Comparison:
    metric: str
    current: float | None
    previous: float | None
    delta: float | None
    ratio: float | None
    status: str


def compare(current: Data, previous: Data | None) -> list:
    results = []
    for metric in COMPARED:
        cur = current.get(metric)
        prev = previous.get(metric) if previous else None
        value = cur.value if cur else None
        before = prev.value if prev else None
        if cur is None or cur.status not in USABLE or value is None:
            state = 'incomparable' if cur and cur.status == 'incomparable' else 'unavailable'
        elif prev is None or prev.status not in USABLE or before is None:
            state = 'incomparable' if prev and prev.status == 'incomparable' else 'no_baseline'
        elif 'partial' in (cur.status, prev.status):
            # 관측 일수가 다른 합계끼리 비교하면 증감이 왜곡된다.
            state = 'partial_data'
        elif metric == 'latency_p95' and _bounds(current) != _bounds(previous):
            state = 'incomparable'
        elif before == 0:
            state = 'both_zero' if value == 0 else 'new'
        else:
            state = 'ok'
        delta = value - before if state == 'ok' else None
        ratio = delta / before if state == 'ok' else None
        results.append(Comparison(metric, value, before, delta, ratio, state))
    return results


def _bounds(data: Data) -> frozenset:
    return frozenset(data.distributions.get(('latency_buckets', ''), {}))
