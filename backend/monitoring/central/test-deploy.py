#!/usr/bin/env python3
"""실제 서버 없이 배포 실패 시 파일 복구·비밀값 보존·상태 기록을 검증한다."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from deploy import Deployment, FILES, SERVICES


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.source, self.target = root / 'source', root / 'target'
        self.source.mkdir()
        self.target.mkdir()
        for name in FILES:
            (self.source / name).write_text('candidate:' + name)
            if name != 'dashboards.yml':
                (self.target / name).write_text('old:' + name)
        (self.target / '.env').write_text('private-value')
        (self.target / 'deployed-commit.txt').write_text('previous')
        self.deployment = Deployment(self.source, self.target, 'a' * 40)

    def test_success_preserves_secret_and_records_commit(self):
        d = self.deployment
        with patch.object(d, 'preflight'), patch.object(d, 'snapshot_grafana'), \
             patch.object(d, 'compose'), patch.object(d, 'container', return_value='grafana'), \
             patch.object(d, 'apply_services'), patch('deploy.run'):
            d.execute()
        self.assertEqual((self.target / '.env').read_text(), 'private-value')
        self.assertFalse((d.backup / '.env').exists())
        self.assertEqual((self.target / 'deployed-commit.txt').read_text(), 'a' * 40 + '\n')
        self.assertEqual((self.target / 'dashboards.yml').stat().st_mode & 0o777, 0o644)

    def test_apply_failure_restores_previous_files_and_grafana(self):
        d = self.deployment
        with patch.object(d, 'preflight'), patch.object(d, 'snapshot_grafana'), \
             patch.object(d, 'compose'), patch.object(d, 'restore_grafana') as restore, \
             patch.object(d, 'apply_services', side_effect=[RuntimeError('apply failed'), None]), \
             patch('deploy.run'):
            with self.assertRaisesRegex(RuntimeError, 'apply failed'):
                d.execute()
        restore.assert_called_once()
        self.assertFalse((self.target / 'dashboards.yml').exists())
        self.assertEqual((self.target / 'prometheus.yml').read_text(), 'old:prometheus.yml')
        self.assertEqual((self.target / '.env').read_text(), 'private-value')
        self.assertEqual((self.target / 'deployed-commit.txt').read_text(), 'previous')

    def test_backup_failure_leaves_files_and_restarts_grafana(self):
        d = self.deployment
        with patch.object(d, 'preflight'), \
             patch.object(d, 'snapshot_grafana', side_effect=RuntimeError('backup failed')), \
             patch.object(d, 'compose') as compose, patch.object(d, 'apply_services') as apply:
            with self.assertRaisesRegex(RuntimeError, 'backup failed'):
                d.execute()
        compose.assert_called_once_with('start', 'grafana')
        apply.assert_not_called()
        self.assertEqual((self.target / 'compose.yml').read_text(), 'old:compose.yml')

    def test_health_failure_restores_without_recording_success(self):
        d = self.deployment
        with patch.object(d, 'preflight'), patch.object(d, 'snapshot_grafana'), \
             patch.object(d, 'compose'), patch.object(d, 'restore_grafana') as restore, \
             patch.object(d, 'apply_services'), \
             patch('deploy.run', side_effect=[RuntimeError('health failed'), None]):
            with self.assertRaisesRegex(RuntimeError, 'health failed'):
                d.execute()
        restore.assert_called_once()
        self.assertEqual((self.target / 'deployed-commit.txt').read_text(), 'previous')
        self.assertEqual((self.target / 'loki.yml').read_text(), 'old:loki.yml')

    def test_image_change_rejected_before_snapshot(self):
        old = {'services': {name: {'image': name + ':1', 'volumes': []} for name in SERVICES}}
        new = json.loads(json.dumps(old))
        new['services']['prometheus']['image'] = 'prometheus:2'
        from subprocess import CompletedProcess
        with patch.object(self.deployment, 'compose', side_effect=[
                CompletedProcess([], 0, json.dumps(old)), CompletedProcess([], 0, json.dumps(new))]):
            with self.assertRaisesRegex(RuntimeError, '이미지 버전 변경'):
                self.deployment.preflight()
        self.deployment.lock.close()


if __name__ == '__main__':
    unittest.main()
