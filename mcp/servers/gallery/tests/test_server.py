"""HTTP authentication and MCP tool integration."""
import tempfile
import unittest
from pathlib import Path
from uuid import uuid4

from starlette.testclient import TestClient

from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.server import create_app


class ServerTests(unittest.TestCase):
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

    def test_http_requires_auth_and_valid_origin_host(self):
        with TestClient(create_app(self.settings,self.service)) as client:
            self.assertEqual(client.get('/healthz').status_code,200)
            request={'jsonrpc':'2.0','id':1,'method':'initialize','params':{'protocolVersion':'2025-11-25','capabilities':{},'clientInfo':{'name':'tests','version':'1'}}}
            self.assertEqual(client.post('/mcp',json=request).status_code,401)
            headers={'Authorization':'Bearer '+self.settings.token,'Accept':'application/json, text/event-stream'}
            self.assertEqual(client.post('/mcp',json=request,headers=headers).status_code,200)
            self.assertEqual(client.post('/mcp',json=request,headers={**headers,'Origin':'https://evil.test'}).status_code,403)
            self.assertEqual(client.post('/mcp',json=request,headers={**headers,'Host':'evil.test'}).status_code,421)

    def test_mcp_returns_structured_completed_result(self):
        job=self.start();self.service.store.claim()
        self.service.store.finish(job['jobId'],'COMPLETED',result={'groups':[]})
        with TestClient(create_app(self.settings,self.service)) as client:
            headers={'Authorization':'Bearer '+self.settings.token,'Accept':'application/json, text/event-stream'}
            response=client.post('/mcp',headers=headers,json={'jsonrpc':'2.0','id':2,'method':'tools/call',
                'params':{'name':'get_gallery_job','arguments':{'room_id':1,'execution_id':job['executionId']}}})
            body=response.json()['result']
            self.assertEqual(body['structuredContent']['state'],'COMPLETED')
            self.assertEqual(body['structuredContent']['result'],{'groups':[]})

    def test_similar_tool_is_registered_and_records_type(self):
        with TestClient(create_app(self.settings,self.service)) as client:
            headers={'Authorization':'Bearer '+self.settings.token,'Accept':'application/json, text/event-stream'}
            listed=client.post('/mcp',headers=headers,json={'jsonrpc':'2.0','id':1,'method':'tools/list'}).json()
            self.assertIn('group_similar_photos',{t['name'] for t in listed['result']['tools']})
            result=client.post('/mcp',headers=headers,json={'jsonrpc':'2.0','id':2,'method':'tools/call',
                'params':{'name':'group_similar_photos','arguments':{'room_id':1,'job_id':str(uuid4()),
                    'request_id':self.request_id,'attempt':1,'photos':[self.photo.model_dump()]}}}).json()['result']
            self.assertFalse(result.get('isError'))
            self.assertEqual(result['structuredContent']['jobType'],'SIMILAR')
            self.assertEqual(self.service.store.get(1,self.request_id)['kind'],'SIMILAR')
