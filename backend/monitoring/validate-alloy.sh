#!/usr/bin/env bash
# 후보 Compose의 이미지·환경변수·설정 마운트를 검사한다. 실제 데이터 볼륨은 연결하지 않는다.
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 - "${1:?local, dev 또는 prod를 지정하세요}" <<'PY'
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

environment = sys.argv[1]
if environment not in ('local', 'dev', 'prod'):
    raise SystemExit('지원하지 않는 환경')
suffix = 'monitoring' if environment == 'local' else environment
compose_file = f'backend/docker-compose.{suffix}.yml'
# Compose 전체를 해석하되 앱 비밀값은 검사에 사용하지 않는다.
keys = ('BACKEND_IMAGE DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET R2_ENDPOINT '
        'R2_ACCESS_KEY R2_SECRET_KEY R2_BUCKET R2_PUBLIC_BASE_URL CORS_ALLOWED_ORIGINS '
        'GRAFANA_ADMIN_PASSWORD').split()
with tempfile.NamedTemporaryFile(mode='w') as dummy:
    dummy.write(''.join(f'{key}=validation-only\n' for key in keys))
    dummy.flush()
    env = dict(os.environ, **{key: 'validation-only' for key in keys})
    result = subprocess.run(
        ['docker', 'compose', '--env-file', dummy.name, '-f', compose_file,
         'config', '--format', 'json'], env=env, capture_output=True, text=True, check=True)
    alloy = json.loads(result.stdout)['services']['alloy']

assert alloy['command'][-1] == '/etc/alloy/config', '설정 디렉터리 실행 누락'
assert '--storage.path=/var/lib/alloy/data' in alloy['command'], '수집 위치 경로 변경'
expected_volume = 'alloy-local-data' if environment == 'local' else 'alloy-data'
assert any(v['type'] == 'volume' and v['source'] == expected_volume
           and v['target'] == '/var/lib/alloy/data' for v in alloy['volumes']), '수집 위치 볼륨 변경'
selected = 'local' if environment == 'local' else 'central'
expected = {
    '/etc/alloy/config/config.alloy': Path('backend/monitoring/alloy/config.alloy').resolve(),
    '/etc/alloy/config/write.alloy': Path(f'backend/monitoring/alloy/write.{selected}.alloy').resolve(),
}
mounts = [v for v in alloy['volumes'] if v['target'].startswith('/etc/alloy/')]
assert len(mounts) == 2, '설정 마운트 수 불일치'
args = ['docker', 'run', '--rm', '--network', 'none']
for v in mounts:
    assert v['type'] == 'bind' and v.get('read_only'), '설정은 읽기 전용 bind mount 필요'
    assert Path(v['source']) == expected[v['target']], '환경별 전송 파일 선택 오류'
    args += ['-v', f"{v['source']}:{v['target']}:ro"]
values = alloy['environment']
assert values['SSSOK_ENVIRONMENT'] == environment, '환경 라벨 오류'
if environment == 'local':
    assert 'CENTRAL_LOKI_URL' not in values, '로컬에 중앙 전송 환경변수 금지'
else:
    assert values.get('CENTRAL_LOKI_URL', '').startswith(('http://', 'https://')), '중앙 URL 누락'
for key, value in values.items():
    args += ['-e', f'{key}={value}']
subprocess.run(args + [alloy['image'], 'validate', '/etc/alloy/config'], check=True)
print(f'{environment}: Compose 선택·수집 위치 보존·Alloy 설정 검사 통과')
PY
