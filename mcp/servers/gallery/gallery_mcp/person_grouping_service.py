import asyncio
from contextlib import contextmanager
import fcntl
import json
import logging
import shutil
import sqlite3
from pathlib import Path
from uuid import UUID
import sys

from .config import Settings
from .inputs import Photo, fingerprint, validate
from .execution_store import Store
from .progress import progress_path, read_progress, snapshot
from .rejection import RetryableRejection


class PersonGroupingService:
    def __init__(self, settings: Settings, command=None):
        self.settings = settings
        self.store = Store(settings.data)
        self.unavailable = False
        self.process = None
        self.command = command or (lambda job: [sys.executable,'-m','gallery_mcp.analysis_worker',str(job['room_id']),job['id']])

    @contextmanager
    def boot(self):
        with (self.settings.data / 'server.lock').open('a') as lock:
            try:
                fcntl.flock(lock,fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as exc:
                raise RuntimeError('One MCP server process is allowed per data directory') from exc
            self.store.recover()
            yield

    def start(self, room_id, request_id, photos: list[Photo], job_id=None, attempt=1, kind='PERSON'):
        # Coordinate admission with deployment across processes. Queries and the
        # worker remain available while queued work drains.
        with (self.settings.data / 'admission.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_SH)
            if self.unavailable:
                raise RetryableRejection('UNAVAILABLE', 'Analysis service unavailable; retry after restart')
            if (self.settings.data / 'draining').exists():
                raise RetryableRejection('DEPLOYING', 'Server deployment in progress; retry later')
            try:
                return self._start(room_id, request_id, photos, job_id, attempt, kind)
            except sqlite3.OperationalError as error:
                if 'locked' not in str(error):
                    raise
                raise RetryableRejection('STORAGE_BUSY', 'Analysis storage is busy; retry later') from None

    def _start(self, room_id, request_id, photos, job_id, attempt, kind):
        if job_id is not None and str(UUID(job_id)) != job_id:
            raise ValueError('Spring job ID must be a canonical UUID')
        if type(attempt) is not int or attempt < 1:
            raise ValueError('Execution attempt must be a positive integer')
        self.store.prune(self.settings.receipt_retention_seconds)
        photos = sorted(photos,key=lambda p:p.mediaId)
        validate(room_id,request_id,photos,self.settings)
        row,reused = self.store.create(room_id,request_id,fingerprint(photos),
                                       [p.model_dump() for p in photos],self.settings.max_queue,job_id,attempt,
                                       max_receipts=self.settings.max_receipts, kind=kind)
        return {**self.view(row),'reused':reused}

    def get(self, room_id, job_id):
        return self.view(self.store.get(room_id,job_id))

    def view(self, row):
        progress = {}
        if row['state'] == 'RUNNING':
            # Until the worker records its first value, the execution has just been claimed:
            # report the first real step so Spring never shows a RUNNING job without a stage.
            progress = read_progress(progress_path(self.settings.data, row['id'])) or snapshot(
                'downloading_photos', 0, len(json.loads(row['photos'])))
        return {'jobType':row['kind'], 'jobId':row['business_job_id'],'executionId':row['id'],'attempt':row['attempt'],'roomId':row['room_id'],'state':row['state'],
                'progress':progress,'result':json.loads(row['result']) if row['result'] else None,
                'errorCode':row['error_code'],'createdAt':row['created_at'],'updatedAt':row['updated_at']}

    def cleanup_finished(self):
        """Delete terminal-job artifacts only when no worker holds the analysis lock."""
        runs = self.settings.data / 'runs'
        if not runs.exists() or not any(runs.iterdir()):
            return
        with (self.settings.data / 'analysis.lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                return
            for job_id in self.store.terminal_ids():
                directory = self.settings.data / 'runs' / job_id
                try:
                    if directory.exists():
                        shutil.rmtree(directory)
                except OSError:
                    logging.getLogger(__name__).warning('Artifact cleanup failed for job %s; will retry', job_id)

    async def dispatch(self):
        while True:
            self.cleanup_finished()
            job = self.store.claim()
            if job:
                await self.execute(job)
            else:
                await asyncio.sleep(1)

    async def execute(self, job):
        directory = self.settings.data / 'logs'
        directory.mkdir(exist_ok=True)
        try:
            with (directory / f'{job["id"]}.log').open('wb') as log:
                self.process = await asyncio.create_subprocess_exec(*self.command(job),stdout=log,stderr=log)
                try:
                    await asyncio.wait_for(self.process.wait(),timeout=self.settings.timeout)
                except TimeoutError:
                    await self.stop_worker()
                    self.store.finish(job['id'],'FAILED',error='ANALYSIS_TIMEOUT')
                    return
            if self.process.returncode != 0:
                with (directory / f'{job["id"]}.log').open('ab') as failure_log:
                    failure_log.write(f'Worker exit code: {self.process.returncode}\n'.encode())
                self.store.finish(job['id'],'FAILED',error='ANALYSIS_FAILED')
                return
            # The worker has already committed result + COMPLETED in SQLite.
            # An exit without a commit must not publish a false success.
            self.store.finish(job['id'],'FAILED',error='WORKER_RESULT_NOT_COMMITTED')
        except asyncio.CancelledError:
            await self.stop_worker()
            self.store.finish(job['id'],'FAILED',error='SERVER_STOPPED')
            raise
        except Exception:
            self.store.finish(job['id'],'FAILED',error='WORKER_START_OR_RESULT_FAILED')
        finally:
            # Ensure an unexpected parent error cannot leave an untracked worker.
            await self.stop_worker()
            self.process = None
            self.cleanup_finished()

    async def stop_worker(self):
        if self.process and self.process.returncode is None:
            try:
                self.process.terminate()
            except ProcessLookupError:
                return
            try:
                await asyncio.wait_for(self.process.wait(),timeout=5)
            except TimeoutError:
                self.process.kill()
                await self.process.wait()
