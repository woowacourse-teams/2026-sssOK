import unittest
import numpy as np

from pipeline.person_grouping import build_face_groups, match_bodies


def face(fid, pid):
    return {'face_id': fid, 'image_id': pid, 'bbox': [5, 5, 10, 10],
            'quality': {'seed_failures': [], 'match_failures': [], 'min_size': 64,
                        'contrast': 30, 'sharpness': 100, 'score': .99,
                        'eye_width': .3, 'nose_offset': .1}}


def body(bid, pid):
    return {'body_id': bid, 'image_id': pid, 'bbox': [0, 0, 40, 100],
            'score': .99, 'area_ratio': .1, 'relative_area': 1.0}


class GroupingTests(unittest.TestCase):
    def test_empty_detections(self):
        groups, evidence = build_face_groups([], np.empty((0, 128), dtype=np.float32))
        self.assertEqual(groups, [])
        additions, pending = match_bodies([], np.empty((0, 512), dtype=np.float32), [], [])
        self.assertEqual((additions, pending), ([], []))

    def test_single_face_does_not_publish_identity(self):
        groups, evidence = build_face_groups([face(0, 'one')], np.ones((1, 128)))
        self.assertEqual(groups, [])
        self.assertEqual(len(evidence['pending_faces']), 1)

    def test_same_photo_faces_cannot_merge_and_single_group_is_supported(self):
        faces = [face(0, 'one'), face(1, 'one'), face(2, 'two')]
        features = np.array([[1., 0.]] * 3)
        groups, _ = build_face_groups(faces, features)
        self.assertEqual(len(groups), 1)
        self.assertEqual(set(groups[0]['face_ids']), {0, 2})

    def test_pair_floor_blocks_identity_chain(self):
        faces = [face(i, str(i)) for i in range(3)]
        features = np.array([[1., 0.], [2**-.5, 2**-.5], [0., 1.]])
        groups, _ = build_face_groups(faces, features)
        self.assertEqual(len(groups), 1)
        self.assertEqual(len(groups[0]['face_ids']), 2)

    def test_body_additions_never_become_references(self):
        faces = [face(0, 'ref1'), face(1, 'ref2')]
        groups = [{'face_ids': [0, 1]}]
        bodies = [body(0, 'ref1'), body(1, 'ref2'), body(2, 'candidate1'), body(3, 'candidate2')]
        additions, _ = match_bodies(bodies, np.array([[1., 0.]] * 4), faces, groups)
        self.assertEqual(len(additions), 2)
        for addition in additions:
            self.assertEqual(set(addition['reference_body_ids']), {0, 1})

    def test_duplicate_body_in_same_photo_has_one_membership(self):
        faces = [face(0, 'ref1'), face(1, 'ref2')]
        bodies = [body(0, 'ref1'), body(1, 'ref2'), body(2, 'candidate'), body(3, 'candidate')]
        additions, pending = match_bodies(bodies, np.array([[1., 0.]] * 4), faces, [{'face_ids': [0, 1]}])
        self.assertEqual(len(additions), 1)
        self.assertTrue(any(p['reason'] == 'already_assigned_in_photo' for p in pending))

    def test_two_independent_reference_photos_required(self):
        faces = [face(0, 'ref1')]
        bodies = [body(0, 'ref1'), body(1, 'candidate')]
        additions, pending = match_bodies(bodies, np.array([[1., 0.]] * 2), faces, [{'face_ids': [0]}])
        self.assertEqual(additions, [])
        self.assertEqual(pending[0]['reason'], 'insufficient_face_anchored_references')

    def test_single_reference_competitor_can_block_but_not_receive_assignment(self):
        # Both identities have two faces, but B has only one usable body reference.
        for competitor_score, expected_count in ((.70, 0), (.60, 0), (.40, 1)):
            with self.subTest(competitor_score=competitor_score):
                faces = [face(0, 'a1'), face(1, 'a2'), face(2, 'b1'), face(3, 'b2')]
                bodies = [body(0, 'a1'), body(1, 'a2'), body(2, 'b1'), body(3, 'candidate')]
                scores = [.62, .62, competitor_score]
                features = np.array([[score, (1-score**2)**.5] for score in scores] + [[1., 0.]])
                additions, pending = match_bodies(
                    bodies, features, faces, [{'face_ids': [0, 1]}, {'face_ids': [2, 3]}])
                self.assertEqual(len(additions), expected_count)
                if additions:
                    self.assertEqual(additions[0]['group'], 0)
                    self.assertEqual(set(additions[0]['reference_body_ids']), {0, 1})
                else:
                    self.assertEqual(pending[0]['reason'], 'insufficient_similarity_or_margin')
                    self.assertAlmostEqual(pending[0]['margin'], .62-competitor_score)

    def test_small_candidate_is_kept_pending(self):
        faces = [face(0, 'ref1'), face(1, 'ref2')]
        bodies = [body(0, 'ref1'), body(1, 'ref2'), body(2, 'candidate')]
        bodies[2]['area_ratio'] = .01
        additions, pending = match_bodies(bodies, np.array([[1., 0.]] * 3), faces, [{'face_ids': [0, 1]}])
        self.assertEqual(additions, [])
        self.assertEqual(pending[0]['reason'], 'body_quality_or_size')


if __name__ == '__main__':
    unittest.main()
