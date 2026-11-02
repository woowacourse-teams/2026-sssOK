"""Local execution receipts for retry delivery, not the service's job database.

Spring owns the durable business job. A single server process writes these files;
workers only read their input. The mounted data volume survives container restart.
"""
from datetime import datetime, timezone
import json
from pathlib import Path
from threading import RLock
from uuid import UUID


def now():
    return datetime.now(timezone.utc).isoformat()


class Store:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.receipts = self.directory / 'receipts'
        self.receipts.mkdir(exist_ok=True)
        self.lock = RLock()

    def _path(self, execution_id):
        if str(UUID(execution_id)) != execution_id:
            raise ValueError('Canonical execution UUID required')
        return self.receipts / f'{execution_id}.json'

    def _write(self, row):
        path = self._path(row['id'])
        temporary = path.with_suffix('.tmp')
        temporary.write_text(json.dumps(row, allow_nan=False))
        temporary.replace(path)

    def rows(self):
        with self.lock:
            return [json.loads(p.read_text()) for p in sorted(self.receipts.glob('*.json'))]

    def create(self, room_id, request_id, fingerprint, photos, max_queue,
               business_job_id=None, attempt=1):
        with self.lock:
            path = self._path(request_id)
            business_job_id = business_job_id or request_id
            if path.exists():
                old = json.loads(path.read_text())
                if (old['room_id'], old['fingerprint'], old['business_job_id'], old['attempt']) != (
                        room_id, fingerprint, business_job_id, attempt):
                    raise ValueError('Execution ID is already bound to another job or snapshot')
                return old, True
            rows = self.rows()
            if sum(r['state'] in ('QUEUED', 'RUNNING') for r in rows) >= max_queue:
                raise ValueError('Analysis queue is full; retry later')
            if any(r['business_job_id'] == business_job_id and r['state'] in ('QUEUED', 'RUNNING') for r in rows):
                raise ValueError('This job already has an active execution')
            timestamp = now()
            row = dict(id=request_id, room_id=room_id, request_id=request_id,
                       business_job_id=business_job_id, attempt=attempt, fingerprint=fingerprint,
                       photos=json.dumps(photos), state='QUEUED', result=None, error_code=None,
                       created_at=timestamp, updated_at=timestamp, acknowledged_at=None)
            self._write(row)
            return row, False

    def get(self, room_id, job_id):
        with self.lock:
            path = self._path(job_id)
            if not path.exists():
                raise ValueError('Execution not found; Spring may resubmit from its job record')
            row = json.loads(path.read_text())
            if row['room_id'] != room_id:
                raise ValueError('Execution not found in this room')
            return row

    def claim(self):
        with self.lock:
            queued = sorted((r for r in self.rows() if r['state'] == 'QUEUED'),
                            key=lambda r: (r['created_at'], r['id']))
            if queued:
                row = queued[0]
                row.update(state='RUNNING', updated_at=now())
                self._write(row)
                return row

    def finish(self, job_id, state, result=None, error=None):
        with self.lock:
            row = json.loads(self._path(job_id).read_text())
            if row['state'] != 'RUNNING':
                return
            row.update(state=state, result=json.dumps(result) if result is not None else None,
                       error_code=error, updated_at=now(), photos='[]')
            self._write(row)

    def recover(self):
        with self.lock:
            for row in self.rows():
                if row['state'] == 'RUNNING':
                    self.finish(row['id'], 'FAILED', error='SERVER_RESTARTED')

    def terminal_ids(self):
        return [r['id'] for r in self.rows() if r['state'] in ('COMPLETED', 'FAILED')]

    def acknowledge(self, execution_id, state):
        with self.lock:
            row = json.loads(self._path(execution_id).read_text())
            if row['state'] == state and state in ('COMPLETED', 'FAILED'):
                row['acknowledged_at'] = now()
                self._write(row)

    def prune(self, retention_seconds):
        with self.lock:
            current = datetime.now(timezone.utc)
            for row in self.rows():
                if not (self.directory / 'runs' / row['id']).exists() and row['acknowledged_at'] and (current - datetime.fromisoformat(row['acknowledged_at'])).total_seconds() >= retention_seconds:
                    self._path(row['id']).unlink()
