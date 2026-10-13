#!/usr/bin/env python3
"""실제 서버 없이 배포 실패 시 파일 복구·비밀값 보존·상태 기록을 검증한다."""
import json
from pathlib import Path
import tempfile
import subprocess
import tarfile
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

    def prepare_data_backup(self):
        d = self.deployment
        d.backup = self.target / 'backup'
        d.backup.mkdir()
        d.grafana_volume = 'expected-volume'
        data = self.target / 'data'
        data.mkdir()
        (data / 'grafana.db').write_text('old database')
        (data / 'grafana.db').chmod(0o640)
        (data / '.old').write_text('hidden backup')
        with tarfile.open(d.backup / 'grafana-data.tar', 'w') as archive:
            archive.add(data, arcname='.')
        (data / 'grafana.db').write_text('new database')
        for name in ('grafana.db-wal', 'grafana.db-shm', '.new'):
            (data / name).write_text('new')
        (data / 'plugins').mkdir()
        (data / 'plugins/new-plugin').write_text('new')
        return data

    def test_restore_removes_new_files_and_preserves_backup_permissions(self):
        data = self.prepare_data_backup()
        d = self.deployment

        def local_helper(command, **kwargs):
            command = command.replace('/var/lib/grafana', str(data)).replace('/backup', str(d.backup))
            subprocess.run(['sh', '-ec', command], check=True)

        with patch.object(d, 'compose'), patch.object(d, 'container', return_value='grafana'), \
             patch.object(d, 'data_volume', return_value=d.grafana_volume), \
             patch.object(d, 'helper', side_effect=local_helper):
            d.restore_grafana()
        self.assertEqual({p.name for p in data.iterdir()}, {'grafana.db', '.old'})
        self.assertEqual((data / 'grafana.db').read_text(), 'old database')
        self.assertEqual((data / 'grafana.db').stat().st_mode & 0o777, 0o640)

    def test_invalid_backup_does_not_touch_current_data(self):
        data = self.prepare_data_backup()
        d = self.deployment
        for kind in ('corrupt', 'missing-db', 'traversal', 'symlink', 'truncated'):
            with self.subTest(kind=kind):
                archive = d.backup / 'grafana-data.tar'
                if kind == 'corrupt':
                    archive.write_bytes(b'broken archive')
                else:
                    with tarfile.open(archive, 'w') as backup:
                        member = tarfile.TarInfo('../escape' if kind == 'traversal' else 'other')
                        if kind == 'symlink':
                            member.type = tarfile.SYMTYPE
                            member.linkname = '/tmp/outside'
                        backup.addfile(member)
                        if kind == 'truncated':
                            backup.add(data / 'grafana.db', arcname='grafana.db')
                    if kind == 'truncated':
                        archive.write_bytes(archive.read_bytes()[:512])
                with patch.object(d, 'compose'), patch.object(d, 'helper') as helper:
                    with self.assertRaises((RuntimeError, tarfile.TarError)):
                        d.restore_grafana()
                    helper.assert_not_called()
                self.assertEqual((data / 'grafana.db').read_text(), 'new database')
                self.assertTrue((data / '.new').exists())

    def test_wrong_volume_blocks_restore(self):
        data = self.prepare_data_backup()
        d = self.deployment
        with patch.object(d, 'compose'), patch.object(d, 'container', return_value='grafana'), \
             patch.object(d, 'data_volume', return_value='other-volume'), patch.object(d, 'helper') as helper:
            with self.assertRaisesRegex(RuntimeError, '볼륨과 다릅니다'):
                d.restore_grafana()
            helper.assert_not_called()
        self.assertEqual((data / 'grafana.db').read_text(), 'new database')

    def test_extract_failure_preserves_current_data(self):
        data = self.prepare_data_backup()
        d = self.deployment

        def failing_extract(command, **kwargs):
            command = command.replace('/var/lib/grafana', str(data)).replace('/backup', str(d.backup))
            # 백업 검증 이후 실제 컨테이너의 tar 추출이 실패하는 상황.
            subprocess.run(['sh', '-ec', 'tar() { return 1; }; ' + command], check=True)

        with patch.object(d, 'compose'), patch.object(d, 'container', return_value='grafana'), \
             patch.object(d, 'data_volume', return_value=d.grafana_volume), \
             patch.object(d, 'helper', side_effect=failing_extract):
            with self.assertRaises(subprocess.CalledProcessError):
                d.restore_grafana()
        self.assertEqual((data / 'grafana.db').read_text(), 'new database')
        self.assertTrue((data / '.new').exists())
        self.assertEqual(list(d.backup.glob('grafana-restore.*')), [])

    def test_data_mount_must_be_writable_volume_without_nested_mounts(self):
        base = {'Destination': '/var/lib/grafana', 'Type': 'volume', 'RW': True, 'Name': 'expected-volume'}
        for mounts in ([], [{**base, 'Type': 'bind'}], [{**base, 'RW': False}],
                       [base, {'Destination': '/var/lib/grafana/plugins'}]):
            with self.subTest(mounts=mounts), patch('deploy.run', return_value=
                    subprocess.CompletedProcess([], 0, json.dumps([{'Mounts': mounts}]))):
                with self.assertRaises(RuntimeError):
                    self.deployment.data_volume('grafana')

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
