#!/usr/bin/env python3
"""중앙 CI: 실제 비밀값·데이터 볼륨·서버 네트워크 없이 설정을 검사한다."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent


def run(args, **kwargs):
    subprocess.run(args, check=True, **kwargs)


def compose_config():
    result = subprocess.run(
        ['docker', 'compose', '--env-file', os.devnull,
         '-f', str(ROOT / 'compose.yml'), '-f', str(ROOT / 'compose.web.yml'),
         'config', '--format', 'json'],
        env=dict(os.environ, GRAFANA_ADMIN_PASSWORD='validation-only'),
        check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def main():
    services = compose_config()['services']
    assert set(services) == {'prometheus', 'loki', 'grafana', 'alertmanager', 'node-exporter', 'nginx'}

    def check(service, arguments, files, entrypoint=None, extra=()):
        args = ['docker', 'run', '--rm', '--network', 'none'] + list(extra)
        for source, target in files:
            assert Path(source).is_file(), f'설정 파일 누락: {source}'
            args += ['--mount', f'type=bind,src={source},dst={target},readonly']
        if entrypoint:
            args += ['--entrypoint', entrypoint]
        run(args + [services[service]['image']] + arguments)

    prom_files = [(ROOT / name, '/etc/prometheus/' + name)
                  for name in ('prometheus.yml', 'rules.yml', 'rules.test.yml')]
    check('prometheus', ['check', 'config', '/etc/prometheus/prometheus.yml'], prom_files, '/bin/promtool')
    check('prometheus', ['test', 'rules', '/etc/prometheus/rules.test.yml'], prom_files, '/bin/promtool',
          ['--workdir', '/etc/prometheus'])
    check('alertmanager', ['check-config', '/etc/alertmanager/config.yml'],
          [(ROOT / 'alertmanager.yml', '/etc/alertmanager/config.yml')], '/bin/amtool')
    check('loki', ['-config.file=/etc/loki/config.yml', '-verify-config=true'],
          [(ROOT / 'loki.yml', '/etc/loki/config.yml')])
    # nginx -t는 정적 upstream 이름을 해석한다. 가짜 주소로 문법만 검사하며 접속하지 않는다.
    check('nginx', ['nginx', '-t'], [(ROOT / 'nginx.conf', '/etc/nginx/conf.d/default.conf')],
          extra=['--add-host', 'grafana:127.0.0.1'])

    grafana = services['grafana']
    files = [v for v in grafana['volumes'] if v['target'].endswith('.json')]
    assert len(files) == 5, '대시보드 마운트는 5개여야 합니다'
    uids, expressions = set(), []
    substitutions = {'${environment:regex}': 'dev|prod', '${destination:regex}': '.*',
                     '$__rate_interval': '5m', '$environment': 'dev', '$window': '5m'}

    def panels(items):
        for panel in items:
            yield panel
            yield from panels(panel.get('panels', []))

    for mount in files:
        assert mount.get('read_only') and not mount.get('bind', {}).get('create_host_path', False)
        document = json.loads(Path(mount['source']).read_text())
        assert document['uid'] not in uids, '중복 대시보드 UID'
        uids.add(document['uid'])
        for panel in panels(document['panels']):
            ds = panel.get('datasource', {}).get('uid')
            for target in panel.get('targets', []):
                uid = target.get('datasource', {}).get('uid', ds)
                assert uid in ('prometheus', 'loki'), f'알 수 없는 데이터소스: {uid}'
                if uid != 'prometheus' or not target.get('expr'):
                    continue
                expression = target['expr']
                for name, value in substitutions.items():
                    expression = expression.replace(name, value)
                assert not re.search(r'\$[A-Za-z_{]', expression), f'미치환 변수: {expression}'
                expressions.append(expression)
    with tempfile.TemporaryDirectory(prefix='sssok-promql-') as tmp:
        path = Path(tmp) / 'queries.yml'
        path.write_text('groups:\n- name: dashboard-syntax\n  rules:\n' + ''.join(
            f'  - record: dashboard_check_{i}\n    expr: {json.dumps(expr)}\n'
            for i, expr in enumerate(expressions)))
        path.chmod(0o644)
        check('prometheus', ['check', 'rules', '/tmp/queries.yml'], [(path, '/tmp/queries.yml')], '/bin/promtool')
    for script in ROOT.glob('*.sh'):
        run(['bash', '-n', str(script)])
    print(f'중앙 Compose·서비스 설정·알림 시험·대시보드 5개 및 PromQL {len(expressions)}개 검사 통과')


if __name__ == '__main__':
    main()
