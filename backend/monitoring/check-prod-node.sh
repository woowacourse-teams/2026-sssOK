#!/usr/bin/env bash
# 운영 서버에서 실행. 중앙 장애와 독립적으로 로컬 지표·접근 제한만 검사한다.
# 실패해도 앱 롤백·컨테이너 재시작·볼륨 변경은 하지 않는다.
set -euo pipefail
private_ip=${MONITORING_PRIVATE_IP:-10.0.20.127}
metrics_file=$(mktemp)
trap 'rm -f "$metrics_file"' EXIT

fetch_metrics() {
  local url=$1
  local attempt
  for attempt in 1 2 3 4 5 6; do
    if docker exec sssok-nginx wget -q -T 5 -O - "$url" > "$metrics_file"; then
      return 0
    fi
    if [ "$attempt" != 6 ]; then sleep 2; fi
  done
  echo "운영 내부 지표 조회 실패: $url" >&2
  return 1
}

require_metric() {
  if ! grep -Eq "^${1}(\{| )" "$metrics_file"; then
    echo "운영 지표 누락: $1" >&2
    return 1
  fi
}

# 호스트에 관리 포트를 추가 공개하지 않고 기존 Nginx 컨테이너에서 조회한다.
fetch_metrics http://node-exporter:9100/metrics
for metric in node_cpu_seconds_total node_memory_MemAvailable_bytes \
  node_vmstat_pswpin node_vmstat_pswpout node_load1 node_filesystem_avail_bytes; do
  require_metric "$metric"
done
if ! grep -E '^node_filesystem_avail_bytes\{[^}]*mountpoint="/"[,}]' "$metrics_file" > /dev/null; then
  echo '운영 node_exporter 루트 디스크 지표 누락' >&2
  exit 1
fi

fetch_metrics http://app:8081/actuator/prometheus
require_metric jvm_memory_used_bytes
fetch_metrics http://alloy:12345/metrics
require_metric loki_write_sent_entries_total

for path in metrics app-metrics alloy-metrics; do
  for forwarded in "" "$private_ip" 10.0.20.46; do
    status=$(curl --noproxy '*' --silent --show-error --max-time 5 \
      -H "X-Forwarded-For: $forwarded" \
      -o /dev/null -w '%{http_code}' "http://${private_ip}:19100/$path")
    if [ "$status" != 403 ]; then
      echo "운영 Nginx 접근 제한 실패: $path HTTP $status (예상 403)" >&2
      exit 1
    fi
  done
done
echo '운영 호스트·앱·Alloy 지표와 경로 차단 정상 (중앙 수집·로그 수신은 별도 확인)'
