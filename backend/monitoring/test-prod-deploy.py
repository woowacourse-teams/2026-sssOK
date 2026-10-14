#!/usr/bin/env python3
"""운영 워크플로의 앱 정상 확인 이후 구간을 모의 명령으로 실행한다."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/deploy-prod.yml'


def monitoring_steps():
    source = WORKFLOW.read_text()
    # 중첩된 롤백 조건이 아닌, 앱 헬스체크 실패 분기의 닫는 fi를 찾는다.
    app_check = source.index('          if [ "$APP_HEALTHY" != true ]; then')
    end = source.index('\n          fi\n', app_check) + len('\n          fi\n')
    return textwrap.dedent(source[end:].split('\n  alias:', 1)[0])


MOCK_COMMANDS = r'''
set -eu
COMPOSE=compose_mock
connection_attempts=0
record() { printf '%s\n' "$1" >> "$TRACE"; }
check_monitoring_connections() {
    connection_attempts=$((connection_attempts + 1))
    record connection-check
    if [ "$FAIL_AT" = recovery ]; then return 1; fi
    if [ "$RECOVER" = 1 ] && [ "$connection_attempts" = 1 ]; then return 1; fi
    return 0
}
compose_mock() {
    case "$*" in
        'up -d --force-recreate prometheus loki grafana') record recovery ;;
        'exec -T alloy /bin/alloy validate /etc/alloy/config')
            record validate
            if [ "$FAIL_AT" = validate ]; then return 1; fi ;;
        "exec -T nginx wget -q -T 10 -O - --post-data= http://alloy:12345/-/reload")
            record reload
            if [ "$FAIL_AT" = reload ]; then return 1; fi ;;
        'logs --tail 100 prometheus loki grafana'|'logs --tail 100 nginx grafana') record logs ;;
        *) record "unexpected-compose:$*"; return 99 ;;
    esac
}
curl() { record routing; [ "$FAIL_AT" != routing ]; }
sleep() { :; }
bash() {
    [ "$*" = monitoring/check-prod-node.sh ] || return 99
    record node-check
    [ "$FAIL_AT" != node-check ]
}
'''


class MonitoringOrderTest(unittest.TestCase):
    def execute_steps(self, fail_at='', recover=False):
        with tempfile.TemporaryDirectory(prefix='sssok-prod-order-') as tmp:
            trace = Path(tmp) / 'trace'
            result = subprocess.run(
                ['bash', '-c', MOCK_COMMANDS + monitoring_steps()],
                env=dict(os.environ, TRACE=str(trace), FAIL_AT=fail_at,
                         RECOVER='1' if recover else '0', LC_ALL='C'),
                capture_output=True, text=True, timeout=10)
            events = trace.read_text().splitlines() if trace.exists() else []
        self.assertFalse(any(event.startswith('unexpected-') for event in events), events)
        return result.returncode, events

    def test_success_runs_all_observation_checks(self):
        code, events = self.execute_steps()
        self.assertEqual(code, 0)
        self.assertEqual(events, ['connection-check', 'routing', 'validate', 'reload', 'node-check'])

    def test_recovery_then_all_checks_can_succeed(self):
        code, events = self.execute_steps(recover=True)
        self.assertEqual(code, 0)
        self.assertEqual(events, ['connection-check', 'recovery', 'connection-check',
                                  'routing', 'validate', 'reload', 'node-check'])

    def test_observation_failures_do_not_skip_recovery_or_routing(self):
        stages = ['validate', 'reload', 'node-check']
        for failed in stages:
            with self.subTest(failed=failed):
                code, events = self.execute_steps(failed, recover=True)
                self.assertNotEqual(code, 0)
                self.assertEqual(events, ['connection-check', 'recovery', 'connection-check',
                                          'routing'] + stages[:stages.index(failed) + 1])

    def test_unrecovered_monitoring_is_still_failure(self):
        code, events = self.execute_steps('recovery')
        self.assertNotEqual(code, 0)
        self.assertEqual(events, ['connection-check', 'recovery', 'connection-check', 'logs'])

    def test_routing_failure_is_not_reported_as_success(self):
        code, events = self.execute_steps('routing')
        self.assertNotEqual(code, 0)
        self.assertEqual(events, ['connection-check'] + ['routing'] * 30 + ['logs'])


if __name__ == '__main__':
    unittest.main()
