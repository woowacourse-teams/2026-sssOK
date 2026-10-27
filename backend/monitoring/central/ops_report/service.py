"""보고 대상 결정 → 조회·집계·저장 → 메시지 작성 → 발송."""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
import time

from . import ENVIRONMENT, message
from .aggregate import Obs, combine, compare, derive, status
from .collect import collect
from .periods import Period, daily, targets, weekly

# 중앙 Prometheus 보존 기간. 이보다 오래된 기간은 조회하지 않는다.
RETENTION = timedelta(days=15)
RECOVER_DAYS = 8
MAX_ATTEMPTS = 3


class Reporter:
    def __init__(self, store, prom, now: datetime | None = None, log=print):
        self.store, self.prom = store, prom
        self.now = now or datetime.now(timezone.utc)
        self.log = log

    def collectable(self, period: Period) -> bool:
        return period.end <= self.now and period.start >= self.now - RETENTION

    def daily(self, day: date, refresh: bool = False):
        """완료된 집계는 재사용하고, 부분·실패 집계는 보존 기간 안에서만 다시 조회한다."""
        period = daily(day)
        stored = self.store.load(period)
        if stored and stored[1] == 'complete' and not refresh:
            return stored
        if not self.collectable(period):
            if stored is None:
                self.log(f'{day}: 완료되지 않았거나 Prometheus 보존 기간 밖이라 조회하지 않음')
            return stored
        data = collect(self.prom, period)
        state = status(data)
        previous = self.store.load(period.previous())
        comparisons = compare(data, previous[0] if previous else None)
        report_id = self.store.save(period, data, state, comparisons, previous[2] if previous else None,
                                    message.links(period))
        self.log(f'{day}: 일간 집계 {state} (report={report_id})')
        return data, state, report_id

    def recover(self, last: date, days: int) -> list:
        """없는 일간 집계만 채운다. 과거 보고는 발송하지 않는다."""
        filled = []
        for offset in range(days - 1, -1, -1):
            day = last - timedelta(days=offset)
            if self.store.report_row(daily(day)) is None and self.collectable(daily(day)):
                self.daily(day)
                filled.append(day)
        return filled

    def _week(self, monday: date):
        period = weekly(monday)
        loaded = [self.daily(day.start.date()) for day in period.days()]
        days = [item[0] if item and item[1] != 'failed' else None for item in loaded]
        week, incompatible = combine(days)
        derive(week, period.seconds, incompatible)
        for day, item in zip(period.days(), days):
            total = item.get('requests_total') if item else None
            if total and total.value is not None:
                week.observations[('daily_requests', f'{day.start:%m-%d}')] = Obs(
                    total.value, 'count', '일별 요청 합계', 'ok', derived=True)
        seen = {}
        for item in days:
            for name in {interval.alertname for interval in (item.intervals if item else [])}:
                seen[name] = seen.get(name, 0) + 1
        repeated = sorted(((name, n) for name, n in seen.items() if n >= 2), key=lambda pair: -pair[1])
        return period, week, status(week), repeated

    def weekly(self, monday: date):
        period, week, state, repeated = self._week(monday)
        _, before, before_state, _ = self._week(monday - timedelta(days=7))
        comparisons = compare(week, before if before_state != 'failed' else None)
        if before_state != 'failed' and self.store.report_row(period.previous()) is None:
            self.store.save(period.previous(), before, before_state, (), None, message.links(period.previous()))
        previous = self.store.report_row(period.previous())
        report_id = self.store.save(period, week, state, comparisons, previous['id'] if previous else None,
                                    message.links(period))
        self.log(f'{monday}: 주간 집계 {state} (report={report_id})')
        return week, state, report_id, repeated

    def build(self, report_day: date, refresh: bool = False):
        periods = targets(report_day)
        self.recover(report_day - timedelta(days=1), RECOVER_DAYS)
        result = self.daily(report_day, refresh)
        if result is None:
            raise RuntimeError(f'{report_day}: 집계할 수 없는 기간입니다(미완료 또는 보존 기간 밖)')
        data, state, report_id = result
        report_ids = [report_id]
        day = periods[0]
        if state == 'failed':
            errors = [o.detail for (metric, dim), o in data.observations.items() if o.status == 'error']
            notice = message.failure(day, errors, message.links(day), weekly_skipped=len(periods) > 1)
            return 'failure', report_ids, message.payload([notice]), state
        embeds = [message.section(day, data, state, self.store.comparisons(report_id), self.store.links(report_id))]
        overall = state
        if len(periods) > 1:
            week, week_state, week_id, repeated = self.weekly(periods[1].start.date())
            report_ids.append(week_id)
            embeds.append(message.section(periods[1], week, week_state, self.store.comparisons(week_id),
                                          self.store.links(week_id), repeated))
            if week_state != 'complete':
                overall = 'partial'
        return 'report', report_ids, message.payload(embeds), overall


def message_key(report_day: date, kind: str) -> str:
    return f'{ENVIRONMENT}:{report_day.isoformat()}:{kind}'


def deliver(store, webhook, key: str, report_day: date, kind: str, report_ids: list, run_id: int,
            payload: dict, force: bool = False, log=print, sleep=time.sleep) -> str:
    """발송 완료·결과 불확실 기록이 있으면 force 없이 다시 보내지 않는다."""
    existing = store.delivery(key)
    if existing and not force:
        if existing['status'] == 'sent':
            log(f'이미 발송됨: {key} (message={existing["message_id"]})')
            return 'sent'
        if existing['status'] in ('unknown', 'sending'):
            log(f'발송 결과 불확실: {key}. 채널을 확인한 뒤 필요하면 --force로 재발송하세요.')
            return 'unknown'
    # 요청 전에 sending을 남겨 두어야 중간 종료를 결과 불확실로 판단할 수 있다.
    delivery_id = store.begin_delivery(key, report_day.isoformat(), kind, report_ids, run_id, payload)
    result = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        result = webhook.send(payload)
        store.record_attempt(delivery_id, run_id, result.outcome, result.http_status, result.error)
        retryable = result.outcome == 'retry' or (result.outcome == 'failed' and result.http_status is None)
        if not retryable or attempt == MAX_ATTEMPTS:
            break
        wait = result.retry_after or 2 * attempt
        log(f'발송 재시도 {attempt}/{MAX_ATTEMPTS - 1}: {result.error} · {wait:.0f}초 후')
        sleep(wait)
    final = 'failed' if result.outcome == 'retry' else result.outcome
    store.finish_delivery(delivery_id, final, result.message_id, result.channel_id, result.error)
    log(f'발송 {final}: {key}' + (f' (message={result.message_id})' if result.message_id else '')
        + (f' · {result.error}' if result.error and final != 'sent' else ''))
    return final
