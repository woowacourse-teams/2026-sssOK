import unittest
from unittest.mock import patch
import tempfile
from pathlib import Path
import numpy as np
from pipeline.exposure_recovery import select_proposals,filter_uncertain_detector_bodies,recover_exposure_faces

class ExposureRecoveryTests(unittest.TestCase):
    def candidate(self,method='stretch',group=0,score=.8):
        return dict(image_id='c',target_face_id=None,method=method,group=group,score=score,margin=.2,bbox=[1,1,64,64],references=[0,1])
    def test_variants_disagreeing_on_group_are_rejected(self):
        self.assertEqual([],select_proposals([self.candidate(),self.candidate('clahe',1)],[],[{'face_ids':[]},{'face_ids':[]}]))
    def test_same_group_different_locations_are_rejected(self):
        other=self.candidate('gamma');other['bbox']=[200,200,64,64]
        self.assertEqual([],select_proposals([self.candidate(),other],[],[{'face_ids':[]}]))
    def test_one_candidate_per_face_and_group_photo(self):
        self.assertEqual(1,len(select_proposals([self.candidate(),self.candidate('gamma')],[],[{'face_ids':[]}])) )
    def test_threshold_and_competitor_margin_are_required(self):
        low=self.candidate(score=.649);close=self.candidate();close['margin']=.149
        self.assertEqual([],select_proposals([low,close],[],[{'face_ids':[]}]))
    def test_already_grouped_photo_is_not_added(self):
        faces=[{'image_id':'c'}]
        self.assertEqual([],select_proposals([self.candidate()],faces,[{'face_ids':[0]}]))
    def test_extra_veto_requires_two_independent_photos(self):
        fs=[{'face_id':i,'image_id':p,'bbox':[0,0,10,10],'quality':{'seed_failures':['score'],'score':.87,'sharpness':80}} for i,p in enumerate(['a','b','c'])]
        bodies=[{'body_id':0,'image_id':'c','bbox':[0,0,20,20]}];adds=[{'body_id':0,'group':0}]
        z=np.array([[1.,0.],[1.,0.],[0.,1.]])
        gs=[{'seed_face_ids':[0,1]}]
        self.assertEqual([],filter_uncertain_detector_bodies(adds,bodies,fs,z,gs,{'a':'a','b':'b','c':'c'})[0])
        self.assertEqual(adds,filter_uncertain_detector_bodies(adds,bodies,fs,z,gs,{'a':'a','b':'a','c':'c'})[0])
    def test_new_face_never_becomes_seed_or_changes_existing_features(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);(out/'faces').mkdir()
            fs=[{'face_id':i,'image_id':p,'bbox':[0,0,10,10]} for i,p in enumerate(['a','b'])]
            z=np.array([[1.,0.],[1.,0.]]);gs=[{'face_ids':[0,1],'seed_face_ids':[0,1]}];photos=[{'image_id':'c','face_ids':[]}]
            c=self.candidate();c.update(feature=np.array([.8,.6]),aligned=np.zeros((112,112,3),np.uint8),quality={'seed_failures':[]})
            with patch('pipeline.exposure_recovery.gather_candidates',return_value=([c],{'attempted_photos':1})):
                result,adds,evidence=recover_exposure_faces(photos,out,{},fs,z,[],gs,[],{'a':'a','b':'b','c':'c'})
            self.assertEqual([0,1],gs[0]['seed_face_ids']);self.assertEqual([0,1,2],gs[0]['face_ids'])
            np.testing.assert_array_equal(result[:2],z)
            self.assertIn('exposure_recovery_not_seed',fs[2]['quality']['seed_failures'])
            self.assertEqual([2],photos[0]['face_ids'])
if __name__=='__main__':unittest.main()
