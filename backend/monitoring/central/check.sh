#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Ubuntu 호스트에서 실행한다. 호스트 포트는 모두 loopback에만 공개한다.
for endpoint in \
  http://127.0.0.1:9090/-/ready \
  http://127.0.0.1:9093/-/ready \
  http://127.0.0.1:3100/ready \
  http://127.0.0.1:3000/api/health; do
  curl --fail --silent --show-error --max-time 5 \
    --retry 20 --retry-delay 3 --retry-all-errors "$endpoint" > /dev/null
done
python3 - <<'PY'
import json
import time
import urllib.parse
import urllib.request

def get(url):
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)

def query(expression):
    result = get('http://127.0.0.1:9090/api/v1/query?' + urllib.parse.urlencode({'query': expression}))
    assert result['status'] == 'success', result
    return result['data']['result']

for attempt in range(12):
    targets = query('up{environment="monitoring",server="monitoring"}')
    jobs = {item['metric']['job'] for item in targets if item['value'][1] == '1'}
    if jobs == {'prometheus', 'alertmanager', 'loki', 'grafana', 'node'}:
        break
    time.sleep(5)
else:
    raise SystemExit('수집 대상 확인 실패: ' + repr(targets))

for expression in ('node_cpu_seconds_total{environment="monitoring",server="monitoring"}',
                   'node_memory_MemAvailable_bytes{environment="monitoring",server="monitoring"}',
                   'node_vmstat_pswpin{environment="monitoring",server="monitoring"}',
                   'node_filesystem_avail_bytes{environment="monitoring",server="monitoring",mountpoint="/"}'):
    assert query(expression), '호스트 지표 누락: ' + expression

# 실제 앱 로그 대신 식별 가능한 테스트 로그 한 줄로 쓰기·읽기를 검증한다.
timestamp = str(time.time_ns())
message = 'central-bootstrap-' + timestamp
payload = {'streams': [{'stream': {'environment': 'monitoring', 'service': 'bootstrap'},
                        'values': [[timestamp, message]]}]}
request = urllib.request.Request('http://127.0.0.1:3100/loki/api/v1/push',
                                 data=json.dumps(payload).encode(),
                                 headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=5) as response:
    assert response.status == 204
for attempt in range(12):
    params = urllib.parse.urlencode({'query': '{service="bootstrap"} |= "' + message + '"',
                                    'start': timestamp, 'end': str(time.time_ns())})
    result = get('http://127.0.0.1:3100/loki/api/v1/query_range?' + params)
    if any(value[1] == message for stream in result['data']['result'] for value in stream['values']):
        break
    time.sleep(2)
else:
    raise SystemExit('Loki 테스트 로그 조회 실패')
print('정상: 서비스 준비 완료, 중앙 5개 대상 수집, 호스트 지표, Loki 로그 저장·조회')
PY
