#!/usr/bin/env bash
set -euo pipefail

intent_dir='.github/backend-version-intents'
version_file='backend/gradle.properties'
intents=()
files=()

while IFS= read -r file; do
    [ -n "$file" ] || continue
    files+=("$file")
    intents+=("$(tr -d ' \t\r\n' < "$file")")
done < <(find "$intent_dir" -maxdepth 1 -type f -name '*.txt' | sort)

[ "${#files[@]}" -gt 0 ] || {
    echo '반영할 백엔드 버전 의도가 없다.'
    exit 0
}

current="$(bash .github/scripts/backend-version.sh read "$version_file")"
bump="$(bash .github/scripts/backend-version.sh highest "${intents[@]}")"
next="$(bash .github/scripts/backend-version.sh next "$current" "$bump")"
bash .github/scripts/backend-version.sh set "$version_file" "$next"
rm "${files[@]}"

echo "백엔드 버전 준비: $current -> $next ($bump, 의도 ${#files[@]}개 반영)"
