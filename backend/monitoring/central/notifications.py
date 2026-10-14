"""개발 알림 선택과 Compose secret 준비. 실제 웹훅 값은 설정·로그에 넣지 않는다."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
OVERRIDE = 'compose.notifications.yml'
SELECTION = 'discord-selection.json'
ENV_KEY = 'CENTRAL_DISCORD_WEBHOOK_URL'
SECRET_PATH = '/run/secrets/central_discord_webhook'
WEBHOOK_PATTERN = r'https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+'

BUNDLES = {
    'app': ('MetricsEndpointDown', 'BackendDown', 'BackendHigh5xxErrorRate', 'BackendHighLatency'),
    'host': ('MetricsEndpointDown', 'NodeMetricsDown', 'HostHighCPU', 'HostMemoryLow', 'HostDiskSpaceLow', 'HostDiskSpaceCritical', 'HostDiskWillFillIn24h'),
    'logs': ('MetricsEndpointDown', 'LogPipelineDown', 'LogShippingFailing', 'LogEntriesDropped'),
}
TITLE = '[{{ .Status | toUpper }}] {{ .CommonLabels.environment }} / {{ .CommonLabels.alertname }}'
MESSAGE = '''환경: {{ .CommonLabels.environment }} · 서버: {{ .CommonLabels.server }}
심각도: {{ .CommonLabels.severity }}
{{ if .CommonLabels.host }}로그 전송 목적지: {{ .CommonLabels.host }}
{{ end }}발생 {{ len .Alerts.Firing }}건 · 복구 {{ len .Alerts.Resolved }}건
{{ range $i, $alert := .Alerts }}{{ if lt $i 3 }}
[{{ .Status | toUpper }}] {{ .Labels.instance }}{{ if .Labels.reason }} · 사유: {{ .Labels.reason }}{{ end }}
{{ printf "%.300s" .Annotations.summary }}
{{ printf "%.500s" .Annotations.description }}
{{ end }}{{ end }}
{{ if gt (len .Alerts) 3 }}상세는 최대 3건만 표시합니다. 전체 상태는 대시보드에서 확인하세요.
{{ end }}[호스트](https://monitor.ssssok.com/central/d/sssok-host-overview?var-environment=dev) · [앱](https://monitor.ssssok.com/central/d/sssok-central-backend?var-environment=dev) · [로그 전송](https://monitor.ssssok.com/central/d/sssok-log-transfer?var-environment=dev)'''


def candidate(bundles):
    unknown = set(bundles) - BUNDLES.keys()
    if unknown:
        raise ValueError(f'알 수 없는 이전 묶음: {sorted(unknown)}')
    base = (ROOT / 'alertmanager.yml').read_text()
    if not bundles:
        return base
    # 기존 grouping/inhibition을 그대로 사용한다. 기준 파일 구조가 바뀌면 조용히 누락하지 않는다.
    anchor = 'receivers:\n  - name: bootstrap\n'
    if base.count(anchor) != 1:
        raise ValueError('기준 Alertmanager 수신 설정이 변경됐습니다. 생성기를 확인하세요.')
    names = sorted({name for bundle in bundles for name in BUNDLES[bundle]})
    routing = ('  routes:\n    - receiver: discord-dev\n      matchers:\n'
               '        - \'environment="dev"\'\n        - \'server="dev"\'\n'
               f'        - \'alertname=~"{"|".join(names)}"\'\n')
    receiver = ('  - name: discord-dev\n    discord_configs:\n'
                '      - webhook_url_file: /run/secrets/central_discord_webhook\n'
                '        send_resolved: true\n'
                f'        title: {json.dumps(TITLE, ensure_ascii=False)}\n'
                f'        message: {json.dumps(MESSAGE, ensure_ascii=False)}\n')
    return base.replace(anchor, routing + anchor + receiver).replace(
        '# 알림 판정·중복 억제만 검증한다. 외부 발송 설정과 비밀값은 연결하지 않는다.',
        '# 생성된 설정: 선택한 개발 알림만 Discord 대상으로 지정.')


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
            or any(not isinstance(item, str) or item not in BUNDLES for item in selection['dev'])):
        raise RuntimeError('Discord 선택 형식은 {"dev": ["app", "host", "logs"]}의 부분집합입니다')
    (staged / 'alertmanager.yml').write_text(candidate(selection['dev']))
    override = {'services': {}}
    if selection['dev']:
        override = json.loads((source / 'compose.discord.yml').read_text())
    (staged / OVERRIDE).write_text(json.dumps(override))
    for name in ('alertmanager.yml', OVERRIDE):
        (staged / name).chmod(0o644)
    return bool(selection['dev'])


def environment(webhook=None):
    env = dict(os.environ)
    env.pop(ENV_KEY, None)  # 평소에는 서버 .env 우선. 자동 복구만 이전 값을 사용한다.
    if webhook is not None:
        env[ENV_KEY] = webhook
    return env


def previous_webhook(container):
    result = subprocess.run(['docker', 'exec', container, 'cat', SECRET_PATH],
                            capture_output=True, text=True, timeout=30)
    value = result.stdout.strip()
    if result.returncode or not re.fullmatch(WEBHOOK_PATTERN, value):
        raise RuntimeError('자동 복구용 기존 Discord 웹훅 읽기 실패')
    return value


def validate(staged, image, enabled, target):
    """실제 Compose secret을 격리 컨테이너에 주입해 읽기·형식·설정을 검사한다."""
    override = json.loads((staged / OVERRIDE).read_text())
    service = {'image': image, 'network_mode': 'none', 'volumes': [{
        'type': 'bind', 'source': str(staged / 'alertmanager.yml'),
        'target': '/test/alertmanager.yml', 'read_only': True}]}
    service.update(override['services'].get('alertmanager', {}))
    config = {'services': {'check': service}, 'secrets': override.get('secrets', {})}
    command = 'exec /bin/amtool check-config /test/alertmanager.yml'
    if enabled:
        # 값은 명령 인수/출력에 포함하지 않는다. 한 줄 URL만 허용한다.
        command = (f'value=$(cat {SECRET_PATH}); test -n "$value"; '
                   'test "$(printf %s "$value" | wc -l)" -eq 0; '
                   f'printf %s "$value" | grep -Eq "^{WEBHOOK_PATTERN}$"; ' + command)
    with tempfile.TemporaryDirectory(prefix='sssok-discord-check-') as tmp:
        path = Path(tmp) / 'compose.json'
        path.write_text(json.dumps(config))
        args = ['docker', 'compose', '-p', Path(tmp).name, '--env-file', str(target / '.env'),
                '-f', str(path)]
        try:
            result = subprocess.run(args + ['run', '--rm', '--no-deps', '--entrypoint', '/bin/sh',
                'check', '-ec', command], env=environment(), capture_output=True, text=True, timeout=60)
            if result.returncode:
                raise RuntimeError('Discord 후보 검사 실패: .env 웹훅·Compose secret·Alertmanager 설정 확인')
        finally:
            subprocess.run(args + ['down'], env=environment(), capture_output=True, timeout=30)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--enable-dev', action='append', choices=sorted(BUNDLES), default=[])
    print(candidate(parser.parse_args().enable_dev), end='')
