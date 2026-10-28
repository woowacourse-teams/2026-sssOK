#!/usr/bin/env python3
"""실제 Prometheus 이미지로 정기 보고 조회식을 검사한다.

합성 운영·개발 시계열을 TSDB 블록으로 적재하고 보고 작업을 미리보기로 실행한다.
개발 값은 운영의 100배로 넣어 섞이면 바로 드러나게 한다. 외부 통신·실제 서버 데이터는 쓰지 않는다.
"""
from datetime import datetime, timedelta, timezone
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from contextlib import redirect_stdout

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from ops_report import cli
from ops_report.aggregate import quantile
from ops_report.periods import KST, daily


def image():
    compose = (ROOT / 'compose.yml').read_text()
    return next(line.split('image:')[1].strip() for line in compose.splitlines()
                if 'image: prom/prometheus' in line)


def series(name, labels, points):
    text = ','.join(f'{k}="{v}"' for k, v in sorted(labels.items()))
    return ''.join(f'{name}{{{text}}} {value} {t}\n' for t, value in points)


def generate(start, end):
    """(start, end] 구간의 합성 지표. 카운터는 60초, up은 15초 간격이다."""
    lines = []
    minutes = range(int(start) + 60, int(end) + 1, 60)
    spike = (start + 86400 + 3 * 3600, start + 86400 + 3 * 3600 + 1200)  # 대상일 03:00부터 20분
    for env, scale in (('prod', 1), ('dev', 100)):
        base = {'environment': env, 'server': env, 'instance': f'{env}:19100'}
        for job in ('node', 'sssok-backend', 'alloy'):
            lines.append(series('up', dict(base, job=job),
                                ((t, 1) for t in range(int(start) + 15, int(end) + 1, 15))))
        app = dict(base, job='sssok-backend')
        for method, uri, status, per_minute in (('GET', '/api/v1/rooms', '200', 4),
                                                ('POST', '/api/v1/rooms/{roomId}/media', '500', 0.1),
                                                ('GET', '/health', '200', 60)):
            labels = dict(app, method=method, uri=uri, status=status)
            rate = per_minute * scale
            lines.append(series('http_server_requests_seconds_count', labels,
                                ((t, (t - start) / 60 * rate) for t in minutes)))
            for le, share in (('0.1', 0.9), ('0.5', 0.98), ('1.0', 1.0), ('+Inf', 1.0)):
                lines.append(series('http_server_requests_seconds_bucket', dict(labels, le=le),
                                    ((t, (t - start) / 60 * rate * share) for t in minutes)))
        lines.append(series('sssok_thumbnail_failed_total', app, ((t, (t - start) // 3600 * scale) for t in minutes)))
        lines.append(series('sssok_upload_files_total', dict(app, stage='issue', result='rejected'),
                            ((t, (t - start) // 7200 * scale) for t in minutes)))
        node = dict(base, job='node')
        idle, value = [], 0.0
        for t in minutes:
            value += 60 * (0.1 if spike[0] < t <= spike[1] else 0.7)  # 단일 코어 사용률 90% / 30%
            idle.append((t, value))
        lines.append(series('node_cpu_seconds_total', dict(node, cpu='0', mode='idle'), idle))
        lines.append(series('node_memory_MemTotal_bytes', node, ((t, 4e9) for t in minutes)))
        lines.append(series('node_memory_MemAvailable_bytes', node,
                            ((t, 1e9 if spike[0] < t <= spike[1] else 2e9) for t in minutes)))
        fs = dict(node, mountpoint='/', device='/dev/root', fstype='ext4')
        lines.append(series('node_filesystem_size_bytes', fs, ((t, 20e9) for t in minutes)))
        lines.append(series('node_filesystem_avail_bytes', fs, ((t, 12e9) for t in minutes)))
        alert = dict(base, alertname='BackendHighLatency', alertstate='firing', severity='warning')
        lines.append(series('ALERTS', alert, ((t, 1) for t in range(int(spike[0]), int(spike[0]) + 720, 60))))
    return ''.join(lines) + '# EOF\n'


def get(base, query, at):
    url = f'{base}/api/v1/query?' + urllib.parse.urlencode({'query': query, 'time': at})
    return json.load(urllib.request.urlopen(url, timeout=10))['data']['result']


def main():
    now = datetime.now(timezone.utc)
    target = (now.astimezone(KST) - timedelta(days=1)).date()
    period = daily(target)
    start, end = period.start.timestamp() - 86400, period.end.timestamp()
    name = f'sssok-ops-report-test-{os.getpid()}'
    with tempfile.TemporaryDirectory(prefix='sssok-ops-report-') as tmp:
        work = Path(tmp)
        work.chmod(0o777)
        (work / 'data.om').write_text(generate(start, end))
        (work / 'data.om').chmod(0o644)
        subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
                        '--mount', f'type=bind,src={work},dst=/work', '--entrypoint', '/bin/promtool', image(),
                        'tsdb', 'create-blocks-from', 'openmetrics', '/work/data.om', '/work/tsdb'],
                       check=True, capture_output=True)
        subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
                        '--mount', f'type=bind,src={work},dst=/work', '--entrypoint', 'chmod', image(),
                        '-R', 'a+rwX', '/work/tsdb'], check=True)
        try:
            subprocess.run(['docker', 'run', '-d', '--name', name, '-p', '127.0.0.1::9090',
                            '--mount', f'type=bind,src={work / "tsdb"},dst=/prometheus', image(),
                            '--config.file=/etc/prometheus/prometheus.yml', '--storage.tsdb.path=/prometheus',
                            '--storage.tsdb.retention.time=15d'], check=True, capture_output=True)
            port = subprocess.run(['docker', 'port', name, '9090/tcp'], check=True, capture_output=True,
                                  text=True).stdout.split(':')[-1].strip()
            base = f'http://127.0.0.1:{port}'
            for _ in range(60):
                try:
                    if urllib.request.urlopen(base + '/-/ready', timeout=2).status == 200:
                        break
                except OSError:
                    time.sleep(1)
            db = str(work / 'state' / 'report.sqlite3')
            output = io.StringIO()
            with redirect_stdout(output):
                code = cli.main(['--db', db, '--prometheus', base, '--settings', str(work / 'none.json'),
                                 '--now', now.isoformat(), 'run', '--preview'])
            text = output.getvalue()
            assert code == 0, text
            rows = sqlite3.connect(db).execute(
                'SELECT o.metric, o.dimension, o.value, o.status FROM observations o JOIN reports r'
                " ON r.id = o.report_id WHERE r.kind = 'daily' AND r.period_start = ?",
                (period.start.isoformat(),)).fetchall()
            obs = {(m, d): (v, s) for m, d, v, s in rows}

            def near(metric, expected, dim='', tolerance=0.02):
                value, state = obs[(metric, dim)]
                assert state == 'ok' and abs(value - expected) <= abs(expected) * tolerance, (metric, value, state)

            near('requests_total', 1440 * 4.1)  # /health 제외, 개발 100배 값이 섞이지 않음
            near('errors_5xx_total', 144)
            near('error_rate', 144 / (1440 * 4.1))
            near('requests_by_api', 1440 * 4, 'GET /api/v1/rooms')
            assert ('requests_by_api', 'GET /health') not in obs
            expected = float(get(base, 'histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket'
                                       '{job="sssok-backend",environment="prod",uri!~"/actuator.*|/health|/api/v1/rooms/[^/]+/events"}'
                                       '[86400s])))', end)[0]['value'][1])
            near('latency_p95', expected, tolerance=0.001)
            near('latency_p95', quantile(0.95, {'0.1': 0.9, '0.5': 0.98, '1.0': 1, '+Inf': 1}), tolerance=0.001)
            near('cpu_peak_ratio', 0.9, tolerance=0.05)
            near('memory_available_min_ratio', 0.25)
            near('disk_available_min_ratio', 0.6)
            near('thumbnail_failed', 24, tolerance=0.1)
            near('scrape_coverage', 1.0, 'sssok-backend', tolerance=0.001)
            near('scrape_failed_samples', 0, 'node')
            assert obs[('errors_5xx_total', '')][1] == 'ok'
            # 적재 블록에는 알림 해소 시 기록되는 stale marker가 없어 마지막 값이 조회 유예(5분)만큼 이어진다.
            # 실제 규칙 평가는 해소 시 stale marker를 쓰므로 운영 구간은 이 유예를 포함하지 않는다.
            intervals = sqlite3.connect(db).execute(
                'SELECT alertname, start_at, observed_seconds FROM threshold_intervals t JOIN reports r'
                " ON r.id = t.report_id WHERE r.kind = 'daily' AND r.period_start = ?",
                (period.start.isoformat(),)).fetchall()
            assert len(intervals) == 1 and intervals[0][0] == 'BackendHighLatency', intervals
            assert intervals[0][1].endswith('T03:00:00+09:00') and 720 <= intervals[0][2] <= 1020, intervals
            assert '응답 지연 기준 초과 `BackendHighLatency` 1회' in text, text
            assert '(전일 +0.0%' in text, text
            print(text)
            print('실제 Prometheus 정기 보고 조회·집계 검사 통과')
        finally:
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
            # Linux에서는 컨테이너가 만든 TSDB 파일을 러너 사용자가 지울 수 없어 컨테이너 안에서 지운다.
            subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
                            '--mount', f'type=bind,src={work},dst=/work', '--entrypoint', 'rm', image(),
                            '-rf', '/work/tsdb'], check=True, capture_output=True)


if __name__ == '__main__':
    main()
