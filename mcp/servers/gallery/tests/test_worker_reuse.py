"""Real subprocess lifecycle tests with lightweight analysis engines."""
import asyncio
from dataclasses import replace
import os
import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4
import gallery_mcp.person_grouping_service as service_module
from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService

ENGINE = '''import json, os, time
PID = os.getpid()
def run(source, output, *args):
    photo = next(source.iterdir())
    mode = photo.read_text()
    if mode == 'fail': raise RuntimeError('private URL must not escape')
    if mode == 'crash': os._exit(2)
    if mode == 'sleep': time.sleep(30)
    print('python output')
    os.write(1, b'native output\\n')
    output.mkdir()
    (output/'manifest.json').write_text(json.dumps({'photos':[
        {'image_id':'image_000001','source_filename':photo.name}]}))
    return {'groups':[], 'small_groups':[], 'counts':{'pid':PID},
            'pipeline_version':'stub','unassigned_image_ids':['image_000001'],'errors':[]}
'''

class WorkerReuseTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.inputs = self.root/'inputs'; self.inputs.mkdir()
        stub = self.root/'stubs'/'pipeline'; stub.mkdir(parents=True)
        (stub/'__init__.py').touch()
        for name in ('photo_analysis', 'similar_analysis'):
            (stub/f'{name}.py').write_text(ENGINE)
        self.settings = Settings(data=self.root/'data', assets=self.root/'models',
                                 token='t'*32, input_root=self.inputs)
        self.env = patch.dict(os.environ, {
            'GALLERY_DATA':str(self.settings.data), 'GALLERY_ASSETS':str(self.settings.assets),
            'PYTHONSAFEPATH':'1', 'GALLERY_TOKEN':'t'*32, 'GALLERY_STANDALONE':'true', 'GALLERY_INPUT_ROOT':str(self.inputs),
            'PYTHONPATH':os.pathsep.join([str(stub.parent), str(Path(service_module.__file__).resolve().parents[1])]),
        })
        self.env.start()
        self.service = PersonGroupingService(self.settings)

    async def asyncTearDown(self):
        await self.service.stop_worker()
        self.env.stop(); self.tmp.cleanup()

    async def run_job(self, media_id=1, mode='ok', kind='PERSON'):
        filename = f'{media_id}.jpg'; (self.inputs/filename).write_text(mode)
        result = self.service.start(1, str(uuid4()), [Photo(mediaId=media_id,source=filename,extension='jpg')], kind=kind)
        await self.service.execute(self.service.store.claim())
        return self.service.get(1, result['executionId'])

    async def test_reuses_process_and_separates_jobs_logs_and_kinds(self):
        first = await self.run_job(1); pid = self.service.process.pid
        second = await self.run_job(2, kind='SIMILAR')
        self.assertEqual(pid, self.service.process.pid)
        self.assertEqual(first['result']['counts']['pid'], second['result']['counts']['pid'])
        for result, media_id in ((first,1), (second,2)):
            self.assertEqual(result['state'], 'COMPLETED')
            self.assertEqual(result['result']['unassignedMediaIds'], [media_id])
            self.assertFalse((self.settings.data/'runs'/result['executionId']).exists())
            log = (self.settings.data/'logs'/f"{result['executionId']}.log").read_text()
            self.assertEqual(log.count('native output'), 1)
            self.assertEqual(log.count('python output'), 1)
        self.assertEqual(second['jobType'], 'SIMILAR')

    async def test_failure_and_crash_recycle_worker_then_next_job_succeeds(self):
        for mode in ('fail', 'crash'):
            result = await self.run_job(mode=mode)
            self.assertEqual(result['errorCode'], 'ANALYSIS_FAILED')
            self.assertIsNone(self.service.process)
            if mode == 'fail':
                log=(self.settings.data/'logs'/f"{result['executionId']}.log").read_text()
                self.assertNotIn('private URL', log)
            self.assertEqual((await self.run_job())['state'], 'COMPLETED')

    async def test_timeout_reaps_worker_and_next_job_succeeds(self):
        self.service.settings = replace(self.settings, timeout=1)
        result = await self.run_job(mode='sleep')
        self.assertEqual(result['errorCode'], 'ANALYSIS_TIMEOUT')
        self.assertIsNone(self.service.process)
        self.service.settings = self.settings
        self.assertEqual((await self.run_job())['state'], 'COMPLETED')

    async def test_job_limit_recycles_after_success(self):
        self.service.settings = replace(self.settings, worker_max_jobs=1)
        first = await self.run_job(); self.assertIsNone(self.service.process)
        second = await self.run_job()
        self.assertNotEqual(first['result']['counts']['pid'], second['result']['counts']['pid'])

    async def test_dispatch_idle_timeout_releases_worker(self):
        await self.run_job(); process = self.service.process
        self.service.settings = replace(self.settings, worker_idle_seconds=1)
        task = asyncio.create_task(self.service.dispatch())
        try:
            await asyncio.wait_for(process.wait(), timeout=4)
        finally:
            task.cancel()
            with self.assertRaises(asyncio.CancelledError): await task
        self.assertIsNone(self.service.process)

    async def test_shutdown_reaps_idle_worker(self):
        await self.run_job(); process = self.service.process
        task = asyncio.create_task(self.service.dispatch()); await asyncio.sleep(.05)
        task.cancel()
        with self.assertRaises(asyncio.CancelledError): await task
        self.assertIsNotNone(process.returncode)
        self.assertIsNone(self.service.process)

    async def test_cancellation_marks_running_job_failed(self):
        task = asyncio.create_task(self.run_job(mode='sleep'))
        while self.service.process is None: await asyncio.sleep(.01)
        task.cancel()
        with self.assertRaises(asyncio.CancelledError): await task
        self.assertIsNone(self.service.process)
        self.assertEqual(self.service.store.rows()[0]['error_code'], 'SERVER_STOPPED')

    async def test_protocol_cannot_publish_uncommitted_or_wrong_job(self):
        create = asyncio.create_subprocess_exec
        for reply, expected in [("{'id': request['id']}", 'WORKER_RESULT_NOT_COMMITTED'),
                                ("{'id': 'other-job'}", 'WORKER_START_OR_RESULT_FAILED')]:
            script = "import sys,json,time;request=json.loads(sys.stdin.readline());print(json.dumps(" + reply + "),flush=True);time.sleep(30)"
            async def spawn(*args, **kwargs):
                return await create(sys.executable, '-c', script, **kwargs)
            with patch('asyncio.create_subprocess_exec', spawn):
                result = await self.run_job()
            self.assertEqual(result['errorCode'], expected)
            self.assertIsNone(self.service.process)
        self.assertEqual((await self.run_job())['state'], 'COMPLETED')

    async def test_commit_survives_worker_exit_before_ack(self):
        create = asyncio.create_subprocess_exec
        script = '''import os
from gallery_mcp.analysis_worker import serve
import gallery_mcp.person_grouping_service as service_module
from gallery_mcp.config import Settings
from gallery_mcp.execution_store import Store
original = Store.complete
def complete(self, *args, **kwargs):
    original(self, *args, **kwargs)
    os._exit(7)
Store.complete = complete
serve(Settings.environment())
'''
        async def spawn(*args, **kwargs):
            return await create(sys.executable, '-c', script, **kwargs)
        with patch('asyncio.create_subprocess_exec', spawn):
            result = await self.run_job()
        self.assertEqual(result['state'], 'COMPLETED')
        self.assertEqual(result['result']['unassignedMediaIds'], [1])
        self.assertIsNone(self.service.process)
