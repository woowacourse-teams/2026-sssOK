#!/usr/bin/env python3
"""실제 Compose 설정 해시로 앱·Nginx 변경의 재생성 범위를 확인한다."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / 'backend/docker-compose.dev.yml'
TEMPLATE = ROOT / 'backend/monitoring/nginx/container/default.conf.template'


class NginxConfigTest(unittest.TestCase):
    def test_compose_hash_changes_only_for_nginx_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / '.env'
            env_file.write_text('')
            template = TEMPLATE.read_bytes()

            def config_hash(image, content, private_ip='10.0.0.69'):
                result = subprocess.run([
                    'docker', 'compose', '--env-file', str(env_file), '-f', str(COMPOSE),
                    'config', '--hash', 'nginx',
                ], env={**os.environ, 'BACKEND_IMAGE': image,
                        'NGINX_CONFIG_SHA': hashlib.sha256(content).hexdigest(),
                        'MONITORING_PRIVATE_IP': private_ip},
                    capture_output=True, text=True, check=True)
                return result.stdout.strip()

            initial = config_hash('app:old', template)
            self.assertEqual(initial, config_hash('app:new', template))
            self.assertNotEqual(initial, config_hash('app:new', template + b'\n# changed\n'))
            self.assertNotEqual(initial, config_hash('app:old', template, '10.0.0.70'))

    def test_dev_uses_only_central_without_legacy_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / '.env'
            env_file.write_text('')
            env = {k: v for k, v in os.environ.items()
                   if k not in ('GRAFANA_ADMIN_PASSWORD', 'DISCORD_WEBHOOK_FILE', 'COMPOSE_PROFILES')}
            # 예전 서버 환경변수가 남아 있어도 중앙 전송을 사용한다.
            env.update(BACKEND_IMAGE='app:test', DEV_ALLOY_WRITER='dual')
            result = subprocess.run([
                'docker', 'compose', '--env-file', str(env_file), '-f', str(COMPOSE),
                'config', '--format', 'json'], env=env,
                capture_output=True, text=True, check=True)
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
                                v.get('target') == '/var/lib/alloy/data'
                                for v in alloy['volumes']))

    def test_workflow_exports_hash_before_all_compose_calls(self):
        workflow = (ROOT / '.github/workflows/deploy-dev.yml').read_text()
        self.assertLess(workflow.index('export NGINX_CONFIG_SHA'), workflow.index('COMPOSE='))
        self.assertIn('sha256sum monitoring/nginx/container/default.conf.template', workflow)
        self.assertIn('$COMPOSE up -d --no-deps nginx', workflow)
        self.assertNotIn('--force-recreate nginx', workflow)


if __name__ == '__main__':
    unittest.main()
