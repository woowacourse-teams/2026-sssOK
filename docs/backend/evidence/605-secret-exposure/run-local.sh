#!/usr/bin/env bash
# #605 인증 흐름 로그·응답 비밀값 노출 점검 (로컬)
#
# 서버는 dev 와 같은 JSON 로그(SssOkJsonLogFormatter)로 띄우고, 표준 출력을 SERVER_LOG 에 남긴다.
# 이 스크립트는 인증 시나리오를 실행하면서 응답으로 받은 토큰·쿠키·state·확인값과 서버에 넣은
# 가짜 비밀값을 NEEDLES 파일에 모은 뒤, 서버 로그와 응답 본문에서 그 값이 나오는지 센다.
# 값 자체는 결과 파일에 쓰지 않는다.
#
#   SERVER_LOG=<서버 로그> SECRETS_ENV=<가짜 비밀값 export 파일> WORK=<임시 디렉터리> ./run-local.sh
set -uo pipefail

BASE="${BASE:-http://localhost:8080/api/v1}"
ORIGIN="${ORIGIN:-http://localhost:5173}"
: "${SERVER_LOG:?}" "${SECRETS_ENV:?}" "${WORK:?}"
OUT="$(cd "$(dirname "$0")" && pwd)/results-local.tsv"
NEEDLES="$WORK/needles.tsv"
BODIES="$WORK/bodies.txt"
: > "$NEEDLES"; : > "$BODIES"
printf 'id\tscenario\tstatus\n' > "$OUT"

needle() { [ -n "$2" ] && [ "$2" != null ] && printf '%s\t%s\n' "$1" "$2" >> "$NEEDLES"; }

# call <id> <설명> <curl 인자...> : 상태 코드를 기록하고 본문·헤더를 $WORK/<id>.* 에 둔다
call() {
  local id="$1" desc="$2"; shift 2
  local status
  status=$(curl -s -o "$WORK/$id.body" -D "$WORK/$id.headers" -w '%{http_code}' "$@")
  cat "$WORK/$id.body" "$WORK/$id.headers" >> "$BODIES"
  printf '%s\t%s\t%s\n' "$id" "$desc" "$status" >> "$OUT"
}
json() { jq -r "$2" "$WORK/$1.body" 2>/dev/null; }
cookie() { grep -i '^set-cookie: sssok_refresh=' "$WORK/$1.headers" | sed -E 's/^[^=]*=([^;]*).*/\1/' | tr -d '\r'; }
JSON=(-H 'Content-Type: application/json')
COOKIE_MODE=(-H "Origin: $ORIGIN" -H 'X-CSRF-Protection: 1')

# --- 서비스 Access/Refresh token ---
call L01 '익명 인증(Refresh 본문 전달)' "${JSON[@]}" -X POST "$BASE/auth/anonymous" \
  -d '{"nickname":"점검","refreshTokenDelivery":"BODY"}'
A1=$(json L01 .data.accessToken); R1=$(json L01 .data.refreshToken)
needle access-L01 "$A1"; needle refresh-L01 "$R1"

call L02 '토큰 갱신(본문)' "${JSON[@]}" -X POST "$BASE/auth/refresh" -d "{\"refreshToken\":\"$R1\"}"
A2=$(json L02 .data.accessToken); R2=$(json L02 .data.refreshToken)
needle access-L02 "$A2"; needle refresh-L02 "$R2"

sleep 6   # 중복 요청 유예 시간이 지난 뒤 재사용
call L03 '이미 쓴 Refresh 재사용' "${JSON[@]}" -X POST "$BASE/auth/refresh" -d "{\"refreshToken\":\"$R1\"}"

call L04 '형식이 틀린 Refresh' "${JSON[@]}" -X POST "$BASE/auth/refresh" \
  -d '{"refreshToken":"probe605-not-a-refresh-token"}'
needle refresh-L04 probe605-not-a-refresh-token

call L05 '익명 인증(Refresh 쿠키 전달)' "${JSON[@]}" "${COOKIE_MODE[@]}" -X POST "$BASE/auth/anonymous" \
  -d '{"nickname":"쿠키","refreshTokenDelivery":"COOKIE"}'
A5=$(json L05 .data.accessToken); C5=$(cookie L05)
needle access-L05 "$A5"; needle cookie-L05 "$C5"

call L06 '토큰 갱신(쿠키)' "${COOKIE_MODE[@]}" -X POST "$BASE/auth/refresh" -b "sssok_refresh=$C5"
A6=$(json L06 .data.accessToken); C6=$(cookie L06)
needle access-L06 "$A6"; needle cookie-L06 "$C6"

call L07 'CSRF 헤더 없는 쿠키 갱신' -H "Origin: $ORIGIN" -X POST "$BASE/auth/refresh" -b "sssok_refresh=$C6"

call L08 'Refresh 재사용으로 폐기된 세션의 로그아웃' -X POST "$BASE/auth/logout" -H "Authorization: Bearer $A2"
call L09 '위조 Bearer 토큰' -X POST "$BASE/auth/logout" -H 'Authorization: Bearer probe605.forged.token'
needle forged-L09 probe605.forged.token

call L10 '익명 인증(로그아웃용)' "${JSON[@]}" -X POST "$BASE/auth/anonymous" \
  -d '{"nickname":"로그아웃","refreshTokenDelivery":"BODY"}'
A10=$(json L10 .data.accessToken); R10=$(json L10 .data.refreshToken)
needle access-L10 "$A10"; needle refresh-L10 "$R10"
call L11 '로그아웃' -X POST "$BASE/auth/logout" -H "Authorization: Bearer $A10"
call L12 '로그아웃한 Access로 다시 로그아웃' -X POST "$BASE/auth/logout" -H "Authorization: Bearer $A10"
call L13 '로그아웃한 세션의 Refresh로 갱신' "${JSON[@]}" -X POST "$BASE/auth/refresh" -d "{\"refreshToken\":\"$R10\"}"

# --- 카카오 ---
call K01 '카카오 로그인 시작(익명 토큰 연결)' "${JSON[@]}" -X POST "$BASE/auth/oauth/kakao/start" \
  -H "Authorization: Bearer $A6" -d '{}'
KS=$(json K01 .data.state); KV=$(json K01 .data.loginVerifier)
needle kakao-state "$KS"; needle kakao-verifier "$KV"
call K02 '카카오 로그인 완료(잘못된 인가 코드)' "${JSON[@]}" -X POST "$BASE/auth/oauth/kakao/login" \
  -d "{\"code\":\"PROBE605KAKAOCODE\",\"state\":\"$KS\",\"loginVerifier\":\"$KV\",\"refreshTokenDelivery\":\"BODY\"}"
needle kakao-code PROBE605KAKAOCODE
call K03 '카카오 로그인 완료(이미 쓴 state)' "${JSON[@]}" -X POST "$BASE/auth/oauth/kakao/login" \
  -d "{\"code\":\"PROBE605KAKAOCODE2\",\"state\":\"$KS\",\"loginVerifier\":\"$KV\",\"refreshTokenDelivery\":\"BODY\"}"
needle kakao-code2 PROBE605KAKAOCODE2

# --- 애플 ---
call P01 '애플 로그인 시작' "${JSON[@]}" -X POST "$BASE/auth/oauth/apple/start" -d '{}'
PS=$(json P01 .data.state); PV=$(json P01 .data.loginVerifier)
needle apple-state "$PS"; needle apple-verifier "$PV"
call P02 '애플 로그인 완료(잘못된 인가 코드)' "${JSON[@]}" -X POST "$BASE/auth/oauth/apple/login" \
  -d "{\"code\":\"PROBE605APPLECODE\",\"state\":\"$PS\",\"loginVerifier\":\"$PV\",\"refreshTokenDelivery\":\"BODY\"}"
needle apple-code PROBE605APPLECODE

# --- 탈퇴 ---
call W01 '익명 회원 탈퇴' -X DELETE "$BASE/auth/account" -H "Authorization: Bearer $A6"

# --- 서버 비밀값(이번 점검용 가짜 값) ---
# shellcheck disable=SC1090
source "$SECRETS_ENV"
needle JWT_SECRET "$JWT_SECRET"
needle OAUTH_TOKEN_ENCRYPTION_KEY "$OAUTH_TOKEN_ENCRYPTION_KEY"
needle OAUTH_KAKAO_CLIENT_SECRET "$OAUTH_KAKAO_CLIENT_SECRET"
needle OAUTH_KAKAO_ADMIN_KEY "$OAUTH_KAKAO_ADMIN_KEY"
needle OAUTH_APPLE_PRIVATE_KEY "$(printf '%s\n' "$OAUTH_APPLE_PRIVATE_KEY" | sed -n 2p)"

sleep 2
printf '\n# 값 검색 (서버 로그 / 응답 본문·헤더)\nneedle\tlog_hits\tresponse_hits\n' >> "$OUT"
while IFS=$'\t' read -r name value; do
  log_hits=$(grep -cF -- "$value" "$SERVER_LOG")
  case "$name" in
    # 발급한 토큰·쿠키·state·확인값은 응답으로 나가는 것이 정상이라 응답 검색은 서버 비밀값만 한다
    JWT_SECRET|OAUTH_*) resp_hits=$(grep -cF -- "$value" "$BODIES") ;;
    *) resp_hits=- ;;
  esac
  printf '%s\t%s\t%s\n' "$name" "$log_hits" "$resp_hits" >> "$OUT"
done < "$NEEDLES"

printf '\n# 패턴 검색 (서버 로그)\npattern\tlog_hits\n' >> "$OUT"
for p in 'Bearer [A-Za-z0-9]' 'KakaoAK' 'eyJ[A-Za-z0-9_-]{4,}\.' 'sssok_refresh=' 'BEGIN PRIVATE KEY' \
         '"(access_token|refresh_token|id_token)"' 'client_secret' '[?&]code='; do
  printf '%s\t%s\n' "$p" "$(grep -cE -- "$p" "$SERVER_LOG")" >> "$OUT"
done
cat "$OUT"
