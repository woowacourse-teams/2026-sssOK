"""Synthetic policy boundaries, not model accuracy measurements."""
import unittest
import numpy as np
from pipeline.person_grouping import build_face_groups, match_bodies
from pipeline.subject_evidence import seed_eligibility, face_body_links
from test_person_grouping import face, body


def distant(fid, pid):
    item = face(fid, pid)
    item['quality'].update(min_size=40, seed_failures=['min_size', 'area_ratio'])
    return item


class SubjectEvidenceTests(unittest.TestCase):
    def test_full_body_recovery_keeps_identity_threshold(self):
        fs = [distant(i, str(i)) for i in range(2)]
        bs = [body(i, str(i)) for i in range(2)]
        groups, evidence = build_face_groups(fs, np.array([[1., 0.]] * 2), bodies=bs)
        self.assertEqual(len(groups), 1)
        self.assertEqual(evidence['seed_reason_counts'], {'full_body': 2})
        self.assertEqual(build_face_groups(fs, np.eye(2), bodies=bs)[0], [])

    def test_background_only_cannot_start_group(self):
        for reason in ('relative_size', 'small_body', 'relative_body', 'no_body', 'low_score'):
            with self.subTest(reason=reason):
                fs = [distant(i, str(i)) for i in range(2)]
                bs = [body(i, str(i)) for i in range(2)]
                for f, b in zip(fs, bs):
                    if reason == 'relative_size': f['quality']['seed_failures'].append('relative_size')
                    if reason == 'small_body': b['area_ratio'] = .039
                    if reason == 'relative_body': b['relative_area'] = .49
                    if reason == 'low_score': b['score'] = .79
                if reason == 'no_body': bs = []
                self.assertEqual(build_face_groups(fs, np.array([[1., 0.]] * 2), bodies=bs)[0], [])

    def test_known_background_joins_unknown_stays_pending(self):
        fs = [face(0, 'a'), face(1, 'b'), distant(2, 'c'), distant(3, 'd')]
        for f in fs[2:]: f['quality']['seed_failures'].append('relative_size')
        groups, evidence = build_face_groups(fs, np.array([[1., 0.], [1., 0.], [1., 0.], [0., 1.]]))
        self.assertEqual(groups[0]['seed_face_ids'], [0, 1])
        self.assertEqual(groups[0]['face_ids'], [0, 1, 2])
        self.assertEqual([p['face_id'] for p in evidence['pending_faces']], [3])

    def test_bad_face_quality_and_tiny_face_not_rescued(self):
        for failure in ('contrast', 'sharpness', 'score', 'eye_width', 'nose_offset'):
            f = distant(0, 'a'); f['quality']['seed_failures'].append(failure)
            self.assertEqual(seed_eligibility([f], [body(0, 'a')])[0], [])
        f = distant(0, 'a'); f['quality']['min_size'] = 31.99
        self.assertEqual(seed_eligibility([f], [body(0, 'a')])[0], [])

    def test_closeup_without_body(self):
        self.assertEqual(seed_eligibility([face(0, 'a')], [])[0], [0])

    def test_ambiguous_many_to_one_and_wrong_photo_geometry(self):
        fs = [distant(0, 'a')]
        self.assertEqual(face_body_links(fs, [body(0, 'a'), body(1, 'a')]), {})
        self.assertEqual(face_body_links(fs + [distant(1, 'a')], [body(0, 'a')]), {})
        self.assertEqual(face_body_links(fs, [body(0, 'b')]), {})
        b = body(0, 'a'); b['bbox'] = [0, -100, 40, 20]
        self.assertEqual(face_body_links(fs, [b]), {})

    def test_duplicate_photos_cannot_create_identity(self):
        fs = [face(0, 'a'), face(1, 'copy')]
        self.assertEqual(build_face_groups(fs, np.array([[1., 0.]] * 2), photo_identities={'a': 'a', 'copy': 'a'})[0], [])

    def test_copy_can_join_but_is_not_second_body_reference(self):
        fs = [face(0, 'a'), face(1, 'copy'), face(2, 'b')]
        ids = {'a': 'a', 'copy': 'a', 'b': 'b', 'candidate': 'candidate'}
        groups, _ = build_face_groups(fs, np.array([[1., 0.]] * 3), photo_identities=ids)
        self.assertEqual(set(groups[0]['face_ids']), {0, 1, 2})
        self.assertEqual(set(groups[0]['seed_face_ids']), {0, 2})
        bs = [body(0, 'a'), body(1, 'copy'), body(2, 'candidate')]
        additions, pending = match_bodies(bs, np.array([[1., 0.]] * 3), fs, groups, photo_identities=ids)
        self.assertEqual(additions, [])
        self.assertEqual(pending[0]['reason'], 'insufficient_face_anchored_references')

    def test_relaxed_attachment_cannot_recursively_expand(self):
        fs = [face(0, 'a'), face(1, 'b'), distant(2, 'c'), distant(3, 'd')]
        for f in fs[2:]: f['quality']['match_failures'] = ['eye_width']
        vectors = np.array([[1., 0.], [1., 0.], [.5, 3**.5/2], [0., 1.]])
        groups, _ = build_face_groups(fs, vectors)
        self.assertEqual(groups[0]['face_ids'], [0, 1, 2])

    def test_ambiguous_body_cannot_reenter_through_body_matching(self):
        fs = [face(0, 'a'), face(1, 'b'), distant(2, 'c'), distant(3, 'c')]
        bs = [body(0, 'a'), body(1, 'b'), body(2, 'c')]
        additions, pending = match_bodies(bs, np.array([[1., 0.]] * 3), fs, [{'face_ids': [0, 1, 2]}, {'face_ids': [3]}])
        self.assertEqual(additions, [])
        self.assertEqual(pending[0]['reason'], 'ambiguous_face_body_overlap')
