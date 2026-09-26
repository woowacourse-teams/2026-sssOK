#!/usr/bin/env bash
set -euo pipefail

body_file="${1:-}"
[ -f "$body_file" ] || {
    echo "::error::PR 본문 파일을 찾을 수 없다: '$body_file'"
    exit 1
}

selected=()
grep -Eq '^- \[[xX]\] MAJOR([[:space:]]|—|$)' "$body_file" && selected+=(major)
grep -Eq '^- \[[xX]\] MINOR([[:space:]]|—|$)' "$body_file" && selected+=(minor)
grep -Eq '^- \[[xX]\] PATCH([[:space:]]|—|$)' "$body_file" && selected+=(patch)
grep -Eq '^- \[[xX]\] 변경 없음([[:space:]]|—|$)' "$body_file" && selected+=(none)

if [ "${#selected[@]}" -ne 1 ]; then
    echo "::error::PR 본문의 '백엔드 버전'에서 MAJOR, MINOR, PATCH, 변경 없음 중 하나만 선택해야 한다."
    exit 1
fi

printf '%s\n' "${selected[0]}"
