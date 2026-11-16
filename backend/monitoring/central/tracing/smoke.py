#!/usr/bin/env python3
"""격리된 로컬 Tempo에서 실제 OTLP 수집과 trace ID 조회를 검증한다."""
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parent
project = 'sssok-tracing-test-' + uuid.uuid4().hex[:10]


def request(url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=5) as response:
        return response.read()


with tempfile.TemporaryDirectory(prefix=project) as directory:
    override = Path(directory) / 'compose.json'
    override.write_text(json.dumps({
        'services': {'tempo': {'ports': ['127.0.0.1::3200', '127.0.0.1::4318']}},
        'networks': {'central': {'external': False, 'name': project}}}))
    command = ['docker', 'compose', '-p', project, '-f', str(ROOT / 'compose.yml'), '-f', str(override)]

    def compose(*args):
        return subprocess.run(command + list(args), check=True, capture_output=True, text=True, timeout=120).stdout

    try:
        compose('up', '-d')
        query = 'http://' + compose('port', 'tempo', '3200').strip()
        ingest = 'http://' + compose('port', 'tempo', '4318').strip()
        for attempt in range(60):
            try:
                if request(query + '/ready') == b'ready\n':
                    break
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(.5)
        else:
            raise AssertionError('Tempo readiness timeout')
        trace_id = uuid.uuid4().hex
        healthy_id = uuid.uuid4().hex
        # 현재 초의 검색 경계에 걸리지 않도록 두 요청 모두 과거에 완료된 것으로 만든다.
        now = time.time_ns() - 5000000000
        result = json.loads(request(ingest + '/v1/traces', {'resourceSpans': [{
            'resource': {'attributes': [{'key': 'service.name', 'value': {'stringValue': 'sssok-backend'}},
                {'key': 'deployment.environment', 'value': {'stringValue': 'dev'}}]},
            'scopeSpans': [{'scope': {'name': 'smoke'}, 'spans': [{
                'traceId': trace_id, 'spanId': '0123456789abcdef', 'name': 'upload.processing', 'kind': 2, 'status': {'code': 2},
                'startTimeUnixNano': str(now - 2000000000), 'endTimeUnixNano': str(now)}, {
                'traceId': healthy_id, 'spanId': '0123456789abcdef', 'name': 'GET /version', 'kind': 2,
                'startTimeUnixNano': str(now - 1000000), 'endTimeUnixNano': str(now)}]}]}]}) or b'{}')
        assert not int(result.get('partialSuccess', {}).get('rejectedSpans', 0)), result
        for attempt in range(30):
            try:
                trace = json.loads(request(query + '/api/traces/' + trace_id))
                healthy = json.loads(request(query + '/api/traces/' + healthy_id))
                assert 'upload.processing' in json.dumps(trace)
                assert 'GET /version' in json.dumps(healthy)
                break
            except (OSError, urllib.error.URLError, AssertionError):
                time.sleep(.5)
        else:
            raise AssertionError('Exported trace not found')
        dashboard = json.loads((ROOT.parent / 'trace-overview.json').read_text())
        for row in dashboard['panels']:
            for panel in row.get('panels', []):
                target = panel['targets'][0]
                for environment in ('dev', 'prod'):
                    params = urllib.parse.urlencode({'q': target['query'].replace('$environment', environment),
                                                     'limit': target['limit'],
                                                     'start': now // 1000000000 - 60,
                                                     'end': now // 1000000000 + 60})
                    # ID 조회와 검색 인덱스 반영 시점은 다르다. 수집 직후 검색은 짧게 재시도한다.
                    expected = set()
                    if environment == 'dev':
                        expected.add(trace_id)
                        if row['title'] == '전체 백엔드':
                            expected.add(healthy_id)
                    for attempt in range(60):
                        result = json.loads(request(query + '/api/search?' + params))
                        # 검색 응답에서 생략될 수 있는 앞쪽 0을 W3C 32자리로 복원한다.
                        ids = {item['traceID'].zfill(32) for item in result.get('traces', [])}
                        if ids == expected:
                            break
                        time.sleep(.5)
                    else:
                        raise AssertionError((panel['title'], result))
        print('Tempo 대시보드 실제 검색 4종 및 환경 격리 검증 성공')
        print('Tempo 512MiB: OTLP 수집·실제 trace ID 조회 성공')
    finally:
        # 이 스크립트가 만든 고유 프로젝트의 시험 데이터만 정리한다.
        compose('down', '-v')
