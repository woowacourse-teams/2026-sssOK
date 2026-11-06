"""Temporary admission refusals carry a machine-readable retry flag; permanent ones do not."""
import asyncio
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from pathlib import Path
from uuid import uuid4

from mcp.server.fastmcp import FastMCP
from mcp.shared.memory import create_connected_server_and_client_session

from gallery_mcp.config import Settings
from gallery_mcp.execution_store import Store
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.rejection import RetryableRejection
from gallery_mcp.tools.people import register


class StubService:
    def __init__(self, error):
        self.error = error

    def start(self, *args, **kwargs):
        raise self.error


def call_start(error, tool='group_photos_by_person'):
    mcp = FastMCP('rejection-test')
    register(mcp, StubService(error))
    arguments = {'room_id': 1, 'job_id': str(uuid4()), 'request_id': str(uuid4()), 'attempt': 1,
                 'photos': [{'mediaId': 1, 'source': 'p.jpg', 'extension': 'jpg'}]}

    async def run():
        async with create_connected_server_and_client_session(mcp._mcp_server) as client:
            context = SimpleNamespace(request_context=SimpleNamespace(
                request=SimpleNamespace(scope={'gallery_environment':'dev'})))
            with patch.object(mcp, 'get_context', return_value=context):
                return await client.call_tool(tool, arguments)
    return asyncio.run(run())


class ToolContractTests(unittest.TestCase):
    def test_retryable_rejection_is_an_error_result_with_a_code(self):
        for tool in ('group_photos_by_person', 'group_similar_photos'):
            result = call_start(RetryableRejection('QUEUE_FULL', 'Analysis queue is full; retry later'), tool)
            self.assertTrue(result.isError)
            self.assertEqual(result.structuredContent, {'errorCode': 'QUEUE_FULL', 'retryable': True})
            self.assertIn('queue is full', result.content[0].text)

    def test_permanent_rejection_has_no_retry_flag(self):
        result = call_start(ValueError('Execution ID is already bound to another job or snapshot'))
        self.assertTrue(result.isError)
        self.assertIsNone(result.structuredContent)


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'input').mkdir()
        (self.root / 'input/p.jpg').write_bytes(b'test')
        self.settings = Settings(data=self.root / 'data', assets=self.root / 'models', token='t' * 32,
                                 input_root=self.root / 'input', allowed_hosts=('testserver',))
        self.service = PersonGroupingService(self.settings)
        self.photo = Photo(mediaId=1, source='p.jpg', extension='jpg')
        self.store = Store(self.root / 'store')

    def tearDown(self):
        self.tmp.cleanup()

    def create(self, request_id, max_queue=5, **options):
        return self.store.create(1, request_id, 'snapshot', [{'mediaId': 1}], max_queue, **options)

    def test_full_queue_is_retryable(self):
        self.create(str(uuid4()), max_queue=1)
        with self.assertRaises(RetryableRejection) as raised:
            self.create(str(uuid4()), max_queue=1)
        self.assertEqual(raised.exception.code, 'QUEUE_FULL')

    def test_full_delivery_backlog_is_retryable(self):
        self.create(str(uuid4()), max_receipts=1)
        with self.assertRaises(RetryableRejection) as raised:
            self.create(str(uuid4()), max_receipts=1)
        self.assertEqual(raised.exception.code, 'BACKLOG_FULL')

    def test_refused_execution_leaves_no_row_and_resending_is_safe(self):
        accepted, refused = str(uuid4()), str(uuid4())
        self.create(accepted, max_queue=1)
        with self.assertRaises(RetryableRejection):
            self.create(refused, max_queue=1)
        self.assertEqual([row['id'] for row in self.store.rows()], [accepted])
        self.store.claim()
        self.store.finish(accepted, 'FAILED', error='ANALYSIS_TIMEOUT')
        row, reused = self.create(refused, max_queue=1)
        self.assertFalse(reused)
        self.assertEqual(row['id'], refused)

    def test_resending_an_accepted_execution_is_not_refused_when_full(self):
        accepted = str(uuid4())
        self.create(accepted, max_queue=1)
        row, reused = self.create(accepted, max_queue=1)
        self.assertTrue(reused)
        self.assertEqual(row['id'], accepted)

    def test_conflict_with_an_active_execution_stays_permanent(self):
        first = str(uuid4())
        self.create(first)
        with self.assertRaises(ValueError) as raised:
            self.create(str(uuid4()), business_job_id=first)
        self.assertNotIsInstance(raised.exception, RetryableRejection)

    def test_unavailable_service_is_retryable(self):
        self.service.unavailable = True
        with self.assertRaises(RetryableRejection) as raised:
            self.service.start(1, str(uuid4()), [self.photo])
        self.assertEqual(raised.exception.code, 'UNAVAILABLE')

    def test_deployment_in_progress_is_retryable(self):
        (self.settings.data / 'draining').write_text('')
        with self.assertRaises(RetryableRejection) as raised:
            self.service.start(1, str(uuid4()), [self.photo])
        self.assertEqual(raised.exception.code, 'DEPLOYING')

    def test_locked_database_is_retryable_but_other_storage_errors_are_not(self):
        def locked(*args):
            raise sqlite3.OperationalError('database is locked')

        def broken(*args):
            raise sqlite3.OperationalError('no such table: executions')

        self.service._start = locked
        with self.assertRaises(RetryableRejection) as raised:
            self.service.start(1, str(uuid4()), [self.photo])
        self.assertEqual(raised.exception.code, 'STORAGE_BUSY')
        self.service._start = broken
        with self.assertRaises(sqlite3.OperationalError):
            self.service.start(1, str(uuid4()), [self.photo])


if __name__ == '__main__':
    unittest.main()
