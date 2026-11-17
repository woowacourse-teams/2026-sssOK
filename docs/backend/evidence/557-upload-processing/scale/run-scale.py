"""Scale only the image count; keep engine, resources, photos and worker concurrency fixed."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--image', default='sssok-ffm-bench:557')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[5]
backend = repo / 'backend'
source, output = args.input.resolve(), args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
jar = output / 'vips-ffm-core-1.11.1.jar'
expected = '4ac3c787ed0a903e9ba9aa6899ffd5e2879669dab9a9a4a8d2ba6c778b9c2926'
if not jar.exists() or hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
    parser.error('Put the verified vips-ffm-core 1.11.1 jar in --output (see ../ffm/run-comparison.py)')
photos = sorted(source.glob('*.jpg'))
if len(photos) != 6:
    parser.error('Exactly six source JPEGs are required')
manifest = {'image': args.image, 'cpus': 2, 'memory_mib': 1536, 'heap_mib': 512,
            'concurrency': 4, 'sizes': [20, 100, 500], 'repeats': 3,
            'warmup_batches': 2, 'warmup_batch_size': 20,
            'input_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in photos},
            'ffm_sha256': expected, 'cases': [], 'identical_jni_ffm_pairs': 0}
base = ['docker', 'run', '--rm', '--network', 'none', '--cpus', '2', '--memory', '1536m',
        '--memory-swap', '1536m', '-e', 'VIPS_CONCURRENCY=1',
        '-v', f'{backend}:/backend:ro', '-v', f'{source}:/inputs:ro', '-v', f'{output}:/experiment']
cp = '/experiment/classes:/experiment/vips-ffm-core-1.11.1.jar:/backend/build/native-image-test/classes:/backend/build/native-image-test/lib/*'

def execute(command, log, timeout=600):
    with log.open('w') as stream:
        subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=timeout)

execute(base + [args.image, 'sh', '-c', f'javac -cp "{cp}" -d /experiment/classes '
        '/backend/src/test/java/com/sssok/benchmark/ImageEngineBenchmark.java '
        '/backend/src/test/ffm/LibvipsFfmImageProcessor.java'], output / 'compile.log')
execute(['docker', 'image', 'inspect', args.image], output / 'image.json')
execute(base + [args.image, 'sh', '-c', 'java -version\nvips --version\nuname -m'], output / 'environment.log')
# Reverse both count and engine order in the second independent process series.
for run, sizes, engines in [(1, [20, 100, 500], ['java', 'jni', 'ffm']),
                            (2, [500, 100, 20], ['ffm', 'jni', 'java'])]:
    for size in sizes:
        for engine in engines:
            name = f'run{run}-{engine}-n{size}'
            dest = output / name
            dest.mkdir(exist_ok=True)
            if (dest / 'run.log').exists():
                raise FileExistsError(f'Refusing to overwrite completed/partial measurements: {dest}')
            print('START', name, flush=True)
            command = f'java --enable-native-access=ALL-UNNAMED -XX:UseSVE=0 -Xmx512m -Djava.library.path=/native -cp "{cp}" '
            command += f'com.sssok.benchmark.ImageEngineBenchmark {engine} 4 /inputs /experiment/{name} 2 3 {size} 20'
            command += f' && cat /sys/fs/cgroup/memory.peak > /experiment/{name}/memory-peak.txt'
            command += f' && cat /sys/fs/cgroup/cpu.stat > /experiment/{name}/cpu-stat.txt'
            execute(base + [args.image, 'sh', '-c', command], dest / 'run.log')
            print('DONE', name, (dest / 'run.log').read_text().strip(), flush=True)
            manifest['cases'].append(name)
            (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        left, right = output / f'run{run}-jni-n{size}', output / f'run{run}-ffm-n{size}'
        pairs = list(left.glob('*.webp'))
        if len(pairs) != 12 or any(p.read_bytes() != (right / p.name).read_bytes() for p in pairs):
            raise AssertionError('JNI/FFM output bytes differ')
        manifest['identical_jni_ffm_pairs'] += len(pairs)
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
