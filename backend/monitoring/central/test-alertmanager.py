#!/usr/bin/env python3
"""실제 Alertmanager에서 동일 대상 억제·타 환경 격리를 확인한다. 외부 발송 없음."""
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import subprocess
import time

from validate import compose_config

ROOT = Path(__file__).resolve().parent


def run(args):
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=30).stdout


def main():
    image = compose_config()['services']['alertmanager']['image']
    cid = run(['docker', 'run', '-d', '--rm', '--network', 'none',
               '--mount', f'type=bind,src={ROOT / "alertmanager.yml"},dst=/etc/alertmanager/config.yml,readonly',
               image, '--config.file=/etc/alertmanager/config.yml',
               '--storage.path=/tmp/alertmanager', '--cluster.listen-address=']).strip()
    try:
        def amtool(*args):
            return run(['docker', 'exec', cid, '/bin/amtool',
                        '--alertmanager.url=http://127.0.0.1:9093', *args])

        for attempt in range(30):
            try:
                amtool('alert', 'query')
                break
            except subprocess.CalledProcessError:
                if attempt == 29:
                    raise
                time.sleep(.2)

        base = dict(environment='prod', server='app', instance='app:19100',
                    device='/dev/root', mountpoint='/')
        alerts = []

        def add(case, name, severity='warning', **overrides):
            labels = dict(base, alertname=name, severity=severity, testcase=case)
            labels.update(overrides)
            alerts.append(labels)
            amtool('alert', 'add', *[f'{k}={v}' for k, v in labels.items()])

        add('critical', 'HostDiskSpaceCritical', 'critical')
        add('same-low', 'HostDiskSpaceLow')
        add('same-prediction', 'HostDiskWillFillIn24h')
        add('dev-low', 'HostDiskSpaceLow', environment='dev')
        add('other-server', 'HostDiskSpaceLow', server='other')
        add('other-instance', 'HostDiskSpaceLow', instance='other:19100')
        add('other-mount', 'HostDiskSpaceLow', mountpoint='/data')
        add('other-device', 'HostDiskSpaceLow', device='/dev/other')
        add('low-source', 'HostDiskSpaceLow', server='low-only')
        add('low-prediction', 'HostDiskWillFillIn24h', server='low-only')
        add('shipping', 'LogShippingFailing', host='central:13100')
        add('dropped', 'LogEntriesDropped', host='central:13100', reason='rate_limited')
        add('missing-critical', 'HostDiskSpaceCritical', 'critical', server='', instance='', mountpoint='')
        add('missing-low', 'HostDiskSpaceLow', server='', instance='', mountpoint='')

        expected = {'same-low', 'same-prediction', 'low-prediction'}
        for attempt in range(30):
            inhibited = json.loads(amtool('-o', 'json', 'alert', 'query', '--inhibited'))
            actual = {alert['labels']['testcase'] for alert in inhibited}
            if actual == expected:
                break
            time.sleep(.2)
        assert actual == expected, (actual, expected)
        active = json.loads(amtool('-o', 'json', 'alert', 'query', '--active'))
        assert {a['labels']['testcase'] for a in active} == {a['testcase'] for a in alerts} - expected

        # critical이 복구되면 warning은 다시 활성화된다. 예측은 남은 warning이 억제한다.
        now = datetime.now(timezone.utc)
        source = alerts[0]
        amtool('alert', 'add', '--start=' + (now - timedelta(minutes=2)).isoformat(),
               '--end=' + (now - timedelta(seconds=1)).isoformat(),
               *[f'{k}={v}' for k, v in source.items()])
        for attempt in range(30):
            active = json.loads(amtool('-o', 'json', 'alert', 'query', '--active'))
            if 'same-low' in {a['labels']['testcase'] for a in active}:
                break
            time.sleep(.2)
        assert 'same-low' in {a['labels']['testcase'] for a in active}
        # 공통 장애는 같은 대상만 억제한다. 대표 알림 복구 후 개별 장애가 남으면 다시 알린다.
        add('endpoint', 'MetricsEndpointDown', 'critical', environment='dev', server='endpoint', instance='endpoint:19100')
        for case, name in [('endpoint-app', 'BackendDown'), ('endpoint-alloy', 'LogPipelineDown'), ('endpoint-node', 'NodeMetricsDown')]:
            add(case, name, environment='dev', server='endpoint', instance='endpoint:19100')
        add('endpoint-other-env', 'BackendDown', environment='prod', server='endpoint', instance='endpoint:19100')
        add('endpoint-other-server', 'BackendDown', environment='dev', server='other', instance='endpoint:19100')
        add('endpoint-other-instance', 'BackendDown', environment='dev', server='endpoint', instance='other:19100')
        expected_endpoint = {'endpoint-app', 'endpoint-alloy', 'endpoint-node'}
        for attempt in range(30):
            inhibited = json.loads(amtool('-o', 'json', 'alert', 'query', '--inhibited'))
            actual_endpoint = {a['labels']['testcase'] for a in inhibited if a['labels']['testcase'].startswith('endpoint')}
            if actual_endpoint == expected_endpoint:
                break
            time.sleep(.2)
        assert actual_endpoint == expected_endpoint, actual_endpoint
        source = next(a for a in alerts if a['testcase'] == 'endpoint')
        now = datetime.now(timezone.utc)
        amtool('alert', 'add', '--start=' + (now - timedelta(minutes=2)).isoformat(),
               '--end=' + (now - timedelta(seconds=1)).isoformat(),
               *[f'{k}={v}' for k, v in source.items()])
        for attempt in range(30):
            active = json.loads(amtool('-o', 'json', 'alert', 'query', '--active'))
            if expected_endpoint <= {a['labels']['testcase'] for a in active}:
                break
            time.sleep(.2)
        assert expected_endpoint <= {a['labels']['testcase'] for a in active}
        print('Alertmanager: 21개 가상 알림의 대상별 억제·격리·복구 확인 (외부 발송 없음)')
    finally:
        run(['docker', 'rm', '-f', cid])


if __name__ == '__main__':
    main()
