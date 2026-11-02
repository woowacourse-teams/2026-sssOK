"""Exercise deployment failures with a fake Docker CLI; no daemon or EC2 needed."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'deploy.sh'
IMAGE = 'ghcr.io/test/gallery@sha256:' + 'a' * 64


class DeployTests(unittest.TestCase):
    def run_deploy(self, failure='', previous=False, invalid_current=None, extra_env=None, changed_data=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            (root / 'gallery').mkdir()
            data = root / 'data'
            data.mkdir()
            receipt = data / 'unacknowledged.json'
            receipt.write_text('pending delivery')
            next_data = root / 'new-data' if changed_data else data
            (root / 'gallery/.env').write_text(f'GALLERY_STANDALONE=true\nGALLERY_DATA_DIR={next_data}\n')
            if previous:
                old = root / 'old'
                old.mkdir()
                for name in ('settings.env', 'image.env', 'compose.yml', 'smoke.py', 'control.py'):
                    (old / name).write_text('')
                (root / 'current').symlink_to(old)
                (old / 'settings.env').write_text(f'GALLERY_DATA_DIR={data}\n')
            if invalid_current == 'broken_link':
                (root / 'current').symlink_to(root / 'missing')
            elif invalid_current == 'directory':
                (root / 'current').mkdir()
            elif invalid_current == 'incomplete_release':
                (root / 'incomplete').mkdir()
                (root / 'current').symlink_to(root / 'incomplete')
            binary = root / 'bin'
            binary.mkdir()
            docker = binary / 'docker'
            docker.write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args = sys.argv[1:]
if os.environ.get('CHECK_GALLERY_ENV'):
    assert not any(key.startswith('GALLERY_') for key in os.environ), 'Leaked GALLERY environment'
root = Path(os.environ['MCP_DEPLOY_ROOT'])
with (root/'calls').open('a') as f: f.write(' '.join(args)+'\\n')
old = str(root/'old/compose.yml') in args
failure = os.environ['FAILURE']
if 'config' in args and '--format' in args:
    settings = Path(args[args.index('--env-file') + 1]).read_text()
    source = next(line.split('=', 1)[1] for line in settings.splitlines() if line.startswith('GALLERY_DATA_DIR='))
    print(json.dumps({'services': {'gallery-mcp': {'volumes': [{'type': 'bind', 'source': source, 'target': '/data'}]}}}))
if failure == 'pull' and 'pull' in args: sys.exit(1)
if failure == 'verify' and 'verify' in args: sys.exit(1)
if failure == 'drain' and 'drain' in args: sys.exit(1)
if failure == 'up' and 'up' in args and not old: sys.exit(1)
if failure == 'resume' and 'resume' in args and not old: sys.exit(1)
if failure == 'smoke' and 'exec' in args and args[-1] == '-' and not old: sys.exit(1)
''')
            docker.chmod(0o755)
            # The production host has flock. The fake harness is single-process.
            lock = binary / 'flock'
            lock.write_text('#!/bin/sh\nexit 0\n')
            lock.chmod(0o755)
            env = {**os.environ, 'PATH': str(binary) + ':' + os.environ['PATH'],
                   'MCP_DEPLOY_ROOT': str(root), 'GALLERY_IMAGE': IMAGE, 'FAILURE': failure}
            env.update(extra_env or {})
            result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
            calls = (root / 'calls').read_text() if (root / 'calls').exists() else ''
            current = (root / 'current').resolve() if (root / 'current').exists() else None
            self.assertEqual(receipt.read_text(), 'pending delivery')
            return result, calls, current == root / 'old', current is not None

    def test_first_deploy(self):
        result, calls, old, exists = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(exists)
        self.assertFalse(old)
        self.assertIn(' verify', calls)
        self.assertNotIn(' drain', calls)

    def test_invalid_current_stops_before_docker_commands(self):
        for kind in ('broken_link', 'directory', 'incomplete_release'):
            with self.subTest(kind=kind):
                result, calls, _, _ = self.run_deploy(invalid_current=kind)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Invalid current release', result.stderr)
                self.assertEqual(calls, '')

    def test_gallery_environment_is_removed_on_deploy_and_rollback(self):
        overrides = {name: 'caller-override' for name in (
            'GALLERY_DATA_DIR', 'GALLERY_MODELS_DIR', 'GALLERY_TOKEN_PATH',
            'GALLERY_BACKEND_TOKEN_PATH', 'GALLERY_DOWNLOAD_HOSTS',
            'GALLERY_STANDALONE', 'GALLERY_BACKEND_URL', 'GALLERY_FUTURE_SETTING')}
        overrides['CHECK_GALLERY_ENV'] = '1'
        for failure in ('', 'smoke'):
            with self.subTest(failure=failure):
                result, calls, old, _ = self.run_deploy(failure, True, extra_env=overrides)
                self.assertNotIn('Leaked GALLERY environment', result.stderr)
                if failure:
                    self.assertNotEqual(result.returncode, 0)
                    self.assertTrue(old)
                    self.assertIn('Previous release restored and verified', result.stdout)
                else:
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertIn(f'Deployed {IMAGE}', result.stdout)
                self.assertIn(' resume', calls)

    def test_preflight_failures_leave_old_release_running(self):
        for failure in ('pull', 'verify'):
            with self.subTest(failure=failure):
                result, calls, old, _ = self.run_deploy(failure, True)
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(old)
                self.assertNotIn(' up ', calls)
                self.assertNotIn(' drain', calls)

    def test_changed_data_directory_keeps_previous_release_and_receipts(self):
        result, calls, old, exists = self.run_deploy(previous=True, changed_data=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Persistent data directory changed', result.stderr)
        self.assertTrue(old)
        self.assertTrue(exists)
        for operation in (' pull', ' run ', ' drain', ' up ', ' stop', ' resume'):
            self.assertNotIn(operation, calls)

    def test_drain_timeout_resumes_without_replacing(self):
        result, calls, old, _ = self.run_deploy('drain', True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(old)
        self.assertNotIn(' up ', calls)
        self.assertIn(' resume', calls)

    def test_start_and_smoke_failure_restore_and_verify_previous(self):
        for failure in ('up', 'smoke'):
            with self.subTest(failure=failure):
                result, calls, old, _ = self.run_deploy(failure, True)
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(old)
                self.assertIn('Previous release restored and verified', result.stdout)
                self.assertIn(' resume', calls)

    def test_resume_failure_restores_previous_current_link(self):
        result, calls, old, exists = self.run_deploy('resume', True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(old)
        self.assertTrue(exists)
        self.assertIn('Previous release restored and verified', result.stdout)

    def test_first_failure_stops_container(self):
        result, calls, _, exists = self.run_deploy('smoke')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(exists)
        self.assertIn(' stop', calls)
        self.assertNotIn(' down', calls)

    def test_successful_update_resumes_and_switches_current(self):
        result, calls, old, exists = self.run_deploy(previous=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(exists)
        self.assertFalse(old)
        self.assertIn(' drain', calls)
        self.assertIn(' resume', calls)


if __name__ == '__main__':
    unittest.main()
