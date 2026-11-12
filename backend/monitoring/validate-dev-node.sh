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
assert not node.get('ports'), '수집기 호스트 포트 게시 금지 (AMI 기본 수집기와 충돌)'
assert any(v['source'] == '/' and v['target'] == '/host' and v.get('read_only')
           and v.get('bind', {}).get('propagation') == 'rslave'
           for v in node['volumes']), '호스트 읽기 전용 마운트 누락'
required = {'--path.rootfs=/host', '--path.procfs=/host/proc', '--path.sysfs=/host/sys',
            '--collector.cpu', '--collector.meminfo', '--collector.vmstat',
            '--collector.loadavg', '--collector.filesystem'}
assert required <= set(node['command']), '호스트 수집 옵션 누락'
nginx = services['nginx']
assert any(p['target'] == 19100 and str(p.get('published')) == '19100'
           and p.get('host_ip') == '10.0.0.104' for p in nginx['ports']), 'Nginx 사설 수집 포트 누락'
assert any(p['target'] == 80 and str(p.get('published')) == '80' for p in nginx['ports']), 'Nginx API 포트 누락'
# 환경 라벨·중앙 IP 는 개발 CD 가 템플릿을 치환해 nginx/default.conf 로 둔다 (운영과 같은 conf.d 마운트).
assert [v['target'] for v in nginx['volumes']] == ['/etc/nginx/conf.d'], 'Nginx conf.d 마운트 누락'
assert nginx['labels']['com.sssok.log.service'] == 'sssok-nginx', 'Docker 로그 수집 라벨 누락'
assert set(nginx['networks']) & set(services['app-blue']['networks']) & set(services['app-green']['networks']) & set(node['networks']), '공통 네트워크 누락'
print('개발 node_exporter·Nginx 배포 구성 정상')
PY
# 개발 CD 와 같은 치환으로 default.conf 를 만들고, 배포 스크립트가 쓰는 형식의 upstream.conf 와 함께 검사한다.
conf_dir=$(mktemp -d)
trap 'rm -rf "$config" "$env_file" "$conf_dir"' EXIT
sed -e 's/${SSSOK_ENVIRONMENT}/dev/g' -e 's/${CENTRAL_PRIVATE_IP}/10.0.20.204/g' \
  backend/monitoring/nginx/container/default.conf.template > "$conf_dir/default.conf"
cat > "$conf_dir/upstream.conf" <<'EOF'
upstream sssok_app {
    zone sssok_app 64k;
    server app-blue:8080 resolve;
}
upstream sssok_app_management {
    zone sssok_app_management 64k;
    server app-blue:8081 resolve;
}
EOF
docker run --rm --network none \
  --mount "type=bind,src=$conf_dir,dst=/etc/nginx/conf.d,readonly" \
  nginx:1.27.5-alpine nginx -t
bash -n backend/monitoring/check-dev-node.sh
