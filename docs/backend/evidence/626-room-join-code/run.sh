#!/usr/bin/env bash
# #626 방 코드 없는 입장 거부 확인 — 실제 서버에 요청을 보내 기록한다
#   BASE  대상 API (기본: 로컬 서버)
#   PSQL  사진 한 장을 DB에 직접 넣을 명령. 비우면 사진이 필요한 정상 흐름 확인을 건너뛴다
#         예) PSQL='docker exec -i loadtest-postgres psql -U sssok -d sssok_probe -qtA'
set -u
BASE="${BASE:-http://localhost:18091/api/v1}"
PSQL="${PSQL:-}"
OUT="$(cd "$(dirname "$0")" && pwd)/out"
rm -rf "$OUT"; mkdir -p "$OUT/res"
LOG="$OUT/results.tsv"
printf 'id\tgroup\tactor\tmethod\tpath\texpected\tactual\tresult\tsummary\n' > "$LOG"
PASS=0; FAIL=0

mask() { sed -E -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/<JWT>/g' -e 's/(X-Amz-[A-Za-z]+=)[^&"[:space:]]+/\1<redacted>/g'; }

# call ID GROUP ACTOR EXPECTED METHOD PATH TOKEN [BODY] [extra curl args...]
call() {
  local id="$1" group="$2" actor="$3" expected="$4" method="$5" path="$6" token="$7" body="${8:-}"
  shift 8 2>/dev/null || shift $#
  local args=(-sS -X "$method" -o "$OUT/res/$id.body" -w '%{http_code}' --max-time 15)
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  local status
  : > "$OUT/res/$id.body"
  status=$(curl "${args[@]}" "$@" "$BASE$path" 2>/dev/null)
  [ -z "$status" ] && status=000
  LAST_RAW=$(cat "$OUT/res/$id.body")
  mask < "$OUT/res/$id.body" > "$OUT/res/$id.m" && mv "$OUT/res/$id.m" "$OUT/res/$id.body"
  local result=PASS
  [ "$status" = "$expected" ] || result=FAIL
  [ "$result" = PASS ] && PASS=$((PASS + 1)) || FAIL=$((FAIL + 1))
  local summary; summary=$(tr -d '\r\n' < "$OUT/res/$id.body" | cut -c1-140)
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$group" "$actor" "$method" "$path" "$expected" "$status" "$result" "$summary" >> "$LOG"
  echo "$result $id [$actor] $method $path expected=$expected actual=$status"
}
body() { cat "$OUT/res/$1.body"; }
anon() {
  curl -sS --max-time 15 -X POST -H 'Content-Type: application/json' -H 'X-CSRF-Protection: 1' \
    --data "{\"nickname\":\"$1\"}" "$BASE/auth/anonymous" | jq -r .data.accessToken
}

echo "== 준비: 방장 H가 방과 폴더를 만든다. S는 초대 링크를 받지 않은 사람, I는 받은 사람이다"
H=$(anon "626방장"); S=$(anon "626외부인"); I=$(anon "626초대받은")
call P01 setup H 201 POST /rooms "$H" '{"name":"626비밀방","uploadPolicy":"everyone"}'
ROOM_ID=$(body P01 | jq -r .data.roomId); ROOM_CODE=$(body P01 | jq -r .data.code)
call P02 setup S 201 POST /rooms "$S" '{"name":"626외부인방"}'
OTHER_CODE=$(body P02 | jq -r .data.code)
call P03 setup H 201 POST "/rooms/$ROOM_ID/folders" "$H" '{"name":"626폴더"}'
FOLDER_ID=$(body P03 | jq -r '.data.id')
MEDIA_ID=999999
if [ -n "$PSQL" ]; then
  HOST_ID=$($PSQL -c "SELECT host_id FROM room WHERE id = $ROOM_ID")
  MEDIA_ID=$($PSQL -c "INSERT INTO stored_file (room_id, uploader_id, original_file_name, media_type, file_size_bytes, storage_key, status, created_at, updated_at, reserved_at, retry_count)
    VALUES ($ROOM_ID, $HOST_ID, '626-secret.jpg', 'JPEG', 1024, 'rooms/$ROOM_ID/626-secret', 'READY', now(), now(), now(), 0) RETURNING id" | head -1)
fi
echo "roomId=$ROOM_ID mediaId=$MEDIA_ID folderId=$FOLDER_ID"

echo "== A. 초대 링크 없는 사람(S)의 입장 시도 — 모두 거부돼야 한다"
call A01 join S 400 POST "/rooms/$ROOM_ID/members" "$S"
call A02 join S 400 POST "/rooms/$ROOM_ID/members" "$S" '{"nickname":"외부인"}'
call A03 join S 400 POST "/rooms/$ROOM_ID/members" "$S" '{"code":""}'
call A04 join S 400 POST "/rooms/$ROOM_ID/members" "$S" "{\"code\":\"$(tr 'A-Z' 'a-z' <<<"$ROOM_CODE")\"}"
call A05 join S 404 POST "/rooms/$ROOM_ID/members" "$S" '{"code":"ZZZZZZZZ"}'
call A06 join S 404 POST "/rooms/$ROOM_ID/members" "$S" "{\"code\":\"$OTHER_CODE\"}"
call A07 join S 404 POST "/rooms/$((ROOM_ID + 1000))/members" "$S" "{\"code\":\"$ROOM_CODE\"}"
call A08 join S 404 GET "/rooms/ZZZZZZZZ" "$S"
call A09 join S 200 GET /members/me/rooms "$S"
[ "$(body A09 | jq "[.data.rooms[] | select(.roomId == $ROOM_ID)] | length")" = 0 ] \
  && echo "  내 방 목록에 H의 방 없음" || { echo "FAIL A09 내 방 목록에 H의 방이 보임"; FAIL=$((FAIL + 1)); }

echo "== B. 입장 거부 뒤 S가 H의 방 자원에 접근 — 모두 403이어야 한다"
call B01 read S 403 GET "/rooms/$ROOM_ID/media" "$S"
call B02 read S 403 GET "/rooms/$ROOM_ID/media/all" "$S"
call B03 read S 403 GET "/rooms/$ROOM_ID/media/$MEDIA_ID" "$S"
call B04 read S 403 GET "/rooms/$ROOM_ID/events" "$S" "" -H 'Accept: text/event-stream'
call B05 read S 403 GET "/rooms/$ROOM_ID/notification-setting" "$S"
call B06 download S 403 GET "/rooms/$ROOM_ID/downloads/media/$MEDIA_ID" "$S"
call B07 download S 403 POST "/rooms/$ROOM_ID/downloads/batch" "$S" "{\"mediaIds\":[$MEDIA_ID]}"
call B08 download S 403 POST "/rooms/$ROOM_ID/downloads/zip" "$S" "{\"mediaIds\":[$MEDIA_ID]}"
call B09 write S 403 POST "/rooms/$ROOM_ID/folders" "$S" '{"name":"침입"}'
call B10 write S 403 PATCH "/rooms/$ROOM_ID/folders/$FOLDER_ID" "$S" '{"name":"침입"}'
call B11 write S 403 DELETE "/rooms/$ROOM_ID/folders/$FOLDER_ID" "$S"
call B12 write S 403 PUT "/rooms/$ROOM_ID/media/folders" "$S" "{\"mediaIds\":[$MEDIA_ID],\"folderIds\":[$FOLDER_ID]}"
call B13 write S 403 DELETE "/rooms/$ROOM_ID/media/folders" "$S" "{\"mediaIds\":[$MEDIA_ID],\"folderIds\":[$FOLDER_ID]}"
call B14 write S 403 PUT "/rooms/$ROOM_ID/media/$MEDIA_ID/likes" "$S"
call B15 write S 403 DELETE "/rooms/$ROOM_ID/media/$MEDIA_ID/likes" "$S"
call B16 write S 403 DELETE "/rooms/$ROOM_ID/media/$MEDIA_ID" "$S"
call B17 write S 403 DELETE "/rooms/$ROOM_ID/media" "$S" "{\"mediaIds\":[$MEDIA_ID]}"
call B18 write S 403 POST "/rooms/$ROOM_ID/media/upload-urls" "$S" '{"files":[{"fileName":"x.jpg","mimeType":"image/jpeg","size":10}]}'
call B19 write S 403 PUT "/rooms/$ROOM_ID/notification-setting" "$S" '{"enabled":true}'
call B20 write S 403 POST "/rooms/$ROOM_ID/upload-requests" "$S"
call B21 write S 403 POST "/rooms/$ROOM_ID/feedbacks" "$S" '{"content":"침입"}'
call B22 host S 403 PATCH "/rooms/$ROOM_ID" "$S" '{"name":"빼앗김"}'
call B23 host S 403 DELETE "/rooms/$ROOM_ID" "$S"

echo "== C. 초대 링크를 받은 사람(I)은 코드로 들어와 이용할 수 있어야 한다"
call C01 invite I 200 GET "/rooms/$ROOM_CODE" "$I"
call C02 invite I 201 POST "/rooms/$ROOM_ID/members" "$I" "{\"code\":\"$ROOM_CODE\",\"nickname\":\"초대받은\"}"
call C03 invite I 200 POST "/rooms/$ROOM_ID/members" "$I" "{\"code\":\"$ROOM_CODE\"}"
call C04 invite I 200 GET "/rooms/$ROOM_ID/media/all" "$I"
call C05 invite I 200 GET "/rooms/$ROOM_ID/notification-setting" "$I"
call C06 invite I 201 POST "/rooms/$ROOM_ID/folders" "$I" '{"name":"초대폴더"}'
if [ -n "$PSQL" ]; then
  call C07 invite I 200 GET "/rooms/$ROOM_ID/media/$MEDIA_ID" "$I"
  call C08 invite I 200 PUT "/rooms/$ROOM_ID/media/$MEDIA_ID/likes" "$I"
  # 단건 다운로드는 서명 URL 로 보내는 302 가 성공이다
  call C09 invite I 302 GET "/rooms/$ROOM_ID/downloads/media/$MEDIA_ID" "$I"
fi
# SSE 는 첫 하트비트(15초 주기)를 보낼 때 응답 헤더가 나간다. 그 뒤 연결을 끊고 상태 코드만 본다
call C10 invite I 200 GET "/rooms/$ROOM_ID/events" "$I" "" -H 'Accept: text/event-stream' --max-time 17
echo "== D. 나간 뒤에는 다시 막히고, 다시 들어오려면 코드가 필요하다"
call D01 leave I 204 DELETE "/rooms/$ROOM_ID/members/me" "$I"
call D02 leave I 403 GET "/rooms/$ROOM_ID/media/all" "$I"
call D03 leave I 400 POST "/rooms/$ROOM_ID/members" "$I" '{}'
call D04 leave I 404 POST "/rooms/$ROOM_ID/members" "$I" "{\"code\":\"$OTHER_CODE\"}"
call D05 leave I 201 POST "/rooms/$ROOM_ID/members" "$I" "{\"code\":\"$ROOM_CODE\"}"
call D06 leave I 200 GET "/rooms/$ROOM_ID/media/all" "$I"

echo "== E. 삭제된 방은 코드가 틀리면 404, 맞으면 410 (코드를 모르면 삭제 여부도 알 수 없다)"
call E01 deleted H 200 DELETE "/rooms/$ROOM_ID" "$H"
call E02 deleted S 404 POST "/rooms/$ROOM_ID/members" "$S" "{\"code\":\"$OTHER_CODE\"}"
call E03 deleted S 410 POST "/rooms/$ROOM_ID/members" "$S" "{\"code\":\"$ROOM_CODE\"}"

echo "PASS=$PASS FAIL=$FAIL"
