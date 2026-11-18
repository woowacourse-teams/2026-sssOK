"""Sequential analysis worker; imports survive jobs, model objects do not."""
from contextlib import contextmanager
import gc
import os
import fcntl
import json
import logging
from pathlib import Path
import sys

from .config import Settings
from .inputs import Photo, stage_photos
from .execution_store import Store
from .progress import ProgressFile, progress_path


def execute(room_id, job_id, settings):
    logging.getLogger('httpx').setLevel(logging.WARNING)
    store = Store(settings.data)
    job = store.get(room_id, job_id)
    directory = settings.data / 'runs' / job_id
    directory.mkdir(parents=True, exist_ok=False)
    photos = [Photo.model_validate(p) for p in json.loads(job['photos'])]
    # The delivery loop reads this file; recording progress never sends anything itself.
    progress = ProgressFile(progress_path(settings.data, job_id))
    # Also excludes a surviving orphan worker after a forced MCP server restart.
    with (settings.data / 'analysis.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        progress.write('downloading_photos', 0, len(photos))
        stage_photos(photos, directory / 'input', settings,
                     lambda done: progress.write('downloading_photos', done, len(photos)))
        if job['kind'] == 'SIMILAR':
            from pipeline.similar_analysis import run
        else:
            from pipeline.photo_analysis import run
        result = run(directory / 'input', directory / 'analysis', settings.assets, settings.threads, progress)
        manifest = json.loads((directory / 'analysis/manifest.json').read_text())
        mapping = {p.filename:p.mediaId for p in photos}
        media_ids = {p['image_id']:mapping[p['source_filename']] for p in manifest['photos']}
        if job['kind'] == 'SIMILAR':
            response = {
                'resultVersion': 1,
                'groups': [{'groupId': g['group_id'], 'mediaIds': [media_ids[i] for i in g['image_ids']]}
                           for g in result['groups']],
                'smallGroupMediaIds': [[media_ids[i] for i in g] for g in result['small_groups']],
                'counts': result['counts'], 'pipelineVersion': result['pipeline_version'],
                'unassignedMediaIds': [media_ids[i] for i in result['unassigned_image_ids']],
                'errors': [{'mediaId': media_ids.get(e['image_id']), 'code': e['reason']} for e in result['errors']],
            }
        else:
            response = {
                'groups':[{'groupId':g['group_id'], 'mediaIds':[media_ids[i] for i in g['image_ids']],
                           'faceMembershipCount':len(g['face_image_ids']), 'bodyAdditionCount':len(g['body_additions'])}
                          for g in result['groups']],
                'counts':result['counts'], 'pipelineVersion':result['pipeline_version'],
                'unassignedMediaIds':[media_ids[i] for i in result['unassigned_image_ids']],
                'errors':[{'mediaId':media_ids.get(e['image_id']), 'code':e['reason']}
                          for e in result['errors']],
            }
        # Preparation failures may not appear in the successful-photo manifest.
        inputs = {f'image_{i:06d}':p.mediaId for i,p in enumerate(sorted(photos,key=lambda p:p.filename),1)}
        for error, raw in zip(response['errors'],result['errors']):
            error['mediaId'] = inputs.get(raw['image_id'])
        # Publish directly: there is no file handoff waiting for the parent to finish.
        # A timeout/restart that already marked this execution failed cannot be undone.
        store.complete(room_id, job_id, response)


@contextmanager
def job_log(path):
    # Redirect native libraries as well as Python output, away from the protocol pipe.
    sys.stdout.flush()
    sys.stderr.flush()
    saved = [os.dup(fd) for fd in (1, 2)]
    try:
        with path.open('ab', buffering=0) as log:
            os.dup2(log.fileno(), 1)
            os.dup2(log.fileno(), 2)
            yield
    finally:
        sys.stdout.flush()
        sys.stderr.flush()
        for fd, original in zip((1, 2), saved):
            os.dup2(original, fd)
            os.close(original)


def serve(settings):
    for line in sys.stdin:
        request = json.loads(line)
        room_id, job_id = request['room_id'], request['id']
        # Resolve through the store before using the ID as a path.
        Store(settings.data).get(room_id, job_id)
        logs = settings.data / 'logs'
        logs.mkdir(exist_ok=True)
        with job_log(logs / f'{job_id}.log'):
            try:
                execute(room_id, job_id, settings)
            except Exception as exc:
                print(f'Analysis failed: {type(exc).__name__}', file=sys.stderr)
                return 1  # Discard potentially damaged runtime state on failure.
            gc.collect()
        print(json.dumps({'id': job_id}), flush=True)
    return 0


if __name__ == '__main__':
    try:
        settings = Settings.environment()
        if sys.argv[1:] == ['--serve']:
            raise SystemExit(serve(settings))
        execute(int(sys.argv[1]), sys.argv[2], settings)
    except Exception as exc:
        # Signed storage URLs must not leak through HTTP exceptions in logs.
        print(f'Analysis failed: {type(exc).__name__}', file=sys.stderr)
        raise SystemExit(1)
