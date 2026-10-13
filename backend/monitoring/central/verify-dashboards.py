#!/usr/bin/env python3
"""서버의 기존 Grafana 자격증명으로 5개 자동 등록을 확인한다. 비밀값은 출력하지 않는다."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--container', required=True)
parser.add_argument('--auth-only', action='store_true')
args = parser.parse_args()
result = subprocess.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', args.container],
                        check=True, capture_output=True, text=True)
values = dict(item.split('=', 1) for item in json.loads(result.stdout))
credentials = base64.b64encode((values['GF_SECURITY_ADMIN_USER'] + ':' +
                               values['GF_SECURITY_ADMIN_PASSWORD']).encode()).decode()
# 비밀값을 URL·명령 인자·로그로 보내지 않고 loopback HTTP 헤더에만 사용한다.
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def get(path):
    request = urllib.request.Request('http://127.0.0.1:3000/central/api/' + path,
                                     headers={'Authorization': 'Basic ' + credentials})
    with opener.open(request, timeout=5) as response:
        return json.load(response)


get('search?type=dash-db')
if args.auth_only:
    print('기존 Grafana 인증 확인 완료')
else:
    root = Path(__file__).resolve().parent
    for name in ('host-overview', 'backend-overview', 'logs', 'media-transfer', 'log-transfer'):
        dashboard = json.loads((root / (name + '.json')).read_text())
        for attempt in range(12):
            try:
                actual = get('dashboards/uid/' + dashboard['uid'])
                if (actual['meta'].get('provisioned') and actual['meta'].get('folderTitle') == 'sssOK'
                        and actual['dashboard']['title'] == dashboard['title']):
                    break
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
            time.sleep(3)
        else:
            raise SystemExit('자동 등록 확인 실패: ' + dashboard['uid'])
    print('중앙 대시보드 5개 자동 등록 확인 완료')
