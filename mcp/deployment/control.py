"""Run inside the container; never print photo URLs, receipts or credentials."""
import fcntl
import hashlib
import json
from pathlib import Path
import sys
import sqlite3
import tempfile
import time


def has_active_work(data):
    database = data / 'executions-v1.sqlite3'
    if database.exists():
        connection = sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True, timeout=5)
        try:
            if connection.execute('PRAGMA user_version').fetchone()[0] not in (1, 2, 3):
                raise RuntimeError('Unsupported execution database version')
            if connection.execute("SELECT 1 FROM executions WHERE state IN ('QUEUED','RUNNING') LIMIT 1").fetchone():
                return True
            if connection.execute("SELECT 1 FROM metadata WHERE key='json_imported'").fetchone():
                return False
        finally:
            connection.close()
    # Also usable against the pre-SQLite release during its final drain.
    return any(json.loads(path.read_text())['state'] in ('QUEUED', 'RUNNING')
               for path in (data / 'receipts').glob('*.json'))

def control(action, settings, timeout=600):
    data = settings.data
    marker = data / 'draining'
    if action == 'verify':
        with tempfile.TemporaryFile(dir=data) as probe:
            probe.write(b'permission-check')
        manifest = json.loads(Path('/tmp/model-checksums.json').read_text())
        for name, info in manifest.items():
            with (settings.assets / name).open('rb') as stream:
                actual = hashlib.file_digest(stream, 'sha256').hexdigest()
            if actual != info['sha256']:
                raise RuntimeError(f'Model checksum mismatch: {name}')
        print('PASS: model checksums and container file access')
        return
    if action not in ('drain', 'resume'):
        raise ValueError('Unknown action')
    with (data / 'admission.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if action == 'resume':
            marker.unlink(missing_ok=True)
            return
        marker.touch()
    deadline = time.monotonic() + timeout
    while True:
        if not has_active_work(data):
            print('PASS: no queued or running analysis')
            return
        if time.monotonic() >= deadline:
            raise TimeoutError('Analysis did not drain; deployment aborted')
        time.sleep(2)


if __name__ == '__main__':
    from gallery_mcp.config import Settings
    control(sys.argv[1], Settings.environment())
