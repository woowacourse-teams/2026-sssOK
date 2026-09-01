#!/bin/bash
# R2 버킷에 CORS 가 걸렸는지 확인한다. 방을 하나 만들고 서명 URL 을 받아
# 브라우저가 보내는 프리플라이트를 그대로 흉내낸다.
set -e
API=https://api.ssssok.com/api/v1
ORIGIN=${1:-http://localhost:3000}

TOKEN=$(curl -s -X POST $API/auth/anonymous -H 'Content-Type: application/json' \
  -d '{"nickname":"cors확인"}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).data.accessToken')

ROOM=$(curl -s -X POST $API/rooms -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"cors확인","uploadPolicy":"everyone","expiryHours":24}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).data.roomId')

URL=$(curl -s -X POST $API/rooms/$ROOM/media/upload-urls -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"files":[{"fileName":"a.png","mimeType":"image/png","size":70}]}' \
  | node -pe 'JSON.parse(require("fs").readFileSync(0)).data.issued[0].uploadUrl')

echo "Origin: $ORIGIN"
echo "--- 브라우저가 PUT 전에 보내는 프리플라이트 ---"
curl -s -i -X OPTIONS "$URL" \
  -H "Origin: $ORIGIN" \
  -H 'Access-Control-Request-Method: PUT' \
  -H 'Access-Control-Request-Headers: content-type' \
  | sed -n '1p;/[Aa]ccess-[Cc]ontrol/p;/<Message>/p'

echo
echo "통과 기준: 200/204 + access-control-allow-origin 헤더"
