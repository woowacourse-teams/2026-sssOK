#!/usr/bin/env python3
"""네트워크 없는 amtool로 후보 라우팅과 발생/복구 메시지를 검증한다."""
from contextlib import ExitStack
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile

from validate import compose_config

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('prepare_discord', ROOT / 'prepare-discord.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


def main():
    assert prepare.candidate([]) == (ROOT / 'alertmanager.yml').read_text()
    try:
        prepare.candidate(['prod'])
        raise AssertionError('허용되지 않은 묶음을 수락함')
    except ValueError:
        pass
    image = compose_config()['services']['alertmanager']['image']
    with tempfile.TemporaryDirectory(prefix='sssok-discord-') as tmp, ExitStack() as cleanup:
        root = Path(tmp)
        root.chmod(0o755)
        (root / 'empty.tmpl').write_text('')

        # 같은 격리 컨테이너를 재사용해 라우팅 조합마다 이미지를 다시 띄우지 않는다.
        cid = subprocess.run(['docker', 'run', '--rm', '-d', '--network', 'none',
                              '--mount', f'type=bind,src={root},dst=/test,readonly',
                              '--entrypoint', '/bin/sh', image, '-c', 'sleep 300'],
                             check=True, capture_output=True, text=True, timeout=30).stdout.strip()
        cleanup.callback(subprocess.run, ['docker', 'rm', '-f', cid],
                         check=True, capture_output=True, timeout=30)

        def amtool(*args):
            return subprocess.run(['docker', 'exec', cid, '/bin/amtool', *args],
                                  check=True, capture_output=True, text=True, timeout=30).stdout

        for bundle in prepare.BUNDLES:
            config = prepare.candidate([bundle])
            assert 'send_resolved: true' in config and 'webhook_url:' not in config
            (root / 'candidate.yml').write_text(config)
            amtool('check-config', '/test/candidate.yml')
            for environment, server in [('dev', 'dev'), ('prod', 'prod'), ('monitoring', 'monitoring'), ('dev', '')]:
                for name in sorted(set(sum(prepare.BUNDLES.values(), ())) | {'CentralComponentDown', 'UnknownAlert'}):
                    expected = 'discord-dev' if environment == server == 'dev' and name in prepare.BUNDLES[bundle] else 'bootstrap'
                    amtool('config', 'routes', 'test', '--config.file=/test/candidate.yml',
                           '--verify.receivers=' + expected, 'environment=' + environment,
                           'server=' + server, 'alertname=' + name)

        for status in ('firing', 'resolved'):
            labels = dict(environment='dev', server='dev', alertname='LogEntriesDropped',
                          severity='warning', host='10.0.20.46:13100')
            alert = dict(status=status, labels=dict(labels, instance='dev:19100', reason='rate_limited'),
                         annotations=dict(summary='로그 유실', description='가' * 2000),
                         startsAt='2026-10-14T00:00:00Z', endsAt='2026-10-14T01:00:00Z')
            (root / 'data.json').write_text(json.dumps(dict(status=status, commonLabels=labels,
                                                            alerts=[alert] * 10)))
            def render(template):
                return amtool('template', 'render', '--template.glob=/test/empty.tmpl',
                              '--template.data=/test/data.json', '--template.text=' + template)
            title, message = render(prepare.TITLE), render(prepare.MESSAGE)
            assert status.upper() in title and len(title.strip()) <= 256
            assert len(message.strip()) <= 4096
            for expected in ('10.0.20.46:13100', 'rate_limited', '서버: dev', '최대 3건',
                             '/central/d/sssok-log-transfer?var-environment=dev',
                             '발생 10건 · 복구 0건' if status == 'firing' else '발생 0건 · 복구 10건'):
                assert expected in message, expected
    print('Discord 후보: 기본 무발송·개발 묶음별 라우팅·발생/복구 메시지 검증 통과 (네트워크 차단)')


if __name__ == '__main__':
    main()
