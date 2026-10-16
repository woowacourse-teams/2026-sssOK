#!/usr/bin/env python3
"""격리 Grafana의 등록·기존 UID 갱신·재생성 회귀 검사. 시험 자원만 정리한다."""
from pathlib import Path
import subprocess, json, os, time, uuid, tempfile
from deploy import Deployment
from validate import compose_config

# 후보 Compose와 같은 이미지·마운트로 검사하되 서버 네트워크·데이터 볼륨은 사용하지 않는다.
def run(args, **kw):
    return subprocess.run(args, text=True, capture_output=True, timeout=60, **kw)

service = compose_config()['services']['grafana']
mounts=[v for v in service['volumes'] if v['type']=='bind']
expected={}
for v in mounts:
 assert Path(v['source']).is_file(),v
 if v['target'].endswith('.json'):
  d=json.loads(Path(v['source']).read_text())
  assert d['uid'] not in expected
  expected[d['uid']]=d
assert len(expected)==5
print('Compose 통합: 5개 JSON 파일·고유 UID·마운트 확인',flush=True)
volume='sssok-dashboard-check-'+uuid.uuid4().hex[:12]
r=run(['docker','volume','create',volume]);assert r.returncode==0,r.stderr
cid=None

def start(provision):
 global cid
 args=['docker','run','-d','--network','none','--memory','256m','-v',f'{volume}:/var/lib/grafana']
 values=dict(service['environment'],GF_SECURITY_ADMIN_PASSWORD='isolated-test-only',GF_SERVER_ROOT_URL='http://localhost:3000/')
 for key,value in values.items():args+=['-e',f'{key}={value}']
 for v in mounts:
  if provision or '/datasources/' in v['target']:args+=['--mount',f"type=bind,src={v['source']},dst={v['target']},readonly"]
 r=run(args+[service['image']]);assert r.returncode==0,r.stderr
 cid=r.stdout.strip()
 for _ in range(60):
  r=api('GET','/api/health',check=False)
  if r.returncode==0:return
  time.sleep(.5)
 raise AssertionError('Grafana 기동 실패: '+run(['docker','logs','--tail','15',cid]).stderr)

def api(method,path,body=None,check=True):
 args=['docker','exec']
 if body is not None:args+=['-i']
 args += [cid,'curl','-fsS','--max-time','10','-u','admin:isolated-test-only','-X',method,'-H','Content-Type: application/json']
 if body is not None:args+=['--data-binary','@-']
 args+=['http://localhost:3000'+path]
 r=run(args,input=json.dumps(body) if body is not None else None)
 if check:assert r.returncode==0,(path,r.stdout,r.stderr)
 return r

def stop():
 global cid
 if cid:
  run(['docker','stop','--time','5',cid])
  removed=run(['docker','rm',cid]);assert removed.returncode==0,removed.stderr
  cid=None

def verify():
 for _ in range(30):
  data=json.loads(api('GET','/api/search?type=dash-db').stdout)
  if {d['uid'] for d in data}==set(expected):break
  time.sleep(.5)
 assert len(data)==5 and {d['uid'] for d in data}==set(expected),data
 ids={}
 for uid,d in expected.items():
  actual=json.loads(api('GET','/api/dashboards/uid/'+uid).stdout)
  assert actual['meta']['provisioned'] is True,uid
  assert actual['meta']['folderTitle']=='sssOK',actual['meta']
  assert actual['dashboard']['title']==d['title']
  assert len(actual['dashboard']['panels'])==len(d['panels'])
  ids[uid]=actual['dashboard']['id']
 return ids

backup = tempfile.TemporaryDirectory(prefix='sssok-grafana-backup-')
class IsolatedBackup(Deployment):
 def compose(self, *args, **kwargs):
  assert args == ('stop', 'grafana')
  return run(['docker', 'stop', '--time', '10', cid])
 def container(self, service_name):
  assert service_name == 'grafana'
  return cid
snapshot = IsolatedBackup(Path('.'), Path('.'), 'a' * 40)
snapshot.backup = Path(backup.name)
snapshot.grafana_image = service['image']
try:
 start(False)
 snapshot.grafana_volume = snapshot.data_volume(cid)
 first=next(iter(expected))
 old={'uid':first,'title':'기존 수동 등록 시험','schemaVersion':39,'panels':[]}
 imported=json.loads(api('POST','/api/dashboards/db',{'dashboard':old,'overwrite':False}).stdout)
 old_id=imported['id']
 snapshot.snapshot_grafana()
 stop()
 start(True)
 ids=verify()
 assert ids[first]==old_id,(ids[first],old_id)
 print('자동 등록: sssOK 폴더 5개, 기존 UID/DB ID 중복 없이 갱신 확인',flush=True)
 stop()
 start(True)
 assert verify()==ids
 print('같은 데이터 볼륨으로 재생성 후 5개 대시보드·DB ID 유지 확인',flush=True)
 snapshot.restore_grafana()
 stop()
 start(False)
 restored=json.loads(api('GET','/api/dashboards/uid/'+first).stdout)
 assert restored['dashboard']['title']==old['title']
 assert restored['dashboard']['id']==old_id
 assert not restored['meta']['provisioned']
 assert len(json.loads(api('GET','/api/search?type=dash-db').stdout))==1
 print('실제 Grafana 백업 복원 후 원래 수동 화면·DB ID 복구 확인',flush=True)
finally:
 stop()
 cleanup=run(['docker','volume','rm',volume])
 assert cleanup.returncode==0, cleanup.stderr
 backup.cleanup()
