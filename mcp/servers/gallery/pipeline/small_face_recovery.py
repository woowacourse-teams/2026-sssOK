"""Bounded native-detail retry for otherwise unassigned, face-free photographs."""
from __future__ import annotations

import time
from collections import Counter
from pathlib import Path

import numpy as np

from .subject_evidence import face_body_links


def recovery_targets(photos, faces, bodies, groups, additions):
    occupied = {faces[fid]['image_id'] for g in groups for fid in g['face_ids']}
    occupied.update(a['image_id'] for a in additions)
    detected = {f['image_id'] for f in faces}
    counts = Counter(b['image_id'] for b in bodies)
    return [p for p in photos if groups and p['image_id'] not in occupied | detected
            and counts[p['image_id']] == 1 and p.get('source_path')]


def match_recovery(feature, pid, faces, features, groups, identities):
    options = []
    for gi, group in enumerate(groups):
        per_photo = {}
        for fid in group['seed_face_ids']:
            ref = identities[faces[fid]['image_id']]
            if ref == identities[pid]:
                continue
            score = float(feature @ features[fid])
            per_photo[ref] = max(per_photo.get(ref, -1), score)
        top = sorted(per_photo.values(), reverse=True)[:2]
        if top:
            options.append((float(np.mean(top)), top[0], len(top), gi))
    options.sort(reverse=True)
    if not options:
        return None
    score, best, count, gi = options[0]
    margin = score - (options[1][0] if len(options) > 1 else -1)
    if count == 2 and score >= .45 and best >= .55 and margin >= .12:
        return {'group': gi, 'score': score, 'best': best, 'margin': margin}
    return None


def recover_small_faces(photos, output, assets, faces, features, bodies, groups, additions, identities):
    targets = recovery_targets(photos, faces, bodies, groups, additions)
    evidence = {'attempted': 0, 'accepted': [], 'skipped': [], 'seconds': 0.0}
    if not targets:
        return features, evidence
    import cv2
    from PIL import Image, ImageOps
    from .image_features import face_quality

    started = time.perf_counter()
    detector = recognizer = None
    for photo in targets:
        pid = photo['image_id']
        try:
            with Image.open(photo['source_path']) as original:
                if original.width * original.height > 24_000_000:
                    evidence['skipped'].append({'image_id': pid, 'reason': 'source_pixel_limit'})
                    continue
                if max(original.size) <= max(photo['width'], photo['height']):
                    evidence['skipped'].append({'image_id': pid, 'reason': 'no_additional_source_detail'})
                    continue
                native = ImageOps.exif_transpose(original).convert('RGB')
                native.thumbnail((3200, 3200), Image.Resampling.LANCZOS)
                image = cv2.cvtColor(np.asarray(native), cv2.COLOR_RGB2BGR)
            if detector is None:
                detector = cv2.FaceDetectorYN.create(str(assets['yunet']), '', (320, 320), .75, .3, 5000)
                recognizer = cv2.FaceRecognizerSF.create(str(assets['sface']), '')
            height, width = image.shape[:2]
            detector.setInputSize((width, height))
            _, rows = detector.detect(image)
            evidence['attempted'] += 1
            rows = [r for r in rows if min(r[2:4]) >= 32] if rows is not None else []
            if len(rows) != 1:
                evidence['skipped'].append({'image_id': pid, 'reason': 'not_one_native_face'})
                continue
            row = rows[0]
            aligned = recognizer.alignCrop(image, row)
            quality, _ = face_quality(row, aligned, (width, height), float(row[2] * row[3]))
            if any(f not in ('min_size', 'area_ratio') for f in quality['seed_failures']):
                evidence['skipped'].append({'image_id': pid, 'reason': 'native_face_quality'})
                continue
            sx, sy = photo['width'] / width, photo['height'] / height
            bbox = [float(row[0] * sx), float(row[1] * sy), float(row[2] * sx), float(row[3] * sy)]
            fid = len(faces)
            candidate = {'face_id': fid, 'image_id': pid, 'bbox': bbox}
            if fid not in face_body_links([candidate], bodies):
                evidence['skipped'].append({'image_id': pid, 'reason': 'native_face_body_geometry'})
                continue
            feature = recognizer.feature(aligned).flatten().copy()
            feature /= max(float(np.linalg.norm(feature)), 1e-12)
            match = match_recovery(feature, pid, faces, features, groups, identities)
            if match is None:
                evidence['skipped'].append({'image_id': pid, 'reason': 'native_face_identity_evidence'})
                continue
            crop = f'faces/{fid:06d}.jpg'
            if not cv2.imwrite(str(Path(output) / crop), aligned):
                raise OSError('Cannot write recovered face crop')
            # min_size stays in prepared-image coordinates, as for ordinary faces.
            quality['native_min_size'] = quality['min_size']
            quality['min_size'] = min(bbox[2:])
            quality['seed_failures'] = sorted(set(quality['seed_failures']) | {'native_recovery_not_seed'})
            if quality['min_size'] < 32 and 'min_size' not in quality['match_failures']:
                quality['match_failures'].append('min_size')
            faces.append({**candidate, 'quality': quality, 'crop': crop, 'source': 'native_recovery'})
            features = np.vstack([features, feature])
            photo['face_ids'].append(fid)
            groups[match['group']]['face_ids'].append(fid)
            evidence['accepted'].append({'face_id': fid, 'image_id': pid, **match})
            # Body matching is already finished; recovered faces never add references.
        except (OSError, ValueError, cv2.error) as exc:
            evidence['skipped'].append({'image_id': pid, 'reason': 'native_recovery_failed',
                                        'error_type': type(exc).__name__})
    evidence['seconds'] = round(time.perf_counter() - started, 4)
    return features, evidence
