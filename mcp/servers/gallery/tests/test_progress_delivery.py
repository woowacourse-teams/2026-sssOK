"""Running-progress delivery cadence, driven by a fake clock (no real waiting)."""
import asyncio
import json
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4

import httpx

from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.progress import ProgressFile, progress_path
from gallery_mcp.result_delivery import Delivery


class FakeClock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


class ProgressDeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.settings = Settings(data=self.root, assets=self.root / 'models', token='m' * 32,
            download_hosts=('account.r2.cloudflarestorage.com',),
            backend_url='https://backend.test/internal/analysis-events', backend_token='b' * 32, standalone=False)
        self.service = PersonGroupingService(self.settings)
        self.execution = str(uuid4())
        self.service.start(1, self.execution, [Photo(mediaId=1, extension='jpg',
            source='https://account.r2.cloudflarestorage.com/bucket/p.jpg?signature=secret')],
            job_id=str(uuid4()))
        self.service.store.claim()
        path = progress_path(self.root, self.execution)
        path.parent.mkdir(parents=True)
        self.recorder = ProgressFile(path)
        self.clock = FakeClock()
        self.delivery = Delivery(self.service, clock=self.clock)
        self.sent = []
        self.status = 200

    def handler(self, request):
        self.sent.append(json.loads(request.content))
        if self.status != 200:
            return httpx.Response(self.status)
        state = self.sent[-1]['state']
        return httpx.Response(200, json={'accepted': True, 'executionId': self.execution, 'state': state})

    def tick(self, advance=0):
        self.clock.now += advance

        async def run():
            async with httpx.AsyncClient(transport=httpx.MockTransport(self.handler)) as client:
                await self.delivery.tick(client)
        asyncio.run(run())

    def progress_of(self, index):
        return self.sent[index]['progress']

    def test_first_running_event_is_sent_even_before_any_progress_is_recorded(self):
        self.tick()
        self.assertEqual(len(self.sent), 1)
        self.assertEqual(self.progress_of(0)['stage'], 'downloading_photos')

    def test_consecutive_changes_in_one_stage_are_coalesced_to_the_latest_every_two_seconds(self):
        self.recorder.write('detecting_faces', 0, 60)
        self.tick()
        for processed, advance in [(1, .5), (2, .5), (3, .5)]:
            self.recorder.write('detecting_faces', processed, 60)
            self.tick(advance)
        self.assertEqual(len(self.sent), 1)  # 1.5 seconds after the first send
        self.recorder.write('detecting_faces', 4, 60)
        self.tick(.5)  # 2.0 seconds
        self.assertEqual(len(self.sent), 2)
        self.assertEqual(self.progress_of(1)['processed'], 4)

    def test_stage_change_is_sent_on_the_next_loop_without_waiting_for_the_interval(self):
        self.recorder.write('detecting_faces', 10, 60)
        self.tick()
        self.recorder.write('grouping_faces')
        self.tick(.5)
        self.assertEqual(len(self.sent), 2)
        self.assertEqual(self.progress_of(1),
                         {'stage': 'grouping_faces', 'processed': None, 'total': None, 'unit': None})

    def test_unchanged_progress_is_only_a_liveness_signal_every_fifteen_seconds(self):
        self.recorder.write('detecting_faces', 10, 60)
        self.tick()
        self.tick(2)
        self.tick(12.9)  # 14.9 seconds
        self.assertEqual(len(self.sent), 1)
        self.tick(.1)  # 15.0 seconds
        self.assertEqual(len(self.sent), 2)
        self.assertEqual(self.progress_of(1), self.progress_of(0))

    def test_elapsed_time_alone_never_counts_as_a_change(self):
        self.recorder.write('detecting_faces', 10, 60)
        self.tick()
        for _ in range(10):
            self.tick(1)  # ten seconds pass, nothing recorded
        self.assertEqual(len(self.sent), 1)

    def test_completion_is_sent_immediately_regardless_of_the_progress_interval(self):
        self.recorder.write('detecting_faces', 10, 60)
        self.tick()
        self.service.store.finish(self.execution, 'COMPLETED', result={'groups': [{'mediaIds': [1]}]})
        self.tick(.1)
        self.assertEqual([event['state'] for event in self.sent], ['RUNNING', 'COMPLETED'])
        self.assertEqual(self.sent[1]['progress'], {})

    def test_failure_is_sent_immediately_and_resent_until_acknowledged(self):
        self.tick()
        self.service.store.finish(self.execution, 'FAILED', error='ANALYSIS_FAILED')
        self.status = 503
        self.tick(.1)
        self.status = 200
        self.tick(2)  # first retry delay is two seconds
        self.assertEqual([event['state'] for event in self.sent], ['RUNNING', 'FAILED', 'FAILED'])
        self.assertIsNotNone(self.service.store.get(1, self.execution)['acknowledged_at'])

    def test_stage_change_does_not_bypass_the_retry_backoff_after_a_failed_send(self):
        self.recorder.write('detecting_faces', 10, 60)
        self.status = 503
        self.tick()
        self.assertEqual(len(self.sent), 1)
        for stage in ('grouping_faces', 'detecting_bodies', 'extracting_body_features'):
            self.recorder.write(stage)
            self.tick(.2)  # each stage change arrives inside the two-second backoff
        self.assertEqual(len(self.sent), 1)
        self.status = 200
        self.tick(2)
        self.assertEqual(len(self.sent), 2)  # one coalesced request with the latest stage
        self.assertEqual(self.progress_of(1)['stage'], 'extracting_body_features')

    def test_backoff_grows_while_the_backend_keeps_failing(self):
        self.status = 503
        self.tick()          # failure 1: retry in 2 seconds
        self.tick(2)         # failure 2: retry in 4 seconds
        self.tick(3.9)
        self.assertEqual(len(self.sent), 2)
        self.tick(.1)
        self.assertEqual(len(self.sent), 3)
