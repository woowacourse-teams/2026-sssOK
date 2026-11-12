"""Offline comparison with separately supplied, private annotations; never used at runtime.

Input directory: data.json (photos/faces/bodies), features.npz (faces/bodies/hashes),
annotations.json (primary: image -> identity list, face_identity: face id -> identity).
Use null identity for an unknown background face. Keep all inputs outside Git.
"""
import argparse
import collections
import copy
import importlib.util
import json
from pathlib import Path
import sys
import time

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from pipeline.person_grouping import build_face_groups, match_bodies
from pipeline.subject_evidence import SubjectPolicy, seed_eligibility
from pipeline.photo_duplicates import independent_photos

VARIANTS = {'duplicates': (), 'no_area': ('area_ratio',),
            'relaxed': ('min_size', 'area_ratio', 'relative_size'),
            'relative': ('min_size', 'area_ratio')}


def predict(data, features, identities, variant, baseline, legacy_hashes=False):
    faces, bodies = copy.deepcopy(data['faces']), copy.deepcopy(data['bodies'])
    if variant == 'baseline':
        groups, evidence = baseline.build_face_groups(faces, features['faces'], features['hashes'])
        additions, _ = baseline.match_bodies(bodies, features['bodies'], faces, groups)
    else:
        for face in faces:
            face['quality']['seed_failures'] = [s for s in face['quality']['seed_failures']
                                              if s not in VARIANTS.get(variant, ())]
        kwargs = {}
        if variant.startswith('body_'):
            kwargs = {'bodies': bodies, 'policy': SubjectPolicy(min_body_area=float(variant[5:]))}
        if legacy_hashes:
            if kwargs:
                eligible, _ = seed_eligibility(faces, bodies, kwargs['policy'])
                for face in faces:
                    if face['face_id'] in eligible: face['quality']['seed_failures'] = []
            groups, evidence = baseline.build_face_groups(faces, features['faces'], features['hashes'])
            additions, _ = baseline.match_bodies(bodies, features['bodies'], faces, groups)
        else:
            groups, evidence = build_face_groups(faces, features['faces'], photo_identities=identities, **kwargs)
            additions, _ = match_bodies(bodies, features['bodies'], faces, groups, photo_identities=identities)
    return groups, additions


def evaluate(data, annotations, groups, additions):
    primary = annotations['primary']
    truth = {int(k): v for k, v in annotations['face_identity'].items()}
    assert set(truth) == {f['face_id'] for f in data['faces']}, 'Annotate every detected face'
    expected = {(identity, pid) for pid, identities in primary.items() for identity in identities}
    valid = expected | {(truth[f['face_id']], f['image_id']) for f in data['faces'] if truth[f['face_id']]}
    labels, predicted = [], []
    wrong_faces = background_wrong_faces = background_groups = background_seeds = 0
    for gi, group in enumerate(groups):
        counts = collections.Counter(truth[fid] for fid in group['face_ids'] if truth[fid])
        label = counts.most_common(1)[0][0] if counts else f'unknown_{gi}'
        labels.append(label)
        background_groups += not any(truth[fid] in primary[data['faces'][fid]['image_id']]
                                     for fid in group['seed_face_ids'])
        background_seeds += sum(truth[fid] is None for fid in group['seed_face_ids'])
        for fid in group['face_ids']:
            wrong_faces += truth[fid] != label
            background_wrong_faces += truth[fid] is None
        photos = {data['faces'][fid]['image_id'] for fid in group['face_ids']}
        photos.update(a['image_id'] for a in additions if a['group'] == gi)
        predicted.extend((label, pid) for pid in photos)
    correct = sum(pair in valid for pair in predicted)
    recovered = len(expected & set(predicted))
    background_count = sum(v is None for v in truth.values())
    return {'groups': len(groups), 'correct_links': correct, 'predicted_links': len(predicted),
            'wrong_links': len(predicted) - correct,
            'precision': correct / len(predicted) if predicted else None,
            'recovered': recovered, 'primary_links': len(expected),
            'recall': recovered / len(expected) if expected else None,
            'wrong_face_assignments': wrong_faces, 'background_wrong_faces': background_wrong_faces,
            'background_faces': background_count, 'background_only_groups': background_groups,
            'background_seeds': background_seeds,
            'splits': sum(n-1 for k, n in collections.Counter(labels).items() if not k.startswith('unknown_') and n > 1)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dataset', type=Path)
    parser.add_argument('--baseline', type=Path, required=True, help='Original person_grouping.py from the base commit')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--legacy-hashes', action='store_true', help='Isolate seed changes using the original grouping implementation')
    parser.add_argument('--repeats', type=int, default=5)
    args = parser.parse_args()
    if args.repeats < 1: parser.error('--repeats must be positive')
    spec = importlib.util.spec_from_file_location('baseline', args.baseline)
    baseline = importlib.util.module_from_spec(spec); spec.loader.exec_module(baseline)
    data = json.loads((args.dataset / 'data.json').read_text())
    annotations = json.loads((args.dataset / 'annotations.json').read_text())
    features = np.load(args.dataset / 'features.npz', allow_pickle=False)
    start = time.perf_counter()
    identities = independent_photos(data['photos'], args.dataset)
    duplicate_seconds = time.perf_counter() - start
    results = {}
    for variant in ['baseline', *VARIANTS, *[f'body_{v}' for v in (.02, .03, .04, .06, .08, .10)]]:
        times = []
        for _ in range(args.repeats):
            start = time.perf_counter()
            groups, additions = predict(data, features, identities, variant, baseline, args.legacy_hashes)
            times.append((time.perf_counter() - start) * 1000)
        result = evaluate(data, annotations, groups, additions)
        result['median_grouping_ms'] = float(np.median(times))
        results[variant] = result
    args.output.write_text(json.dumps({'photos': len(data['photos']), 'duplicate_seconds': duplicate_seconds,
                                      'independent_photos': len(set(identities.values())), 'variants': results}, indent=2))
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
