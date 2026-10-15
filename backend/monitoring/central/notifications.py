"""개발·운영 알림 선택과 Compose secret 준비. 실제 웹훅 값은 설정·로그에 넣지 않는다."""
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
    'platform': ('CentralComponentDown', 'LokiRejectingLogs'),
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
{{ end }}[호스트](https://monitor.ssssok.com/central/d/sssok-host-overview?var-environment={{ .CommonLabels.environment }}) · [앱](https://monitor.ssssok.com/central/d/sssok-central-backend?var-environment={{ .CommonLabels.environment }}) · [로그 전송](https://monitor.ssssok.com/central/d/sssok-log-transfer?var-environment={{ .CommonLabels.environment }})'''


def candidate(bundles, prod_bundles=(), monitoring_bundles=()):
    selection = {'dev': bundles, 'prod': prod_bundles, 'monitoring': monitoring_bundles}
    for env, selected in selection.items():
        allowed = {'platform', 'host'} if env == 'monitoring' else {'app', 'host', 'logs'}
        unknown = set(selected) - allowed
        if unknown:
            raise ValueError(f'알 수 없는 이전 묶음: {sorted(unknown)}')
    base = (ROOT / 'alertmanager.yml').read_text()
    if not any(selection.values()):
        return base
    anchor = 'receivers:\n  - name: bootstrap\n'
    if base.count(anchor) != 1:
        raise ValueError('기준 Alertmanager 수신 설정이 변경됐습니다. 생성기를 확인하세요.')
    routing = '  routes:\n'
    receivers = ''
    for env, selected in selection.items():
        if not selected:
            continue
        names = sorted({name for bundle in selected for name in BUNDLES[bundle]})
        routing += (f'    - receiver: discord-{env}\n      matchers:\n'
                    f'        - \'environment="{env}"\'\n'
                    f'        - \'server="{env}"\'\n'
                    f'        - \'alertname=~"{"|".join(names)}"\'\n')
        receivers += (f'  - name: discord-{env}\n    discord_configs:\n'
                      '      - webhook_url_file: /run/secrets/central_discord_webhook\n'
                      '        send_resolved: true\n'
                      f'        title: {json.dumps(TITLE, ensure_ascii=False)}\n'
                      f'        message: {json.dumps(MESSAGE, ensure_ascii=False)}\n')
    return base.replace(anchor, routing + anchor + receivers).replace(
        '# 알림 판정·중복 억제만 검증한다. 외부 발송 설정과 비밀값은 연결하지 않는다.',
        '# 생성된 설정: 환경별로 선택한 알림만 Discord 대상으로 지정.')


def prepare(source, target, staged):
    policy = target / SELECTION
    if policy.is_symlink():
        raise RuntimeError('Discord 선택 파일은 심볼릭 링크일 수 없습니다')
    try:
        selection = json.loads(policy.read_text()) if policy.exists() else {'dev': []}
    except (ValueError, OSError):
        raise RuntimeError('Discord 선택 파일을 읽을 수 없습니다') from None
    if (not isinstance(selection, dict) or not selection or set(selection) - {'dev', 'prod', 'monitoring'}
            or any(not isinstance(items, list)
                   or any(not isinstance(item, str) or item not in BUNDLES for item in items)
                   for items in selection.values())):
        raise RuntimeError('Discord 선택 형식은 dev/prod의 app·host·logs, monitoring의 platform·host 목록이어야 합니다')
    try:
        config = candidate(selection.get('dev', []), selection.get('prod', []), selection.get('monitoring', []))
    except ValueError as error:
        raise RuntimeError('Discord 선택 형식 오류: ' + str(error)) from None
    (staged / 'alertmanager.yml').write_text(config)
    override = {'services': {}}
    if any(selection.values()):
        override = json.loads((source / 'compose.discord.yml').read_text())
    (staged / OVERRIDE).write_text(json.dumps(override))
    for name in ('alertmanager.yml', OVERRIDE):
        (staged / name).chmod(0o644)
    return any(selection.values())


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
    parser.add_argument('--enable-dev', action='append', choices=['app', 'host', 'logs'], default=[])
    parser.add_argument('--enable-prod', action='append', choices=['app', 'host', 'logs'], default=[])
    parser.add_argument('--enable-monitoring', action='append', choices=['platform', 'host'], default=[])
    args = parser.parse_args()
    print(candidate(args.enable_dev, args.enable_prod, args.enable_monitoring), end='')
