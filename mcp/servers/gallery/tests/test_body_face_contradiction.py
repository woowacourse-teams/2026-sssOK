import unittest

import numpy as np

from pipeline.person_grouping import match_bodies
from test_person_grouping import face, body


class BodyFaceContradictionTests(unittest.TestCase):
    def setUp(self):
        self.faces = [face(i, pid) for i, pid in enumerate(('a', 'b', 'candidate'))]
        self.bodies = [body(i, pid) for i, pid in enumerate(('a', 'b', 'candidate'))]
        self.groups = [{'face_ids': [0, 1], 'seed_face_ids': [0, 1]}]
        self.body_features = np.array([[1., 0.]] * 3)
        self.face_features = np.array([[1., 0.], [1., 0.], [0., 1.]])

    def match(self, **kwargs):
        return match_bodies(self.bodies, self.body_features, self.faces, self.groups,
                            face_features=self.face_features, **kwargs)

    def test_clear_unassigned_face_vetoes_identical_clothing(self):
        additions, pending = self.match()
        self.assertEqual(additions, [])
        self.assertEqual(pending[0]['reason'], 'conflicting_face_features')
        self.assertEqual(pending[0]['face_id'], 2)

    def test_one_plausible_seed_preserves_body_match(self):
        self.face_features[1] = [0., 1.]
        self.assertEqual(len(self.match()[0]), 1)

    def test_contradiction_boundary_is_strict(self):
        self.face_features[2] = [.40, (1 - .40 ** 2) ** .5]
        self.assertEqual(len(self.match()[0]), 1)
        self.face_features[2] = [.399, (1 - .399 ** 2) ** .5]
        self.assertEqual(self.match()[0], [])

    def test_face_seed_without_body_has_an_independent_photo(self):
        self.faces.append(face(3, 'face_only'))
        self.face_features = np.vstack([self.face_features, [1., 0.]])
        self.groups[0]['face_ids'].append(3)
        self.groups[0]['seed_face_ids'].append(3)
        self.assertEqual(self.match()[0], [])

    def test_profile_or_low_quality_face_does_not_veto(self):
        self.faces[2]['quality']['seed_failures'] = ['nose_offset']
        self.assertEqual(len(self.match()[0]), 1)

    def test_unrelated_face_in_photo_does_not_veto_body(self):
        self.faces[2]['bbox'] = [60, 5, 10, 10]
        self.assertEqual(len(self.match()[0]), 1)

    def test_multiple_contained_unassigned_faces_do_not_veto(self):
        self.faces.append(face(3, 'candidate'))
        self.face_features = np.vstack([self.face_features, [0., 1.]])
        self.assertEqual(len(self.match()[0]), 1)

    def test_only_seed_faces_count_as_veto_references(self):
        self.groups[0]['seed_face_ids'] = [0]
        self.assertEqual(len(self.match()[0]), 1)

    def test_same_photo_copies_are_not_two_face_references(self):
        self.faces.append(face(3, 'c'))
        self.bodies.append(body(3, 'c'))
        self.body_features = np.array([[1., 0.]] * 4)
        self.face_features = np.vstack([self.face_features, [1., 0.]])
        self.groups[0]['face_ids'].append(3)
        # Body references a and c are independent; face seeds a and b are copies.
        additions, _ = self.match(photo_identities={'a': 'a', 'b': 'a', 'c': 'c', 'candidate': 'candidate'})
        self.assertEqual([a['body_id'] for a in additions], [2])

    def test_moderate_blur_only_vetoes_at_boundary(self):
        q = self.faces[2]['quality']
        q['seed_failures'] = ['sharpness']
        q['sharpness'] = 40.
        self.assertEqual(self.match()[0], [])
        q['sharpness'] = 39.99
        self.assertEqual(len(self.match()[0]), 1)

    def test_moderate_blur_does_not_bypass_other_quality_failures(self):
        self.faces[2]['quality'].update(seed_failures=['sharpness', 'score'], sharpness=50.)
        self.assertEqual(len(self.match()[0]), 1)

    def test_missing_sharpness_does_not_bypass_blur_failure(self):
        self.faces[2]['quality']['seed_failures'] = ['sharpness']
        self.faces[2]['quality'].pop('sharpness', None)
        self.assertEqual(len(self.match()[0]), 1)
