"""Conservative whole-photo duplicate evidence; never deduplicate face crops."""
from __future__ import annotations

import cv2
import numpy as np


def independent_photos(photos, output):
    """Collapse near-identical full frames, not similar people against the same backdrop.

    Both global and worst-tile differences must be tiny. Representatives are frozen:
    A~B and B~C cannot turn A and C into duplicates by transitive chaining.
    Images with materially different aspect ratios are never compared.
    """
    representatives = []
    assignments = {}
    for photo in photos:
        image = cv2.imread(str(output / photo['prepared_path']))
        if image is None:
            raise ValueError('Prepared photo cannot be read for duplicate evidence')
        h, w = image.shape[:2]
        # Area resampling removes JPEG noise while retaining local subject movement.
        small = cv2.resize(image, (96, 96), interpolation=cv2.INTER_AREA).astype(np.float32)
        identity = photo['image_id']
        for ref_id, ratio, reference in representatives:
            if abs(w / h - ratio) > .01 * ratio:
                continue
            diff = np.abs(small - reference)
            tiles = diff.reshape(12, 8, 12, 8, 3).mean(axis=(1, 3, 4))
            if float(diff.mean()) <= 1.5 and float(tiles.max()) <= 6:
                identity = ref_id
                break
        if identity == photo['image_id']:
            representatives.append((identity, w / h, small))
        assignments[photo['image_id']] = identity
    return assignments
