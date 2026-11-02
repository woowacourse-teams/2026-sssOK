"""Essential task failure must stop admission without terminating the server."""
import asyncio
from dataclasses import replace
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
from uuid import uuid4

from starlette.testclient import TestClient
from gallery_mcp.config import Settings
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.server import create_app

async def broken():
    await asyncio.sleep(0.02)
    raise RuntimeError('private-detail-must-not-be-logged')

async def returned():
    await asyncio.sleep(0.02)

class SupervisionTests(unittest.TestCase):
    def test_failures_and_unexpected_return_stop_admission(self):
        for target, failure in [('dispatch', broken), ('dispatch', returned), ('delivery', broken)]:
            with self.subTest(target=target, failure=failure.__name__), tempfile.TemporaryDirectory() as root:
                root = Path(root)
                settings = Settings(data=root/'data', assets=root/'models', token='t'*32)
                settings.assets.mkdir()
                for name in ('yunet.onnx','sface.onnx','ssdlite.pth','osnet.pth'):
                    (settings.assets/name).touch()
                if target == 'delivery':
                    settings = replace(settings, backend_url='http://127.0.0.1/events', backend_token='b'*32)
                service = PersonGroupingService(settings)
                context = patch.object(service, 'dispatch', failure) if target == 'dispatch' else patch(
                    'gallery_mcp.server.Delivery.run', lambda self: failure())
                with context, TestClient(create_app(settings, service)) as client:
                    deadline = time.monotonic() + 3
                    while not service.unavailable and time.monotonic() < deadline:
                        time.sleep(0.01)
                    self.assertTrue(service.unavailable)
                    self.assertEqual(client.get('/healthz').status_code, 503)
                    self.assertEqual(client.get('/readyz').status_code, 503)
                    with self.assertRaisesRegex(ValueError, 'unavailable'):
                        service.start(1, str(uuid4()), [])
                    self.assertEqual(service.store.rows(), [])

    def test_normal_shutdown_has_no_failure_log(self):
        with tempfile.TemporaryDirectory() as root:
            settings = Settings(data=Path(root)/'data', assets=Path(root)/'models', token='t'*32)
            with self.assertNoLogs('gallery_mcp.server', level='ERROR'):
                with TestClient(create_app(settings)) as client:
                    self.assertEqual(client.get('/healthz').status_code, 200)
