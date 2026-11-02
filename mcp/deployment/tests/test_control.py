import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('control', Path(__file__).resolve().parents[1] / 'control.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ControlTests(unittest.TestCase):
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
