"""Check denominators independently of the grouping implementation."""
import unittest
from evaluation.compare_person_grouping import evaluate


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.data = {'faces': [{'face_id': i, 'image_id': str(i)} for i in range(3)]}
        self.truth = {'primary': {'0': ['a'], '1': ['a'], '2': []},
                      'face_identity': {'0': 'a', '1': 'a', '2': None}}

    def test_empty_predictions_have_undefined_precision_and_zero_recall(self):
        result = evaluate(self.data, self.truth, [], [])
        self.assertIsNone(result['precision'])
        self.assertEqual(result['recall'], 0)

    def test_wrong_background_is_counted_at_face_and_photo_levels(self):
        groups = [{'seed_face_ids': [0, 1], 'face_ids': [0, 1, 2]}]
        result = evaluate(self.data, self.truth, groups, [])
        self.assertEqual(result['precision'], 2/3)
        self.assertEqual(result['recall'], 1)
        self.assertEqual(result['wrong_links'], 1)
        self.assertEqual(result['background_wrong_faces'], 1)

    def test_known_background_is_valid_without_inflating_primary_recall(self):
        self.truth['face_identity']['2'] = 'a'
        groups = [{'seed_face_ids': [0, 1], 'face_ids': [0, 2]}]
        result = evaluate(self.data, self.truth, groups, [])
        self.assertEqual(result['precision'], 1)
        self.assertEqual(result['recall'], .5)

    def test_split_identity_cannot_inflate_recall(self):
        groups = [{'seed_face_ids': [0], 'face_ids': [0]}, {'seed_face_ids': [0], 'face_ids': [0]}]
        result = evaluate(self.data, self.truth, groups, [])
        self.assertEqual(result['recall'], .5)
        self.assertEqual(result['splits'], 1)
