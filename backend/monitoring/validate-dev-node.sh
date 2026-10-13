#!/usr/bin/env bash
# CI에서 실제 비밀값 없이 개발 수집기의 배포 구성을 검증한다.
set -euo pipefail
cd "$(dirname "$0")/../.."
config=$(mktemp)
env_file=$(mktemp)
trap 'rm -f "$config" "$env_file"' EXIT
for key in BACKEND_IMAGE DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET R2_ENDPOINT \
  R2_ACCESS_KEY R2_SECRET_KEY R2_BUCKET R2_PUBLIC_BASE_URL CORS_ALLOWED_ORIGINS \
  GRAFANA_ADMIN_PASSWORD; do
  printf '%s=validation-only\n' "$key" >> "$env_file"
done
docker compose --env-file "$env_file" -f backend/docker-compose.dev.yml config --format json > "$config"
python3 - "$config" <<'PY'
import json
import sys

services = json.load(open(sys.argv[1]))['services']
node = services['node-exporter']
assert node['pid'] == 'host', '호스트 PID 설정 누락'
assert any(p.get('host_ip') == '127.0.0.1' and str(p.get('published')) == '9100'
           and p['target'] == 9100 for p in node['ports']), 'loopback 9100 연결 누락'
assert all(p.get('host_ip') == '127.0.0.1' for p in node['ports']), '수집기 외부 포트 공개'
assert any(v['source'] == '/' and v['target'] == '/host' and v.get('read_only')
           and v.get('bind', {}).get('propagation') == 'rslave'
           for v in node['volumes']), '호스트 읽기 전용 마운트 누락'
required = {'--path.rootfs=/host', '--path.procfs=/host/proc', '--path.sysfs=/host/sys',
            '--collector.cpu', '--collector.meminfo', '--collector.vmstat',
            '--collector.loadavg', '--collector.filesystem'}
assert required <= set(node['command']), '호스트 수집 옵션 누락'
nginx = services['nginx']
assert any(p['target'] == 19100 and str(p.get('published')) == '19100'
           and p.get('host_ip') == '10.0.0.69' for p in nginx['ports']), 'Nginx 사설 수집 포트 누락'
assert any(p['target'] == 80 and str(p.get('published')) == '80' for p in nginx['ports']), 'Nginx API 포트 누락'
assert nginx['environment']['SSSOK_ENVIRONMENT'] == 'dev'
assert nginx['environment']['CENTRAL_PRIVATE_IP'] == '10.0.20.46'
assert nginx['labels']['com.sssok.log.service'] == 'sssok-nginx', 'Docker 로그 수집 라벨 누락'
assert set(nginx['networks']) & set(services['app']['networks']) & set(node['networks']), '공통 네트워크 누락'
print('개발 node_exporter·Nginx 배포 구성 정상')
PY
docker run --rm \
  -e SSSOK_ENVIRONMENT=dev -e CENTRAL_PRIVATE_IP=10.0.20.46 \
  -e 'NGINX_ENVSUBST_FILTER=^(SSSOK_ENVIRONMENT|CENTRAL_PRIVATE_IP)$' \
  -v "$PWD/backend/monitoring/nginx/container/default.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  nginx:1.27-alpine nginx -t
bash -n backend/monitoring/check-dev-node.sh
