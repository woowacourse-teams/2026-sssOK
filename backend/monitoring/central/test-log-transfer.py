#!/usr/bin/env python3
"""실제 대시보드 선택값의 PromQL 문법과 목적지 격리를 배포 이미지로 검사한다.

Grafana의 단일 값 :regex 보간(정규식 특수문자 이스케이프)과 custom All 값을
재현한다. 브라우저 E2E는 아니며, 보간 결과는 실제 promtool로 평가한다.
"""
import itertools
import json
from pathlib import Path
import re
import subprocess
import tempfile

from validate import compose_config

ROOT = Path(__file__).resolve().parent


def main():
    dashboard = json.loads((ROOT / 'log-transfer.json').read_text())
    variables = {v['name']: v for v in dashboard['templating']['list']}

    def choices(name):
        variable = variables[name]
        assert variable['type'] == 'custom' and not variable['multi']
        values = variable['query'].split(',')
        assert {o['value'] for o in variable['options']} == set(values) | {'$__all'}
        return ['$__all', *values]

    def interpolate(expr, selections):
        for name, value in selections.items():
            # Grafana custom All는 이스케이프하지 않는다. 단일 값은 regex 형식.
            formatted = (variables[name]['allValue'] if value == '$__all' else
                         re.sub(r'([\\.^$*+?()\[\]{}|])', r'\\\1', value))
            expr = expr.replace('${' + name + ':regex}', formatted)
        assert '${' not in expr
        return expr

    targets = [t['expr'] for p in dashboard['panels'] for t in p.get('targets', [])
               if '${destination:regex}' in t.get('expr', '')]
    assert len(targets) == 8, '로그 전송 패널 8개의 목적지 필터를 검사해야 합니다'
    hosts = choices('destination')[1:]
    # IP의 점이 와일드카드로 바뀌는 잘못된 수정도 잡는다.
    samples = [(env, host) for env in ('dev', 'prod')
               for host in [*hosts, '10x0x20x46:13100']]
    series = [{'series': 'loki_write_sent_entries_total{job="alloy",environment="%s",host="%s"}' % pair,
               'values': '10+0x10'} for pair in samples]
    cumulative = next(e for e in targets if 'loki_write_sent_entries_total' in e
                      and 'increase(' not in e and 'rate(' not in e)
    expressions, tests = [], []
    for env, host in itertools.product(choices('environment'), choices('destination')):
        selected = {'environment': env, 'destination': host}
        expressions.extend(interpolate(e, selected) for e in targets)
        expected = [{'labels': '{environment="%s",host="%s"}' % pair, 'value': 10}
                    for pair in samples if (env == '$__all' or pair[0] == env)
                    and (host == '$__all' or pair[1] == host)]
        tests.append({'expr': interpolate(cumulative, selected), 'eval_time': '5m',
                      'exp_samples': expected})
    with tempfile.TemporaryDirectory(prefix='sssok-destination-') as temp:
        path = Path(temp)
        (path / 'queries.yml').write_text(json.dumps({'groups': [{'name': 'destination', 'rules': [
            {'record': f'destination_check_{i}', 'expr': expr} for i, expr in enumerate(expressions)]}]}))
        (path / 'test.yml').write_text(json.dumps({'rule_files': [], 'evaluation_interval': '1m',
            'tests': [{'interval': '1m', 'input_series': series, 'promql_expr_test': tests}]}))
        image = compose_config()['services']['prometheus']['image']
        for args in (['check', 'rules', '/tests/queries.yml'], ['test', 'rules', '/tests/test.yml']):
            subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--mount',
                f'type=bind,src={path},dst=/tests,readonly', '--entrypoint', '/bin/promtool',
                image, *args], check=True)
    print(f'목적지 필터: PromQL {len(expressions)}개·환경/목적지 격리 {len(tests)}개 통과')


if __name__ == '__main__':
    main()
