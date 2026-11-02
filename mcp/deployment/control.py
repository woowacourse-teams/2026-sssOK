"""Run inside the container; never print photo URLs, receipts or credentials."""
import fcntl
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import time

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
        rows = [json.loads(p.read_text()) for p in (data / 'receipts').glob('*.json')]
        if not any(r['state'] in ('QUEUED', 'RUNNING') for r in rows):
            print('PASS: no queued or running analysis')
            return
        if time.monotonic() >= deadline:
            raise TimeoutError('Analysis did not drain; deployment aborted')
        time.sleep(2)


if __name__ == '__main__':
    from gallery_mcp.config import Settings
    control(sys.argv[1], Settings.environment())
