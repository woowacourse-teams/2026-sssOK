#!/usr/bin/env python3
"""개발 알림 이전 후보를 stdout에 생성한다. 배포·비밀값 읽기·발송은 하지 않는다."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BUNDLES = {
    'app': ('BackendDown', 'BackendHigh5xxErrorRate', 'BackendHighLatency'),
    'host': ('HostHighCPU', 'HostMemoryLow', 'HostDiskSpaceLow', 'HostDiskSpaceCritical', 'HostDiskWillFillIn24h'),
    'logs': ('LogPipelineDown', 'LogShippingFailing', 'LogEntriesDropped'),
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--enable-dev', action='append', choices=sorted(BUNDLES), default=[],
                        help='이전할 묶음. 여러 번 지정 가능. 생략하면 발송 없는 기존 설정')
    args = parser.parse_args()
    print(candidate(args.enable_dev), end='')


if __name__ == '__main__':
    main()
