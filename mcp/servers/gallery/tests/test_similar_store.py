import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from uuid import uuid4

from gallery_mcp.execution_store import Store, validated_result


class SimilarStoreTests(unittest.TestCase):
    def test_kind_is_bound_to_execution_id_and_persisted_after_restart(self):
        with tempfile.TemporaryDirectory() as path:
            store=Store(path);eid=str(uuid4());photos=[{'mediaId':1}]
            store.create(1,eid,'same',photos,8,kind='SIMILAR')
            self.assertTrue(store.create(1,eid,'same',photos,8,kind='SIMILAR')[1])
            with self.assertRaises(ValueError):store.create(1,eid,'same',photos,8,kind='PERSON')
            store.claim()
            result={'resultVersion':1,'groups':[],'smallGroupMediaIds':[],
                    'unassignedMediaIds':[1],'counts':{},'pipelineVersion':'test','errors':[]}
            store.complete(1,eid,result)
            restored=Store(path);restored.recover()
            self.assertEqual(restored.get(1,eid)['kind'],'SIMILAR')
            self.assertEqual(json.loads(restored.get(1,eid)['result']),result)


    def test_v1_migration_keeps_records_and_defaults_to_person(self):
        with tempfile.TemporaryDirectory() as path:
            store=Store(path);eid=str(uuid4());store.create(1,eid,'same',[{'mediaId':1}],8)
            with sqlite3.connect(store.database) as db:
                db.execute('ALTER TABLE executions DROP COLUMN kind')
                db.execute('PRAGMA user_version=1')
            migrated=Store(path)
            self.assertEqual(migrated.get(1,eid)['kind'],'PERSON')
            self.assertEqual(migrated.get(1,eid)['state'],'QUEUED')
            self.assertTrue(migrated.create(1,eid,'same',[{'mediaId':1}],8)[1])
            with sqlite3.connect(store.database) as db:self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],2)


    def test_result_requires_disjoint_complete_partition(self):
        photos=json.dumps([{'mediaId':i} for i in range(1,7)])
        result={'resultVersion':1,'groups':[{'groupId':'g1','mediaIds':[1,2,3]}],
                'smallGroupMediaIds':[[4,5]],'unassignedMediaIds':[],
                'counts':{},'pipelineVersion':'test','errors':[{'mediaId':6,'code':'decode'}]}
        self.assertEqual(validated_result(result,photos,'SIMILAR'),result)
        for bad in [dict(result,unassignedMediaIds=[1]),dict(result,errors=[]),
                    dict(result,smallGroupMediaIds=[[4]]),
                    dict(result,groups=[{'groupId':'g1','mediaIds':[1,2]}])]:
            with self.assertRaises(ValueError):validated_result(bad,photos,'SIMILAR')


    def test_migration_refuses_active_old_server(self):
        import fcntl
        with tempfile.TemporaryDirectory() as path:
            store=Store(path)
            with sqlite3.connect(store.database) as db:
                db.execute('ALTER TABLE executions DROP COLUMN kind');db.execute('PRAGMA user_version=1')
            with (Path(path)/'server.lock').open('a') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX)
                with self.assertRaisesRegex(RuntimeError,'previous server'):Store(path)
            with sqlite3.connect(store.database) as db:
                self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],1)
                self.assertNotIn('kind',{r[1] for r in db.execute('PRAGMA table_info(executions)')})
