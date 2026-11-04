"""Local SQLite execution receipts; Spring owns the durable business history."""
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import fcntl
from pathlib import Path
import sqlite3
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from .rejection import RetryableRejection


class _ResultModel(BaseModel):
    model_config = ConfigDict(strict=True, extra='forbid')


class _Group(_ResultModel):
    groupId: str = Field(min_length=1)
    mediaIds: list[Annotated[int, Field(gt=0)]]
    faceMembershipCount: int = Field(ge=0)
    bodyAdditionCount: int = Field(ge=0)


class _PhotoError(_ResultModel):
    mediaId: Annotated[int, Field(gt=0)] | None
    code: str = Field(min_length=1)


class _CompletedResponse(_ResultModel):
    groups: list[_Group]
    counts: dict[str, Annotated[int, Field(ge=0)]]
    pipelineVersion: str = Field(min_length=1)
    unassignedMediaIds: list[Annotated[int, Field(gt=0)]]
    errors: list[_PhotoError]


class _SimilarGroup(_ResultModel):
    groupId: str = Field(min_length=1)
    mediaIds: list[Annotated[int, Field(gt=0)]] = Field(min_length=3)


class _SimilarResponse(_ResultModel):
    resultVersion: Literal[1]
    groups: list[_SimilarGroup]
    smallGroupMediaIds: list[list[Annotated[int, Field(gt=0)]]]
    unassignedMediaIds: list[Annotated[int, Field(gt=0)]]
    counts: dict[str, Annotated[int, Field(ge=0)]]
    pipelineVersion: str = Field(min_length=1)
    errors: list[_PhotoError]


def now():
    return datetime.now(timezone.utc).isoformat()


def canonical_id(value):
    if str(UUID(value)) != value:
        raise ValueError('Canonical execution UUID required')
    return value


def validated_result(value, photos, kind='PERSON'):
    result = (_SimilarResponse if kind == 'SIMILAR' else _CompletedResponse).model_validate(value)
    allowed = {p['mediaId'] for p in json.loads(photos)}
    referenced = set(result.unassignedMediaIds)
    if kind == 'SIMILAR':
        if len({g.groupId for g in result.groups}) != len(result.groups):
            raise ValueError('Duplicate group IDs')
        if any(len(g) != 2 for g in result.smallGroupMediaIds):
            raise ValueError('Small groups must contain two photos')
        buckets = [g.mediaIds for g in result.groups] + result.smallGroupMediaIds + [result.unassignedMediaIds]
        ids = [i for bucket in buckets for i in bucket]
        failed = [e.mediaId for e in result.errors]
        if None in failed or len(ids + failed) != len(set(ids + failed)) or set(ids + failed) != allowed:
            raise ValueError('Similar result must partition the complete input snapshot')
        referenced.update(i for g in result.smallGroupMediaIds for i in g)
    for group in result.groups:
        referenced.update(group.mediaIds)
    referenced.update(e.mediaId for e in result.errors if e.mediaId is not None)
    if not referenced <= allowed:
        raise ValueError('Response contains photos outside the execution snapshot')
    return result.model_dump()


class Store:
    DATABASE = 'executions-v1.sqlite3'
    COLUMNS = ('id', 'room_id', 'request_id', 'business_job_id', 'attempt', 'fingerprint',
               'photos', 'state', 'result', 'error_code', 'created_at', 'updated_at', 'acknowledged_at')

    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.database = self.directory / self.DATABASE
        with self._connection(write=True) as db:
            version = db.execute('PRAGMA user_version').fetchone()[0]
            if version not in (0, 1, 2):
                raise RuntimeError('Unsupported execution database version')
            db.execute('''CREATE TABLE IF NOT EXISTS executions (
                id TEXT PRIMARY KEY, room_id INTEGER NOT NULL CHECK(room_id > 0),
                request_id TEXT NOT NULL CHECK(request_id = id), business_job_id TEXT NOT NULL,
                attempt INTEGER NOT NULL CHECK(attempt > 0), fingerprint TEXT NOT NULL,
                photos TEXT NOT NULL, state TEXT NOT NULL
                    CHECK(state IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
                result TEXT, error_code TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
                acknowledged_at TEXT, legacy INTEGER NOT NULL DEFAULT 0,
                CHECK(state != 'COMPLETED' OR (result IS NOT NULL AND error_code IS NULL)),
                CHECK(state != 'FAILED' OR error_code IS NOT NULL),
                CHECK(acknowledged_at IS NULL OR state IN ('COMPLETED', 'FAILED'))
            )''')
            db.execute('''CREATE UNIQUE INDEX IF NOT EXISTS active_business_job
                ON executions(business_job_id) WHERE state IN ('QUEUED', 'RUNNING')''')
            db.execute('CREATE INDEX IF NOT EXISTS execution_state ON executions(state, created_at, id)')
            db.execute('CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
            if 'kind' not in {r[1] for r in db.execute('PRAGMA table_info(executions)')}:
                # Migration must not modify the database of a running v1 server.
                with (self.directory / 'server.lock').open('a') as lock:
                    try:
                        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    except BlockingIOError as exc:
                        raise RuntimeError('Stop the previous server before schema migration') from exc
                    db.execute("ALTER TABLE executions ADD COLUMN kind TEXT NOT NULL DEFAULT 'PERSON' CHECK(kind IN ('PERSON','SIMILAR'))")
            db.execute('PRAGMA user_version=2')

    @contextmanager
    def _connection(self, write=False):
        # Connections are operation-scoped, never shared between threads/processes.
        db = sqlite3.connect(self.database, timeout=5, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            # Default rollback journal, plus directory sync after journal removal.
            db.execute('PRAGMA synchronous=EXTRA')
            if write:
                db.execute('BEGIN IMMEDIATE')
            yield db
            if write:
                db.commit()
        except BaseException:
            if db.in_transaction:
                db.rollback()
            raise
        finally:
            db.close()

    def rows(self):
        with self._connection() as db:
            return [dict(r) for r in db.execute('SELECT * FROM executions ORDER BY id')]

    def create(self, room_id, request_id, fingerprint, photos, max_queue,
               business_job_id=None, attempt=1, max_receipts=1000, kind='PERSON'):
        if kind not in ('PERSON', 'SIMILAR'):
            raise ValueError('Unknown analysis kind')
        canonical_id(request_id)
        business_job_id = business_job_id or request_id
        canonical_id(business_job_id)
        with self._connection(write=True) as db:
            if (not db.execute("SELECT 1 FROM metadata WHERE key='json_imported'").fetchone()
                    and any((self.directory / 'receipts').glob('*.json'))):
                raise RuntimeError('Recover legacy JSON receipts before accepting work')
            old = db.execute('SELECT * FROM executions WHERE id=?', (request_id,)).fetchone()
            if old:
                if (old['room_id'], old['fingerprint'], old['business_job_id'], old['attempt'], old['kind']) != (
                        room_id, fingerprint, business_job_id, attempt, kind):
                    raise ValueError('Execution ID is already bound to another job or snapshot')
                return dict(old), True
            if db.execute('SELECT COUNT(*) FROM executions').fetchone()[0] >= max_receipts:
                raise RetryableRejection('BACKLOG_FULL', 'Delivery backlog is full; retry after Spring acknowledges results')
            active = db.execute("SELECT COUNT(*) FROM executions WHERE state IN ('QUEUED', 'RUNNING')").fetchone()[0]
            if active >= max_queue:
                raise RetryableRejection('QUEUE_FULL', 'Analysis queue is full; retry later')
            if db.execute("SELECT 1 FROM executions WHERE business_job_id=? AND state IN ('QUEUED', 'RUNNING')",
                          (business_job_id,)).fetchone():
                raise ValueError('This job already has an active execution')
            timestamp = now()
            row = dict(kind=kind, id=request_id, room_id=room_id, request_id=request_id,
                       business_job_id=business_job_id, attempt=attempt, fingerprint=fingerprint,
                       photos=json.dumps(photos, allow_nan=False), state='QUEUED', result=None,
                       error_code=None, created_at=timestamp, updated_at=timestamp, acknowledged_at=None)
            self._insert(db, row)
            return row, False

    def _insert(self, db, row, legacy=0):
        columns = ','.join(self.COLUMNS)
        placeholders = ','.join('?' for _ in self.COLUMNS)
        db.execute(f'INSERT INTO executions ({columns},legacy,kind) VALUES ({placeholders},?,?)',
                   (*[row[k] for k in self.COLUMNS], legacy, row.get('kind', 'PERSON')))

    def get(self, room_id, job_id):
        canonical_id(job_id)
        with self._connection() as db:
            row = db.execute('SELECT * FROM executions WHERE id=? AND room_id=?', (job_id, room_id)).fetchone()
            if not row:
                raise ValueError('Execution not found in this room; Spring may resubmit from its job record')
            return dict(row)

    def claim(self):
        with self._connection(write=True) as db:
            if db.execute("SELECT 1 FROM executions WHERE state='RUNNING'").fetchone():
                return None
            row = db.execute("SELECT * FROM executions WHERE state='QUEUED' ORDER BY created_at,id LIMIT 1").fetchone()
            if row:
                timestamp = now()
                db.execute("UPDATE executions SET state='RUNNING',updated_at=? WHERE id=?", (timestamp, row['id']))
                return {**dict(row), 'state': 'RUNNING', 'updated_at': timestamp}

    def finish(self, job_id, state, result=None, error=None):
        canonical_id(job_id)
        if state not in ('COMPLETED', 'FAILED'):
            raise ValueError('Only terminal states can finish an execution')
        with self._connection(write=True) as db:
            row = db.execute('SELECT * FROM executions WHERE id=?', (job_id,)).fetchone()
            if not row:
                raise ValueError('Execution not found')
            if row['state'] != 'RUNNING':
                return False
            encoded = json.dumps(result, allow_nan=False) if result is not None else None
            db.execute('''UPDATE executions SET state=?,result=?,error_code=?,updated_at=?,photos='[]'
                WHERE id=? AND state='RUNNING' ''', (state, encoded, error, now(), job_id))
            return True

    def complete(self, room_id, job_id, result):
        # The worker commits the final result, state and pending delivery together.
        row = self.get(room_id, job_id)
        if row['state'] != 'RUNNING':
            return False
        result = validated_result(result, row['photos'], row['kind'])
        return self.finish(job_id, 'COMPLETED', result=result)

    def _import_json(self, db):
        if db.execute("SELECT 1 FROM metadata WHERE key='json_imported'").fetchone():
            return
        # The server holds server.lock. Import all records or none; keep originals.
        for path in sorted((self.directory / 'receipts').glob('*.json')):
            row = json.loads(path.read_text())
            if canonical_id(row['id']) != path.stem:
                raise ValueError('Legacy receipt filename does not match execution')
            canonical_id(row['business_job_id'])
            json.loads(row['photos'])
            if row['result'] is not None:
                _CompletedResponse.model_validate(json.loads(row['result']))
            for key in ('created_at', 'updated_at', 'acknowledged_at'):
                if row[key] is not None:
                    datetime.fromisoformat(row[key])
            self._insert(db, row, legacy=1)
        db.execute("INSERT INTO metadata VALUES ('json_imported','1')")

    def recover(self):
        with self._connection(write=True) as db:
            self._import_json(db)
            rows = db.execute("SELECT * FROM executions WHERE state='RUNNING'").fetchall()
            for row in rows:
                result = None
                if row['legacy']:
                    try:
                        path = self.directory / 'runs' / row['id'] / 'response.json'
                        result = validated_result(json.loads(path.read_text()), row['photos'])
                    except (FileNotFoundError, ValueError):
                        pass
                state, error = ('COMPLETED', None) if result is not None else ('FAILED', 'SERVER_RESTARTED')
                db.execute('''UPDATE executions SET state=?,result=?,error_code=?,updated_at=?,photos='[]'
                    WHERE id=? AND state='RUNNING' ''',
                    (state, json.dumps(result, allow_nan=False) if result is not None else None,
                     error, now(), row['id']))

    def terminal_ids(self):
        with self._connection() as db:
            return [r[0] for r in db.execute("SELECT id FROM executions WHERE state IN ('COMPLETED','FAILED')")]

    def acknowledge(self, execution_id, state):
        with self._connection(write=True) as db:
            db.execute('''UPDATE executions SET acknowledged_at=COALESCE(acknowledged_at,?)
                WHERE id=? AND state=? AND state IN ('COMPLETED','FAILED')''', (now(), execution_id, state))

    def prune(self, retention_seconds):
        with self._connection(write=True) as db:
            current = datetime.now(timezone.utc)
            rows = db.execute('SELECT id,acknowledged_at FROM executions WHERE acknowledged_at IS NOT NULL').fetchall()
            for row in rows:
                if (not (self.directory / 'runs' / row['id']).exists()
                        and (current - datetime.fromisoformat(row['acknowledged_at'])).total_seconds() >= retention_seconds):
                    db.execute('DELETE FROM executions WHERE id=?', (row['id'],))
