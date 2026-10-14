"""서버의 발송 선택을 배포 후보에 반영한다. 비밀값은 복사하거나 출력하지 않는다."""
import json
from pathlib import Path
import re
import stat
import subprocess
import sys

OVERRIDE = 'compose.notifications.yml'
SELECTION = 'discord-selection.json'
SECRET = 'secrets/central_discord_webhook'


def prepare(source, target, staged):
    policy = target / SELECTION
    if policy.is_symlink():
        raise RuntimeError('Discord 선택 파일은 심볼릭 링크일 수 없습니다')
    try:
        selection = json.loads(policy.read_text()) if policy.exists() else {'dev': []}
    except (ValueError, OSError):
        raise RuntimeError('Discord 선택 파일을 읽을 수 없습니다') from None
    if (not isinstance(selection, dict) or set(selection) != {'dev'}
            or not isinstance(selection['dev'], list)
            or any(not isinstance(item, str) or item not in ('app', 'host', 'logs')
                   for item in selection['dev'])):
        raise RuntimeError('Discord 선택 형식은 {"dev": ["app", "host", "logs"]}의 부분집합입니다')
    args = [sys.executable, '-B', str(source / 'prepare-discord.py')]
    for bundle in sorted(set(selection['dev'])):
        args += ['--enable-dev', bundle]
    config = subprocess.run(args, check=True, capture_output=True, text=True, timeout=30).stdout
    (staged / 'alertmanager.yml').write_text(config)
    override = {'services': {}}
    secret = None
    if selection['dev']:
        secret = target / SECRET
        if (secret.parent.is_symlink() or secret.is_symlink() or not secret.is_file()
                or stat.S_IMODE(secret.stat().st_mode) & 0o027):
            raise RuntimeError('Discord 비밀 파일이 없거나 권한이 넓습니다. 일반 파일·권한 640 이하가 필요합니다')
        # 오류에 URL을 포함하지 않는다. 읽을 때도 크기를 제한한다.
        try:
            with secret.open() as stream:
                value = stream.read(4097).strip()
            valid = re.fullmatch(r'https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+', value)
        except (OSError, UnicodeError):
            valid = False
        if not valid:
            raise RuntimeError('Discord 비밀 파일의 웹훅 URL 형식을 확인하세요')
        override['services']['alertmanager'] = {'volumes': [{
            'type': 'bind', 'source': str(secret),
            'target': '/run/secrets/central_discord_webhook', 'read_only': True,
            'bind': {'create_host_path': False}}]}
    (staged / OVERRIDE).write_text(json.dumps(override))
    for name in ('alertmanager.yml', OVERRIDE):
        (staged / name).chmod(0o644)
    return secret


def validate(staged, image, secret):
    args = ['docker', 'run', '--rm', '--network', 'none', '--mount',
            f'type=bind,src={staged / "alertmanager.yml"},dst=/test/alertmanager.yml,readonly']
    if secret:
        args += ['--mount', f'type=bind,src={secret},dst=/run/secrets/central_discord_webhook,readonly']
    command = 'exec /bin/amtool check-config /test/alertmanager.yml'
    if secret:
        command = 'test -r /run/secrets/central_discord_webhook && test -s /run/secrets/central_discord_webhook && ' + command
    result = subprocess.run(args + ['--entrypoint', '/bin/sh', image, '-ec', command],
                            capture_output=True, text=True, timeout=30)
    if result.returncode:
        # amtool의 원문 오류에는 설정 값이 포함될 수 있으므로 CI에 내보내지 않는다.
        raise RuntimeError('Alertmanager 후보 검사 실패: 설정 문법과 컨테이너의 비밀 파일 읽기 권한을 확인하세요')
