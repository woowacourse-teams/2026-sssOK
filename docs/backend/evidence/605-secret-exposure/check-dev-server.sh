#!/usr/bin/env bash
# #605 dev 서버 점검. dev EC2 에서 DEPLOY_PATH 로 이동한 뒤 실행한다.
#
#   ./check-dev-server.sh <시작 시각, 예: 2026-10-28T16:00:00+09:00> [env 파일, 기본 .env]
#
# 1. 서버 .env 의 JWT_SECRET·OAUTH_TOKEN_ENCRYPTION_KEY 가 저장소에 공개된 고정값(테스트·CI·local 기본값)과
#    같은지 SHA-256 으로만 비교한다. 값은 출력하지 않는다.
# 2. 시작 시각 이후 앱 컨테이너 로그에서 서버 비밀값 원문과 토큰·헤더 패턴을 센다.
#    앱 로그는 컨테이너 표준 출력에 찍힐 때 이미 SssOkJsonLogFormatter 마스킹을 거친다. Loki 에 가는 것도 이 로그다.
#    Loki 확인은 LOGGING_RUNBOOK 7절 질의를 쓴다.
# prod 는 CONTAINER=sssok-app 으로 실행한다.
set -uo pipefail

SINCE="${1:?시작 시각이 필요합니다}"
ENV_FILE="${2:-.env}"
CONTAINER="${CONTAINER:-sssok-app-dev}"

# 저장소 이력에 커밋된 고정 키 7개의 SHA-256 (값은 이미 공개돼 있고, 여기에는 해시만 둔다)
PUBLIC_KEY_HASHES="
839ce6c6b8d6d0228f3dfa88e078d8818f54dc93f0a61329665826563b587829
26f9a9227eecacc40e1b047aafe90182e6804305a455c93e740ffc87a97c0b38
c2e493341ae7a6fcf6ae2fd03810f9fd4b0d1bb71fe2bddbff033c93d71a7493
baa51c7466c26e40fc7f7bf875189e7c40fe7822a0f060004da98eb7e2f34203
faca9d2499289ee96b46ae0b846ae00174165f1805081532b26fe32d2edf3dd3
1bcd4c1d7f2d7916aa4db61666bbec079c84332563888e633eeeb1f7868e781e
44d526161ab857ac454ff3fe1b615cb5ba337b72e7f79d720c406576702802a1
"

env_value() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | sed -E "s/^['\"]//; s/['\"]$//"; }

echo "# 1. 서버 키와 공개 고정값 비교"
for name in JWT_SECRET OAUTH_TOKEN_ENCRYPTION_KEY; do
  value="$(env_value "$name")"
  if [ -z "$value" ]; then echo "$name	비어 있음"; continue; fi
  hash="$(printf '%s' "$value" | sha256sum | cut -c1-64)"
  if grep -qx "$hash" <<< "$PUBLIC_KEY_HASHES"; then echo "$name	공개 고정값과 같음 — 교체 필요"
  else echo "$name	다름"; fi
done

echo
echo "# 2. $SINCE 이후 $CONTAINER 로그 검색 (건수)"
LOG="$(mktemp)"; trap 'rm -f "$LOG"' EXIT
docker logs --since "$SINCE" "$CONTAINER" > "$LOG" 2>&1
echo "로그 줄 수	$(wc -l < "$LOG")"
for name in JWT_SECRET OAUTH_TOKEN_ENCRYPTION_KEY OAUTH_KAKAO_CLIENT_SECRET OAUTH_KAKAO_ADMIN_KEY OPENAI_API_KEY; do
  value="$(env_value "$name")"
  [ -n "$value" ] && echo "$name 원문	$(grep -cF -- "$value" "$LOG")"
done
# 한 줄(\n 이스케이프)과 여러 줄 두 형식 모두에서 PEM 본문 첫 줄을 꺼낸다
apple_line="$(sed -n '/^OAUTH_APPLE_PRIVATE_KEY=/,/END PRIVATE KEY/p' "$ENV_FILE" | tr -d '\r' \
  | sed 's/\\n/\n/g' | grep -v 'PRIVATE KEY' | grep -oE '[A-Za-z0-9+/=]{40,}' | head -n 1)"
[ -n "$apple_line" ] && echo "OAUTH_APPLE_PRIVATE_KEY 본문	$(grep -cF -- "$apple_line" "$LOG")"
for p in 'Bearer [A-Za-z0-9]' 'KakaoAK' 'eyJ[A-Za-z0-9_-]{4,}\.' 'sssok_refresh=' 'BEGIN PRIVATE KEY' \
         '"(access_token|refresh_token|id_token)"' 'client_secret' '[?&]code=' '"refreshToken"'; do
  echo "$p	$(grep -cE -- "$p" "$LOG")"
done
