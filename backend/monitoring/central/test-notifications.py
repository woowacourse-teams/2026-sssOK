#!/usr/bin/env python3
"""선택·Compose secret·교체/복구·라우팅을 실제 이미지로 검사한다. 외부 발송 없음."""
from contextlib import ExitStack
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import notifications
from validate import compose_config

ROOT = Path(__file__).resolve().parent


class NotificationTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='sssok-notification-test-')
        self.addCleanup(temp.cleanup)
        self.target = Path(temp.name)
        self.staged = self.target / 'staged'
        self.staged.mkdir()
        self.policy = self.target / notifications.SELECTION
        self.env = self.target / '.env'
        self.env.write_text('')
        self.image = compose_config()['services']['alertmanager']['image']

    def prepare(self):
        return notifications.prepare(ROOT, self.target, self.staged)

    def test_disabled_and_selection_validation(self):
        self.assertFalse(self.prepare())
        notifications.validate(self.staged, self.image, False, self.target)
        self.assertEqual((self.staged / 'alertmanager.yml').read_text(), (ROOT / 'alertmanager.yml').read_text())
        for text in ('broken', '{}', '[]', '{"prod": ["app"]}', '{"dev": "app"}',
                     '{"dev": [null]}', '{"dev": ["unknown"]}'):
            self.policy.write_text(text)
            with self.assertRaises(RuntimeError):
                self.prepare()
        self.policy.write_text('{"dev": ["app"]}')
        for _ in range(2):
            self.assertTrue(self.prepare())
        self.policy.write_text('{"dev": []}')
        self.assertFalse(self.prepare())
        self.assertEqual(json.loads((self.staged / notifications.OVERRIDE).read_text()), {'services': {}})
        self.assertFalse((self.target / 'secrets').exists())

    def test_invalid_or_missing_value_fails_without_exposure(self):
        self.policy.write_text('{"dev": ["app"]}')
        self.prepare()
        for value in ('', 'do-not-expose', 'https://example.com/secret'):
            self.env.write_text(f'{notifications.ENV_KEY}={value}\n')
            with self.assertRaises(RuntimeError) as error:
                notifications.validate(self.staged, self.image, True, self.target)
            if value:
                self.assertNotIn(value, str(error.exception))

    def test_compose_secret_permissions_rotation_and_restore(self):
        first = 'https://discord.com/api/webhooks/123/first-test-only'
        second = 'https://discord.com/api/webhooks/123/second-test-only'
        self.env.write_text(f"{notifications.ENV_KEY}='{first}'\n")
        original = self.env.read_bytes()
        self.assertFalse(self.prepare())  # 값 등록만으로 발송하지 않는다.
        self.policy.write_text('{"dev": ["app"]}')
        self.prepare()
        with patch.dict(os.environ, {notifications.ENV_KEY: 'shell-must-not-win'}):
            notifications.validate(self.staged, self.image, True, self.target)
        self.assertEqual(self.env.read_bytes(), original)
        config = json.loads((self.staged / notifications.OVERRIDE).read_text())
        config['services']['alertmanager'].update({'image': self.image, 'network_mode': 'none',
            'entrypoint': ['/bin/sh', '-ec', 'sleep 120']})
        path = self.target / 'probe.json'
        path.write_text(json.dumps(config))
        args = ['docker', 'compose', '-p', self.target.name, '--env-file', str(self.env), '-f', str(path)]
        self.addCleanup(subprocess.run, args + ['down'], capture_output=True, env=notifications.environment())

        def compose(*command, webhook=None):
            return subprocess.run(args + list(command), env=notifications.environment(webhook),
                                  capture_output=True, text=True, check=True).stdout.strip()

        compose('up', '-d')
        cid = compose('ps', '-q', 'alertmanager')
        old = notifications.previous_webhook(cid)
        self.assertEqual(old, first)
        mode = compose('exec', '-T', 'alertmanager', 'stat', '-c', '%u:%g:%a', notifications.SECRET_PATH)
        self.assertEqual(mode, '65534:65534:400')
        self.env.write_text(f'{notifications.ENV_KEY}="{second}"\n')
        compose('up', '-d', '--force-recreate')
        self.assertEqual(notifications.previous_webhook(compose('ps', '-q', 'alertmanager')), second)
        compose('up', '-d', '--force-recreate', webhook=old)
        cid = compose('ps', '-q', 'alertmanager', webhook=old)
        self.assertEqual(notifications.previous_webhook(cid), first)
        inspected = subprocess.run(['docker', 'inspect', cid], capture_output=True, text=True, check=True).stdout
        self.assertNotIn(first, inspected)
        self.assertNotIn(second, inspected)
        for file in self.staged.iterdir():
            self.assertNotIn(first, file.read_text())
        self.assertFalse((self.target / 'secrets').exists())

    def test_routes_and_templates(self):
        check_routes_and_templates()


def check_routes_and_templates():
    assert notifications.candidate([]) == (ROOT / 'alertmanager.yml').read_text()
    try:
        notifications.candidate(['prod'])
        raise AssertionError('허용되지 않은 묶음을 수락함')
    except ValueError:
        pass
    image = compose_config()['services']['alertmanager']['image']
    with tempfile.TemporaryDirectory(prefix='sssok-discord-') as tmp, ExitStack() as cleanup:
        root = Path(tmp)
        root.chmod(0o755)
        (root / 'empty.tmpl').write_text('')

        # 같은 격리 컨테이너를 재사용해 라우팅 조합마다 이미지를 다시 띄우지 않는다.
        cid = subprocess.run(['docker', 'run', '--rm', '-d', '--network', 'none',
                              '--mount', f'type=bind,src={root},dst=/test,readonly',
                              '--entrypoint', '/bin/sh', image, '-c', 'sleep 300'],
                             check=True, capture_output=True, text=True, timeout=30).stdout.strip()
        cleanup.callback(subprocess.run, ['docker', 'rm', '-f', cid],
                         check=True, capture_output=True, timeout=30)

        def amtool(*args):
            return subprocess.run(['docker', 'exec', cid, '/bin/amtool', *args],
                                  check=True, capture_output=True, text=True, timeout=30).stdout

        for bundle in notifications.BUNDLES:
            config = notifications.candidate([bundle])
            assert 'send_resolved: true' in config and 'webhook_url:' not in config
            (root / 'candidate.yml').write_text(config)
            amtool('check-config', '/test/candidate.yml')
            for environment, server in [('dev', 'dev'), ('prod', 'prod'), ('monitoring', 'monitoring'), ('dev', '')]:
                for name in sorted(set(sum(notifications.BUNDLES.values(), ())) | {'CentralComponentDown', 'UnknownAlert'}):
                    expected = 'discord-dev' if environment == server == 'dev' and name in notifications.BUNDLES[bundle] else 'bootstrap'
                    amtool('config', 'routes', 'test', '--config.file=/test/candidate.yml',
                           '--verify.receivers=' + expected, 'environment=' + environment,
                           'server=' + server, 'alertname=' + name)

        for status in ('firing', 'resolved'):
            labels = dict(environment='dev', server='dev', alertname='LogEntriesDropped',
                          severity='warning', host='10.0.20.46:13100')
            alert = dict(status=status, labels=dict(labels, instance='dev:19100', reason='rate_limited'),
                         annotations=dict(summary='로그 유실', description='가' * 2000),
                         startsAt='2026-10-14T00:00:00Z', endsAt='2026-10-14T01:00:00Z')
            (root / 'data.json').write_text(json.dumps(dict(status=status, commonLabels=labels,
                                                            alerts=[alert] * 10)))
            def render(template):
                return amtool('template', 'render', '--template.glob=/test/empty.tmpl',
                              '--template.data=/test/data.json', '--template.text=' + template)
            title, message = render(notifications.TITLE), render(notifications.MESSAGE)
            assert status.upper() in title and len(title.strip()) <= 256
            assert len(message.strip()) <= 4096
            for expected in ('10.0.20.46:13100', 'rate_limited', '서버: dev', '최대 3건',
                             '/central/d/sssok-log-transfer?var-environment=dev',
                             '발생 10건 · 복구 0건' if status == 'firing' else '발생 0건 · 복구 10건'):
                assert expected in message, expected
    print('Discord 후보: 기본 무발송·개발 묶음별 라우팅·발생/복구 메시지 검증 통과 (네트워크 차단)')


if __name__ == '__main__':
    unittest.main()
