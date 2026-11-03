import json
import tempfile
import unittest
from pathlib import Path
from uuid import uuid4
from unittest.mock import patch
from types import SimpleNamespace

from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.analysis_worker import execute


class SimilarExecutionTests(unittest.TestCase):
    def test_worker_converts_small_groups_errors_and_preserves_kind(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);inputs=root/'input';inputs.mkdir()
            (inputs/'a.jpg').write_bytes(b'photo')
            settings=Settings(data=root/'data',assets=root/'models',token='t'*32,input_root=inputs)
            service=PersonGroupingService(settings);eid=str(uuid4())
            photos=[Photo(mediaId=i,source='a.jpg',extension='jpg') for i in range(1,7)]
            service.start(1,eid,photos,kind='SIMILAR');service.store.claim()
            def analyze(source,output,*args):
                output.mkdir();(output/'manifest.json').write_text(json.dumps({'photos':[
                    {'image_id':f'image_{i:06d}','source_filename':photos[i-1].filename} for i in range(1,6)]}))
                return {'groups':[{'group_id':'g1','image_ids':[f'image_{i:06d}' for i in range(1,4)]}],
                        'small_groups':[['image_000004','image_000005']], 'unassigned_image_ids':[],
                        'errors':[{'image_id':'image_000006','reason':'decode'}], 'counts':{},'pipeline_version':'test'}
            with patch.dict('sys.modules',{'pipeline.similar_analysis':SimpleNamespace(run=analyze)}):execute(1,eid,settings)
            result=service.get(1,eid)
            self.assertEqual(result['jobType'],'SIMILAR')
            self.assertEqual(result['state'],'COMPLETED')
            self.assertEqual(result['result']['smallGroupMediaIds'],[[4,5]])
            self.assertEqual(result['result']['errors'],[{'mediaId':6,'code':'decode'}])


    def test_similar_delivery_retries_identically_after_restart(self):
        import asyncio
        import httpx
        from gallery_mcp.result_delivery import Delivery
        with tempfile.TemporaryDirectory() as path:
            settings=Settings(data=Path(path),assets=Path(path),token='t'*32,
                              backend_url='https://backend.test/events',backend_token='b'*32)
            service=PersonGroupingService(settings);eid=str(uuid4())
            service.store.create(1,eid,'snapshot',[{'mediaId':1}],8,kind='SIMILAR');service.store.claim()
            service.store.complete(1,eid,{'resultVersion':1,'groups':[],'smallGroupMediaIds':[],
                                       'unassignedMediaIds':[1],'counts':{},'pipelineVersion':'test','errors':[]})
            events=[]
            def handler(request):
                events.append(json.loads(request.content))
                return httpx.Response(503) if len(events)==1 else httpx.Response(200,json={
                    'accepted':True,'executionId':eid,'state':'COMPLETED'})
            async def tick(s):
                async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:await Delivery(s).tick(client)
            asyncio.run(tick(service));asyncio.run(tick(PersonGroupingService(settings)))
            self.assertEqual(events[0],events[1]);self.assertEqual(events[0]['jobType'],'SIMILAR')
            self.assertIsNotNone(service.store.get(1,eid)['acknowledged_at'])
