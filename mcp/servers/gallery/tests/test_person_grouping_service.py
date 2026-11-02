import asyncio
from dataclasses import replace
import json
from pathlib import Path
import sys
import tempfile
import unittest
from uuid import uuid4


from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService


class GalleryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root/'input').mkdir(); (self.root/'input/p.jpg').write_bytes(b'test')
        self.settings = Settings(data=self.root/'data', assets=self.root/'models',token='t'*32,
                                 input_root=self.root/'input',allowed_hosts=('testserver',))
        self.service = PersonGroupingService(self.settings)
        self.photo = Photo(mediaId=1,source='p.jpg',extension='jpg')
        self.request_id = str(uuid4())

    def tearDown(self):
        self.tmp.cleanup()

    def start(self, room=1, request_id=None, photos=None):
        return self.service.start(room,request_id or self.request_id,photos or [self.photo])

    def test_same_request_reuses_job(self):
        first=self.start();second=self.start()
        self.assertEqual(first['jobId'],second['jobId']);self.assertTrue(second['reused'])

    def test_deployment_blocks_admission_but_preserves_queries_and_queue(self):
        job = self.start()
        marker = self.settings.data / 'draining'
        marker.touch()
        with self.assertRaisesRegex(ValueError, 'deployment'):
            self.start(request_id=str(uuid4()))
        self.assertEqual(self.service.get(1, job['executionId'])['state'], 'QUEUED')
        self.assertIsNotNone(self.service.store.claim())
        marker.unlink()
        self.assertEqual(self.start(request_id=str(uuid4()))['state'], 'QUEUED')

    def test_request_cannot_change_snapshot(self):
        self.start()
        with self.assertRaises(ValueError):self.start(photos=[self.photo.model_copy(update={'mediaId':2})])

    def test_cross_room_result_hidden(self):
        job=self.start()
        with self.assertRaises(ValueError):self.service.get(2,job['jobId'])

    def test_duplicate_media_and_invalid_uuid_rejected(self):
        with self.assertRaises(ValueError):self.start(photos=[self.photo,self.photo])
        with self.assertRaises(ValueError):self.start(request_id='../invalid')

    def test_queue_bounded_and_idempotency_still_works(self):
        self.service=PersonGroupingService(replace(self.settings,max_queue=1))
        job=self.start()
        with self.assertRaises(ValueError):self.start(request_id=str(uuid4()))
        self.assertEqual(self.start()['jobId'],job['jobId'])

    def test_claim_does_not_claim_running_job(self):
        self.start();self.assertIsNotNone(self.service.store.claim());self.assertIsNone(self.service.store.claim())

    def test_restart_preserves_completed_and_queued(self):
        running=self.start();self.service.store.claim()
        queued=self.start(request_id=str(uuid4()))
        with self.service.boot():
            self.assertEqual(self.service.get(1,running['jobId'])['errorCode'],'SERVER_RESTARTED')
            self.assertEqual(self.service.get(1,queued['jobId'])['state'],'QUEUED')
        self.service.store.claim();self.service.store.finish(queued['jobId'],'COMPLETED',result={'groups':[]})
        with self.service.boot():self.assertEqual(self.service.get(1,queued['jobId'])['result'],{'groups':[]})

    def test_second_server_on_same_data_rejected(self):
        with self.service.boot():
            with self.assertRaises(RuntimeError):
                with PersonGroupingService(self.settings).boot():pass


    def test_public_status_never_exposes_urls_or_local_paths(self):
        result=self.start()
        serialized=json.dumps(result)
        self.assertNotIn(str(self.root),serialized);self.assertNotIn('p.jpg',serialized)

    def test_worker_failure_and_timeout_persist(self):
        async def run():
            for command,expected in [([sys.executable,'-c','raise SystemExit(1)'],'ANALYSIS_FAILED'),
                                     ([sys.executable,'-c','import time;time.sleep(5)'],'ANALYSIS_TIMEOUT')]:
                service=PersonGroupingService(replace(self.settings,timeout=1),command=lambda job:command)
                job=service.start(1,str(uuid4()),[self.photo]);claim=service.store.claim()
                await service.execute(claim)
                self.assertEqual(service.get(1,job['jobId'])['errorCode'],expected)
        asyncio.run(run())

    def test_whole_snapshot_over_limit_is_rejected_without_job(self):
        self.service = PersonGroupingService(replace(self.settings, max_photos=1))
        photos = [self.photo, self.photo.model_copy(update={'mediaId': 2})]
        with self.assertRaises(ValueError):
            self.service.start(1, self.request_id, photos)
        self.assertIsNone(self.service.store.claim())

    def test_success_persists_result_before_artifact_cleanup(self):
        job = self.start()
        directory = self.settings.data / 'runs' / job['jobId']
        directory.mkdir(parents=True)
        (directory / 'response.json').write_text(json.dumps({'groups': [{'mediaIds': [1]}]}))
        (directory / 'input').mkdir()
        (directory / 'input' / 'private.jpg').write_bytes(b'private')
        self.service.command = lambda job: [sys.executable, '-c', 'pass']
        asyncio.run(self.service.execute(self.service.store.claim()))
        self.assertFalse(directory.exists())
        result = self.service.get(1, job['jobId'])
        self.assertEqual(result['state'], 'COMPLETED')
        self.assertEqual(result['result']['groups'][0]['mediaIds'], [1])
        self.assertEqual(self.service.store.get(1, job['jobId'])['photos'], '[]')
        self.assertEqual(self.start()['jobId'], job['jobId'])

    def test_failed_and_timed_out_workers_remove_partial_files(self):
        for script in ['raise SystemExit(1)', 'import time; time.sleep(10)']:
            service = PersonGroupingService(replace(self.settings, timeout=1),
                command=lambda job: [sys.executable, '-c', script])
            job = service.start(1, str(uuid4()), [self.photo])
            directory = self.settings.data / 'runs' / job['jobId']
            directory.mkdir(parents=True)
            (directory / 'partial.jpg').write_bytes(b'private')
            asyncio.run(service.execute(service.store.claim()))
            self.assertFalse(directory.exists())
            self.assertEqual(service.get(1, job['jobId'])['state'], 'FAILED')

    def test_restart_cleanup_waits_for_orphan_worker_lock(self):
        import fcntl
        job = self.start()
        self.service.store.claim()
        directory = self.settings.data / 'runs' / job['jobId']
        directory.mkdir(parents=True)
        with (self.settings.data / 'analysis.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.service.boot():
                self.service.cleanup_finished()
                self.assertTrue(directory.exists())
        self.service.cleanup_finished()
        self.assertFalse(directory.exists())

    def test_cleanup_preserves_active_job(self):
        job = self.start()
        directory = self.settings.data / 'runs' / job['jobId']
        directory.mkdir(parents=True)
        self.service.cleanup_finished()
        self.assertTrue(directory.exists())


if __name__ == '__main__': unittest.main()
