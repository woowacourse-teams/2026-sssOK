#!/usr/bin/env bash
# #604 소셜 회원 경계 확인 — 통합된 익명 계정의 기존 토큰, 탈퇴 후 기존 토큰
# 카카오 로그인과 인가 코드 입력은 실행하는 사람이 직접 한다. 토큰·코드 원문은 기록하지 않는다.
set -u
BASE="${BASE:-https://dev-api.ssssok.com/api/v1}"
OUT="$(cd "$(dirname "$0")" && pwd)/kakao-out"
rm -rf "$OUT"; mkdir -p "$OUT/res"
LOG="$OUT/results.tsv"
printf 'id\tgroup\tmethod\tpath\tactor\texpected\tactual\tsummary\n' > "$LOG"

mask() {
  sed -E \
    -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/<JWT>/g' \
    -e 's#https://[0-9a-f]{8,}#https://<R2_ACCOUNT>#g' \
    -e 's#(client_id=)[^&"[:space:]]+#\1<redacted>#g' \
    -e 's/("refreshToken":")[A-Za-z0-9_-]{43}"/\1<REFRESH>"/g' \
    -e 's/("(loginVerifier|state)":")[^"]+"/\1<redacted>"/g' \
    -e 's/([?&](state|code|nonce)=)[^&"[:space:]]+/\1<redacted>/g'
}

# call ID GROUP ACTOR EXPECTED METHOD PATH TOKEN [BODY]
call() {
  local id="$1" group="$2" actor="$3" expected="$4" method="$5" path="$6" token="$7" body="${8:-}"
  local args=(-sS -X "$method" -o "$OUT/res/$id.body" -w '%{http_code}' --max-time 20)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  local status; status=$(curl "${args[@]}" "$BASE$path" 2>/dev/null); [ -z "$status" ] && status=000
  LAST_RAW=$(cat "$OUT/res/$id.body")
  mask < "$OUT/res/$id.body" > "$OUT/res/$id.m" && mv "$OUT/res/$id.m" "$OUT/res/$id.body"
  local summary; summary=$(tr -d '\r\n' < "$OUT/res/$id.body" | cut -c1-160)
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$group" "$method" "$path" "$actor" "$expected" "$status" "$summary" >> "$LOG"
  LAST_STATUS="$status"
  echo "$id $method $path [$actor] expected=$expected actual=$status  $(cut -c1-90 <<<"$summary")"
}

echo "== 1. 익명 계정 X 생성과 활동"
call K01 setup X 201 POST /auth/anonymous "" '{"nickname":"604X","refreshTokenDelivery":"BODY"}'
X_AT=$(jq -r .data.accessToken <<<"$LAST_RAW"); X_RT=$(jq -r .data.refreshToken <<<"$LAST_RAW"); X_ID=$(jq -r .data.userId <<<"$LAST_RAW")
call K02 setup X 201 POST /rooms "$X_AT" '{"name":"604X익명방","nickname":"604X"}'
X_ROOM=$(jq -r .data.roomId <<<"$LAST_RAW")
echo "X userId=$X_ID roomId=$X_ROOM"

echo "== 2. X 토큰을 붙여 카카오 로그인 시작"
call K03 setup X 200 POST /auth/oauth/kakao/start "$X_AT" '{}'
AUTH_URL=$(jq -r .data.authorizationUrl <<<"$LAST_RAW")
STATE=$(jq -r .data.state <<<"$LAST_RAW"); VERIFIER=$(jq -r .data.loginVerifier <<<"$LAST_RAW")
[ "$AUTH_URL" = null ] && { echo "로그인 시작 실패"; exit 1; }

printf '%s' "$AUTH_URL" | pbcopy
cat <<'MSG'

------------------------------------------------------------------
카카오 로그인 주소를 클립보드에 복사했습니다.
 1) 브라우저에서 새 탭을 연다 (Cmd+T)
 2) 그 탭에서 개발자 도구를 연다 (Option+Cmd+I) → Network 탭
 3) "Keep log" 체크, Filter 칸에 code= 입력
 4) 주소창에 아래 주소를 붙여넣기 (Cmd+V, 클립보드에 이미 있음) 후 Enter → 카카오 로그인
 5) dev 웹 화면은 신경 쓰지 않는다
 6) Network 목록에 남은 kakao?code=... 요청을 클릭 → Headers 의 Request URL 에서
    code= 뒤부터 &state 앞까지만 복사
------------------------------------------------------------------
MSG
echo; echo "[카카오 로그인 주소]"; echo "$AUTH_URL"; echo
read -r -s -p "카카오 인가 코드 붙여넣기 (화면에 안 보임): " KAKAO_CODE; echo
[ -z "$KAKAO_CODE" ] && { echo "코드가 비어 있어 중단"; exit 1; }

echo "== 3. 카카오 로그인 완료 (익명 활동 통합)"
call K04 social M 200 POST /auth/oauth/kakao/login "" \
  "$(jq -nc --arg c "$KAKAO_CODE" --arg s "$STATE" --arg v "$VERIFIER" '{code:$c,state:$s,loginVerifier:$v,refreshTokenDelivery:"BODY"}')"
unset KAKAO_CODE
M_AT=$(jq -r .data.accessToken <<<"$LAST_RAW"); M_RT=$(jq -r .data.refreshToken <<<"$LAST_RAW")
M_ID=$(jq -r .data.userId <<<"$LAST_RAW"); M_STATUS=$(jq -r .data.memberStatus <<<"$LAST_RAW")
[ "$LAST_STATUS" != 200 ] && { echo "로그인 실패 — 코드 만료·오입력일 수 있다. 처음부터 다시 실행"; exit 1; }
echo "회원 userId=$M_ID status=$M_STATUS (익명 X=$X_ID)"

echo "== 4. 통합된 익명 계정 X의 기존 토큰"
call M01 merged X 401 GET /members/me/rooms "$X_AT"
call M02 merged X 401 GET "/rooms/$X_ROOM/media/all" "$X_AT"
call M03 merged X 401 POST /auth/refresh "" "{\"refreshToken\":\"$X_RT\"}"
call M04 merged M 200 GET /members/me/rooms "$M_AT"
grep -q "604X익명방" "$OUT/res/M04.body" && echo "  → X가 만든 방이 회원 목록으로 옮겨졌다" | tee -a "$OUT/notes.txt"
HOSTED=$(jq '[.data.rooms[] | select(.isHost)] | length' "$OUT/res/M04.body")

echo
echo "탈퇴하면 이 카카오 계정의 dev 회원과 방장인 방 ${HOSTED}개가 삭제되고 카카오 연결이 해제된다."
read -r -p "탈퇴 확인을 진행할까요? (y 입력 시 진행): " GO
if [ "$GO" != y ]; then echo "탈퇴 확인 건너뜀"; exit 0; fi

echo "== 5. 탈퇴 후 기존 토큰"
call W01 withdraw M 204 DELETE /auth/account "$M_AT"
call W02 withdraw M 401 GET /members/me/rooms "$M_AT"
call W03 withdraw M 401 POST /auth/refresh "" "{\"refreshToken\":\"$M_RT\"}"
call W04 withdraw M 401 GET "/rooms/$X_ROOM/media/all" "$M_AT"
call W05 withdraw X 401 GET /members/me/rooms "$X_AT"
echo "== done $(date)"
