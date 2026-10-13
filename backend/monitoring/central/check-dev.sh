#!/usr/bin/env bash
# 중앙 서버에서 실행: 호스트 curl 성공뿐 아니라 Prometheus의 실제 수집을 확인한다.
set -euo pipefail
python3 - <<'PY'
import json
import time
import urllib.parse
import urllib.request

def query(expression):
    url = 'http://127.0.0.1:9090/api/v1/query?' + urllib.parse.urlencode({'query': expression})
    with urllib.request.urlopen(url, timeout=5) as response:
        result = json.load(response)
    if result['status'] != 'success':
        raise SystemExit('Prometheus 쿼리 실패')
    return result['data']['result']

selector = '{job="node",environment="dev",server="dev"}'
for attempt in range(12):
    result = query('up' + selector)
    if len(result) == 1 and result[0]['value'][1] == '1':
        break
    time.sleep(5)
else:
    raise SystemExit('개발 수집 실패: Prometheus /api/v1/targets의 lastError를 확인하세요.')

for metric in ('node_cpu_seconds_total', 'node_memory_MemAvailable_bytes',
               'node_memory_MemTotal_bytes', 'node_vmstat_pswpin', 'node_vmstat_pswpout',
               'node_load1'):
    if not query(metric + selector):
        raise SystemExit('개발 지표 누락: ' + metric)
if not query('node_filesystem_avail_bytes' + selector[:-1] + ',mountpoint="/"}'):
    raise SystemExit('개발 루트 디스크 지표 누락')
print('정상: 중앙 Prometheus에서 개발 호스트 수집 및 필수 지표 확인')
PY
