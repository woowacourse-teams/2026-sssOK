"""One run from input images to result. All state is under the new output directory."""
from __future__ import annotations

import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np

from .person_grouping import FACE_CONTRADICTION_FLOOR, FACE_CONTRADICTION_MIN_SHARPNESS, build_face_groups, match_bodies, body_feature_ids
from .photo_duplicates import independent_photos
from .small_face_recovery import recover_small_faces
from .late_face_recovery import propose_late_faces, filter_conflicting_bodies
from .exposure_recovery import recover_exposure_faces, filter_uncertain_detector_bodies

PIPELINE_VERSION = '1.1.7-selective-body-features'
POLICY = {
    'face_detector': 'YuNet', 'face_features': 'SFace', 'body_detector': 'SSDLite320 MobileNetV3',
    'body_feature_selection': 'all anchor references and eligible unanchored candidates only',
    'body_features': 'OSNet-x1.0 MSMT17, full body, no flip', 'face_detection_score': .75,
    'seed_average_similarity': .45, 'seed_pair_floor': .25, 'seed_min_photos': 2,
    'initial_face_attachment': {'mean': .45, 'best': .55, 'margin': .08},
    'face_expansion': {'mean': .45, 'best': .45, 'margin': .12, 'references': 'frozen initial groups'},
    'body_attachment': {'top2_mean': .60, 'margin': .06, 'min_score': .8,
                        'min_area_ratio': .03, 'min_relative_area': .35,
                        'min_assignment_photos': 2, 'min_competitor_photos': 1,
                        'known_face_overlap_guard': '80% face coverage outside accepted anchor blocks body',
                        'face_contradiction': {'max_seed_similarity_below': FACE_CONTRADICTION_FLOOR,
                                               'quality': 'single contained face passing seed quality except sharpness',
                                               'min_sharpness_if_seed_failed': FACE_CONTRADICTION_MIN_SHARPNESS,
                                               'min_independent_seed_photos': 2},
                        'references': 'frozen face-anchored bodies from different images'},
    'new_body_references': False, 'group_ids': 'run scoped',
    'exposure_recovery': {'methods': ['stretch', 'clahe', 'gamma'],
                          'mean_similarity': .65, 'margin': .15,
                          'min_independent_seed_photos': 2,
                          'new_references': False, 'reject_conflicting_variants': True},
    'additional_body_veto': {'min_detector_score': .85, 'max_seed_similarity_below': .30,
                             'min_sharpness': 40, 'single_contained_face': True},
    'late_face_recovery': {'min_sharpness': 30, 'min_mean_similarity': .50,
                           'min_margin': .15, 'independent_seed_photos': 2,
                           'new_references': False, 'after_body_matching': True},
    'low_contrast_recovery': {'min_contrast': 12, 'min_sharpness': 30,
                              'min_size': 64, 'min_detector_score': .90,
                              'min_mean_similarity': .60, 'min_margin': .15,
                              'independent_seed_photos': 2},
    'native_face_recovery': {'max_source_edge': 3200, 'min_native_face_size': 32,
                             'scope': 'unassigned photo, no detected face, exactly one body and one native face',
                             'min_independent_seed_photos': 2, 'mean': .45, 'best': .55, 'margin': .12,
                             'new_references': False},
    'seed_recovery': {'min_face_size': 32, 'min_body_area': .04,
                      'min_relative_body_area': .50, 'min_body_score': .80},
    'duplicate_evidence': 'whole-frame mean <=1.5 and worst 8x8 tile <=6, 96x96 RGB',
    'attachment_references': 'frozen passes, duplicate reference photos count once',
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
        faces, face_features, _hashes, face_errors = extract_faces(photos, output, assets, job.update)
        errors.extend(face_errors)
        np.savez_compressed(output / 'face-features.npz', features=face_features)
        job.update('grouping_faces', faces=len(faces))
        photo_identities = independent_photos(photos, output)
        job.update('detecting_bodies')
        bodies, body_errors = extract_bodies(photos, output, assets, job.update)
        errors.extend(body_errors)
        groups, face_evidence = build_face_groups(
            faces, face_features, bodies=bodies, photo_identities=photo_identities)
        selected_body_ids = body_feature_ids(bodies, faces, groups)
        job.update('extracting_body_features', bodies=len(bodies), processed=0, total=len(selected_body_ids))
        # Zero rows are never consulted by matching; keep existing body-ID indexing.
        body_features = np.zeros((len(bodies), 512), dtype=np.float32)
        if selected_body_ids:
            body_features[selected_body_ids] = extract_body_features(
                [bodies[bid] for bid in selected_body_ids], photos, output, assets, job.update)
        np.savez_compressed(output / 'body-features.npz', features=body_features)
        job.update('matching_bodies')
        additions, pending_bodies = match_bodies(
            bodies, body_features, faces, groups, photo_identities=photo_identities,
            face_features=face_features)
        face_features, recovery = recover_small_faces(
            photos, output, assets, faces, face_features, bodies, groups, additions, photo_identities)
        if recovery['accepted']:
            np.savez_compressed(output / 'face-features.npz', features=face_features)
        # Keep body reference pools frozen. Recovered faces may reject a
        # conflicting body proposal but never become anchors or new references.
        late_faces = propose_late_faces(faces, face_features, groups, photo_identities)
        additions, rejected_bodies = filter_conflicting_bodies(additions, bodies, faces, late_faces)
        pending_bodies.extend(rejected_bodies)
        for proposal in late_faces:
            groups[proposal['group']]['face_ids'].append(proposal['face_id'])
        additions, uncertain_bodies = filter_uncertain_detector_bodies(
            additions, bodies, faces, face_features, groups, photo_identities)
        pending_bodies.extend(uncertain_bodies)
        face_features, additions, exposure = recover_exposure_faces(
            photos, output, assets, faces, face_features, bodies, groups, additions, photo_identities)
        pending_bodies.extend(exposure['rejected_bodies'])
        if exposure['accepted']:
            np.savez_compressed(output / 'face-features.npz', features=face_features)
        recovered_ids = {fid for g in groups for fid in g['face_ids']}
        face_evidence['pending_faces'] = [p for p in face_evidence['pending_faces']
                                          if p['face_id'] not in recovered_ids]
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
                             'reference_body_ids': a['reference_body_ids']} for a in attached
                            if a['image_id'] not in face_ids]
            result_groups.append({**group, 'face_image_ids': face_ids, 'body_additions': attached,
                                  'image_ids': image_ids, 'memberships': memberships})
        assigned = {pid for g in result_groups for pid in g['image_ids']}
        unassigned = sorted({p['image_id'] for p in photos} - assigned)
        result = {'schema_version': 1, 'pipeline_version': PIPELINE_VERSION,
                  'status': 'completed_with_errors' if errors else 'completed',
                  'counts': {'photos_prepared': len(photos), 'groups': len(groups), 'faces': len(faces),
                             'bodies': len(bodies), 'face_memberships': sum(len(g['face_image_ids']) for g in result_groups),
                             'body_additions': len(additions), 'memberships': sum(len(g['image_ids']) for g in result_groups),
                             'unassigned_photos': len(unassigned), 'errors': len(errors),
                             'seed_faces': face_evidence['seed_faces'],
                             'independent_photos': face_evidence['independent_photos']},
                  'groups': result_groups, 'unassigned_image_ids': unassigned, 'errors': errors,
                  'artifacts': {'manifest': 'manifest.json', 'evidence': 'evidence.json'},
                  'notes': ['No identity annotations or previous experiment outputs loaded.',
                            'Group IDs are scoped to this run; no cross-run identity matching.',
                            'Unassigned photos and pending person detections are different.']}
        evidence = {'computed_body_feature_ids': selected_body_ids, 'faces': faces, 'bodies': bodies, 'face_grouping': face_evidence, 'photo_identities': photo_identities,
                    'pending_bodies': pending_bodies, 'body_additions': additions,
                    'native_face_recovery': recovery, 'late_face_recovery': late_faces,
                    'exposure_recovery': exposure}
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
