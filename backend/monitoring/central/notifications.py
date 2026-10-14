"""서버의 발송 선택과 .env를 배포 후보에 반영한다. 비밀값을 로그·설정에 출력하지 않는다."""
import json
import os
import tempfile
import uuid
from pathlib import Path
import re
import stat
import subprocess
import sys

OVERRIDE = 'compose.notifications.yml'
SELECTION = 'discord-selection.json'
SECRET = 'secrets/central_discord_webhook'


ENV_KEY = 'CENTRAL_DISCORD_WEBHOOK_URL'


def generate_secret(target, image):
    """Compose의 dotenv 해석을 사용한다. 기존 실행/복구용 파일은 덮어쓰지 않는다."""
    env_file = target / '.env'
    if env_file.is_symlink() or not env_file.is_file():
        raise RuntimeError('중앙 .env 일반 파일이 필요합니다')
    try:
        with tempfile.TemporaryDirectory(prefix='sssok-webhook-env-') as tmp:
            probe = Path(tmp) / 'compose.json'
            probe.write_text(json.dumps({'services': {'probe': {'image': 'unused',
                'environment': {ENV_KEY: '${' + ENV_KEY + ':-}'}}}}))
            # 셸 변수보다 서버 .env를 원본으로 사용한다. 결과/오류는 로그에 출력하지 않는다.
            env = dict(os.environ)
            env.pop(ENV_KEY, None)
            result = subprocess.run(['docker', 'compose', '--env-file', str(env_file),
                '-f', str(probe), 'config', '--format', 'json'], env=env,
                capture_output=True, text=True, timeout=30)
            if result.returncode:
                raise ValueError()
            value = json.loads(result.stdout)['services']['probe']['environment'][ENV_KEY]
        if not isinstance(value, str) or not re.fullmatch(
                r'https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+', value):
            raise ValueError()
    except (OSError, ValueError, KeyError, subprocess.SubprocessError):
        raise RuntimeError('중앙 .env의 CENTRAL_DISCORD_WEBHOOK_URL 형식을 확인하세요') from None

    directory = target / 'secrets'
    if directory.is_symlink():
        raise RuntimeError('비밀 파일 디렉터리는 심볼릭 링크일 수 없습니다')
    directory.mkdir(mode=0o700, exist_ok=True)
    path = directory / ('central_discord_webhook-' + uuid.uuid4().hex)
    try:
        # 실제 이미지의 기본 사용자 확인. 호스트 sudo와 고정 UID 가정이 필요 없다.
        identity = subprocess.run(['docker', 'run', '--rm', '--network', 'none',
            '--entrypoint', '/bin/sh', image, '-ec', 'id -u; id -g'],
            capture_output=True, text=True, timeout=30)
        ids = identity.stdout.split()
        if identity.returncode or len(ids) != 2 or not all(x.isdigit() for x in ids):
            raise ValueError()
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as stream:
            stream.write(value + '\n')
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
            '--mount', f'type=bind,src={path},dst=/secret', '--entrypoint', '/bin/sh', image,
            '-ec', f'chown {ids[0]}:{ids[1]} /secret && chmod 400 /secret && '
            f'test "$(stat -c %u:%g:%a /secret)" = "{ids[0]}:{ids[1]}:400"'],
            capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise ValueError()
    except (OSError, ValueError, subprocess.SubprocessError):
        path.unlink(missing_ok=True)
        raise RuntimeError('Discord 비밀 파일 자동 생성·권한 설정 실패') from None
    return path


def prepare(source, target, staged, image=None):
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
        if image is not None:
            secret = generate_secret(target, image() if callable(image) else image)
        else:
            # 기존 파일 기반 호출과 복구 검증 호환용. 중앙 CD는 항상 image를 전달한다.
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
