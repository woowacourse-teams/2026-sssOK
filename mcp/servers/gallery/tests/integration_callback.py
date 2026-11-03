import json, tempfile, time, os, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from uuid import uuid4
from starlette.testclient import TestClient
from gallery_mcp.config import Settings
from gallery_mcp.person_grouping_service import PersonGroupingService
from gallery_mcp.server import create_app

received=[]
class Receiver(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_POST(self):
        assert self.path=='/internal/analysis-events'
        assert self.headers['Authorization']=='Bearer '+'b'*32
        payload=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        received.append(payload)
        terminal=[x for x in received if x['state']=='COMPLETED']
        if payload['state']=='COMPLETED' and len(terminal)==1:
            self.send_response(503);self.end_headers();return
        body=json.dumps({'accepted':True,'executionId':payload['executionId'],'state':payload['state']}).encode()
        self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)

receiver=ThreadingHTTPServer(('127.0.0.1',0),Receiver)
threading.Thread(target=receiver.serve_forever,daemon=True).start()
with tempfile.TemporaryDirectory(dir='/data') as tmp:
    url=f'http://127.0.0.1:{receiver.server_port}/internal/analysis-events'
    os.environ.update(GALLERY_DATA=tmp,GALLERY_TOKEN='m'*32,GALLERY_INPUT_ROOT='/input',GALLERY_ASSETS='/models',GALLERY_BACKEND_URL=url,GALLERY_BACKEND_TOKEN='b'*32)
    settings=Settings(data=Path(tmp),assets=Path('/models'),token='m'*32,input_root=Path('/input'),allowed_hosts=('testserver',),backend_url=url,backend_token='b'*32,standalone=False)
    service=PersonGroupingService(settings)
    headers={'Authorization':'Bearer '+settings.token,'Accept':'application/json, text/event-stream'}
    def call(client,name,args):
        response=client.post('/mcp',headers=headers,json={'jsonrpc':'2.0','id':1,'method':'tools/call','params':{'name':name,'arguments':args}})
        response.raise_for_status();result=response.json()['result']
        assert not result.get('isError'),result
        return result['structuredContent']
    started=time.monotonic()
    args={'room_id':1,'job_id':str(uuid4()),'request_id':str(uuid4()),'attempt':1,'photos':json.loads(Path('/photos.json').read_text())}
    with TestClient(create_app(settings,service)) as client:
        job=call(client,'group_photos_by_person',args)
        while job['state'] in ('QUEUED','RUNNING'):
            assert time.monotonic()-started<180
            time.sleep(2)
            job=call(client,'get_gallery_job',{'room_id':1,'execution_id':job['executionId']})
        assert job['state']=='COMPLETED',job
        for _ in range(15):
            if service.store.get(1,job['executionId'])['acknowledged_at']:break
            time.sleep(1)
        assert service.store.get(1,job['executionId'])['acknowledged_at']
        terminal=[x for x in received if x['state']=='COMPLETED']
        assert len(terminal)==2 and terminal[0]==terminal[1]
        assert terminal[-1]['jobId']==args['job_id']
        service.cleanup_finished()
        assert not (Path(tmp)/'runs'/job['executionId']).exists()
        assert (Path(tmp) / 'executions-v1.sqlite3').is_file()
        baseline=json.loads(Path('/baseline.json').read_text());baseline=baseline.get('result',baseline)
        groups=lambda result:sorted(sorted(g['mediaIds']) for g in result['groups'])
        assert groups(job['result'])==groups(baseline)
        assert call(client,'group_photos_by_person',args)['reused']
    restored=PersonGroupingService(settings)
    with TestClient(create_app(settings,restored)) as client:
        assert call(client,'get_gallery_job',{'room_id':1,'execution_id':job['executionId']})['result']==job['result']
    print(json.dumps({'counts':job['result']['counts'],'sameMemberships':True,'sqliteReceipts':True,'callbackRetryAndAck':True,'terminalEvents':len(terminal),'artifactsRemoved':True,'restartResultPreserved':True,'elapsedSeconds':round(time.monotonic()-started,2),'memoryPeakBytes':int(Path('/sys/fs/cgroup/memory.peak').read_text())}))
receiver.shutdown()
