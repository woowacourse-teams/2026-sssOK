#!/usr/bin/env python3
"""서버 선택 보존·잘못된 입력 차단·실제 이미지의 비밀 파일 접근을 격리 검증한다."""
import json
import os
import subprocess
import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import notifications
from validate import compose_config

ROOT = Path(__file__).resolve().parent


class NotificationTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.target = Path(tmp.name) / 'target'
        self.staged = Path(tmp.name) / 'staged'
        self.target.mkdir()
        self.staged.mkdir()
        self.policy = self.target / notifications.SELECTION
        self.secret = self.target / notifications.SECRET

    def enable(self):
        self.policy.write_text('{"dev": ["app"]}')
        self.secret.parent.mkdir(exist_ok=True)
        self.secret.write_text('https://discord.com/api/webhooks/123/test-only')
        self.secret.chmod(0o640)

    def test_default_and_repeated_deployment_and_disable(self):
        self.assertIsNone(notifications.prepare(ROOT, self.target, self.staged))
        self.assertEqual((self.staged / 'alertmanager.yml').read_text(), (ROOT / 'alertmanager.yml').read_text())
        self.enable()
        for _ in range(2):
            self.assertEqual(notifications.prepare(ROOT, self.target, self.staged), self.secret)
            self.assertIn('discord-dev', (self.staged / 'alertmanager.yml').read_text())
            mount = json.loads((self.staged / notifications.OVERRIDE).read_text())['services']['alertmanager']['volumes'][0]
            self.assertEqual(mount['source'], str(self.secret))
            self.assertTrue(mount['read_only'])
            self.assertNotIn('test-only', (self.staged / 'alertmanager.yml').read_text())
        self.policy.write_text('{"dev": []}')
        self.assertIsNone(notifications.prepare(ROOT, self.target, self.staged))
        self.assertEqual(json.loads((self.staged / notifications.OVERRIDE).read_text()), {'services': {}})
        self.assertTrue(self.secret.exists())

    def test_dotenv_generation_rotation_and_rollback_mount(self):
        image = compose_config()['services']['alertmanager']['image']
        env_file = self.target / '.env'
        first = 'https://discord.com/api/webhooks/123/first-test-only'
        second = 'https://discord.com/api/webhooks/123/second-test-only'
        env_file.write_text(f"GRAFANA_ADMIN_PASSWORD=unrelated\n{notifications.ENV_KEY}='{first}'\n")
        original_env = env_file.read_bytes()
        # URL을 등록하는 것만으로는 활성화/파일 생성이 일어나지 않는다.
        self.assertIsNone(notifications.prepare(ROOT, self.target, self.staged, image=image))
        self.assertFalse(self.secret.parent.exists())
        self.policy.write_text('{"dev": ["app"]}')
        with patch.dict(os.environ, {notifications.ENV_KEY: 'shell-must-not-win'}):
            old_secret = notifications.prepare(ROOT, self.target, self.staged, image=image)
        old_mount = (self.staged / notifications.OVERRIDE).read_bytes()
        notifications.validate(self.staged, image, old_secret)
        self.assertEqual(env_file.read_bytes(), original_env)
        # Docker Desktop은 호스트 파일의 소유권/모드를 가상화한다. Linux에서 실권한 확인.
        if sys.platform == 'linux':
            self.assertEqual(old_secret.stat().st_mode & 0o777, 0o400)
        env_file.write_text(f'{notifications.ENV_KEY}="{second}"\n')
        new_secret = notifications.prepare(ROOT, self.target, self.staged, image=image)
        self.assertNotEqual(old_secret, new_secret)
        notifications.validate(self.staged, image, new_secret)
        # 복구는 이전 override 경로를 복원한다. 두 파일의 값은 독립적으로 유지된다.
        (self.staged / notifications.OVERRIDE).write_bytes(old_mount)
        restored = Path(json.loads(old_mount)['services']['alertmanager']['volumes'][0]['source'])
        for path, value in ((restored, first), (new_secret, second)):
            content = subprocess.run(['docker', 'run', '--rm', '--network', 'none',
                '--mount', f'type=bind,src={path},dst=/secret,readonly',
                '--entrypoint', '/bin/cat', image, '/secret'],
                capture_output=True, text=True, check=True).stdout.strip()
            self.assertEqual(content, value)
        for path in self.staged.iterdir():
            self.assertNotIn(first, path.read_text())
            self.assertNotIn(second, path.read_text())
        self.policy.write_text('{"dev": []}')
        self.assertIsNone(notifications.prepare(ROOT, self.target, self.staged, image=image))
        self.assertTrue(old_secret.exists())

    def test_dotenv_invalid_missing_and_links_fail_without_value(self):
        image = compose_config()['services']['alertmanager']['image']
        self.policy.write_text('{"dev": ["app"]}')
        env_file = self.target / '.env'
        for value in ('', 'do-not-expose-this', 'https://example.com/secret'):
            env_file.write_text(f'{notifications.ENV_KEY}={value}\n')
            with self.assertRaises(RuntimeError) as error:
                notifications.prepare(ROOT, self.target, self.staged, image=image)
            if value:
                self.assertNotIn(value, str(error.exception))
            self.assertFalse(self.secret.parent.exists())
        env_file.unlink()
        with self.assertRaises(RuntimeError):
            notifications.prepare(ROOT, self.target, self.staged, image=image)
        env_file.symlink_to(self.policy)
        with self.assertRaises(RuntimeError):
            notifications.prepare(ROOT, self.target, self.staged, image=image)
        env_file.unlink()
        env_file.write_text(f'{notifications.ENV_KEY}=https://discord.com/api/webhooks/123/test-only\n')
        self.secret.parent.symlink_to(self.staged, target_is_directory=True)
        with self.assertRaises(RuntimeError):
            notifications.prepare(ROOT, self.target, self.staged, image=image)

    def test_permission_failure_removes_candidate_without_exposing_url(self):
        image = compose_config()['services']['alertmanager']['image']
        self.policy.write_text('{"dev": ["app"]}')
        value = 'https://discord.com/api/webhooks/123/permission-test-secret'
        (self.target / '.env').write_text(f'{notifications.ENV_KEY}={value}\n')
        real_run = subprocess.run

        def fail_permission(args, **kwargs):
            if '--user' in args and '0' in args:
                return subprocess.CompletedProcess(args, 1, '', 'failure')
            return real_run(args, **kwargs)

        with patch('notifications.subprocess.run', side_effect=fail_permission):
            with self.assertRaisesRegex(RuntimeError, '권한 설정 실패') as error:
                notifications.prepare(ROOT, self.target, self.staged, image=image)
        self.assertNotIn(value, str(error.exception))
        self.assertEqual(list(self.secret.parent.iterdir()), [])
        self.assertFalse((self.staged / notifications.OVERRIDE).exists())

    def test_invalid_selection_fails_closed(self):
        for text in ('broken', '{}', '[]', '{"prod": ["app"]}', '{"dev": "app"}',
                     '{"dev": [null]}', '{"dev": ["unknown"]}'):
            with self.subTest(text=text):
                self.policy.write_text(text)
                with self.assertRaises(RuntimeError):
                    notifications.prepare(ROOT, self.target, self.staged)

    def test_missing_broad_permissions_and_invalid_secret(self):
        self.enable()
        for kind in ('missing', 'public', 'invalid', 'symlink'):
            with self.subTest(kind=kind):
                self.secret.unlink(missing_ok=True)
                self.enable()
                if kind == 'missing':
                    self.secret.unlink()
                elif kind == 'public':
                    self.secret.chmod(0o644)
                elif kind == 'invalid':
                    self.secret.write_text('do-not-print-this-value')
                else:
                    self.secret.unlink()
                    self.secret.symlink_to(self.policy)
                with self.assertRaises(RuntimeError) as error:
                    notifications.prepare(ROOT, self.target, self.staged)
                self.assertNotIn('do-not-print-this-value', str(error.exception))

    def test_real_image_config_and_secret_readability_without_network(self):
        image = compose_config()['services']['alertmanager']['image']
        notifications.prepare(ROOT, self.target, self.staged)
        notifications.validate(self.staged, image, None)
        self.enable()
        notifications.prepare(ROOT, self.target, self.staged)
        config = subprocess.run(['docker', 'compose', '--project-directory', str(self.target),
                                 '--env-file', os.devnull, '-f', str(ROOT / 'compose.yml'),
                                 '-f', str(ROOT / 'compose.web.yml'), '-f', str(self.staged / notifications.OVERRIDE),
                                 'config', '--format', 'json'],
                                env=dict(os.environ, GRAFANA_ADMIN_PASSWORD='test-only'),
                                check=True, capture_output=True, text=True)
        mounts = json.loads(config.stdout)['services']['alertmanager']['volumes']
        self.assertEqual(len([m for m in mounts if m['target'] == '/run/secrets/central_discord_webhook']), 1)
        base = next(m for m in mounts if m['target'] == '/etc/alertmanager/alertmanager.yml')
        self.assertEqual(base['source'], str(self.target / 'alertmanager.yml'))
        # Docker Desktop의 파일 소유권 매핑과 무관하게 내용만 읽는 격리 검사.
        # 서버에서 prepare는 공개 읽기 권한을 거부하고 실제 컨테이너 사용자로 다시 검사한다.
        self.secret.chmod(0o644)
        notifications.validate(self.staged, image, self.secret)
        self.secret.unlink()
        with self.assertRaisesRegex(RuntimeError, '후보 검사 실패'):
            notifications.validate(self.staged, image, self.secret)


if __name__ == '__main__':
    unittest.main()
