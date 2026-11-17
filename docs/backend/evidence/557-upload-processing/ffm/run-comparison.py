"""Run from any directory; originals and generated artifacts stay outside Git."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--image25', default='sssok-ffm-comparison:557')
parser.add_argument('--image21', default='sssok-jni-comparison:557')
parser.add_argument('--skip-build', action='store_true')
parser.add_argument('--check-only', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[5]
backend = repo / 'backend'
tests = backend / 'build/native-image-test'
source, output = args.input.resolve(), args.output.resolve()
if len(list(source.glob('*.jpg'))) != 6:
    parser.error('--input must contain exactly six .jpg files')
if not (tests / 'classes').is_dir():
    parser.error('Run ./gradlew prepareNativeImageTest in backend first')
output.mkdir(parents=True, exist_ok=True)


def execute(command, log, timeout=600):
    print('RUN', log.name, flush=True)
    with log.open('w') as stream:
        subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=timeout)


if not args.skip_build:
    for target, tag in [('jni21', args.image21), ('ffm25', args.image25)]:
        execute(['docker', 'build', '-f', str(backend / 'src/test/native/Dockerfile'),
                 '--target', target, '-t', tag, str(backend)], output / f'build-{target}.log')

jar = output / 'vips-ffm-core-1.11.1.jar'
if not jar.exists():
    urllib.request.urlretrieve(
        'https://repo.maven.apache.org/maven2/com/lopcode/vips-ffm/vips-ffm-core/1.11.1/vips-ffm-core-1.11.1.jar', jar)
expected = '4ac3c787ed0a903e9ba9aa6899ffd5e2879669dab9a9a4a8d2ba6c778b9c2926'
if hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
    raise ValueError('FFM artifact SHA-256 differs from the measured version')

base = ['docker', 'run', '--rm', '--network', 'none', '--cpus', '2',
        '--memory', '1536m', '--memory-swap', '1536m', '-e', 'VIPS_CONCURRENCY=1',
        '-v', f'{tests}:/tests:ro', '-v', f'{backend / "src/test/ffm"}:/ffm:ro',
        '-v', f'{source}:/inputs:ro', '-v', f'{output}:/experiment']
cp = '/experiment/classes:/experiment/vips-ffm-core-1.11.1.jar:/tests/classes:/tests/lib/*'
java = ['java', '--enable-native-access=ALL-UNNAMED', '-XX:UseSVE=0', '-Xmx512m',
        '-Djava.library.path=/native', '-cp', cp]
execute(base + [args.image25, 'sh', '-c',
                f'javac -cp "{cp}" -d /experiment/classes /ffm/*.java'], output / 'compile-ffm.log')
execute(base + [args.image25] + java + ['com.sssok.benchmark.FfmComparisonRunner'], output / 'contracts.log')
for tag, label in [(args.image25, 'jdk25'), (args.image21, 'jdk21')]:
    execute(base + [tag, 'sh', '-c', 'java -version\nvips --version\nuname -m\napk list --installed --no-network vips libwebp'],
            output / f'environment-{label}.log')
    execute(['docker', 'image', 'inspect', tag], output / f'image-{label}.json')
if args.check_only:
    raise SystemExit(0)

manifest = {'inputs': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(source.glob('*.jpg'))},
            'ffm_jar_sha256': expected, 'cases': [], 'byte_identical_pairs': 0}
for run, engines in [(1, ['jni', 'ffm']), (2, ['ffm', 'jni']),
                     (3, ['jni', 'ffm']), (4, ['ffm', 'jni'])]:
    for concurrency in ([1, 4] if run <= 2 else [4]):
        for engine in engines:
            name = f'run{run}-{engine}-c{concurrency}'
            dest = output / name
            dest.mkdir(exist_ok=True)
            # Shell arguments are fixed or generated integers/engine names; no user path is interpolated.
            command = ' '.join(java[:java.index('-cp')]) + f' -cp "{cp}" '
            command += f'com.sssok.benchmark.ImageEngineBenchmark {engine} {concurrency} /inputs /experiment/{name} 2 5'
            command += f' && cat /sys/fs/cgroup/memory.peak > /experiment/{name}/memory-peak.txt'
            command += f' && cat /sys/fs/cgroup/cpu.stat > /experiment/{name}/cpu-stat.txt'
            execute(base + [args.image25, 'sh', '-c', command], dest / 'run.log', 240)
            manifest['cases'].append(name)
        left = output / f'run{run}-jni-c{concurrency}'
        right = output / f'run{run}-ffm-c{concurrency}'
        images = list(left.glob('*.webp'))
        if len(images) != 12 or any(p.read_bytes() != (right / p.name).read_bytes() for p in images):
            raise AssertionError('JNI/FFM outputs differ')
        manifest['byte_identical_pairs'] += len(images)

for run in [1, 2]:
    name = f'control21-run{run}-jni-c4'
    dest = output / name
    dest.mkdir(exist_ok=True)
    # Java 21 classpath deliberately excludes Java 25 experiment classes.
    command = 'java -XX:UseSVE=0 -Xmx512m -Djava.library.path=/native -cp "/tests/classes:/tests/lib/*" '
    command += f'com.sssok.benchmark.ImageEngineBenchmark jni 4 /inputs /experiment/{name} 2 5'
    command += f' && cat /sys/fs/cgroup/memory.peak > /experiment/{name}/memory-peak.txt'
    command += f' && cat /sys/fs/cgroup/cpu.stat > /experiment/{name}/cpu-stat.txt'
    execute(base + [args.image21, 'sh', '-c', command], dest / 'run.log', 240)
    manifest['cases'].append(name)
(output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Completed; raw batches.csv and rows.csv are under', output)
