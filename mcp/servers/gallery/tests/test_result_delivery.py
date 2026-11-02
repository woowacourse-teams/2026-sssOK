import asyncio
from dataclasses import replace
import json
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

import httpx

from gallery_mcp.config import Settings
from gallery_mcp.result_delivery import Delivery
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.execution_store import Store


class DeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.settings = Settings(data=self.root, assets=self.root / 'models', token='m'*32,
            download_hosts=('account.r2.cloudflarestorage.com',), backend_url='https://backend.test/internal/analysis-events',
            backend_token='b'*32, standalone=False)
        self.service = PersonGroupingService(self.settings)
        self.business = str(uuid4())
        self.execution = str(uuid4())
        self.photo = Photo(mediaId=1, source='https://account.r2.cloudflarestorage.com/bucket/p.jpg?signature=secret', extension='jpg')
        self.service.start(1, self.execution, [self.photo], job_id=self.business, attempt=2)
        self.service.store.claim()

    def tearDown(self):
        self.temp.cleanup()

    def complete(self):
        self.service.store.finish(self.execution, 'COMPLETED', result={'groups':[{'mediaIds':[1]}]})

    def tick(self, handler, delivery=None):
        async def run():
            async with httpx.AsyncClient(transport=httpx.MockTransport(handler), follow_redirects=False) as client:
                await (delivery or Delivery(self.service)).tick(client)
        asyncio.run(run())

    def test_result_delivered_with_identity_and_separate_token(self):
        self.complete()
        def handler(request):
            self.assertEqual(request.headers['Authorization'], 'Bearer '+'b'*32)
            payload = json.loads(request.content)
            self.assertEqual(payload['jobId'], self.business)
            self.assertEqual(payload['executionId'], self.execution)
            self.assertEqual(payload['attempt'], 2)
            self.assertNotIn('signature', request.content.decode())
            return httpx.Response(200, json={'accepted':True, 'executionId':self.execution,'state':'COMPLETED'})
        self.tick(handler)
        self.assertIsNotNone(self.service.store.get(1,self.execution)['acknowledged_at'])

    def test_failure_survives_restart_and_resends_identical_event(self):
        self.complete()
        events=[]
        def failed(request):
            events.append(json.loads(request.content))
            return httpx.Response(503)
        self.tick(failed)
        self.assertIsNone(self.service.store.get(1,self.execution)['acknowledged_at'])
        self.service=PersonGroupingService(self.settings)
        def succeeded(request):
            events.append(json.loads(request.content))
            return httpx.Response(200,json={'accepted':True,'executionId':self.execution,'state':'COMPLETED'})
        self.tick(succeeded)
        self.assertEqual(events[0],events[1])

    def test_committed_response_survives_restart_cleanup_and_delivery_retry(self):
        result = {'groups': [], 'counts': {'groups': 0}, 'pipelineVersion': 'test',
                  'unassignedMediaIds': [1], 'errors': []}
        directory = self.root / 'runs' / self.execution
        directory.mkdir(parents=True)
        worker_store = Store(self.root)
        self.assertTrue(worker_store.complete(1, self.execution, result))
        self.service = PersonGroupingService(self.settings)
        with self.service.boot():
            self.service.cleanup_finished()
            self.assertFalse(directory.exists())
            self.assertEqual(self.service.get(1, self.execution)['result'], result)
            self.tick(lambda request: httpx.Response(503))
            self.assertIsNone(self.service.store.get(1, self.execution)['acknowledged_at'])
        self.service = PersonGroupingService(self.settings)
        def accepted(request):
            payload = json.loads(request.content)
            self.assertEqual(payload['state'], 'COMPLETED')
            self.assertEqual(payload['result'], result)
            self.assertEqual(payload['jobId'], self.business)
            self.assertEqual(payload['attempt'], 2)
            return httpx.Response(200, json={'accepted': True,
                'executionId': self.execution, 'state': 'COMPLETED'})
        with self.service.boot():
            self.tick(accepted)
            self.assertIsNotNone(self.service.store.get(1, self.execution)['acknowledged_at'])

    def test_wrong_ack_and_redirect_do_not_discard_result(self):
        self.complete()
        for response in [httpx.Response(200,json={'accepted':True,'executionId':str(uuid4()),'state':'COMPLETED'}),
                         httpx.Response(302,headers={'Location':'https://other.test'}),
                         httpx.Response(200,json={'accepted':True,'executionId':self.execution,'state':'RUNNING'})]:
            self.tick(lambda request:response)
            self.assertIsNone(self.service.store.get(1,self.execution)['acknowledged_at'])

    def test_running_heartbeat_is_not_final_ack(self):
        self.tick(lambda request:httpx.Response(200,json={'accepted':True,'executionId':self.execution,'state':'RUNNING'}))
        self.assertIsNone(self.service.store.get(1,self.execution)['acknowledged_at'])

    def test_backoff_prevents_immediate_retry(self):
        self.complete()
        calls=[]
        delivery=Delivery(self.service)
        def handler(request):
            calls.append(1)
            return httpx.Response(503)
        self.tick(handler,delivery);self.tick(handler,delivery)
        self.assertEqual(len(calls),1)

    def test_acknowledged_receipt_expires_but_unacknowledged_is_preserved(self):
        self.complete()
        self.service.store.prune(0)
        self.assertEqual(len(self.service.store.rows()),1)
        self.service.store.acknowledge(self.execution,'COMPLETED')
        self.service.store.prune(0)
        self.assertEqual(self.service.store.rows(),[])
        self.assertTrue((self.root / Store.DATABASE).is_file())

    def test_identity_cannot_change_on_retry(self):
        with self.assertRaises(ValueError):
            self.service.start(1,self.execution,[self.photo],job_id=self.business,attempt=3)
        with self.assertRaises(ValueError):
            self.service.start(1,str(uuid4()),[self.photo],job_id=self.business,attempt=3)

    def test_undelivered_backlog_blocks_new_jobs(self):
        self.complete()
        service=PersonGroupingService(replace(self.settings,max_receipts=1))
        with self.assertRaises(ValueError):
            service.start(1,str(uuid4()),[self.photo],job_id=str(uuid4()))
        self.assertTrue(service.start(1,self.execution,[self.photo],job_id=self.business,attempt=2)['reused'])

    def test_missing_callback_or_unsafe_url_rejected(self):
        with self.assertRaises(ValueError):replace(self.settings,backend_url=None)
        for url in ['http://backend.test/events','https://user:pass@backend.test/events','https://backend.test/events?token=x']:
            with self.assertRaises(ValueError):replace(self.settings,backend_url=url)

    def test_invalid_callback_ports_and_urls_rejected_before_delivery(self):
        urls = [
            'https://backend.test:bad/events',
            'https://backend.test:65536/events',
            'https://backend.test:-1/events',
            'https://backend.test:0/events',
            'https://backend.test:/events',
            'https://[broken/events',
            'https://backend.test/\nevents',
            'https://user:private-secret@backend.test:bad/events',
        ]
        for url in urls:
            with self.subTest(url=url):
                with self.assertRaisesRegex(ValueError, '^Callback URL or port is invalid$') as error:
                    replace(self.settings, backend_url=url)
                self.assertNotIn(url, str(error.exception))
                self.assertTrue(error.exception.__suppress_context__)

    def test_valid_callback_urls_remain_deliverable(self):
        for url in ('https://backend.test/events', 'https://backend.test:8443/events',
                    'http://localhost:8080/events', 'http://127.0.0.1:8080/events'):
            with self.subTest(url=url), tempfile.TemporaryDirectory() as directory:
                settings = replace(self.settings, backend_url=url, data=Path(directory))
                service = PersonGroupingService(settings)
                calls = []
                def handler(request):
                    calls.append(str(request.url))
                    return httpx.Response(200, json={'accepted': True,
                        'executionId': self.execution, 'state': 'COMPLETED'})
                # Each endpoint gets an independent unacknowledged result.
                service.start(1, self.execution, [self.photo], job_id=self.business, attempt=2)
                service.store.claim()
                service.store.finish(self.execution, 'COMPLETED', result={'groups': []})
                self.tick(handler, Delivery(service))
                self.assertEqual(calls, [url])
                self.assertIsNotNone(service.store.get(1, self.execution)['acknowledged_at'])
