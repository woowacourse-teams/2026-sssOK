#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -e .env ]; then
  echo '.env가 이미 있습니다. 기존 비밀번호를 유지합니다.'
  exit 0
fi
umask 077
password="$(openssl rand -hex 24)"
# 재실행이나 동시 실행 시 기존 파일을 덮어쓰지 않는다.
set -o noclobber
printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$password" > .env
echo '.env 생성 완료. 비밀번호는 화면에 출력하지 않습니다.'
