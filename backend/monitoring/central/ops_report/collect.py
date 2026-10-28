"""하루 단위 Prometheus 조회 → 원시 관측값. 실패한 지표만 error로 남기고 나머지는 계속 조회한다."""
from __future__ import annotations

from datetime import datetime
import json
import math

from . import queries
from .aggregate import Data, Interval, Obs, derive
from .periods import KST, Period
from .prometheus import Prometheus, QueryError

INTERVAL_LABELS = ('server', 'instance', 'job', 'host', 'reason', 'device', 'mountpoint')


def _dimension(metric: dict, by: tuple) -> str:
    return ' '.join(metric.get(label, '') for label in by if label != 'le')


def _short(error: Exception) -> str:
    return str(error)[:300]


def collect(prom: Prometheus, period: Period) -> Data:
    if period.kind != 'daily':
        raise ValueError('Prometheus 조회는 하루 단위로만 한다. 주간은 일간 값을 재집계한다.')
    data = Data()
    at = period.end.timestamp()
    seconds = period.seconds
    attempted, succeeded = {}, {}
    # "시계열 없음 = 0"으로 볼 수 있는지 판단하려면 수집 시도·성공 기록을 먼저 알아야 한다.
    first = ('scrape_samples', 'scrape_success_samples')
    ordered = sorted(queries.SPECS, key=lambda spec: spec.name not in first)
    for spec in ordered:
        try:
            result = prom.instant(queries.render(spec, seconds), at)
        except QueryError as error:
            data.observations[(spec.name, '')] = Obs(None, spec.unit, spec.method, 'error', _short(error))
            continue
        _store(data, spec, result, attempted, succeeded)
        if spec.name in first:
            seen = attempted if spec.name == 'scrape_samples' else succeeded
            for job, obs in data.dims(spec.name).items():
                seen[job] = bool(obs.value)
    _latency_windows(prom, period, data)
    _alert_intervals(prom, period, data)
    derive(data, seconds)
    return data


def _store(data: Data, spec, result: list, attempted: dict, succeeded: dict) -> None:
    obs = data.observations
    if spec.name == 'scrape_failed_samples':
        # 실패 기록이 없는 job은 시계열도 없다. 수집을 시도한 job만 0으로 채운다.
        for job, seen in attempted.items():
            if seen:
                obs[(spec.name, job)] = Obs(0.0, spec.unit, spec.method, 'ok', '실패 기록 없음')
    # 0으로 나눈 비율 등 NaN은 값으로 쓰지 않는다.
    samples = [(s['metric'], float(s['value'][1])) for s in result if not math.isnan(float(s['value'][1]))]
    if not samples:
        # 수집에 한 번도 성공하지 못한 대상의 카운터는 발생 없음(0)이 아니라 데이터 없음이다.
        # 수집 실패 횟수만은 시도 기록이 있으면 0으로 볼 수 있다.
        basis = attempted if spec.name == 'scrape_failed_samples' else succeeded
        zero = spec.absent == 'zero' and (spec.source == '' and any(basis.values())
                                         or basis.get(spec.source))
        if zero:
            obs[(spec.name, '')] = Obs(0.0, spec.unit, spec.method, 'ok', '시계열 없음 = 발생 없음')
        else:
            if not spec.source:
                reason = '시계열 없음'
            elif succeeded.get(spec.source):
                reason = '시계열 없음'
            elif attempted.get(spec.source):
                reason = '수집 성공 기록 없음'
            else:
                reason = '수집 기록 없음'
            obs[(spec.name, '')] = Obs(None, spec.unit, spec.method, 'no_data', reason)
        return
    for metric, value in samples:
        if spec.distribution:
            key = (spec.name, _dimension(metric, spec.by))
            data.distributions.setdefault(key, {})[metric.get('le', '')] = value
        elif spec.by:
            obs[(spec.name, _dimension(metric, spec.by))] = Obs(value, spec.unit, spec.method, 'ok')
    if spec.distribution:
        value = None
    elif spec.aggregate == 'sum':
        value = sum(v for _, v in samples)
    else:
        value = (max if spec.aggregate == 'max' else min)(v for _, v in samples)
    obs[(spec.name, '')] = Obs(value, spec.unit, spec.method, 'ok')


def _latency_windows(prom: Prometheus, period: Period, data: Data) -> None:
    method = '5분 요청 20건 이상 구간의 5분 p95 최댓값(5분 간격)'
    step = queries.LATENCY_WINDOW_STEP
    try:
        result = prom.range(queries.LATENCY_WINDOW, period.start.timestamp() + step,
                            period.end.timestamp(), step)
    except QueryError as error:
        data.observations[('latency_p95_5m_max', '')] = Obs(None, 'seconds', method, 'error', _short(error))
        return
    points = [(float(v), float(t)) for series in result for t, v in series['values'] if float(v) == float(v)]
    if not points:
        data.observations[('latency_p95_5m_max', '')] = Obs(None, 'seconds', method, 'no_requests',
                                                            '20건 이상 5분 구간 없음')
        return
    value, at = max(points)
    when = datetime.fromtimestamp(at, KST).strftime('%m-%d %H:%M')
    data.observations[('latency_p95_5m_max', '')] = Obs(value, 'seconds', method, 'ok', f'at={when}')


def _alert_intervals(prom: Prometheus, period: Period, data: Data) -> None:
    method = '기존 알림 규칙 firing 구간(1분 간격 관측)'
    step = queries.ALERTS_STEP
    start, end = period.start.timestamp(), period.end.timestamp()
    try:
        result = prom.range(queries.ALERTS, start, end - step, step)
    except QueryError as error:
        data.observations[('alert_intervals', '')] = Obs(None, 'count', method, 'error', _short(error))
        return
    for series in result:
        metric = series['metric']
        labels = json.dumps({k: metric[k] for k in INTERVAL_LABELS if k in metric}, sort_keys=True,
                            ensure_ascii=False)
        times = sorted(float(t) for t, _ in series['values'])
        begin = previous = times[0]
        count = 1
        for t in times[1:] + [None]:
            if t is not None and t - previous <= step:
                previous, count = t, count + 1
                continue
            data.intervals.append(Interval(metric.get('alertname', ''), metric.get('severity', ''), labels,
                                           begin, min(previous + step, end), count * step))
            if t is not None:
                begin = previous = t
                count = 1
    data.observations[('alert_intervals', '')] = Obs(float(len(data.intervals)), 'count', method, 'ok')
