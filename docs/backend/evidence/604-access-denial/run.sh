#!/usr/bin/env bash
# #604 다른 사용자 자원 접근 거부 확인 — dev 서버 실제 요청 기록
set -u
BASE="${BASE:-https://dev-api.ssssok.com/api/v1}"
OUT="$(cd "$(dirname "$0")" && pwd)/out"
rm -rf "$OUT"; mkdir -p "$OUT/res"
LOG="$OUT/results.tsv"
printf 'id\tgroup\tmethod\tpath\tactor\texpected\tactual\tsummary\n' > "$LOG"

# 응답 원문에서 토큰·서명을 가린다
mask() {
  sed -E \
    -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/<JWT>/g' \
    -e 's#https://[0-9a-f]{8,}#https://<R2_ACCOUNT>#g' \
    -e 's#(client_id=)[^&"[:space:]]+#\1<redacted>#g' \
    -e 's/("refreshToken":")[A-Za-z0-9_-]{43}"/\1<REFRESH>"/g' \
    -e 's/(sssok_refresh=)[A-Za-z0-9_-]{43}/\1<REFRESH>/g' \
    -e 's/(X-Amz-[A-Za-z]+=)[^&"[:space:]]+/\1<redacted>/g'
}

# call ID GROUP ACTOR EXPECTED METHOD PATH TOKEN [BODY] [extra curl args...]
call() {
  local id="$1" group="$2" actor="$3" expected="$4" method="$5" path="$6" token="$7" body="${8:-}"
  shift 8 2>/dev/null || shift $#
  local args=(-sS -X "$method" -o "$OUT/res/$id.body" -D "$OUT/res/$id.headers" -w '%{http_code}' --max-time 15)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  local status
  status=$(curl "${args[@]}" "$@" "$BASE$path" 2>/dev/null)
  [ -z "$status" ] && status=000
  LAST_RAW=$(cat "$OUT/res/$id.body")
  mask < "$OUT/res/$id.body" > "$OUT/res/$id.body.m" && mv "$OUT/res/$id.body.m" "$OUT/res/$id.body"
  mask < "$OUT/res/$id.headers" > "$OUT/res/$id.headers.m" && mv "$OUT/res/$id.headers.m" "$OUT/res/$id.headers"
  local summary
  summary=$(tr -d '\r\n' < "$OUT/res/$id.body" | cut -c1-160)
  local loc; loc=$(grep -i '^location:' "$OUT/res/$id.headers" | cut -c1-60 | tr -d '\r')
  [ -n "$loc" ] && summary="$summary [$loc...]"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$group" "$method" "$path" "$actor" "$expected" "$status" "$summary" >> "$LOG"
  LAST_STATUS="$status"
  echo "$id $method $path [$actor] expected=$expected actual=$status"
}
body() { cat "$OUT/res/$1.body"; }
raw()  { curl -sS --max-time 15 "$@"; }

anon() { # 원문 토큰은 메모리에만 둔다 (기록 파일에는 마스킹본만)
  raw -X POST -H 'Content-Type: application/json' \
    --data "{\"nickname\":\"$1\",\"refreshTokenDelivery\":\"BODY\"}" "$BASE/auth/anonymous"
}

echo "== 0. 준비"
A_JSON=$(anon "604A"); A_AT=$(jq -r .data.accessToken <<<"$A_JSON"); A_RT=$(jq -r .data.refreshToken <<<"$A_JSON"); A_ID=$(jq -r .data.userId <<<"$A_JSON")
B_JSON=$(anon "604B"); B_AT=$(jq -r .data.accessToken <<<"$B_JSON"); B_ID=$(jq -r .data.userId <<<"$B_JSON")
echo "A userId=$A_ID token=${A_AT:0:10}…  B userId=$B_ID token=${B_AT:0:10}…"

call S01 setup A 201 POST /rooms "$A_AT" '{"name":"604A비밀방","uploadPolicy":"everyone","nickname":"604A"}'
ROOM_ID=$(body S01 | jq -r .data.roomId); ROOM_CODE=$(body S01 | jq -r .data.code)
call S02 setup A 201 POST "/rooms/$ROOM_ID/folders" "$A_AT" '{"name":"604A폴더"}'
FOLDER_ID=$(body S02 | jq -r '.data.folderId // .data.id')

# 64x64 JPEG 생성
python3 - "$OUT/a.bmp" <<'PY'
import struct, sys
w = h = 64
row = b''.join(bytes((x * 4 % 256, y * 4 % 256, 128)) for x in range(w) for y in [0])
pix = b''.join(b''.join(bytes((x * 4 % 256, y * 4 % 256, 128)) for x in range(w)) for y in range(h))
size = 54 + len(pix)
hdr = b'BM' + struct.pack('<IHHI', size, 0, 0, 54) + struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, len(pix), 2835, 2835, 0, 0)
open(sys.argv[1], 'wb').write(hdr + pix)
PY
sips -s format jpeg "$OUT/a.bmp" --out "$OUT/a.jpg" >/dev/null
SIZE=$(stat -f%z "$OUT/a.jpg")
call S03 setup A 200 POST "/rooms/$ROOM_ID/media/upload-urls" "$A_AT" \
  "{\"files\":[{\"fileName\":\"604-A-secret.jpg\",\"mimeType\":\"image/jpeg\",\"size\":$SIZE}],\"folderIds\":[$FOLDER_ID]}"
MEDIA_ID=$(body S03 | jq -r '.data.issued[0].mediaId')
UP_URL=$(jq -r '.data.issued[0].uploadUrl' <<<"$LAST_RAW")
HDRS=(); while IFS= read -r h; do HDRS+=(-H "$h"); done < <(jq -r '.data.issued[0].headers // {} | to_entries[] | "\(.key): \(.value)"' <<<"$LAST_RAW")
PUT_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT "${HDRS[@]}" --data-binary "@$OUT/a.jpg" "$UP_URL")
echo "R2 PUT status=$PUT_STATUS"
call S04 setup A 201 POST "/rooms/$ROOM_ID/media" "$A_AT" "{\"mediaIds\":[$MEDIA_ID]}"
call S05 setup A 200 PUT "/rooms/$ROOM_ID/media/$MEDIA_ID/likes" "$A_AT"
call S06 setup A 202 POST "/rooms/$ROOM_ID/downloads/zip" "$A_AT" "{\"mediaIds\":[$MEDIA_ID]}"
JOB_ID=$(body S06 | jq -r .data.jobId)
call S07 setup B 201 POST /rooms "$B_AT" '{"name":"604B방","nickname":"604B"}'
B_ROOM_ID=$(body S07 | jq -r .data.roomId)
echo "roomId=$ROOM_ID folderId=$FOLDER_ID mediaId=$MEDIA_ID jobId=$JOB_ID B roomId=$B_ROOM_ID"

snapshot() { # A 토큰으로 방·미디어·폴더 상태를 비교 가능한 형태로 남긴다
  local tag="$1"
  call "$tag-room" verify A 200 GET "/rooms/$ROOM_CODE" "$A_AT"
  call "$tag-media" verify A 200 GET "/rooms/$ROOM_ID/media/all" "$A_AT"
  call "$tag-detail" verify A 200 GET "/rooms/$ROOM_ID/media/$MEDIA_ID" "$A_AT"
  { body "$tag-room"   | jq -S '.data | {roomId,name,status,hostId,uploadPolicy,photoCount,folders}'
    body "$tag-media"  | jq -S '[.. | objects | select(has("mediaId")) | {mediaId, fileName, folderIds, likeCount}]'
    body "$tag-detail" | jq -S '.data | {mediaId, fileName, folderIds, likeCount, likedByMe}'
  } > "$OUT/$tag.snapshot.json"
}
snapshot BEFORE

echo "== 1. B 토큰으로 A의 방 자원 요청 (B는 A의 방 미참여)"
R=/rooms/$ROOM_ID
call T01 room B 404 GET "/rooms/$ROOM_ID" "$B_AT"
call T02 room B 403 PATCH "$R" "$B_AT" '{"name":"B가바꿈"}'
call T03 room B 403 GET "$R/events" "$B_AT" '' --max-time 3
call T04 room B 403 GET "$R/notification-setting" "$B_AT"
call T05 room B 403 PUT "$R/notification-setting" "$B_AT" '{"enabled":false}'
call T06 room B 403 POST "$R/upload-requests" "$B_AT"
call T07 media B 403 GET "$R/media" "$B_AT"
call T08 media B 403 GET "$R/media/all" "$B_AT"
call T09 media B 403 GET "$R/media/$MEDIA_ID" "$B_AT"
call T10 media B 403 PUT "$R/media/$MEDIA_ID/likes" "$B_AT"
call T11 media B 403 DELETE "$R/media/$MEDIA_ID/likes" "$B_AT"
call T12 media B 403 POST "$R/media/upload-urls" "$B_AT" '{"files":[{"fileName":"b.jpg","mimeType":"image/jpeg","size":1000}]}'
call T13 media B 403 POST "$R/media/$MEDIA_ID/upload-url" "$B_AT" '{}'
call T14 media B 403 POST "$R/media" "$B_AT" "{\"mediaIds\":[$MEDIA_ID]}"
call T15 download B 403 GET "$R/downloads/media/$MEDIA_ID" "$B_AT"
call T16 download B 403 POST "$R/downloads/batch" "$B_AT" "{\"mediaIds\":[$MEDIA_ID]}"
call T17 download B 403 POST "$R/downloads/zip" "$B_AT" "{\"mediaIds\":[$MEDIA_ID]}"
call T18 download B 403 GET "$R/downloads/zip/$JOB_ID" "$B_AT"
call T19 download B 404 GET "/rooms/$B_ROOM_ID/downloads/zip/$JOB_ID" "$B_AT"
call T20 folder B 403 POST "$R/folders" "$B_AT" '{"name":"B폴더"}'
call T21 folder B 403 PATCH "$R/folders/$FOLDER_ID" "$B_AT" '{"name":"B가바꿈"}'
call T22 folder B 403 PUT "$R/media/folders" "$B_AT" "{\"mediaIds\":[$MEDIA_ID],\"folderId\":$FOLDER_ID}"
call T23 folder B 403 DELETE "$R/media/folders" "$B_AT" "{\"mediaIds\":[$MEDIA_ID],\"folderIds\":[$FOLDER_ID]}"
call T24 folder B 403 DELETE "$R/folders/$FOLDER_ID" "$B_AT"
call T25 media B 403 DELETE "$R/media" "$B_AT" "{\"mediaIds\":[$MEDIA_ID]}"
call T26 media B 403 DELETE "$R/media/$MEDIA_ID" "$B_AT"
call T27 room B 403 DELETE "$R" "$B_AT"
call T28 room B 200 GET /members/me/rooms "$B_AT"
call T29 anon none 401 GET "$R/media/all" ""
call T30 anon forged 401 GET "$R/media/all" "${A_AT%?}x"

echo "== 2. 거부 후 A 데이터 변경 여부"
snapshot AFTER
if diff "$OUT/BEFORE.snapshot.json" "$OUT/AFTER.snapshot.json" > "$OUT/snapshot.diff"; then
  echo "SNAPSHOT: 변경 없음"; else echo "SNAPSHOT: 변경 있음"; cat "$OUT/snapshot.diff"; fi

echo "== 3. 응답 본문의 A 데이터 노출 검사"
: > "$OUT/leak.txt"
for f in "$OUT"/res/T*.body; do
  for m in 604A비밀방 604A폴더 604-A-secret "$ROOM_CODE"; do
    grep -q -- "$m" "$f" && echo "$(basename "$f" .body) contains $m" >> "$OUT/leak.txt"
  done
done
cat "$OUT/leak.txt"

echo "== 4. 방 ID만으로 입장 (공유 코드 없이)"
call T31 join B 403 POST "$R/members" "$B_AT" '{"nickname":"604B"}'
if [ "$LAST_STATUS" = 201 ] || [ "$LAST_STATUS" = 200 ]; then
  call T32 join B 403 GET "$R/media/all" "$B_AT"
  call T33 join B 204 DELETE "$R/members/me" "$B_AT"
fi

echo "== 5. Refresh token 재사용 (C)"
C_JSON=$(anon "604C"); C2_RT1=$(jq -r .data.refreshToken <<<"$C_JSON")
call U01 refresh C 200 POST /auth/refresh "" "{\"refreshToken\":\"$C2_RT1\"}"
C2_AT2=$(jq -r .data.accessToken <<<"$LAST_RAW"); C2_RT2=$(jq -r .data.refreshToken <<<"$LAST_RAW")
call U02 refresh C 401 POST /auth/refresh "" "{\"refreshToken\":\"$C2_RT1\"}"
echo "  (5초 이내 재사용 — 유예 구간, 세션 유지 기대)"
call U03 refresh C 200 GET /members/me/rooms "$C2_AT2"
sleep 6
call U04 refresh C 401 POST /auth/refresh "" "{\"refreshToken\":\"$C2_RT1\"}"
call U05 refresh C 401 POST /auth/refresh "" "{\"refreshToken\":\"$C2_RT2\"}"
call U06 refresh C 401 GET /members/me/rooms "$C2_AT2"

echo "== 6. 연결 코드 (B가 A 계정으로 로그인 시도)"
call L01 link B 400 POST /auth/link "" '{"refreshTokenDelivery":"BODY"}'
call L02 link B 404 POST /auth/link "" '{"linkCode":"000000","refreshTokenDelivery":"BODY"}'
L1=$(raw -X POST -H "Authorization: Bearer $A_AT" "$BASE/auth/link-code" | jq -r .data.linkCode)
A2_JSON=$(raw -X POST -H 'Content-Type: application/json' --data "{\"linkCode\":\"$L1\",\"refreshTokenDelivery\":\"BODY\"}" "$BASE/auth/link")
A2_AT=$(jq -r .data.accessToken <<<"$A2_JSON"); A2_RT=$(jq -r .data.refreshToken <<<"$A2_JSON")
echo "A의 두 번째 기기가 연결 코드 사용: userId=$(jq -r .data.userId <<<"$A2_JSON") (A=$A_ID)"
call L03 link B 404 POST /auth/link "" "{\"linkCode\":\"$L1\",\"refreshTokenDelivery\":\"BODY\"}"
L2=$(raw -X POST -H "Authorization: Bearer $A_AT" "$BASE/auth/link-code" | jq -r .data.linkCode)
echo "  만료 대기 (5분 10초)"
sleep 310
call L04 link B 410 POST /auth/link "" "{\"linkCode\":\"$L2\",\"refreshTokenDelivery\":\"BODY\"}"

echo "== 7. 로그아웃 (A의 두 번째 기기 세션)"
call O01 logout A2 204 POST /auth/logout "$A2_AT"
call O02 logout A2 401 GET /members/me/rooms "$A2_AT"
call O03 logout A2 401 POST /auth/refresh "" "{\"refreshToken\":\"$A2_RT\"}"
call O04 logout A 200 GET /members/me/rooms "$A_AT"

echo "== 8. 쿠키 방식 CSRF 헤더"
call X01 csrf D 400 POST /auth/anonymous "" '{"nickname":"604D","refreshTokenDelivery":"COOKIE"}'
# 쿠키 파일에는 refresh token 원문이 들어가므로 기록 폴더 밖에 두고 끝나면 지운다
JAR="$(mktemp)"; trap 'rm -f "$JAR"' EXIT
call X02 csrf D 201 POST /auth/anonymous "" '{"nickname":"604D","refreshTokenDelivery":"COOKIE"}' -H 'X-CSRF-Protection: 1' -c "$JAR"
D_AT=$(jq -r .data.accessToken <<<"$LAST_RAW")
call X03 csrf D 400 POST /auth/refresh "" '' -b "$JAR"
call X04 csrf D 400 POST /auth/refresh "" '' -b "$JAR" -H 'X-CSRF-Protection: 1' -H 'Origin: https://evil.example.com'
call X05 csrf D 200 POST /auth/refresh "" '' -b "$JAR" -c "$JAR" -H 'X-CSRF-Protection: 1'
rm -f "$JAR"

echo "== 9. 탈퇴 후 기존 토큰 (A)"
call Z01 cleanup B 200 DELETE "/rooms/$B_ROOM_ID" "$B_AT"
call Z02 cleanup A 200 DELETE "$R" "$A_AT"
call W01 withdraw A 204 DELETE /auth/account "$A_AT"
call W02 withdraw A 401 GET /members/me/rooms "$A_AT"
call W03 withdraw A 401 POST /auth/refresh "" "{\"refreshToken\":\"$A_RT\"}"
call W04 withdraw A 401 GET "/rooms/$ROOM_ID/media/all" "$A_AT"
for t in "$B_AT" "$D_AT"; do raw -o /dev/null -X DELETE -H "Authorization: Bearer $t" "$BASE/auth/account"; done
echo "== done"; date
