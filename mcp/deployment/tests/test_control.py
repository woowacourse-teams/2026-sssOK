import importlib.util
import json
import sqlite3
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('control', Path(__file__).resolve().parents[1] / 'control.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ControlTests(unittest.TestCase):
    def database(self, root, state):
        database = root / 'executions-v1.sqlite3'
        with sqlite3.connect(database) as db:
            db.execute('CREATE TABLE executions(state TEXT, result TEXT, acknowledged_at TEXT)')
            db.execute('INSERT INTO executions VALUES (?, ?, NULL)', (state, '{"groups": []}'))
            db.execute('CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT)')
            db.execute("INSERT INTO metadata VALUES ('json_imported','1')")
            db.execute('PRAGMA user_version=1')
        return database

    def test_active_work_keeps_gate_closed_on_timeout(self):
        for state in ('QUEUED', 'RUNNING'):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'receipts').mkdir()
                (root / 'receipts/job.json').write_text(json.dumps({'state': state}))
                settings = SimpleNamespace(data=root)
                with self.assertRaises(TimeoutError):
                    module.control('drain', settings, timeout=0)
                self.assertTrue((root / 'draining').exists())
                module.control('resume', settings)
                self.assertFalse((root / 'draining').exists())

    def test_pending_delivery_does_not_prevent_deployment_or_get_deleted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'receipts').mkdir()
            receipt = root / 'receipts/job.json'
            receipt.write_text(json.dumps({'state': 'COMPLETED', 'acknowledged_at': None}))
            original = receipt.read_bytes()
            module.control('drain', SimpleNamespace(data=root), timeout=0)
            self.assertTrue((root / 'draining').exists())
            self.assertEqual(receipt.read_bytes(), original)

    def test_sqlite_active_work_blocks_drain(self):
        for state in ('QUEUED', 'RUNNING'):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.database(root, state)
                with self.assertRaises(TimeoutError):
                    module.control('drain', SimpleNamespace(data=root), timeout=0)
                self.assertTrue((root / 'draining').exists())

    def test_sqlite_pending_result_is_preserved_and_legacy_backup_is_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = self.database(root, 'COMPLETED')
            (root / 'receipts').mkdir()
            (root / 'receipts/old.json').write_text('{"state":"RUNNING"}')
            module.control('drain', SimpleNamespace(data=root), timeout=0)
            with sqlite3.connect(database) as db:
                self.assertEqual(db.execute('SELECT * FROM executions').fetchone(),
                                 ('COMPLETED', '{"groups": []}', None))

    def test_unreadable_database_does_not_report_drained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'executions-v1.sqlite3').write_text('not a database')
            with self.assertRaises(sqlite3.DatabaseError):
                module.control('drain', SimpleNamespace(data=root), timeout=0)
