"""Run-scoped, disjoint similarity groups. Never use filenames or identity labels."""
import numpy as np

SIMILARITY_FLOOR = .54
CENTER_FLOOR = .72
MIN_GROUP_SIZE = 3


def build_groups(similarity, center):
    similarity, center = np.asarray(similarity), np.asarray(center)
    if (similarity.ndim != 2 or similarity.shape[0] != similarity.shape[1]
            or center.shape != similarity.shape or not np.isfinite(similarity).all()
            or not np.isfinite(center).all()):
        raise ValueError('Finite square similarity matrices required')
    if not np.allclose(similarity, similarity.T) or not np.allclose(center, center.T):
        raise ValueError('Symmetric similarities required')
    groups = [[i] for i in range(len(similarity))]
    while True:
        best = None
        for a in range(len(groups)):
            for b in range(a):
                ix = np.ix_(groups[a], groups[b])
                score = float(similarity[ix].min())
                if (score >= SIMILARITY_FLOOR and float(center[ix].min()) >= CENTER_FLOOR
                        and (best is None or score > best[0])):
                    best = score, a, b
        if best is None:
            break
        _, a, b = best
        groups[b] += groups.pop(a)
    groups = sorted(sorted(g) for g in groups)
    return ([g for g in groups if len(g) >= MIN_GROUP_SIZE],
            [g for g in groups if len(g) == 2],
            [g[0] for g in groups if len(g) == 1])
