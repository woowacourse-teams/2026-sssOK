#!/usr/bin/env python3
"""운영 수집기 구성과 앱 배포 이후 관측 장애 처리를 검증한다."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/deploy-prod.yml'
PROBE = '      - name: 중앙 Loki 수신 확인 (앱 배포와 별도)'


def monitoring_steps():
    source = WORKFLOW.read_text()
    app_check = source.index('          if [ "$APP_HEALTHY" != true ]; then')
    end = source.index('\n          fi\n', app_check) + len('\n          fi\n')
    return textwrap.dedent(source[end:].split(PROBE, 1)[0])


MOCK_COMMANDS = r'''
set -eu
COMPOSE=compose_mock
record() { printf '%s\n' "$1" >> "$TRACE"; }
compose_mock() {
    case "$*" in
        'exec -T alloy /bin/alloy validate /etc/alloy/config')
            record validate
            [ "$FAIL_AT" != validate ] ;;
        "exec -T nginx wget -q -T 10 -O - --post-data= http://alloy:12345/-/reload")
            record reload
            [ "$FAIL_AT" != reload ] ;;
        *) record "unexpected-compose:$*"; return 99 ;;
    esac
}
bash() {
    [ "$*" = monitoring/check-prod-node.sh ] || return 99
    record node-check
    [ "$FAIL_AT" != node-check ]
}
'''


class MonitoringTest(unittest.TestCase):
    def test_local_observation_failures_never_roll_back_app(self):
        stages = ['validate', 'reload', 'node-check']
        for fail_at in ('', *stages):
            with self.subTest(fail_at=fail_at), tempfile.TemporaryDirectory() as tmp:
                trace = Path(tmp) / 'trace'
                result = subprocess.run(['bash', '-c', MOCK_COMMANDS + monitoring_steps()],
                    env=dict(os.environ, TRACE=str(trace), FAIL_AT=fail_at),
                    capture_output=True, text=True, timeout=10)
                events = trace.read_text().splitlines()
                expected = stages if not fail_at else stages[:stages.index(fail_at) + 1]
                self.assertEqual(events, expected)
                self.assertEqual(result.returncode == 0, not fail_at)

    def test_central_probe_success_failure_and_timeout(self):
        section = WORKFLOW.read_text().split(PROBE, 1)[1].split('\n  alias:', 1)[0]
        self.assertIn('continue-on-error: true', section)
        script = textwrap.dedent(section.split('        run: |\n', 1)[1])
        mocks = r'''
docker() { printf '%s' 'http://central.invalid/loki/api/v1/push'; }
curl() {
    [ "$CASE" != timeout ] || return 28
    if [ "$CASE" = success ]; then printf 204; else printf 503; fi
}
'''
        for case in ('success', 'failure', 'timeout'):
            with self.subTest(case=case), tempfile.TemporaryDirectory() as tmp:
                result = subprocess.run(['bash', '-c', mocks + script],
                    env=dict(os.environ, DEPLOY_PATH=tmp, CASE=case),
                    capture_output=True, text=True, timeout=10)
                self.assertEqual(result.returncode == 0, case == 'success')
                if case != 'success':
                    self.assertIn('::warning::', result.stdout)

    def test_prod_nginx_without_local_grafana(self):
        subprocess.run(['docker', 'run', '--rm', '--network', 'none',
            '--add-host', 'app:127.0.0.1', '--mount',
            f'type=bind,src={ROOT}/backend/nginx.conf,dst=/etc/nginx/conf.d/default.conf,readonly',
            'nginx:1.27-alpine', 'nginx', '-t'], check=True,
            capture_output=True, text=True, timeout=30)

    def test_prod_compose_without_legacy_credentials(self):
        env = {k: v for k, v in os.environ.items()
               if k not in ('GRAFANA_ADMIN_PASSWORD', 'DISCORD_WEBHOOK_FILE', 'COMPOSE_PROFILES')}
        env['BACKEND_IMAGE'] = 'app:test'
        result = subprocess.run(['docker', 'compose', '--env-file', os.devnull,
            '-f', str(ROOT / 'backend/docker-compose.prod.yml'), 'config', '--format', 'json'],
            env=env, capture_output=True, text=True, check=True)
        config = json.loads(result.stdout)
        self.assertEqual(set(config['services']),
            {'app', 'nginx', 'alloy', 'node-exporter', 'docker-socket-proxy'})
        self.assertEqual(set(config['volumes']), {'alloy-data'})
        self.assertFalse(config.get('secrets'))
        alloy = config['services']['alloy']
        self.assertNotIn('loki', alloy.get('depends_on', {}))
        self.assertTrue(any(v.get('source', '').endswith('write.central.alloy')
                            for v in alloy['volumes']))
        self.assertTrue(any(v.get('source') == 'alloy-data' and
                            v['target'] == '/var/lib/alloy/data' for v in alloy['volumes']))
        self.assertEqual(alloy['environment']['SSSOK_ENVIRONMENT'], 'prod')


if __name__ == '__main__':
    unittest.main()
