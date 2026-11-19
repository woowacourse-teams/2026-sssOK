"""Offline benchmark: fresh workers versus sequential reuse, with normal artifact cleanup."""
import argparse
import asyncio,json,os,sys,time,subprocess
from pathlib import Path
from uuid import uuid4
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from gallery_mcp.config import Settings
from gallery_mcp.inputs import Photo
from gallery_mcp.person_grouping_service import PersonGroupingService
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--input',type=Path,action='append',required=True)
parser.add_argument('--assets',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
OUT=args.output.resolve()
OUT.mkdir(parents=True,exist_ok=False)
# The subprocess receives only local standalone settings, never service credentials.
for key in list(os.environ):
 if key.startswith('GALLERY_'):del os.environ[key]
def rss(pid):
 try:
  if sys.platform=='linux':
   return int(next(line.split()[1] for line in Path(f'/proc/{pid}/status').read_text().splitlines() if line.startswith('VmRSS:')))*1024
  return int(subprocess.check_output(['ps','-o','rss=','-p',str(pid)],stderr=subprocess.DEVNULL).strip())*1024
 except (OSError,StopIteration,ValueError,subprocess.CalledProcessError):return 0
def cgroup():
 try:
  root=Path('/sys/fs/cgroup')
  mem=dict(line.split() for line in (root/'memory.stat').read_text().splitlines())
  return int((root/'memory.current').read_text()),int(mem['anon'])
 except (OSError,ValueError):return 0,0
def save_json(path,value):
 temporary=path.with_suffix('.tmp')
 with temporary.open('w') as stream:
  json.dump(value,stream,indent=2);stream.flush();os.fsync(stream.fileno())
 temporary.replace(path)
reports=[]
async def main():
 for index,source in enumerate(args.input):
  inputs=source.resolve();album=f'{index}-{inputs.name}'
  data=OUT/f'{album}-{uuid4().hex}'
  settings=Settings(data=data,assets=args.assets.resolve(),token='t'*32,input_root=inputs)
  os.environ.update(GALLERY_DATA=str(data),GALLERY_ASSETS=str(settings.assets),GALLERY_TOKEN='t'*32,GALLERY_STANDALONE='true',GALLERY_INPUT_ROOT=str(inputs),GALLERY_THREADS='2',PYTHONPATH=str(ROOT))
  photos=[Photo(mediaId=i+1,source=p.name,extension=p.suffix[1:].lower()) for i,p in enumerate(sorted(p for p in inputs.iterdir() if p.suffix.lower() in ('.jpg','.webp','.jpeg','.png')))]
  service=PersonGroupingService(settings);baseline=None
  # Preserve result JSON, then exercise normal service artifact cleanup.
  original_cleanup=service.cleanup_finished
  captured={}
  def cleanup():
   runs=data/'runs'
   if runs.exists():
    for directory in runs.iterdir():
     result=directory/'analysis/result.json'
     if result.is_file():
      raw=json.loads(result.read_text());captured[directory.name]=raw
      save_json(OUT/f'{directory.name}-result.json',raw)
   original_cleanup()
  service.cleanup_finished=cleanup
  try:
   for mode,repeats in [('fresh',3),('reuse',6)]:
    service.command=(lambda job:[sys.executable,'-m','gallery_mcp.analysis_worker',str(job['room_id']),job['id']]) if mode=='fresh' else None
    for i in range(repeats):
     submitted=service.start(1,str(uuid4()),photos);job=service.store.claim();peak=0;cgroup_peak=0;anon_peak=0;pids=set();stop=asyncio.Event()
     async def sample():
      nonlocal peak,cgroup_peak,anon_peak
      while not stop.is_set():
       process=service.process
       if process and process.returncode is None:
        pids.add(process.pid)
        peak=max(peak,rss(process.pid))
       current,anon=cgroup();cgroup_peak=max(cgroup_peak,current);anon_peak=max(anon_peak,anon)
       await asyncio.sleep(.1)
     watcher=asyncio.create_task(sample());t=time.perf_counter()
     await service.execute(job);elapsed=time.perf_counter()-t;stop.set();await watcher
     state=service.get(1,job['id']);assert state['state']=='COMPLETED',state['errorCode']
     raw=captured.pop(job['id'])
     canonical={'groups':raw['groups'],'counts':raw['counts'],'unassigned':raw['unassigned_image_ids'],'errors':raw['errors']}
     if baseline is None:baseline=canonical
     idle=0
     if service.process:
      idle=rss(service.process.pid)
     r={'album':album,'photos':len(photos),'mode':mode,'repeat':i,'seconds':elapsed,'peak_worker_rss_sampled':peak,'idle_worker_rss':idle,'pids':sorted(pids),'sampled_container_peak':cgroup_peak,'sampled_anon_peak':anon_peak,'container_memory_current':cgroup()[0],'same_full_result':canonical==baseline,'pipeline_version':raw['pipeline_version'],'counts':raw['counts']}
     reports.append(r);save_json(OUT/'results.json',reports);print(json.dumps(r),flush=True)
     assert r['same_full_result'],'Classification changed'
    await service.stop_worker()
  finally:await service.stop_worker()
asyncio.run(main())
