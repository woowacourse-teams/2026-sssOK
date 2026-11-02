"""Face grouping and body association. No files, identity labels or fixed person count."""
from __future__ import annotations

import numpy as np


def build_face_groups(faces, features, hashes):
    """Reproduce the selected seed -> initial attachment -> frozen expansion policy."""
    similarity = features @ features.T
    seeds = [f['face_id'] for f in faces if not f['quality']['seed_failures']]
    clusters = [[fid] for fid in seeds]
    while True:
        best = None
        for a, left in enumerate(clusters):
            photo_ids = {faces[i]['image_id'] for i in left}
            for b in range(a + 1, len(clusters)):
                right = clusters[b]
                if photo_ids & {faces[i]['image_id'] for i in right}:
                    continue
                scores = similarity[np.ix_(left, right)]
                score = float(scores.mean())
                if float(scores.min()) >= .25 and score >= .45 and (best is None or score > best[0]):
                    best = (score, a, b)
        if best is None:
            break
        _, a, b = best
        clusters[a] += clusters[b]
        clusters.pop(b)
    clusters.sort(key=lambda g: (-len(g), min(g)))
    groups, unpublished = [], []
    for cluster in clusters:
        distinct = []
        for fid in cluster:
            if all(np.count_nonzero(hashes[fid] != hashes[other]) > 6 for other in distinct):
                distinct.append(fid)
        if len({faces[i]['image_id'] for i in cluster}) >= 2 and len(distinct) >= 2:
            groups.append({'seed_face_ids': cluster.copy(), 'face_ids': cluster.copy()})
        else:
            unpublished.append(cluster.copy())
    seed_assignments = {fid for g in groups for fid in g['face_ids']}
    first = []
    for face in faces:
        fid = face['face_id']
        if fid in seed_assignments or fid in seeds or face['quality']['match_failures']:
            continue
        options = []
        for gi, group in enumerate(groups):
            values = np.sort(similarity[fid, group['seed_face_ids']])[::-1]
            options.append((float(values[:2].mean()), float(values[0]), gi))
        options.sort(reverse=True)
        if not options:
            continue
        score, best, gi = options[0]
        margin = score - (options[1][0] if len(options) > 1 else -1)
        if score >= .45 and best >= .55 and margin >= .08:
            first.append((score, fid, gi, margin))
    first_accepted = []
    for score, fid, gi, margin in sorted(first, reverse=True):
        if faces[fid]['image_id'] in {faces[i]['image_id'] for i in groups[gi]['face_ids']}:
            continue
        groups[gi]['face_ids'].append(fid)
        first_accepted.append({'face_id': fid, 'group': gi, 'score': score, 'margin': margin})
    # Freeze this stage's references. No candidate accepted below can alter these pools.
    frozen = [g['face_ids'].copy() for g in groups]
    locked = {fid for refs in frozen for fid in refs}
    proposals = []
    for face in faces:
        fid, q = face['face_id'], face['quality']
        if fid in locked or q['min_size'] < 32 or q['contrast'] < 18 or q['sharpness'] < 60:
            continue
        if q['score'] < .85 or q['eye_width'] < .09 or q['nose_offset'] > 1.8:
            continue
        options = []
        for gi, pool in enumerate(frozen):
            values = np.sort(similarity[fid, pool])[::-1]
            options.append({'group': gi, 'score': float(values[:2].mean()), 'best': float(values[0])})
        options.sort(key=lambda x: x['score'], reverse=True)
        if not options:
            continue
        best = options[0]
        margin = best['score'] - (options[1]['score'] if len(options) > 1 else -1)
        if best['score'] >= .45 and best['best'] >= .45 and margin >= .12:
            proposals.append({'face_id': fid, 'group': best['group'], 'score': best['score'], 'margin': margin})
    expanded = []
    for proposal in sorted(proposals, key=lambda p: p['score'], reverse=True):
        fid, gi = proposal['face_id'], proposal['group']
        if faces[fid]['image_id'] in {faces[i]['image_id'] for i in groups[gi]['face_ids']}:
            continue
        groups[gi]['face_ids'].append(fid)
        expanded.append(proposal)
    for gi, group in enumerate(groups):
        group['group_id'] = f'person_{gi + 1:03d}'
    assignments = {fid: gi for gi, g in enumerate(groups) for fid in g['face_ids']}
    pending = [{'face_id': f['face_id'], 'reason': 'insufficient_identity_evidence'}
               for f in faces if f['face_id'] not in assignments]
    return groups, {'unpublished_seed_groups': unpublished, 'initial_attachments': first_accepted,
                    'expanded_attachments': expanded, 'pending_faces': pending,
                    'initial_face_memberships': len(locked)}


def anchor_bodies(bodies, faces, groups):
    face_group = {fid: gi for gi, g in enumerate(groups) for fid in g['face_ids']}
    for body in bodies:
        body.update(anchor_faces=[], anchor_group=None, anchor_status='no_face_anchor')
    for face in faces:
        fid = face['face_id']
        if fid not in face_group:
            continue
        fx, fy, fw, fh = face['bbox']
        cx, cy = fx + fw / 2, fy + fh / 2
        options = []
        for body in bodies:
            if body['image_id'] != face['image_id']:
                continue
            x1, y1, x2, y2 = body['bbox']
            bw, bh = x2 - x1, y2 - y1
            ix = max(0, min(fx + fw, x2) - max(fx, x1))
            iy = max(0, min(fy + fh, y2) - max(fy, y1))
            if ix * iy / (fw * fh) < .8 or not 0 <= (cy - y1) / bh <= .5:
                continue
            cost = abs(cx - (x1 + x2) / 2) / bw + abs((cy - y1) / bh - .15)
            options.append((cost, body['body_id']))
        options.sort()
        if options and (len(options) == 1 or options[1][0] - options[0][0] >= .08):
            bodies[options[0][1]]['anchor_faces'].append(fid)
    for body in bodies:
        matches = {face_group[fid] for fid in body['anchor_faces']}
        if len(matches) == 1 and len(body['anchor_faces']) == 1:
            body.update(anchor_group=next(iter(matches)), anchor_status='face_locked')
        elif matches:
            body['anchor_status'] = 'ambiguous_face_body_overlap'


def match_bodies(bodies, features, faces, groups):
    anchor_bodies(bodies, faces, groups)
    similarity = features @ features.T
    references = [[b['body_id'] for b in bodies if b['anchor_group'] == gi
                   and b['score'] >= .8 and b['area_ratio'] >= .02] for gi in range(len(groups))]
    face_photos = [{faces[fid]['image_id'] for fid in group['face_ids']} for group in groups]
    proposals, pending = [], []
    for body in bodies:
        bid, pid = body['body_id'], body['image_id']
        if body['anchor_status'] == 'face_locked':
            continue
        if body['anchor_status'] != 'no_face_anchor':
            pending.append({'body_id': bid, 'reason': 'ambiguous_face_body_overlap'})
            continue
        if body['score'] < .8 or body['area_ratio'] < .03 or body['relative_area'] < .35:
            pending.append({'body_id': bid, 'reason': 'body_quality_or_size'})
            continue
        options = []
        for gi, pool in enumerate(references):
            per_photo = {}
            for ref in pool:
                reference_photo = bodies[ref]['image_id']
                if reference_photo == pid:
                    continue
                value = float(similarity[bid, ref])
                if reference_photo not in per_photo or value > per_photo[reference_photo][0]:
                    per_photo[reference_photo] = (value, ref)
            top = sorted(per_photo.values(), reverse=True)[:2]
            if len(top) < 2:
                continue
            options.append({'group': gi, 'score': float(np.mean([x[0] for x in top])),
                            'reference_body_ids': [x[1] for x in top]})
        options.sort(key=lambda x: x['score'], reverse=True)
        if not options:
            pending.append({'body_id': bid, 'reason': 'insufficient_face_anchored_references'})
            continue
        best = options[0]
        margin = best['score'] - (options[1]['score'] if len(options) > 1 else -1)
        if best['score'] < .60 or margin < .06:
            pending.append({'body_id': bid, 'reason': 'insufficient_similarity_or_margin',
                            'best_group': best['group'], 'score': best['score'], 'margin': margin})
            continue
        proposals.append({'body_id': bid, 'image_id': pid, **best, 'margin': margin})
    occupied = [set(x) for x in face_photos]
    accepted = []
    for proposal in sorted(proposals, key=lambda p: p['score'], reverse=True):
        gi, pid = proposal['group'], proposal['image_id']
        if pid in occupied[gi]:
            pending.append({'body_id': proposal['body_id'], 'reason': 'already_assigned_in_photo'})
            continue
        occupied[gi].add(pid)
        accepted.append(proposal)
    return accepted, pending
