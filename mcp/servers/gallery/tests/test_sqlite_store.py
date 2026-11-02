"""Real SQLite transactions and separate-process crash/concurrency regression tests."""
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from uuid import uuid4

from gallery_mcp import execution_store
from gallery_mcp.execution_store import Store, now


RESULT = {'groups': [], 'counts': {}, 'pipelineVersion': 'test',
          'unassignedMediaIds': [1], 'errors': []}


class SqliteStoreTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.store = Store(self.root)
        self.execution = str(uuid4())
        self.store.create(1, self.execution, 'snapshot', [{'mediaId': 1}], 8)
        self.env = {**os.environ, 'PYTHONPATH': str(Path(execution_store.__file__).resolve().parents[1])}

    def process(self, code, *arguments):
        return subprocess.Popen([sys.executable, '-c', code, str(self.root), *arguments],
                                env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def result(self, process, expected=0):
        try:
            output, error = process.communicate(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
            raise
        self.assertEqual(process.returncode, expected, error)
        return output

    def test_worker_commit_survives_exit_without_parent_finish(self):
        self.store.claim()
        process = self.process('''import json, os, sys
from gallery_mcp.execution_store import Store
store=Store(sys.argv[1])
assert store.complete(1,sys.argv[2],json.loads(sys.argv[3]))
os._exit(17)
''', self.execution, json.dumps(RESULT))
        self.result(process, 17)
        restored = Store(self.root)
        restored.recover()
        row = restored.get(1, self.execution)
        self.assertEqual(row['state'], 'COMPLETED')
        self.assertEqual(json.loads(row['result']), RESULT)
        self.assertIsNone(row['acknowledged_at'])
        self.assertEqual(row['photos'], '[]')
        self.assertFalse((self.root / 'runs' / self.execution / 'response.json').exists())
        self.assertFalse(restored.finish(self.execution, 'FAILED', error='PARENT_EXITED'))
        self.assertEqual(restored.get(1, self.execution), row)

    def test_process_exit_before_commit_rolls_back_entire_update(self):
        self.store.claim()
        process = self.process('''import os, sqlite3, sys
from pathlib import Path
from gallery_mcp.execution_store import Store
db=sqlite3.connect(Path(sys.argv[1])/Store.DATABASE, isolation_level=None)
db.execute('BEGIN IMMEDIATE')
db.execute("UPDATE executions SET state='COMPLETED',result=?,photos='[]' WHERE id=?",(sys.argv[3],sys.argv[2]))
os._exit(19)
''', self.execution, json.dumps(RESULT))
        self.result(process, 19)
        restored = Store(self.root)
        row = restored.get(1, self.execution)
        self.assertEqual(row['state'], 'RUNNING')
        self.assertIsNone(row['result'])
        self.assertEqual(json.loads(row['photos']), [{'mediaId': 1}])
        restored.recover()
        self.assertEqual(restored.get(1, self.execution)['error_code'], 'SERVER_RESTARTED')

    def test_two_processes_cannot_claim_two_jobs_while_one_is_running(self):
        self.store.create(1, str(uuid4()), 'second', [{'mediaId': 1}], 8)
        code = '''import json,sys
from gallery_mcp.execution_store import Store
row=Store(sys.argv[1]).claim()
print(json.dumps(row['id'] if row else None))
'''
        processes = [self.process(code) for _ in range(2)]
        claims = [json.loads(self.result(p)) for p in processes]
        self.assertEqual(sum(c is not None for c in claims), 1)
        self.assertEqual(sorted(r['state'] for r in self.store.rows()), ['QUEUED', 'RUNNING'])

    def test_concurrent_identical_requests_create_one_execution(self):
        execution = str(uuid4())
        code = '''import json,sys
from gallery_mcp.execution_store import Store
_,reused=Store(sys.argv[1]).create(1,sys.argv[2],'same',[{'mediaId':1}],8)
print(json.dumps(reused))
'''
        processes = [self.process(code, execution) for _ in range(2)]
        self.assertEqual(sorted(json.loads(self.result(p)) for p in processes), [False, True])
        self.assertEqual(len(self.store.rows()), 2)

    def test_failed_execution_cannot_be_resurrected_by_late_worker(self):
        self.store.claim()
        self.store.finish(self.execution, 'FAILED', error='ANALYSIS_TIMEOUT')
        self.assertFalse(Store(self.root).complete(1, self.execution, RESULT))
        self.assertEqual(self.store.get(1, self.execution)['error_code'], 'ANALYSIS_TIMEOUT')

    def test_concurrent_requests_cannot_exceed_queue_limit(self):
        code = '''import sys
from gallery_mcp.execution_store import Store
try:
 Store(sys.argv[1]).create(1,sys.argv[2],'snapshot',[{'mediaId':1}],2)
 print('accepted')
except ValueError as error:
 assert 'queue is full' in str(error)
 print('full')
'''
        processes = [self.process(code, str(uuid4())) for _ in range(2)]
        self.assertEqual(sorted(self.result(p).strip() for p in processes), ['accepted', 'full'])
        self.assertEqual(len(self.store.rows()), 2)

    def test_legacy_queued_job_and_failed_delivery_are_imported(self):
        receipts = self.root / 'receipts'
        receipts.mkdir()
        expected = {}
        for state in ('QUEUED', 'FAILED'):
            execution = str(uuid4())
            row = dict(id=execution, request_id=execution, business_job_id=execution,
                       room_id=1, attempt=1, fingerprint='legacy', photos='[]', state=state,
                       result=None, error_code='ANALYSIS_FAILED' if state == 'FAILED' else None,
                       acknowledged_at=None, created_at=now(), updated_at=now())
            (receipts / f'{execution}.json').write_text(json.dumps(row))
            expected[execution] = row
        self.store.recover()
        for execution, row in expected.items():
            restored = self.store.get(1, execution)
            self.assertEqual({key: restored[key] for key in Store.COLUMNS}, row)

    def test_invalid_completion_does_not_change_state_or_result(self):
        self.store.claim()
        with self.assertRaises(ValueError):
            self.store.complete(1, self.execution, {**RESULT, 'unassignedMediaIds': [999]})
        row = self.store.get(1, self.execution)
        self.assertEqual(row['state'], 'RUNNING')
        self.assertIsNone(row['result'])

    def test_legacy_import_is_once_and_preserves_original_bytes(self):
        execution = str(uuid4())
        receipt = self.root / 'receipts' / f'{execution}.json'
        receipt.parent.mkdir()
        row = dict(id=execution, request_id=execution, business_job_id=execution,
                   room_id=1, attempt=2, fingerprint='legacy', photos='[]', state='COMPLETED',
                   result=json.dumps(RESULT), error_code=None, acknowledged_at=None,
                   created_at=now(), updated_at=now())
        receipt.write_text(json.dumps(row))
        original = receipt.read_bytes()
        self.store.recover()
        self.assertEqual(json.loads(self.store.get(1, execution)['result']), RESULT)
        self.assertIsNone(self.store.get(1, execution)['acknowledged_at'])
        self.store.acknowledge(execution, 'COMPLETED')
        self.store.prune(0)
        self.store.recover()
        with self.assertRaises(ValueError):
            self.store.get(1, execution)
        self.assertEqual(receipt.read_bytes(), original)

    def test_bad_legacy_receipt_rolls_back_import_and_preserves_files(self):
        receipts = self.root / 'receipts'
        receipts.mkdir()
        good = dict(self.store.get(1, self.execution))
        execution = str(uuid4())
        good.update(id=execution, request_id=execution, business_job_id=execution)
        (receipts / f'{execution}.json').write_text(json.dumps(good))
        (receipts / 'zz-invalid.json').write_text('{')
        with self.assertRaises(ValueError):
            self.store.recover()
        self.assertEqual(len(self.store.rows()), 1)
        (receipts / 'zz-invalid.json').unlink()
        self.store.recover()
        self.assertEqual(len(self.store.rows()), 2)

    def test_unknown_database_version_is_not_overwritten(self):
        with sqlite3.connect(self.store.database) as db:
            db.execute('PRAGMA user_version=99')
        with self.assertRaisesRegex(RuntimeError, 'version'):
            Store(self.root)
        with sqlite3.connect(self.store.database) as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0], 99)
