"""JSON receipts retain results independently of the MCP server process."""
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

from gallery_mcp.execution_store import Store


class ExecutionStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.store = Store(self.root)

    def create(self, request_id=None, fingerprint='snapshot'):
        return self.store.create(1, request_id or str(uuid4()), fingerprint,
                                 [{'mediaId': 1, 'source': 'private-url'}], 8)

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
