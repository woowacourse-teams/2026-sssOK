#!/usr/bin/env bash
# 개발 호스트에서 실행한다. 실패해도 정상 앱을 롤백하지 않고 배포 실패로 보고한다.
set -euo pipefail
metrics_file=$(mktemp)
trap 'rm -f "$metrics_file"' EXIT

curl --noproxy '*' --fail --silent --show-error --max-time 5 \
  --retry 5 --retry-delay 2 --retry-all-errors \
  http://127.0.0.1:9100/metrics -o "$metrics_file"
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

# 개발 호스트 자신은 중앙 IP가 아니므로 거절되어야 한다.
status=$(curl --noproxy '*' --silent --show-error --max-time 5 \
  -o /dev/null -w '%{http_code}' http://10.0.0.69:19100/metrics)
if [ "$status" != 403 ]; then
  echo "개발 Nginx 접근 제한 확인 실패: HTTP $status (예상 403)" >&2
  exit 1
fi
echo '개발 호스트 지표·Nginx 차단 확인 성공 (중앙 수집 성공 여부는 중앙에서 별도 확인)'
