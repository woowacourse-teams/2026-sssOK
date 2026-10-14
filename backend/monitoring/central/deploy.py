#!/usr/bin/env python3
"""중앙 설정 전용 배포. 같은 이미지·볼륨을 유지하고 실패 시 설정/Grafana DB를 복구한다."""
import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

import notifications

FILES = ('compose.yml', 'compose.web.yml', 'prometheus.yml', 'rules.yml',
         'alertmanager.yml', 'loki.yml', 'datasources.yml', 'dashboards.yml', 'nginx.conf',
         'host-overview.json', 'backend-overview.json', 'logs.json', 'media-transfer.json',
         'log-transfer.json', 'check.sh', 'check-alerts.py', 'verify-dashboards.py',
         'compose.discord.yml', 'notifications.py')
MANAGED_FILES = FILES + (notifications.OVERRIDE,)
SERVICES = ('prometheus', 'alertmanager', 'loki', 'grafana', 'node-exporter', 'nginx')


def run(args, capture=False, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=capture, timeout=300, **kwargs)


class Deployment:
    def __init__(self, source, target, commit):
        self.source, self.target, self.commit = source, target, commit
        self.backup = None
        self.old_config = None
        self.lock = None
        self.staged = source
        self.staging = None
        self.rollback_webhook = None
        self.restoring = False

    def compose(self, *args, source=None, capture=False):
        source = source or self.target
        override = source / notifications.OVERRIDE
        extra = ['-f', str(override)] if override.is_file() else []
        return run(['docker', 'compose', '--project-name', 'sssok-monitoring',
                    '--project-directory', str(self.target), '--env-file', str(self.target / '.env'),
                    '-f', str(source / 'compose.yml'), '-f', str(source / 'compose.web.yml'), *extra, *args],
                   capture=capture, env=notifications.environment(
                       self.rollback_webhook if self.restoring else None))

    def container(self, service):
        ids = self.compose('ps', '-a', '-q', service, capture=True).stdout.split()
        if len(ids) != 1:
            raise RuntimeError(f'{service}: 컨테이너 하나가 필요합니다')
        return ids[0]

    def preflight(self):
        if not self.target.is_dir() or not (self.target / '.env').is_file():
            raise RuntimeError('기존 중앙 배포 폴더와 .env가 필요합니다')
        if self.source == self.target:
            raise RuntimeError('후보 소스와 배포 폴더를 분리하세요')
        if not (self.target / 'check.sh').is_file():
            raise RuntimeError('복구 확인에 사용할 기존 check.sh가 필요합니다')
        self.lock = (self.target / '.central-deploy.lock').open('a')
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        for name in FILES:
            if not (self.source / name).is_file():
                raise RuntimeError(f'후보 파일 누락: {name}')
            if (self.target / name).is_symlink():
                raise RuntimeError(f'기존 배포 파일의 심볼릭 링크는 지원하지 않습니다: {name}')
        if (self.target / notifications.OVERRIDE).is_symlink():
            raise RuntimeError('알림 Compose 파일의 심볼릭 링크는 지원하지 않습니다')
        self.staging = tempfile.TemporaryDirectory(prefix='sssok-central-candidate-')
        self.staged = Path(self.staging.name)
        for name in FILES:
            shutil.copyfile(self.source / name, self.staged / name)
        enabled = notifications.prepare(self.source, self.target, self.staged)
        old = json.loads(self.compose('config', '--format', 'json', capture=True).stdout)
        new = json.loads(self.compose('config', '--format', 'json', source=self.staged, capture=True).stdout)
        notifications.validate(self.staged, new['services']['alertmanager']['image'], enabled, self.target)
        self.old_config = old
        if set(old['services']) != set(SERVICES) or set(new['services']) != set(SERVICES):
            raise RuntimeError('기존 중앙 6개 서비스만 배포할 수 있습니다')
        if old.get('volumes') != new.get('volumes'):
            raise RuntimeError('저장 볼륨 정의 변경은 별도 이전 검증이 필요합니다')
        for name in SERVICES:
            before, after = old['services'][name], new['services'][name]
            if before['image'] != after['image']:
                raise RuntimeError(f'{name}: 이미지 버전 변경은 별도 배포 검증이 필요합니다')
            if before.get('mem_limit') != after.get('mem_limit'):
                raise RuntimeError(f'{name}: 메모리 한도 변경은 부하 검증 후 적용하세요')
            volumes = lambda svc: [v for v in svc.get('volumes', []) if v['type'] == 'volume']
            if volumes(before) != volumes(after):
                raise RuntimeError(f'{name}: 데이터 마운트 변경 감지')
            cid = self.container(name)
            inspected = json.loads(run(['docker', 'inspect', cid], capture=True).stdout)[0]
            if not inspected['State']['Running'] or inspected['Config']['Image'] != before['image']:
                raise RuntimeError(f'{name}: 실행 상태와 기존 Compose 이미지를 먼저 일치시키세요')
            # 태그가 재게시됐어도 현재 실행 중인 이미지 ID와 같아야 한다. pull은 수행하지 않는다.
            image_id = run(['docker', 'image', 'inspect', '--format', '{{.Id}}', after['image']], capture=True).stdout.strip()
            if image_id != inspected['Image']:
                raise RuntimeError(f'{name}: 로컬 이미지 태그와 실행 이미지 ID가 다릅니다')
        if old.get('secrets', {}).get('central_discord_webhook', {}).get('environment') == notifications.ENV_KEY:
            self.rollback_webhook = notifications.previous_webhook(self.container('alertmanager'))
        self.grafana_id = self.container('grafana')
        self.grafana_volume = self.data_volume(self.grafana_id)
        self.grafana_image = old['services']['grafana']['image']
        self.grafana_owner = run(['docker', 'exec', self.grafana_id, 'id', '-u'], capture=True).stdout.strip()
        self.grafana_group = run(['docker', 'exec', self.grafana_id, 'id', '-g'], capture=True).stdout.strip()
        if not self.grafana_owner.isdigit() or not self.grafana_group.isdigit():
            raise RuntimeError('Grafana 데이터 소유자 확인 실패')
        size_kb = int(run(['docker', 'exec', self.grafana_id, 'du', '-sk', '/var/lib/grafana'], capture=True).stdout.split()[0])
        if shutil.disk_usage(self.target).free < size_kb * 1024 + 256 * 1024 * 1024:
            raise RuntimeError('Grafana 백업과 256MiB 여유 공간이 필요합니다')
        run([sys.executable, str(self.source / 'verify-dashboards.py'), '--container', self.grafana_id, '--auth-only'])

    def data_volume(self, container):
        mounts = json.loads(run(['docker', 'inspect', container], capture=True).stdout)[0]['Mounts']
        data = [m for m in mounts if m['Destination'] == '/var/lib/grafana']
        if (len(data) != 1 or data[0]['Type'] != 'volume' or not data[0].get('RW')
                or any(m['Destination'].startswith('/var/lib/grafana/') for m in mounts)):
            raise RuntimeError('Grafana 데이터 볼륨과 하위 마운트를 확인하세요')
        return data[0]['Name']

    def helper(self, command, container=None):
        run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
             '--volumes-from', container or self.container('grafana'),
             '--mount', f'type=bind,src={self.backup},dst=/backup',
             '--entrypoint', 'sh', self.grafana_image, '-ec', command])

    def backup_files(self):
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
        self.backup = self.target / 'backups' / (stamp + '-' + self.commit[:12])
        self.backup.mkdir(parents=True, mode=0o700)
        for name in MANAGED_FILES:
            path = self.target / name
            if path.exists():
                shutil.copy2(path, self.backup / name)
        # .env는 덮어쓰지도, CI 산출물이나 백업에 복사하지도 않는다.
        print(f'설정 백업: {self.backup}', flush=True)

    def snapshot_grafana(self):
        self.compose('stop', 'grafana')
        self.helper('tar -cf /backup/grafana-data.tar -C /var/lib/grafana .; '
                    f'chown {os.getuid()}:{os.getgid()} /backup/grafana-data.tar; '
                    'chmod 600 /backup/grafana-data.tar')

    def restore_grafana(self):
        # 삭제 전 백업 전체를 읽어 손상·경로 이탈·링크/특수 파일을 거부한다.
        # 실제 추출도 별도 디렉터리에 먼저 끝내므로 추출 실패가 현재 DB를 지우지 않는다.
        archive = self.backup / 'grafana-data.tar'
        total = 0
        has_database = False
        with tarfile.open(archive, 'r:') as backup:
            for member in backup:
                path = PurePosixPath(member.name)
                if path.is_absolute() or '..' in path.parts or not (member.isfile() or member.isdir()):
                    raise RuntimeError('Grafana 백업에 지원하지 않는 경로·파일이 있습니다')
                if member.isfile():
                    has_database |= str(path) == 'grafana.db'
                    total += member.size
                    with backup.extractfile(member) as content:
                        while content.read(1024 * 1024):
                            pass
        if not has_database:
            raise RuntimeError('Grafana 백업 DB가 없습니다')
        if shutil.disk_usage(self.backup).free < total + 256 * 1024 * 1024:
            raise RuntimeError('Grafana 복원 임시 공간이 부족합니다')
        self.compose('stop', 'grafana')
        container = self.container('grafana')
        if self.data_volume(container) != self.grafana_volume:
            raise RuntimeError('Grafana 복원 대상이 백업 당시 볼륨과 다릅니다')
        self.helper('stage=$(mktemp -d /backup/grafana-restore.XXXXXX); '
                    'trap \'rm -rf "$stage"\' EXIT; '
                    'tar -xpf /backup/grafana-data.tar -C "$stage"; '
                    'test -f "$stage/grafana.db"; '
                    'test -d /var/lib/grafana && test ! -L /var/lib/grafana; '
                    'find /var/lib/grafana -mindepth 1 -maxdepth 1 -exec rm -rf {} +; '
                    'cp -a "$stage/." /var/lib/grafana/', container=container)

    def restore(self, snapshot):
        self.restoring = True
        self.compose('stop', 'grafana')
        if snapshot:
            # 현재 컨테이너의 마운트 파일이 아직 존재할 때 같은 볼륨으로 복원한다.
            # 신규 플러그인·숨김 파일·SQLite 보조 파일까지 백업 당시 상태로 되돌린다.
            self.restore_grafana()
        for name in MANAGED_FILES:
            saved = self.backup / name
            if saved.exists():
                shutil.copy2(saved, self.target / name)
            elif (self.target / name).exists():
                (self.target / name).unlink()
        self.apply_services(force_reload=True)
        # 최초 전환 이전에는 자동 등록이 없을 수 있으므로 이전 check.sh 범위로 확인한다.
        run(['bash', str(self.target / 'check.sh')])
        print('이전 설정·Grafana 데이터 복구 및 기본 점검 완료', flush=True)

    def apply_services(self, force_reload=False):
        # Compose는 서비스 정의 변경을 감지하지만 bind 파일 내용 변경은 감지하지 않는다.
        # Loki·Nginx는 실제 파일이 바뀔 때만 강제 재생성해 로그 전송 경로 중단을 줄인다.
        # 복원은 적용 도중 어떤 파일을 읽었는지 확정할 수 없으므로 강제 재생성한다.
        for service in ('loki', 'prometheus', 'alertmanager', 'grafana', 'nginx'):
            config = {'loki': 'loki.yml', 'nginx': 'nginx.conf'}.get(service)
            changed = (config is None or force_reload or self.backup is None
                       or not (self.backup / config).is_file()
                       or (self.backup / config).read_bytes() != (self.target / config).read_bytes())
            recreate = ['--force-recreate'] if changed else []
            self.compose('up', '-d', '--no-deps', '--pull', 'never', *recreate, service)
        self.compose('up', '-d', '--no-deps', '--pull', 'never', 'node-exporter')

    def execute(self):
        try:
            self._execute()
        finally:
            self.rollback_webhook = None
            if self.staging:
                self.staging.cleanup()
            if self.lock:
                self.lock.close()

    def _execute(self):
        self.preflight()
        self.backup_files()
        snapshot = False
        try:
            self.snapshot_grafana()
            snapshot = True
            # bind mount의 inode를 유지하도록 복사한다. 모든 프로세스는 이후 재생성한다.
            for name in MANAGED_FILES:
                if name == notifications.OVERRIDE and not (self.staged / name).is_file():
                    continue
                shutil.copyfile(self.staged / name, self.target / name)
                (self.target / name).chmod(0o644)
            self.compose('config', '--quiet')
            self.apply_services()
            run(['bash', str(self.target / 'check.sh')])
            run([sys.executable, str(self.target / 'verify-dashboards.py'), '--container', self.container('grafana')])
            marker = self.target / '.deployed-commit.tmp'
            marker.write_text(self.commit + '\n')
            marker.replace(self.target / 'deployed-commit.txt')
            print(f'중앙 설정 배포 성공: {self.commit}', flush=True)
        except Exception:
            print('중앙 배포 실패: 이전 설정 복구 시작', file=sys.stderr, flush=True)
            try:
                if snapshot:
                    self.restore(snapshot=True)
                else:
                    # 아직 설정 파일을 바꾸지 않은 백업 실패 단계다.
                    self.compose('start', 'grafana')
            except Exception as error:
                print(f'자동 복구 실패. 백업 경로: {self.backup}; 원인: {error}', file=sys.stderr)
            raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--target', type=Path, required=True)
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        parser.error('40자리 소문자 커밋 SHA가 필요합니다')
    if args.target.resolve() != Path('/home/ubuntu/monitoring'):
        parser.error('중앙 배포 경로 /home/ubuntu/monitoring만 허용합니다')
    os.umask(0o077)
    Deployment(args.source.resolve(), args.target.resolve(), args.commit).execute()


if __name__ == '__main__':
    main()
