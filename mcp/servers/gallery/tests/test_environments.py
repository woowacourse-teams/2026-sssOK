"""Shared MCP: authenticated environment binding survives delivery/restarts."""
import asyncio
from dataclasses import replace
import fcntl
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4

import httpx
from starlette.testclient import TestClient

from gallery_mcp.config import Settings
from gallery_mcp.execution_store import Store
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.result_delivery import Delivery
from gallery_mcp.server import create_app


class EnvironmentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.routes = {env: dict(token=token * 32, backend_url=f'https://{env}.test/events',
                                backend_token=callback * 32)
                       for env, token, callback in [('dev', 'd', 'a'), ('prod', 'p', 'b')]}
        self.settings = Settings(data=self.root/'data', assets=self.root/'models', token='',
            routes=self.routes, standalone=False, input_root=self.root, allowed_hosts=('testserver',))
        (self.root/'image.jpg').write_bytes(b'test')
        self.photo = Photo(mediaId=1, source='image.jpg', extension='jpg')
        self.service = PersonGroupingService(self.settings)

    def test_http_token_binds_environment_and_blocks_cross_environment_reads(self):
        async def idle():
            await asyncio.Event().wait()
        self.service.dispatch = idle
        with TestClient(create_app(self.settings, self.service)) as client:
            def call(env, name, arguments):
                return client.post('/mcp', headers={'Authorization':'Bearer '+self.routes[env]['token'],
                    'Accept':'application/json, text/event-stream'}, json={'jsonrpc':'2.0','id':1,
                    'method':'tools/call','params':{'name':name,'arguments':arguments}}).json()['result']
            jobs = {}
            business = str(uuid4())
            for env in ('dev','prod'):
                execution = str(uuid4()); jobs[env] = execution
                result = call(env, 'group_similar_photos', dict(room_id=1, job_id=business,
                    request_id=execution, attempt=1, photos=[self.photo.model_dump()]))
                self.assertFalse(result.get('isError'), result)
                self.assertEqual(self.service.store.get(1,execution)['environment'], env)
            for env, other in [('dev','prod'),('prod','dev')]:
                self.assertTrue(call(env,'get_gallery_job',dict(room_id=1,execution_id=jobs[other]))['isError'])
                self.assertFalse(call(env,'get_gallery_job',dict(room_id=1,execution_id=jobs[env])).get('isError'))
                reused = call(env,'group_similar_photos',dict(room_id=1,job_id=business,
                    request_id=jobs[other],attempt=1,photos=[self.photo.model_dump()]))
                self.assertTrue(reused['isError'])
            # A caller-supplied environment is not a tool parameter and cannot choose a route.
            listed = client.post('/mcp', headers={'Authorization':'Bearer '+'d'*32,
                'Accept':'application/json, text/event-stream'}, json={'jsonrpc':'2.0','id':2,'method':'tools/list'}).json()
            for tool in listed['result']['tools']:
                self.assertNotIn('environment', tool['inputSchema']['properties'])
            self.assertEqual(client.post('/mcp', headers={'Authorization':'Bearer '+'x'*32},json={}).status_code,401)

    def test_delivery_retry_and_restart_preserve_each_destination_and_token(self):
        expected = {}
        for env in ('dev','prod'):
            execution = str(uuid4())
            self.service.start(1,execution,[self.photo],environment=env)
            self.service.store.claim()
            self.service.store.finish(execution,'COMPLETED',result={'groups':[]})
            expected[execution] = env
        requests = []
        def handle(request, success):
            payload = json.loads(request.content); env = expected[payload['executionId']]
            self.assertEqual(str(request.url),self.routes[env]['backend_url'])
            self.assertEqual(request.headers['Authorization'],'Bearer '+self.routes[env]['backend_token'])
            requests.append((env,payload['eventId']))
            return httpx.Response(200,json={'accepted':True,'executionId':payload['executionId'],
                'state':payload['state']}) if success else httpx.Response(503)
        async def tick(service,success):
            async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r:handle(r,success))) as client:
                await Delivery(service).tick(client)
        asyncio.run(tick(self.service,False))
        restored = PersonGroupingService(self.settings)
        asyncio.run(tick(restored,True))
        self.assertEqual(requests[:2],requests[2:])
        self.assertTrue(all(row['acknowledged_at'] for row in restored.store.rows()))

    def test_missing_route_never_falls_back_to_dev(self):
        execution = str(uuid4())
        self.service.start(1,execution,[self.photo],environment='prod')
        self.service.store.claim(); self.service.store.finish(execution,'COMPLETED',result={'groups':[]})
        legacy = PersonGroupingService(replace(self.settings,routes=None,token='d'*32,
            backend_url='https://dev.test/events',backend_token='a'*32))
        async def tick():
            async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r:self.fail('Must not send PROD to DEV'))) as client:
                await Delivery(legacy).tick(client)
        asyncio.run(tick())
        self.assertIsNone(legacy.store.get(1,execution)['acknowledged_at'])

    def test_route_validation_rejects_ambiguous_tokens_and_untrusted_urls(self):
        for change in ({'token':'d'*32},{'backend_token':'a'*32},{'backend_url':'http://external.test/events'},
                       {'backend_url':'https://prod.test/events?token=secret'}):
            routes = {k:dict(v) for k,v in self.routes.items()}; routes['prod'].update(change)
            with self.assertRaises(ValueError): replace(self.settings,routes=routes)
        with self.assertRaises(ValueError): replace(self.settings,routes={'dev':self.routes['dev']})

    def test_environment_file_overrides_legacy_settings_and_rejects_invalid_files(self):
        path = self.root/'environments.json'
        path.write_text(json.dumps(self.routes))
        env = {'GALLERY_ENVIRONMENTS_FILE':str(path),
               'GALLERY_TOKEN_FILE':'/missing/legacy-token',
               'GALLERY_BACKEND_TOKEN_FILE':'/missing/legacy-callback',
               'GALLERY_BACKEND_URL':'http://invalid-legacy.test'}
        with patch.dict('os.environ',env,clear=True):
            settings = Settings.environment()
            self.assertEqual(settings.credentials(),{'dev':'d'*32,'prod':'p'*32})
            self.assertEqual(settings.callback('prod'),('https://prod.test/events','b'*32))
            for invalid in ('null','[]','{}','not-json'):
                path.write_text(invalid)
                with self.assertRaises(ValueError): Settings.environment()

    def test_v2_receipts_migrate_to_dev_and_migration_refuses_running_server(self):
        execution = str(uuid4()); self.service.start(1,execution,[self.photo])
        with sqlite3.connect(self.service.store.database) as db:
            db.execute('DROP INDEX active_business_job')
            db.execute('ALTER TABLE executions DROP COLUMN environment')
            db.execute('PRAGMA user_version=2')
        with (self.settings.data/'server.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(RuntimeError): Store(self.settings.data)
            with sqlite3.connect(self.service.store.database) as db:
                self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],2)
        migrated = Store(self.settings.data)
        self.assertEqual(migrated.get(1,execution)['environment'],'dev')
        with sqlite3.connect(migrated.database) as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],3)
