"""SQLite 저장소. 웹훅 주소는 저장하지 않는다."""
from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3

from . import ENVIRONMENT, SCHEMA_VERSION
from .aggregate import Data, Interval, Obs
from .periods import KST, Period

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS reports (
    id INTEGER PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('daily', 'weekly')),
    environment TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('complete', 'partial', 'failed')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (environment, kind, period_start)
);
CREATE TABLE IF NOT EXISTS observations (
    report_id INTEGER NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    metric TEXT NOT NULL,
    dimension TEXT NOT NULL DEFAULT '',
    value REAL,
    unit TEXT NOT NULL,
    method TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ok', 'partial', 'no_data', 'no_requests', 'error', 'incomparable')),
    detail TEXT NOT NULL DEFAULT '',
    derived INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (report_id, metric, dimension)
);
CREATE TABLE IF NOT EXISTS comparisons (
    report_id INTEGER NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    metric TEXT NOT NULL,
    previous_report_id INTEGER,
    current_value REAL,
    previous_value REAL,
    delta REAL,
    ratio REAL,
    status TEXT NOT NULL CHECK (status IN ('ok', 'new', 'both_zero', 'no_baseline', 'partial_data',
                                           'unavailable', 'incomparable')),
    PRIMARY KEY (report_id, metric)
);
CREATE TABLE IF NOT EXISTS distributions (
    report_id INTEGER NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    metric TEXT NOT NULL,
    dimension TEXT NOT NULL DEFAULT '',
    le TEXT NOT NULL,
    value REAL NOT NULL,
    PRIMARY KEY (report_id, metric, dimension, le)
);
CREATE TABLE IF NOT EXISTS threshold_intervals (
    id INTEGER PRIMARY KEY,
    report_id INTEGER NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    alertname TEXT NOT NULL,
    severity TEXT NOT NULL,
    labels TEXT NOT NULL,
    start_at TEXT NOT NULL,
    end_at TEXT NOT NULL,
    observed_seconds INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS links (
    report_id INTEGER NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    PRIMARY KEY (report_id, name)
);
CREATE TABLE IF NOT EXISTS runs (
    id INTEGER PRIMARY KEY,
    trigger TEXT NOT NULL,
    target_date TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL CHECK (status IN ('running', 'succeeded', 'partial', 'failed')),
    error TEXT
);
CREATE TABLE IF NOT EXISTS deliveries (
    id INTEGER PRIMARY KEY,
    message_key TEXT NOT NULL UNIQUE,
    environment TEXT NOT NULL,
    target_date TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('report', 'failure')),
    report_ids TEXT NOT NULL,
    run_id INTEGER REFERENCES runs(id),
    status TEXT NOT NULL CHECK (status IN ('sending', 'sent', 'failed', 'unknown')),
    attempts INTEGER NOT NULL DEFAULT 0,
    message_id TEXT,
    channel_id TEXT,
    payload TEXT NOT NULL,
    last_error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS delivery_attempts (
    id INTEGER PRIMARY KEY,
    delivery_id INTEGER NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    run_id INTEGER REFERENCES runs(id),
    attempted_at TEXT NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('sent', 'failed', 'retry', 'unknown')),
    http_status INTEGER,
    error TEXT
);
"""


def now_text() -> str:
    return datetime.now(timezone.utc).astimezone(KST).isoformat(timespec='seconds')


def _iso(timestamp: float) -> str:
    return datetime.fromtimestamp(timestamp, KST).isoformat(timespec='seconds')


class Store:
    def __init__(self, path: str | Path):
        if str(path) != ':memory:':
            Path(path).parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.db = sqlite3.connect(str(path), timeout=30)
        self.db.row_factory = sqlite3.Row
        self.db.execute('PRAGMA foreign_keys = ON')
        if str(path) != ':memory:':
            self.db.execute('PRAGMA journal_mode = WAL')
        with self.db:
            self.db.executescript(SCHEMA)
            self.db.execute('INSERT OR IGNORE INTO meta VALUES (?, ?)', ('schema_version', str(SCHEMA_VERSION)))
        stored = int(self.db.execute("SELECT value FROM meta WHERE key = 'schema_version'").fetchone()[0])
        if stored > SCHEMA_VERSION:
            raise RuntimeError(f'DB 스키마 {stored}가 코드({SCHEMA_VERSION})보다 새롭습니다')

    def close(self):
        self.db.close()

    def report_row(self, period: Period):
        return self.db.execute('SELECT * FROM reports WHERE environment = ? AND kind = ? AND period_start = ?',
                               (ENVIRONMENT, period.kind, period.start.isoformat())).fetchone()

    def save(self, period: Period, data: Data, status: str, comparisons=(), previous_id=None, links=()) -> int:
        stamp = now_text()
        with self.db:
            self.db.execute(
                'INSERT INTO reports (schema_version, kind, environment, period_start, period_end, status,'
                ' created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
                ' ON CONFLICT (environment, kind, period_start) DO UPDATE SET'
                ' schema_version = excluded.schema_version, period_end = excluded.period_end,'
                ' status = excluded.status, updated_at = excluded.updated_at',
                (SCHEMA_VERSION, period.kind, ENVIRONMENT, period.start.isoformat(), period.end.isoformat(),
                 status, stamp, stamp))
            report_id = self.report_row(period)['id']
            for table in ('observations', 'comparisons', 'distributions', 'threshold_intervals', 'links'):
                self.db.execute(f'DELETE FROM {table} WHERE report_id = ?', (report_id,))
            self.db.executemany(
                'INSERT INTO observations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
                [(report_id, metric, dim, o.value, o.unit, o.method, o.status, o.detail, int(o.derived))
                 for (metric, dim), o in data.observations.items()])
            self.db.executemany(
                'INSERT INTO distributions VALUES (?, ?, ?, ?, ?)',
                [(report_id, metric, dim, le, value)
                 for (metric, dim), buckets in data.distributions.items() for le, value in buckets.items()])
            self.db.executemany(
                'INSERT INTO threshold_intervals (report_id, alertname, severity, labels, start_at, end_at,'
                ' observed_seconds) VALUES (?, ?, ?, ?, ?, ?, ?)',
                [(report_id, i.alertname, i.severity, i.labels, _iso(i.start), _iso(i.end), i.observed_seconds)
                 for i in data.intervals])
            self.db.executemany(
                'INSERT INTO comparisons VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                [(report_id, c.metric, previous_id, c.current, c.previous, c.delta, c.ratio, c.status)
                 for c in comparisons])
            self.db.executemany('INSERT INTO links VALUES (?, ?, ?)',
                                [(report_id, name, url) for name, url in links])
        return report_id

    def load(self, period: Period) -> tuple[Data, str, int] | None:
        row = self.report_row(period)
        if row is None:
            return None
        data = Data()
        for o in self.db.execute('SELECT * FROM observations WHERE report_id = ?', (row['id'],)):
            data.observations[(o['metric'], o['dimension'])] = Obs(
                o['value'], o['unit'], o['method'], o['status'], o['detail'], bool(o['derived']))
        for d in self.db.execute('SELECT * FROM distributions WHERE report_id = ?', (row['id'],)):
            data.distributions.setdefault((d['metric'], d['dimension']), {})[d['le']] = d['value']
        for i in self.db.execute('SELECT * FROM threshold_intervals WHERE report_id = ? ORDER BY start_at',
                                 (row['id'],)):
            data.intervals.append(Interval(i['alertname'], i['severity'], i['labels'],
                                           datetime.fromisoformat(i['start_at']).timestamp(),
                                           datetime.fromisoformat(i['end_at']).timestamp(),
                                           i['observed_seconds']))
        return data, row['status'], row['id']

    def comparisons(self, report_id: int) -> dict:
        return {r['metric']: r for r in self.db.execute('SELECT * FROM comparisons WHERE report_id = ?',
                                                         (report_id,))}

    def links(self, report_id: int) -> list:
        return [(r['name'], r['url']) for r in
                self.db.execute('SELECT name, url FROM links WHERE report_id = ? ORDER BY rowid', (report_id,))]

    def start_run(self, trigger: str, target_date: str | None) -> int:
        with self.db:
            return self.db.execute('INSERT INTO runs (trigger, target_date, started_at, status)'
                                   " VALUES (?, ?, ?, 'running')", (trigger, target_date, now_text())).lastrowid

    def finish_run(self, run_id: int, status: str, error: str | None = None):
        with self.db:
            self.db.execute('UPDATE runs SET status = ?, finished_at = ?, error = ? WHERE id = ?',
                            (status, now_text(), error, run_id))

    def delivery(self, key: str):
        return self.db.execute('SELECT * FROM deliveries WHERE message_key = ?', (key,)).fetchone()

    def begin_delivery(self, key, target_date, kind, report_ids, run_id, payload) -> int:
        stamp = now_text()
        with self.db:
            self.db.execute(
                'INSERT INTO deliveries (message_key, environment, target_date, kind, report_ids, run_id, status,'
                " attempts, payload, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'sending', 0, ?, ?, ?)"
                " ON CONFLICT (message_key) DO UPDATE SET status = 'sending', run_id = excluded.run_id,"
                ' report_ids = excluded.report_ids, payload = excluded.payload, updated_at = excluded.updated_at',
                (key, ENVIRONMENT, target_date, kind, json.dumps(report_ids), run_id,
                 json.dumps(payload, ensure_ascii=False), stamp, stamp))
            return self.delivery(key)['id']

    def record_attempt(self, delivery_id, run_id, outcome, http_status=None, error=None):
        with self.db:
            self.db.execute('INSERT INTO delivery_attempts (delivery_id, run_id, attempted_at, outcome,'
                            ' http_status, error) VALUES (?, ?, ?, ?, ?, ?)',
                            (delivery_id, run_id, now_text(), outcome, http_status, error))
            self.db.execute('UPDATE deliveries SET attempts = attempts + 1 WHERE id = ?', (delivery_id,))

    def finish_delivery(self, delivery_id, status, message_id=None, channel_id=None, error=None):
        with self.db:
            self.db.execute('UPDATE deliveries SET status = ?, message_id = ?, channel_id = ?, last_error = ?,'
                            ' updated_at = ? WHERE id = ?',
                            (status, message_id, channel_id, error, now_text(), delivery_id))

    def recent(self, limit: int):
        runs = self.db.execute('SELECT * FROM runs ORDER BY id DESC LIMIT ?', (limit,)).fetchall()
        deliveries = self.db.execute('SELECT id, message_key, kind, status, attempts, message_id, last_error,'
                                     ' updated_at FROM deliveries ORDER BY id DESC LIMIT ?', (limit,)).fetchall()
        return runs, deliveries
