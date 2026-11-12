"""Fixed-detection counterexamples. These numbers are NOT real-photo accuracy."""
import json
import numpy as np
from compare_person_grouping import predict


def scene(kind):
    faces, bodies = [], []
    for i in range(2):
        quality = {'seed_failures': ['min_size', 'area_ratio'], 'match_failures': [],
                   'min_size': 40, 'contrast': 30, 'sharpness': 100, 'score': .99,
                   'eye_width': .3, 'nose_offset': .1}
        f = {'face_id': i, 'image_id': str(i), 'bbox': [30, 10, 40, 40], 'quality': quality}
        b = {'body_id': i, 'image_id': str(i), 'bbox': [0, 0, 100, 300],
             'score': .99, 'area_ratio': .08, 'relative_area': 1.}
        if kind == 'behind_larger_person':
            quality['seed_failures'].append('relative_size'); b['relative_area'] = .3
        if kind == 'small_person_alone': b['area_ratio'] = .02
        if kind == 'small_relative_body': b['relative_area'] = .4
        faces.append(f)
        if kind != 'no_body': bodies.append(b)
    return {'faces': faces, 'bodies': bodies}


def main():
    features = {'faces': np.array([[1., 0.]] * 2), 'bodies': np.array([[1., 0.]] * 2)}
    results = {}
    for variant in ['duplicates', 'no_area', 'relaxed', 'relative', 'body_0.02', 'body_0.03', 'body_0.04', 'body_0.06']:
        results[variant] = {}
        for kind in ['behind_larger_person', 'small_person_alone', 'small_relative_body', 'no_body', 'primary_full_body']:
            data = scene(kind)
            # No body features are accessed when there are no body detections.
            groups, _ = predict(data, features, {'0': '0', '1': '1'}, variant, None)
            results[variant][kind] = len(groups)
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
