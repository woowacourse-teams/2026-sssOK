#!/usr/bin/env python3
"""파일 배치 전에 후보 Compose의 지표 바인딩과 실제 호스트 주소를 비교한다."""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import subprocess
import sys


def read_json(command, **kwargs):
    # Compose 출력에는 비밀값이 포함된다. 실패 시에도 stdout/stderr를 로그에 노출하지 않는다.
    result = subprocess.run(command, capture_output=True, text=True, **kwargs)
    if result.returncode:
        raise ValueError(f'{command[0]} 조회 실패: 설치·권한·설정 파일을 확인하세요')
    return json.loads(result.stdout)


def validate_binding(config, interfaces):
    ports = [p for p in config['services']['nginx']['ports'] if p['target'] == 19100]
    if len(ports) != 1 or str(ports[0].get('published')) != '19100':
        raise ValueError('Nginx 지표 포트는 19100 하나여야 합니다')
    address = ipaddress.IPv4Address(ports[0].get('host_ip', ''))
    if not any(address in ipaddress.IPv4Network(cidr) for cidr in
               ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')):
        raise ValueError('MONITORING_PRIVATE_IP는 RFC1918 사설 IPv4여야 합니다')
    assigned = {info['local'] for interface in interfaces
                for info in interface.get('addr_info', []) if info.get('family') == 'inet'}
    if str(address) not in assigned:
        raise ValueError(f'지표 바인딩 주소 {address}가 이 서버에 없습니다. .env의 MONITORING_PRIVATE_IP를 확인하세요')
    return str(address)


def check(environment, deploy_path):
    deploy_path = Path(deploy_path).resolve()
    compose = Path(__file__).resolve().parents[1] / f'docker-compose.{environment}.yml'
    # image.env는 이후 배포에서 이미지·버전만 새로 기록한다. 최초 배포도 검사할 수 있도록
    # 이미지에만 임시값을 주고, 사설 IP 등 서버 설정은 실제 .env를 Compose로 해석한다.
    config = read_json([
        'docker', 'compose', '--project-directory', str(deploy_path),
        '--env-file', str(deploy_path / '.env'), '-f', str(compose),
        'config', '--format', 'json',
    ], env={**os.environ, 'BACKEND_IMAGE': 'preflight-only:latest'})
    interfaces = read_json(['ip', '-j', '-4', 'addr', 'show'])
    return validate_binding(config, interfaces)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=('dev', 'prod'))
    parser.add_argument('deploy_path')
    args = parser.parse_args()
    try:
        print(check(args.environment, args.deploy_path))
    except (ValueError, KeyError, TypeError, OSError) as error:
        print(f'사설 IP 사전 검사 실패: {error}', file=sys.stderr)
        sys.exit(1)
