"""SQLite receipts retain results across processes and import legacy JSON once."""
from pathlib import Path
import json
import tempfile
import unittest
from uuid import uuid4
from unittest.mock import patch

from gallery_mcp.execution_store import Store, now


class ExecutionStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.store = Store(self.root)

    def create(self, request_id=None, fingerprint='snapshot'):
        return self.store.create(1, request_id or str(uuid4()), fingerprint,
                                 [{'mediaId': 1, 'source': 'private-url'}], 8)

    def legacy(self, state='RUNNING', result=None):
        execution_id = str(uuid4())
        row = dict(id=execution_id, room_id=1, request_id=execution_id,
                   business_job_id=execution_id, attempt=1, fingerprint='snapshot',
                   photos=json.dumps([{'mediaId': 1, 'source': 'private-url'}]),
                   state=state, result=json.dumps(result) if result is not None else None,
                   error_code=None, created_at=now(), updated_at=now(), acknowledged_at=None)
        receipts = self.root / 'receipts'
        receipts.mkdir(exist_ok=True)
        (receipts / f'{execution_id}.json').write_text(json.dumps(row))
        return row

    def test_retry_reuses_receipt_but_changed_snapshot_is_rejected(self):
        row, reused = self.create()
        self.assertFalse(reused)
        self.assertTrue(self.create(row['id'])[1])
        with self.assertRaises(ValueError):
            self.create(row['id'], 'different-snapshot')
        with self.assertRaises(ValueError):
            self.store.get(2, row['id'])

    def test_completed_result_survives_new_store_and_drops_input_sources(self):
        row, _ = self.create()
        self.store.claim()
        result = {'groups': [{'mediaIds': [1]}]}
        self.store.finish(row['id'], 'COMPLETED', result=result)
        restored = Store(self.root)
        restored.recover()
        saved = restored.get(1, row['id'])
        import json
        self.assertEqual(json.loads(saved['result']), result)
        self.assertEqual(saved['photos'], '[]')
        self.assertEqual(saved['state'], 'COMPLETED')
        self.assertFalse(list(self.root.rglob('*.tmp')))

    def test_restart_fails_interrupted_work_and_preserves_queue(self):
        running, _ = self.create()
        self.store.claim()
        queued, _ = self.create()
        restored = Store(self.root)
        restored.recover()
        self.assertEqual(restored.get(1, running['id'])['error_code'], 'SERVER_RESTARTED')
        self.assertEqual(restored.get(1, queued['id'])['state'], 'QUEUED')
        self.assertEqual(restored.claim()['id'], queued['id'])
        self.assertIsNone(restored.claim())

    def test_restart_recovers_published_response_before_parent_finish(self):
        row = self.legacy()
        result = {'groups': [{'groupId': 'person_001', 'mediaIds': [1],
                  'faceMembershipCount': 1, 'bodyAdditionCount': 0}],
                  'counts': {'groups': 1}, 'pipelineVersion': 'test',
                  'unassignedMediaIds': [], 'errors': []}
        directory = self.root / 'runs' / row['id']
        directory.mkdir(parents=True)
        temporary = directory / 'response.tmp'
        temporary.write_text(json.dumps(result))
        temporary.replace(directory / 'response.json')
        restored = Store(self.root)
        restored.recover()
        saved = restored.get(1, row['id'])
        self.assertEqual(saved['state'], 'COMPLETED')
        self.assertEqual(json.loads(saved['result']), result)
        self.assertEqual(saved['photos'], '[]')
        self.assertIsNone(saved['acknowledged_at'])
        self.assertIsNone(saved['error_code'])
        restored.recover()
        self.assertEqual(restored.get(1, row['id']), saved)

    def test_restart_rejects_incomplete_invalid_or_foreign_response(self):
        valid = {'groups': [], 'counts': {}, 'pipelineVersion': 'test',
                 'unassignedMediaIds': [1], 'errors': []}
        cases = [('response.tmp', json.dumps(valid)), ('response.json', '{'),
                 ('response.json', '[]'), ('response.json', '{"groups": []}'),
                 ('response.json', json.dumps({**valid, 'unassignedMediaIds': [999]})),
                 ('response.json', json.dumps({**valid, 'counts': {'photos': 'one'}})),
                 ('response.json', json.dumps({**valid, 'errors': [{'mediaId': 999, 'code': 'bad'}]}))]
        for filename, content in cases:
            with self.subTest(content=content, filename=filename):
                # Use an independent store so each case exercises first-start migration.
                self.root = Path(tempfile.mkdtemp(dir=self.temp.name))
                self.store = Store(self.root)
                row = self.legacy()
                directory = self.root / 'runs' / row['id']
                directory.mkdir(parents=True)
                (directory / filename).write_text(content)
                self.store.recover()
                saved = self.store.get(1, row['id'])
                self.assertEqual(saved['state'], 'FAILED')
                self.assertEqual(saved['error_code'], 'SERVER_RESTARTED')
                self.assertIsNone(saved['result'])

    def test_restart_read_error_preserves_running_receipt(self):
        row = self.legacy()
        original = Path.read_text
        def read(path, *args, **kwargs):
            if path.name == 'response.json':
                raise PermissionError('Cannot read result')
            return original(path, *args, **kwargs)
        with patch.object(Path, 'read_text', read), self.assertRaises(PermissionError):
            self.store.recover()
        self.assertEqual(self.store.rows(), [])
        self.assertEqual(json.loads((self.root / 'receipts' / f"{row['id']}.json").read_text())['state'], 'RUNNING')

    def test_unacknowledged_result_is_preserved_and_acknowledged_can_expire(self):
        row, _ = self.create()
        self.store.claim()
        self.store.finish(row['id'], 'COMPLETED', result={'groups': []})
        self.store.prune(0)
        self.assertIsNone(self.store.get(1, row['id'])['acknowledged_at'])
        self.store.acknowledge(row['id'], 'FAILED')
        self.assertIsNone(self.store.get(1, row['id'])['acknowledged_at'])
        self.store.acknowledge(row['id'], 'COMPLETED')
        self.store.prune(0)
        with self.assertRaises(ValueError):
            self.store.get(1, row['id'])

    def test_existing_artifacts_prevent_receipt_expiration(self):
        row, _ = self.create()
        self.store.claim()
        self.store.finish(row['id'], 'FAILED', error='ANALYSIS_FAILED')
        self.store.acknowledge(row['id'], 'FAILED')
        (self.root / 'runs' / row['id']).mkdir(parents=True)
        self.store.prune(0)
        self.assertEqual(self.store.get(1, row['id'])['state'], 'FAILED')
