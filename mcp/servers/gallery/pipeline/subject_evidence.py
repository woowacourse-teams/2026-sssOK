"""Evidence for starting an identity, separate from matching an existing identity."""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass


@dataclass(frozen=True)
class SubjectPolicy:
    min_face_size: float = 32
    min_body_area: float = .04
    min_relative_body_area: float = .50
    min_body_score: float = .80


DEFAULT_SUBJECT_POLICY = SubjectPolicy()


def face_body_evidence(faces, bodies):
    """Only unambiguous, one-face-to-one-body geometry can establish a subject."""
    links, ambiguous = {}, set()
    for face in faces:
        fx, fy, fw, fh = face['bbox']
        if min(fw, fh) <= 0:
            continue
        cx, cy = fx + fw / 2, fy + fh / 2
        options = []
        for body in bodies:
            if body['image_id'] != face['image_id']:
                continue
            x1, y1, x2, y2 = body['bbox']
            bw, bh = x2 - x1, y2 - y1
            if min(bw, bh) <= 0:
                continue
            ix = max(0, min(fx + fw, x2) - max(fx, x1))
            iy = max(0, min(fy + fh, y2) - max(fy, y1))
            if ix * iy / (fw * fh) < .8 or not 0 <= (cy - y1) / bh <= .5:
                continue
            cost = abs(cx - (x1 + x2) / 2) / bw + abs((cy - y1) / bh - .15)
            options.append((cost, body['body_id']))
        options.sort()
        if options and (len(options) == 1 or options[1][0] - options[0][0] >= .08):
            links[face['face_id']] = options[0][1]
        elif options:
            ambiguous.update(bid for _, bid in options)
    counts = Counter(links.values())
    ambiguous.update(bid for bid, count in counts.items() if count > 1)
    return {fid: bid for fid, bid in links.items() if bid not in ambiguous}, ambiguous


def face_body_links(faces, bodies):
    return face_body_evidence(faces, bodies)[0]


def seed_eligibility(faces, bodies, policy=DEFAULT_SUBJECT_POLICY):
    links = face_body_links(faces, bodies)
    by_id = {b['body_id']: b for b in bodies}
    reasons = {}
    for face in faces:
        fid, q = face['face_id'], face['quality']
        failures = q['seed_failures']
        # Preserve established close-up seeds, including portraits with no body detection.
        if not failures:
            reasons[fid] = 'closeup'
            continue
        # Size and global area may be rescued, never blur, pose, detector confidence
        # or a face that is small relative to another face in this photograph.
        if any(key not in ('min_size', 'area_ratio') for key in failures):
            reasons[fid] = 'face_quality_or_relative_size'
        elif q['min_size'] < policy.min_face_size:
            reasons[fid] = 'face_too_small'
        elif fid not in links:
            reasons[fid] = 'missing_or_ambiguous_body'
        else:
            body = by_id[links[fid]]
            if (body['score'] < policy.min_body_score or body['area_ratio'] < policy.min_body_area
                    or body['relative_area'] < policy.min_relative_body_area):
                reasons[fid] = 'body_not_prominent'
            else:
                reasons[fid] = 'full_body'
    eligible = [fid for fid, reason in reasons.items() if reason in ('closeup', 'full_body')]
    return eligible, reasons
