from pathlib import Path
import subprocess,json,time
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--input',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
root=args.output.resolve();root.mkdir(parents=True,exist_ok=True)
repo=Path(__file__).resolve().parents[5]
tests=repo/'backend/build/native-image-test'
source=args.input.resolve()
for run,engines in [(1,['java','jni','cli']),(2,['cli','jni','java'])]:
 for c in [1,4]:
  for engine in engines:
   case=f'run{run}-{engine}-c{c}'
   out=root/case;out.mkdir(exist_ok=True)
   command=['docker','run','--rm','--network','none','--cpus','2','--memory','1536m','--memory-swap','1536m',
    '-v',f'{tests}:/tests:ro',
    '-v',f'{source}:/inputs:ro','-v',f'{out}:/output',
    'sssok-image-engine-bench:557','sh','-c',
    'java -XX:UseSVE=0 -Xmx512m -Djava.library.path=/native -cp "/tests/classes:/tests/lib/*" com.sssok.benchmark.ImageEngineBenchmark '
    +f'{engine} {c} /inputs /output && cat /sys/fs/cgroup/memory.peak > /output/memory-peak.txt && cat /sys/fs/cgroup/cpu.stat > /output/cpu-stat.txt']
   print('START',case,flush=True)
   with (out/'run.log').open('w') as log:
    subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,check=True,timeout=240)
   print('DONE',case,(out/'run.log').read_text().strip(),flush=True)
