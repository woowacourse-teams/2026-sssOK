#!/usr/bin/env bash
# ZIP 다운로드 부하를 단계별로 돌린다. 병목을 가르려고 두 묶음을 잰다.
#   동시성: 사진 수를 고정하고 동시 작업 수를 늘린다 — 워커 풀 대기(zip_wait_ms)가 언제 늘어나는가
#   크기:   동시 1개로 사진 수를 늘린다 — 압축 시간(zip_run_ms)이 사진 수에 어떻게 비례하는가
# access 토큰이 30분이라 단계마다 먼저 갱신한다. 단계 시작 시각(UTC)은 timeline 파일에 남겨 Grafana 구간을 맞춘다.
#
# 사용: bash scripts/run-zip.sh <라벨(old|new)> <fixtures 경로>
#   CONCURRENCIES='1 2 4 5 8 12'  PHOTOS_SET='30 100 300'  DURATION=4m  REST=30  SKIP_SMOKE=0
set -euo pipefail

label=${1:?라벨(old|new)이 필요합니다}
fixtures=$(cd "$(dirname "$2")" && pwd)/$(basename "$2")
cd "$(dirname "$0")/.."

K6=${K6:-k6}
CONCURRENCIES=${CONCURRENCIES:-'1 2 4 5 8 12'}
PHOTOS_SET=${PHOTOS_SET:-'30 100 300'}
DURATION=${DURATION:-4m}
REST=${REST:-30}
SKIP_SMOKE=${SKIP_SMOKE:-0}
SCRIPT_VERSION=${SCRIPT_VERSION:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}
stamp=$(date -u +%Y%m%dT%H%MZ)
timeline=results/582-${label}-${stamp}-timeline.txt
mkdir -p results

run_stage() {
  local name=$1; shift
  node seed/refresh-tokens.mjs --fixtures "$fixtures" --yes
  echo "${name} $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$timeline"
  FIXTURES="$fixtures" "$K6" run -q -e RUN_ID="582-${label}-${name}-${stamp}" -e SCRIPT_VERSION="$SCRIPT_VERSION" \
    -e POLL_INTERVAL=1s -e ZIP_TIMEOUT=10m "$@" k6/scenarios/media-zip.js || echo "${name} k6 종료 코드 $?" | tee -a "$timeline"
}

if [ "$SKIP_SMOKE" != 1 ]; then
  run_stage smoke -e PROFILE=smoke -e ZIP_PHOTOS=30 -e ZIP_FETCH=1
  sleep "$REST"
fi

for c in $CONCURRENCIES; do
  run_stage "c${c}" -e PROFILE=load -e ZIP_CONCURRENCY="$c" -e ZIP_PHOTOS=30 -e ZIP_FETCH=0 -e DURATION="$DURATION"
  sleep "$REST"
done

for p in $PHOTOS_SET; do
  run_stage "p${p}" -e PROFILE=load -e ZIP_CONCURRENCY=1 -e ZIP_PHOTOS="$p" -e ZIP_FETCH=0 -e DURATION="$DURATION"
  sleep "$REST"
done

echo "end $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$timeline"
