"""Face grouping and body association. No files, identity labels or fixed person count."""
from __future__ import annotations

from collections import Counter

import numpy as np

from .subject_evidence import DEFAULT_SUBJECT_POLICY, face_body_evidence, seed_eligibility


def build_face_groups(faces, features, *, bodies=(), photo_identities=None,
                      policy=DEFAULT_SUBJECT_POLICY):
    """Primary independent photographs create groups; attachment passes are frozen."""
    similarity = features @ features.T
    independent_ids = photo_identities or {f['image_id']: f['image_id'] for f in faces}
    eligible, reasons = seed_eligibility(faces, bodies, policy)
    # Only the representative frame supplies seeds. Copies can join an established
    # group, but cannot manufacture a second reference or bias cluster averages.
    seeds = [fid for fid in eligible if independent_ids[faces[fid]['image_id']] == faces[fid]['image_id']]
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
        if len({independent_ids[faces[i]['image_id']] for i in cluster}) >= 2:
            groups.append({'seed_face_ids': cluster.copy(), 'face_ids': cluster.copy()})
        else:
            unpublished.append(cluster.copy())

    def reference_scores(fid, pool):
        per_photo = {}
        for ref in pool:
            pid = independent_ids[faces[ref]['image_id']]
            if pid == independent_ids[faces[fid]['image_id']]:
                continue
            per_photo[pid] = max(per_photo.get(pid, -1), float(similarity[fid, ref]))
        return sorted(per_photo.values(), reverse=True)

    seed_assignments = {fid for g in groups for fid in g['face_ids']}
    first = []
    for face in faces:
        fid = face['face_id']
        if fid in seed_assignments or fid in seeds or face['quality']['match_failures']:
            continue
        options = []
        for gi, group in enumerate(groups):
            values = reference_scores(fid, group['seed_face_ids'])
            if values:
                options.append((float(np.mean(values[:2])), values[0], gi))
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
    # Freeze the stricter first pass before relaxed pose matching. Relaxed matches
    # never recursively expand the face reference pool.
    frozen = [g['face_ids'].copy() for g in groups]
    locked = {fid for group in groups for fid in group['face_ids']}
    proposals = []
    for face in faces:
        fid, q = face['face_id'], face['quality']
        if fid in locked or q['min_size'] < 32 or q['contrast'] < 18 or q['sharpness'] < 60:
            continue
        if q['score'] < .85 or q['eye_width'] < .09 or q['nose_offset'] > 1.8:
            continue
        options = []
        for gi, pool in enumerate(frozen):
            values = reference_scores(fid, pool)
            if values:
                options.append({'group': gi, 'score': float(np.mean(values[:2])), 'best': values[0]})
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
    return groups, {'seed_eligibility': reasons, 'seed_reason_counts': dict(Counter(reasons.values())),
                    'seed_failure_counts': dict(Counter(key for f in faces for key in f['quality']['seed_failures'])),
                    'duplicate_seed_faces': len(eligible) - len(seeds),
                    'seed_faces': len(seeds), 'independent_photos': len(set(independent_ids.values())),
                    'unpublished_seed_groups': unpublished, 'initial_attachments': first_accepted,
                    'expanded_attachments': expanded, 'pending_faces': pending,
                    'initial_face_memberships': len(locked)}


def anchor_bodies(bodies, faces, groups):
    face_group = {fid: gi for gi, g in enumerate(groups) for fid in g['face_ids']}
    for body in bodies:
        body.update(anchor_faces=[], anchor_group=None, anchor_status='no_face_anchor')
    by_id = {b['body_id']: b for b in bodies}
    links, ambiguous = face_body_evidence(
        [face for face in faces if face['face_id'] in face_group], bodies)
    for bid in ambiguous:
        by_id[bid]['anchor_status'] = 'ambiguous_face_body_overlap'
    for fid, bid in links.items():
        if fid in face_group:
            by_id[bid]['anchor_faces'].append(fid)
    for body in bodies:
        matches = {face_group[fid] for fid in body['anchor_faces']}
        if len(matches) == 1 and len(body['anchor_faces']) == 1:
            body.update(anchor_group=next(iter(matches)), anchor_status='face_locked')
        elif matches:
            body['anchor_status'] = 'ambiguous_face_body_overlap'


def match_bodies(bodies, features, faces, groups, *, photo_identities=None):
    independent_ids = photo_identities or {b['image_id']: b['image_id'] for b in bodies}
    anchor_bodies(bodies, faces, groups)
    similarity = features @ features.T
    references = [[b['body_id'] for b in bodies if b['anchor_group'] == gi
                   and b['score'] >= .8 and b['area_ratio'] >= .02]
                  for gi in range(len(groups))]
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
                reference_photo = independent_ids[bodies[ref]['image_id']]
                if reference_photo == independent_ids[pid]:
                    continue
                value = float(similarity[bid, ref])
                if reference_photo not in per_photo or value > per_photo[reference_photo][0]:
                    per_photo[reference_photo] = (value, ref)
            top = sorted(per_photo.values(), reverse=True)[:2]
            if not top:
                continue
            options.append({'group': gi, 'score': float(np.mean([x[0] for x in top])),
                            'reference_body_ids': [x[1] for x in top]})
        options.sort(key=lambda x: x['score'], reverse=True)
        # Two independent photos are required to assign an identity. A single
        # reference still counts as competing evidence against that assignment.
        eligible = [option for option in options if len(option['reference_body_ids']) >= 2]
        if not eligible:
            pending.append({'body_id': bid, 'reason': 'insufficient_face_anchored_references'})
            continue
        best = eligible[0]
        competitors = [option['score'] for option in options if option['group'] != best['group']]
        margin = best['score'] - max(competitors, default=-1)
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
