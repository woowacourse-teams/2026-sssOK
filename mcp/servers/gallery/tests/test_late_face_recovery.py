import copy
import unittest
import numpy as np
from pipeline.late_face_recovery import propose_late_faces, filter_conflicting_bodies

def propose(ev, groups, z, _cfg):
    return propose_late_faces(ev['faces'], z, groups, ev['photo_identities'])

CONFIGS = {'blur40_balanced': None}

class RecoveryGuards(unittest.TestCase):
    def fixture(self):
        faces=[{'face_id':i,'image_id':str(i),'quality':dict(min_size=64,contrast=40,sharpness=45,score=.99,eye_width=.3,nose_offset=.1)} for i in range(4)]
        ev={'faces':faces,'photo_identities':{str(i):str(i) for i in range(4)}}
        groups=[{'seed_face_ids':[0,1],'face_ids':[0,1]}]
        z=np.array([[1.,0.],[1.,0.],[.8,.6],[.8,.6]])
        return ev,groups,z
    def test_two_independent_references_required(self):
        ev,gs,z=self.fixture();ev['photo_identities']['1']='0'
        self.assertEqual([],propose(ev,gs,z,CONFIGS['blur40_balanced']))
    def test_existing_faces_and_inputs_unchanged(self):
        ev,gs,z=self.fixture();before=copy.deepcopy((ev,gs))
        pp=propose(ev,gs,z,CONFIGS['blur40_balanced'])
        self.assertEqual({2,3},{p['face_id'] for p in pp})
        self.assertEqual(before,(ev,gs))
        self.assertTrue(all(p['reference_ids']==[1,0] for p in pp))
    def test_single_reference_competitor_blocks(self):
        ev,gs,z=self.fixture();gs.append({'seed_face_ids':[3],'face_ids':[3]})
        self.assertEqual([],propose(ev,gs,z,CONFIGS['blur40_balanced']))
    def test_same_photo_does_not_join_same_group_twice(self):
        ev,gs,z=self.fixture();ev['faces'][3]['image_id']='2'
        self.assertEqual(1,len(propose(ev,gs,z,CONFIGS['blur40_balanced'])))
    def test_quality_floor_is_enforced(self):
        ev,gs,z=self.fixture()
        for f in ev['faces']:f['quality']['sharpness']=29
        self.assertEqual([],propose(ev,gs,z,CONFIGS['blur40_balanced']))

    def test_low_contrast_requires_stronger_similarity(self):
        ev,gs,z=self.fixture()
        for f in ev['faces']:f['quality']['contrast']=14
        z[2]=[.59, (1-.59**2)**.5];z[3]=[.65,(1-.65**2)**.5]
        self.assertEqual([3],[p['face_id'] for p in propose_late_faces(ev['faces'],z,gs,ev['photo_identities'])])

    def test_very_low_contrast_is_not_recovered(self):
        ev,gs,z=self.fixture()
        for f in ev['faces']:f['quality']['contrast']=11.99
        self.assertEqual([],propose_late_faces(ev['faces'],z,gs,ev['photo_identities']))

    def test_recovered_face_rejects_only_conflicting_body(self):
        faces=[{'face_id':0,'image_id':'x','bbox':[1,1,2,2]}]
        bodies=[{'body_id':0,'image_id':'x','bbox':[0,0,10,10]}]
        additions=[{'body_id':0,'group':1}]
        pp=[{'face_id':0,'group':0}]
        kept,rejected=filter_conflicting_bodies(additions,bodies,faces,pp)
        self.assertEqual([],kept);self.assertEqual('conflicting_recovered_face',rejected[0]['reason'])
        additions[0]['group']=0
        self.assertEqual((additions,[]),filter_conflicting_bodies(additions,bodies,faces,pp))

    def test_multi_face_body_is_not_rejected_by_one_recovery(self):
        faces=[{'face_id':i,'image_id':'x','bbox':[1+i,1,2,2]} for i in range(2)]
        bodies=[{'body_id':0,'image_id':'x','bbox':[0,0,10,10]}]
        additions=[{'body_id':0,'group':1}]
        self.assertEqual((additions,[]),filter_conflicting_bodies(additions,bodies,faces,[{'face_id':0,'group':0}]))

if __name__=='__main__':unittest.main()
