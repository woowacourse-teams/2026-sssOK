"""One subprocess per analysis; heavy model imports stay outside the MCP process."""
import fcntl
import json
import logging
from pathlib import Path
import sys

from .config import Settings
from .inputs import Photo, stage_photos
from .execution_store import Store


def execute(room_id, job_id, settings):
    logging.getLogger('httpx').setLevel(logging.WARNING)
    store = Store(settings.data)
    job = store.get(room_id, job_id)
    directory = settings.data / 'runs' / job_id
    directory.mkdir(parents=True, exist_ok=False)
    photos = [Photo.model_validate(p) for p in json.loads(job['photos'])]
    # Also excludes a surviving orphan worker after a forced MCP server restart.
    with (settings.data / 'analysis.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        stage_photos(photos, directory / 'input', settings)
        if job['kind'] == 'SIMILAR':
            from pipeline.similar_analysis import run
        else:
            from pipeline.photo_analysis import run
        result = run(directory / 'input', directory / 'analysis', settings.assets, settings.threads)
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


if __name__ == '__main__':
    try:
        execute(int(sys.argv[1]),sys.argv[2],Settings.environment())
    except Exception as exc:
        # Signed storage URLs must not leak through HTTP exceptions in logs.
        print(f'Analysis failed: {type(exc).__name__}',file=sys.stderr)
        raise SystemExit(1)
