import unittest
import numpy as np
from pipeline.similar_grouping import build_groups


class SimilarGroupingTests(unittest.TestCase):
    def test_transitive_chain_cannot_merge_dissimilar_endpoints(self):
        score = np.array([[1,.9,.4],[.9,1,.8],[.4,.8,1]])
        groups, pairs, singles = build_groups(score, np.ones((3,3)))
        self.assertEqual(groups, [])
        self.assertEqual(pairs, [[0,1]])
        self.assertEqual(singles, [2])

    def test_center_guard_blocks_shared_background(self):
        center = np.ones((3,3));center[0,2]=center[2,0]=.6
        groups, pairs, singles = build_groups(np.ones((3,3)), center)
        self.assertFalse(groups)
        self.assertEqual(sorted(sum(pairs,[])+singles),[0,1,2])

    def test_minimum_size_and_disjoint_partition(self):
        score=np.eye(6);score[:3,:3]=.9;score[3:5,3:5]=.9
        groups,pairs,singles=build_groups(score,np.ones((6,6)))
        self.assertEqual((groups,pairs,singles),([[0,1,2]],[[3,4]],[5]))

    def test_invalid_or_nonfinite_features_rejected(self):
        with self.assertRaises(ValueError):build_groups([[float('nan')]],[[1]])
        with self.assertRaises(ValueError):build_groups([[1,2]],[[1]])
