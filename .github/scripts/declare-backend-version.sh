#!/usr/bin/env bash
set -euo pipefail

issue="${1:-}"
bump="${2:-}"
issue="${issue#\#}"

echo "$issue" | grep -Eq '^[0-9]+$' || {
    echo "사용법: $0 <이슈번호> <major|minor|patch>" >&2
    exit 2
}

bash .github/scripts/backend-version.sh next 0.0.0 "$bump" > /dev/null

intent_dir='.github/backend-version-intents'
intent_file="${intent_dir}/${issue}.txt"
mkdir -p "$intent_dir"
printf '%s\n' "$bump" > "$intent_file"
echo "백엔드 버전 의도 생성: $intent_file ($bump)"
