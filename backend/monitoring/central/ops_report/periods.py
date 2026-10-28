"""KST 날짜 경계."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone

# 서머타임이 없어 tzdata 없이 고정 오프셋을 쓴다.
KST = timezone(timedelta(hours=9), 'KST')


@dataclass(frozen=True)
class Period:
    kind: str
    start: datetime
    end: datetime  # 미포함

    @property
    def seconds(self) -> int:
        return int((self.end - self.start).total_seconds())

    def previous(self) -> Period:
        return Period(self.kind, self.start - (self.end - self.start), self.start)

    def days(self) -> list[Period]:
        return [daily(self.start.date() + timedelta(days=i)) for i in range(self.seconds // 86400)]

    def label(self) -> str:
        last = (self.end - timedelta(days=1)).date()
        if self.kind == 'daily':
            return f'{self.start:%Y-%m-%d} ({_weekday(self.start)})'
        return f'{self.start:%Y-%m-%d} ~ {last:%Y-%m-%d}'


def _weekday(value: datetime) -> str:
    return '월화수목금토일'[value.weekday()]


def daily(day: date) -> Period:
    start = datetime.combine(day, time(0), KST)
    return Period('daily', start, start + timedelta(days=1))


def weekly(monday: date) -> Period:
    if monday.weekday() != 0:
        raise ValueError('주간 보고는 월요일부터 시작합니다')
    start = datetime.combine(monday, time(0), KST)
    return Period('weekly', start, start + timedelta(days=7))


def today(now: datetime | None = None) -> date:
    return (now or datetime.now(timezone.utc)).astimezone(KST).date()


def targets(report_day: date) -> list[Period]:
    """보고 대상 하루. 그다음 날이 월요일이면 지난주 주간 보고를 함께 만든다."""
    periods = [daily(report_day)]
    run_day = report_day + timedelta(days=1)
    if run_day.weekday() == 0:
        periods.append(weekly(run_day - timedelta(days=7)))
    return periods
