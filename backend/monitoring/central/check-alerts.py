#!/usr/bin/env python3
"""중앙 호스트에서 규칙 적용·평가·수집을 조회한다. 알림 발송이나 설정 변경은 하지 않는다."""
import json
import sys
import urllib.parse
import urllib.request

EXPECTED = {
    'CentralComponentDown', 'CentralMemoryLow', 'BackendDown', 'BackendHigh5xxErrorRate',
    'HostDiskSpaceLow', 'HostDiskSpaceCritical', 'HostDiskWillFillIn24h',
    'LogPipelineDown', 'LogShippingFailing', 'LogEntriesDropped', 'LokiRejectingLogs',
}
TARGETS = {(env, env, job) for env in ('dev', 'prod') for job in ('node', 'sssok-backend', 'alloy')}


def get(path):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open('http://127.0.0.1:9090/api/v1/' + path, timeout=10) as response:
        result = json.load(response)
    if result.get('status') != 'success':
        raise RuntimeError('Prometheus API 조회 실패')
    if result.get('warnings'):
        raise RuntimeError('Prometheus API 경고가 있어 결과를 확정할 수 없습니다')
    return result['data']


def inspect(rules_data, target_data):
    errors, lines = [], []
    rules = [r for group in rules_data['groups'] for r in group['rules'] if r['type'] == 'alerting']
    names = [r['name'] for r in rules]
    missing, extra = EXPECTED - set(names), set(names) - EXPECTED
    if missing:
        errors.append('미적용 규칙: ' + ', '.join(sorted(missing)))
    if extra:
        errors.append('예상 외 규칙(구버전·중복 여부 확인): ' + ', '.join(sorted(extra)))
    if len(names) != len(set(names)):
        errors.append('중복된 규칙 이름이 있습니다')
    for rule in rules:
        if rule.get('health') != 'ok' or rule.get('lastError') or rule.get('lastEvaluation', '').startswith('0001-') or not rule.get('lastEvaluation'):
            errors.append('평가 미완료 또는 오류: ' + rule['name'])
        lines.append(f"{rule['name']}: {rule.get('state', 'unknown')}")
        for alert in rule.get('alerts', []):
            labels = alert['labels']
            lines.append('  ' + ' / '.join(f'{key}={labels[key]}' for key in
                         ('environment', 'server', 'instance', 'host', 'reason') if key in labels))
    samples = target_data['result']
    for environment, server, job in sorted(TARGETS):
        selected = [item for item in samples if all(item['metric'].get(k) == v for k, v in
                    (('environment', environment), ('server', server), ('job', job)))]
        if len(selected) != 1 or float(selected[0]['value'][1]) != 1:
            errors.append(f'수집 실패·누락·중복: {environment}/{server}/{job}')
    return errors, lines


def main():
    try:
        rules = get('rules?type=alert')
        expression = 'up{environment=~"dev|prod",job=~"node|sssok-backend|alloy"}'
        targets = get('query?' + urllib.parse.urlencode({'query': expression}))
        errors, lines = inspect(rules, targets)
        print('\n'.join(lines))
        if errors:
            raise RuntimeError('\n'.join(errors))
        print('정상: 11개 알림 규칙 평가, 개발·운영 6개 대상 수집')
        print('pending/firing은 실제 판정 결과입니다. Discord 발송·억제 상태·로그 전송 성공은 별도 확인하세요.')
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print(f'알림 적용 점검 실패: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
