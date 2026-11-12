#!/usr/bin/env python3
"""개발 서버가 운영과 같은 블루-그린 구조(두 칸·upstream.conf·배포 스크립트)인지 확인한다."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / 'backend/docker-compose.dev.yml'
TEMPLATE = ROOT / 'backend/monitoring/nginx/container/default.conf.template'
WORKFLOW = ROOT / '.github/workflows/deploy-dev.yml'

UPSTREAM = '''
upstream sssok_app {
    zone sssok_app 64k;
    server app-green:8080 resolve;
}
upstream sssok_app_management {
    zone sssok_app_management 64k;
    server app-green:8081 resolve;
}
'''


def compose_config(**extra_env):
    env = {k: v for k, v in os.environ.items()
           if k not in ('GRAFANA_ADMIN_PASSWORD', 'DISCORD_WEBHOOK_FILE', 'COMPOSE_PROFILES')}
    env.update(extra_env)
    result = subprocess.run([
        'docker', 'compose', '--env-file', os.devnull, '-f', str(COMPOSE),
        'config', '--format', 'json'], env=env,
        capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def render_template():
    """개발 CD(compose 파일 배치 단계)와 같은 치환을 한다."""
    workflow = WORKFLOW.read_text()
    match = re.search(r"sed -e '([^']+)' -e '([^']+)'", workflow)
    assert match, '개발 CD 에서 nginx 템플릿 치환 명령을 찾지 못했다'
    result = subprocess.run(['sed', '-e', match.group(1), '-e', match.group(2), str(TEMPLATE)],
                            capture_output=True, text=True, check=True)
    return result.stdout


class DevBlueGreenTest(unittest.TestCase):
    def test_dev_compose_has_same_two_slots_as_prod(self):
        config = compose_config()
        self.assertEqual(set(config['services']),
                         {'app-blue', 'app-green', 'nginx', 'alloy', 'node-exporter',
                          'docker-socket-proxy'})
        for color in ('blue', 'green'):
            app = config['services'][f'app-{color}']
            self.assertEqual(app['container_name'], f'sssok-app-dev-{color}')
            self.assertEqual(app['labels']['com.sssok.log.service'], 'sssok-backend')
            self.assertEqual(app['stop_grace_period'], '50s')
            self.assertEqual(app['mem_limit'], str(1536 * 1024 * 1024))
            self.assertEqual(app['environment']['SPRING_PROFILES_ACTIVE'], 'dev')
            self.assertIn('-Xmx512m', app['environment']['JAVA_OPTS'])
            # 두 칸이 함께 뜨므로 호스트 포트를 게시하면 두 번째 칸이 뜨지 못한다.
            self.assertFalse(app.get('ports'))
        nginx = config['services']['nginx']
        self.assertNotIn('depends_on', nginx)
        self.assertEqual([v['target'] for v in nginx['volumes']], ['/etc/nginx/conf.d'])

    def test_dev_and_prod_slots_share_settings(self):
        """개발에서 확인한 배포가 운영에서도 같게 돌도록, 앱 칸 설정은 프로필·이름 말고는 같아야 한다."""
        prod_result = subprocess.run([
            'docker', 'compose', '--env-file', os.devnull,
            '-f', str(ROOT / 'backend/docker-compose.prod.yml'), 'config', '--format', 'json'],
            capture_output=True, text=True, check=True)
        prod = json.loads(prod_result.stdout)['services']
        dev = compose_config()['services']
        ignored = {'container_name', 'environment', 'env_file'}
        for color in ('blue', 'green'):
            name = f'app-{color}'
            self.assertEqual({k: v for k, v in dev[name].items() if k not in ignored},
                             {k: v for k, v in prod[name].items() if k not in ignored})
            self.assertEqual(dev[name]['environment']['JAVA_OPTS'],
                             prod[name]['environment']['JAVA_OPTS'])
        self.assertEqual(dev['nginx']['image'], prod['nginx']['image'])

    def test_dev_nginx_starts_with_upstream_even_if_slot_is_down(self):
        # 앱 컨테이너 이름을 찾을 수 없는(network none) 상태에서도 upstream resolve 로 설정이 통과해야
        # 꺼진 칸을 가리킨 채 nginx 가 재시작돼도 뜬다.
        rendered = render_template()
        self.assertNotIn('${', rendered)
        self.assertIn('"environment":"dev"', rendered)
        self.assertIn('X-Sssok-Upstream', rendered)
        image = compose_config()['services']['nginx']['image']
        with tempfile.TemporaryDirectory() as conf:
            Path(conf, 'default.conf').write_text(rendered)
            Path(conf, 'upstream.conf').write_text(UPSTREAM)
            subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--mount',
                            f'type=bind,src={conf},dst=/etc/nginx/conf.d,readonly',
                            image, 'nginx', '-t'], check=True,
                           capture_output=True, text=True, timeout=30)

    def test_app_metrics_sends_valid_host_to_app(self):
        # upstream 이름(sssok_app_management)이 Host 로 가면 Tomcat 이 밑줄 든 Host 를 400 으로 거부한다(#793).
        block = re.search(r'location = /app-metrics \{(.*?)\n    \}', render_template(), re.S)
        self.assertIsNotNone(block)
        host = re.search(r'proxy_set_header Host ([^;]+);', block.group(1))
        self.assertIsNotNone(host, '/app-metrics 가 Host 헤더를 지정하지 않는다')
        self.assertNotIn('_', host.group(1))

    def test_dev_uses_only_central_without_legacy_credentials(self):
        # 예전 서버 환경변수가 남아 있어도 중앙 전송을 사용한다.
        config = compose_config(DEV_ALLOY_WRITER='dual')
        self.assertEqual(set(config['volumes']), {'alloy-data'})
        self.assertFalse(config.get('secrets'))
        alloy = config['services']['alloy']
        self.assertNotIn('loki', alloy.get('depends_on', {}))
        self.assertTrue(any(v.get('source', '').endswith('write.central.alloy')
                            for v in alloy['volumes']))
        self.assertTrue(any(v.get('source') == 'alloy-data' and
                            v.get('target') == '/var/lib/alloy/data'
                            for v in alloy['volumes']))
        self.assertEqual(alloy['environment']['SSSOK_ENVIRONMENT'], 'dev')

    def test_workflow_deploys_with_shared_blue_green_script(self):
        workflow = WORKFLOW.read_text()
        self.assertIn('DEPLOY_ENV=dev bash deploy/blue-green.sh deploy', workflow)
        self.assertIn('cp backend/deploy/blue-green.sh', workflow)
        # 배포 스크립트만 바뀌어도 개발에서 먼저 돌아야 운영 전에 확인된다.
        self.assertNotIn("'!backend/deploy/**'", workflow)
        # 활성 칸 파일은 배포 스크립트만 쓴다.
        self.assertNotRegex(workflow, r'(cp |> *)"?\$DEPLOY_PATH/nginx/upstream\.conf')
        # 앱을 직접 재생성하던 이전 방식이 남아 있으면 두 칸 구조와 어긋난다.
        self.assertNotIn('up -d app', workflow)
        self.assertNotIn('localhost:8080', workflow)


if __name__ == '__main__':
    unittest.main()
