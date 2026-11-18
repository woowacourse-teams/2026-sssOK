import unittest

import numpy as np

from pipeline.small_face_recovery import match_recovery, recovery_targets
from test_person_grouping import face, body


class SmallFaceRecoveryTests(unittest.TestCase):
    def setUp(self):
        self.faces = [face(0, 'a'), face(1, 'b')]
        self.groups = [{'seed_face_ids': [0, 1], 'face_ids': [0, 1]}]
        self.features = np.array([[1., 0.], [1., 0.]])
        self.ids = {'a': 'a', 'b': 'b', 'candidate': 'candidate'}

    def test_only_unassigned_face_free_single_body_photo_is_retried(self):
        photos = [{'image_id': pid, 'source_path': '/unused'} for pid in ('candidate', 'a', 'crowd', 'empty', 'body_assigned')]
        bodies = [body(i, pid) for i, pid in enumerate(('candidate', 'a', 'crowd', 'crowd', 'body_assigned'))]
        selected = recovery_targets(photos, self.faces, bodies, self.groups, [{'image_id': 'body_assigned'}])
        self.assertEqual([p['image_id'] for p in selected], ['candidate'])

    def test_unassigned_detected_face_is_not_retried(self):
        self.faces.append(face(2, 'candidate'))
        self.assertEqual(recovery_targets([{'image_id': 'candidate', 'source_path': '/unused'}],
                                         self.faces, [body(0, 'candidate')], self.groups, []), [])

    def test_no_new_groups_from_recovery(self):
        self.assertEqual(recovery_targets([{'image_id': 'candidate', 'source_path': '/unused'}],
                                         [], [body(0, 'candidate')], [], []), [])

    def test_two_independent_seed_photos_required(self):
        args = (np.array([1., 0.]), 'candidate', self.faces, self.features, self.groups)
        self.assertEqual(match_recovery(*args, self.ids)['group'], 0)
        self.ids['b'] = 'a'
        self.assertIsNone(match_recovery(*args, self.ids))

    def test_recovered_faces_cannot_recursively_expand(self):
        self.faces.append(face(2, 'previous_recovery'))
        self.features = np.vstack([self.features, [0., 1.]])
        self.groups[0]['face_ids'].append(2)
        self.ids['previous_recovery'] = 'previous_recovery'
        self.assertIsNone(match_recovery(np.array([0., 1.]), 'candidate', self.faces,
                                         self.features, self.groups, self.ids))

    def test_single_reference_competitor_blocks_recovery(self):
        self.faces.append(face(2, 'c'))
        self.features = np.vstack([self.features, [1., 0.]])
        self.groups.append({'seed_face_ids': [2], 'face_ids': [2]})
        self.ids['c'] = 'c'
        self.assertIsNone(match_recovery(np.array([1., 0.]), 'candidate', self.faces,
                                         self.features, self.groups, self.ids))
