"""One run from input images to result. All state is under the new output directory."""
from __future__ import annotations

import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np

from .person_grouping import build_face_groups, match_bodies

PIPELINE_VERSION = '1.0.1-local'
POLICY = {
    'face_detector': 'YuNet', 'face_features': 'SFace', 'body_detector': 'SSDLite320 MobileNetV3',
    'body_features': 'OSNet-x1.0 MSMT17, full body, no flip', 'face_detection_score': .75,
    'seed_average_similarity': .45, 'seed_pair_floor': .25, 'seed_min_photos': 2,
    'initial_face_attachment': {'mean': .45, 'best': .55, 'margin': .08},
    'face_expansion': {'mean': .45, 'best': .45, 'margin': .12, 'references': 'frozen initial groups'},
    'body_attachment': {'top2_mean': .60, 'margin': .06, 'min_score': .8,
                        'min_area_ratio': .03, 'min_relative_area': .35,
                        'min_assignment_photos': 2, 'min_competitor_photos': 1,
                        'references': 'frozen face-anchored bodies from different images'},
    'new_body_references': False, 'group_ids': 'run scoped',
}


def write_json(path, data):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')
    temporary.replace(path)


class Job:
    def __init__(self, output, input_dir, progress=None):
        self.output = output
        # Optional recorder for the server's delivery loop. Local runs write job.json only.
        self.progress = progress
        self.started = time.perf_counter()
        self.state = {'schema_version': 1, 'pipeline_version': PIPELINE_VERSION, 'status': 'running',
                      'stage': 'initializing', 'input': str(input_dir), 'output': str(output), 'timings': {}}
        self.stage_started = self.started
        self.last_printed = None
        self.update('initializing')

    def update(self, stage, _report=True, **details):
        now = time.perf_counter()
        old = self.state['stage']
        if old != stage:
            self.state['timings'][old] = round(now - self.stage_started, 4)
            self.stage_started = now
        self.state.update(stage=stage, progress=details, elapsed_seconds=round(now - self.started, 4))
        write_json(self.output / 'job.json', self.state)
        if self.progress and _report:
            self.progress.write(stage, details.get('processed'), details.get('total'))
        if stage != self.last_printed:
            print(json.dumps({'stage': stage, **details}, ensure_ascii=False), flush=True)
            self.last_printed = stage

    def finish(self, status, **details):
        # A terminal status is not a stage: the result itself is what the server reports next.
        self.update(status, _report=False, **details)
        self.state['status'] = status
        write_json(self.output / 'job.json', self.state)


def run(input_dir, output, assets_root, threads=4, progress=None):
    # Load model libraries only when analysis starts. Local reporting is a
    # separate consumer of the JSON result, never part of server execution.
    from .image_features import (configure_runtime, resolve_assets, prepare_photos,
                                 extract_faces, extract_bodies, extract_body_features)
    input_dir, output = Path(input_dir).resolve(), Path(output).resolve()
    if not input_dir.is_dir():
        raise ValueError(f'Input directory does not exist: {input_dir}')
    if output == input_dir or input_dir in output.parents:
        raise ValueError('Output must be outside the input directory')
    # Exclusive creation prevents accidental overwrite and concurrent runs sharing a result directory.
    output.mkdir(parents=True, exist_ok=False)
    job = Job(output, input_dir, progress)
    try:
        job.update('loading_models')
        assets = resolve_assets(assets_root)
        runtime = configure_runtime(threads)
        manifest = {'schema_version': 1, 'pipeline_version': PIPELINE_VERSION, 'policy': POLICY,
                    'runtime': {'python': platform.python_version(), **runtime},
                    'models': {name: {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                               for name, path in assets.items()}}
        photos, errors = prepare_photos(input_dir, output / 'images', job.update)
        manifest['photos'] = photos
        manifest['preparation_errors'] = errors.copy()
        write_json(output / 'manifest.json', manifest)
        if not photos:
            raise ValueError('Every supported input image failed decoding; see manifest.json')
        job.update('detecting_faces')
        faces, face_features, hashes, face_errors = extract_faces(photos, output, assets, job.update)
        errors.extend(face_errors)
        np.savez_compressed(output / 'face-features.npz', features=face_features)
        job.update('grouping_faces', faces=len(faces))
        groups, face_evidence = build_face_groups(faces, face_features, hashes)
        job.update('detecting_bodies', groups=len(groups))
        bodies, body_errors = extract_bodies(photos, output, assets, job.update)
        errors.extend(body_errors)
        job.update('extracting_body_features', bodies=len(bodies))
        body_features = extract_body_features(bodies, photos, output, assets, job.update)
        np.savez_compressed(output / 'body-features.npz', features=body_features)
        job.update('matching_bodies')
        additions, pending_bodies = match_bodies(bodies, body_features, faces, groups)
        result_groups = []
        for gi, group in enumerate(groups):
            face_ids = sorted({faces[fid]['image_id'] for fid in group['face_ids']})
            attached = [a for a in additions if a['group'] == gi]
            image_ids = sorted(set(face_ids) | {a['image_id'] for a in attached})
            memberships = [{'image_id': pid, 'method': 'face',
                            'face_ids': [fid for fid in group['face_ids'] if faces[fid]['image_id'] == pid]}
                           for pid in face_ids]
            memberships += [{'image_id': a['image_id'], 'method': 'body', 'body_id': a['body_id'],
                             'score': a['score'], 'margin': a['margin'],
                             'reference_body_ids': a['reference_body_ids']} for a in attached]
            result_groups.append({**group, 'face_image_ids': face_ids, 'body_additions': attached,
                                  'image_ids': image_ids, 'memberships': memberships})
        assigned = {pid for g in result_groups for pid in g['image_ids']}
        unassigned = sorted({p['image_id'] for p in photos} - assigned)
        result = {'schema_version': 1, 'pipeline_version': PIPELINE_VERSION,
                  'status': 'completed_with_errors' if errors else 'completed',
                  'counts': {'photos_prepared': len(photos), 'groups': len(groups), 'faces': len(faces),
                             'bodies': len(bodies), 'face_memberships': sum(len(g['face_image_ids']) for g in result_groups),
                             'body_additions': len(additions), 'memberships': sum(len(g['image_ids']) for g in result_groups),
                             'unassigned_photos': len(unassigned), 'errors': len(errors)},
                  'groups': result_groups, 'unassigned_image_ids': unassigned, 'errors': errors,
                  'artifacts': {'manifest': 'manifest.json', 'evidence': 'evidence.json'},
                  'notes': ['No identity annotations or previous experiment outputs loaded.',
                            'Group IDs are scoped to this run; no cross-run identity matching.',
                            'Unassigned photos and pending person detections are different.']}
        evidence = {'faces': faces, 'bodies': bodies, 'face_grouping': face_evidence,
                    'pending_bodies': pending_bodies, 'body_additions': additions}
        job.update('exporting')
        write_json(output / 'evidence.json', evidence)
        manifest['photos'] = photos
        write_json(output / 'manifest.json', manifest)
        write_json(output / 'result.json', result)
        job.finish(result['status'], counts=result['counts'])
        print(json.dumps({'status': result['status'], **result['counts'], 'result': str(output / 'result.json')}, ensure_ascii=False), flush=True)
        return result
    except BaseException as exc:
        job.finish('interrupted' if isinstance(exc, KeyboardInterrupt) else 'failed', error=str(exc))
        raise
