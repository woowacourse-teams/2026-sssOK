"""Late recovery from frozen seed references; never creates or merges groups."""
import numpy as np
import copy
from .subject_evidence import contains_face

MIN_SHARPNESS = 30.0
MIN_MEAN_SIMILARITY = .50
MIN_MARGIN = .15

def propose_late_faces(faces, features, groups, photo_identities):
    evidence = {'faces': faces, 'photo_identities': photo_identities}
    proposals = _propose(evidence, groups, features,
                         (2, MIN_MEAN_SIMILARITY, MIN_MARGIN, MIN_SHARPNESS, 'mean'))
    # Low contrast needs a separate, stronger evidence gate. Keep the normal
    # contrast rule intact and never promote recovered faces to reference seeds.
    relaxed = copy.deepcopy(evidence)
    eligible = set()
    locked = {fid for group in groups for fid in group['face_ids']}
    for face in relaxed['faces']:
        if face['face_id'] in locked:
            continue
        q = face['quality']
        if (12 <= q['contrast'] < 18 and q['sharpness'] >= 30
                and q['min_size'] >= 64 and q['score'] >= .90):
            q['contrast'] = 18
            eligible.add(face['face_id'])
    extra = _propose(relaxed, groups, features, (2, .60, .15, 30, 'mean'))
    occupied = {(p['group'], p['image_id']) for p in proposals}
    assigned = {p['face_id'] for p in proposals}
    for p in extra:
        key = (p['group'], p['image_id'])
        if p['face_id'] in eligible and p['face_id'] not in assigned and key not in occupied:
            proposals.append(p)
            occupied.add(key)
            assigned.add(p['face_id'])
    return proposals


def filter_conflicting_bodies(additions, bodies, faces, proposals):
    """Reject clothing only when a unique contained recovered face disagrees."""
    recovered = {p['face_id']: p['group'] for p in proposals}
    by_id = {b['body_id']: b for b in bodies}
    kept, rejected = [], []
    for addition in additions:
        contained = [f['face_id'] for f in faces if contains_face(by_id[addition['body_id']], f)]
        if (len(contained) == 1 and contained[0] in recovered
                and recovered[contained[0]] != addition['group']):
            rejected.append({'body_id': addition['body_id'], 'face_id': contained[0],
                             'reason': 'conflicting_recovered_face'})
        else:
            kept.append(addition)
    return kept, rejected

def _propose(ev, groups, z, cfg):
    if cfg is None: return []
    n, threshold, margin_min, sharp, method = cfg
    faces, ids = ev['faces'], ev['photo_identities']
    locked = {fid for g in groups for fid in g['face_ids']}
    proposals = []
    for f in faces:
        fid, q = f['face_id'], f['quality']
        if fid in locked: continue
        if q['min_size'] < 32 or q['contrast'] < 18 or q['sharpness'] < sharp: continue
        if q['score'] < .85 or q['eye_width'] < .09 or q['nose_offset'] > 1.8: continue
        options = []
        for gi, g in enumerate(groups):
            # All competing groups contribute scores, even if they lack n refs.
            per_photo = {}
            for ref in g['seed_face_ids']:
                pid = ids[faces[ref]['image_id']]
                if pid == ids[f['image_id']]: continue
                score = float(z[fid] @ z[ref])
                if pid not in per_photo or score > per_photo[pid][0]:
                    per_photo[pid] = (score, ref)
            top = sorted(per_photo.values(), reverse=True)[:n]
            if not top: continue
            score = float(np.mean([x[0] for x in top]))
            if method == 'centroid':
                centroid = np.mean([z[x[1]] for x in top], axis=0)
                score = float(z[fid] @ centroid / max(np.linalg.norm(centroid), 1e-12))
            options.append((score, gi, top))
        options.sort(reverse=True)
        if not options: continue
        score, gi, refs = options[0]
        margin = score - (options[1][0] if len(options) > 1 else -1)
        if len(refs) < n or score < threshold or margin < margin_min: continue
        if f['image_id'] in {faces[i]['image_id'] for i in groups[gi]['face_ids']}: continue
        proposals.append({'face_id':fid, 'group':gi, 'image_id':f['image_id'],
                          'score':score, 'margin':margin, 'reference_ids':[x[1] for x in refs]})
    accepted, used = [], set()
    for p in sorted(proposals, key=lambda p:(-p['score'],p['face_id'])):
        key = (p['group'], p['image_id'])
        if key in used: continue
        used.add(key); accepted.append(p)
    return accepted
