#!/usr/bin/env bash
# 개발 호스트에서 실행한다. 실패해도 정상 앱을 롤백하지 않고 배포 실패로 보고한다.
set -euo pipefail
private_ip=${MONITORING_PRIVATE_IP:-10.0.0.104}
metrics_file=$(mktemp)
trap 'rm -f "$metrics_file"' EXIT

# AMI 기본 수집기가 아닌 팀 수집기를 Docker 내부 주소로 검사한다.
fetch_node_metrics() {
  local attempt
  for attempt in 1 2 3 4 5 6; do
    if docker exec sssok-nginx-dev wget -q -T 5 -O - \
      http://node-exporter:9100/metrics > "$metrics_file"; then
      return 0
    fi
    if [ "$attempt" != 6 ]; then sleep 2; fi
  done
  echo '개발 팀 node_exporter 내부 지표 조회 실패' >&2
  return 1
}
fetch_node_metrics
for metric in node_cpu_seconds_total node_memory_MemAvailable_bytes \
  node_vmstat_pswpin node_vmstat_pswpout node_load1 node_filesystem_avail_bytes; do
  if ! grep -Eq "^${metric}(\{| )" "$metrics_file"; then
    echo "개발 node_exporter 지표 누락: $metric" >&2
    exit 1
  fi
done
if ! grep -E '^node_filesystem_avail_bytes\{' "$metrics_file" | grep -q 'mountpoint="/"'; then
  echo '개발 node_exporter 루트 디스크 지표 누락' >&2
  exit 1
fi

# 세 경로 모두 실제 출발지로 제한한다. 헤더가 없거나 중앙 IP를 위조해도 차단한다.
for path in metrics app-metrics alloy-metrics; do
  for forwarded in "" "$private_ip" 10.0.20.204; do
    status=$(curl --noproxy '*' --silent --show-error --max-time 5 \
      -H "X-Forwarded-For: $forwarded" \
      -o /dev/null -w '%{http_code}' "http://${private_ip}:19100/$path")
    if [ "$status" != 403 ]; then
      echo "개발 Nginx 접근 제한 실패: $path HTTP $status (예상 403)" >&2
      exit 1
    fi
  done
done
# 블루-그린 활성 칸은 배포 스크립트가 쓰는 nginx/upstream.conf 에서 읽는다 (운영 검사와 같은 방식).
# 관리 포트를 호스트에 게시하지 않으므로 nginx 컨테이너에서 조회한다.
app_host=$(grep -oE 'server app(-blue|-green)?:8081' nginx/upstream.conf 2>/dev/null | head -n 1 | sed 's/^server //; s/:8081$//')
fetch_app_metrics() {
  local attempt
  for attempt in 1 2 3 4 5 6; do
    if docker exec sssok-nginx-dev wget -q -T 5 -O - \
      "http://${app_host:-app}:8081/actuator/prometheus" > "$metrics_file"; then
      return 0
    fi
    if [ "$attempt" != 6 ]; then sleep 2; fi
  done
  echo "개발 앱 내부 지표 조회 실패: ${app_host:-app}" >&2
  return 1
}
fetch_app_metrics
grep -q '^jvm_memory_used_bytes{' "$metrics_file" || { echo '앱 JVM 지표 누락' >&2; exit 1; }
echo '개발 호스트·앱 지표와 호스트·앱·Alloy 경로 차단 정상 (중앙 수집은 중앙에서 별도 확인)'
