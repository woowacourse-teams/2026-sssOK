#!/usr/bin/env bash
set -euo pipefail

SEMVER='^[0-9]+\.[0-9]+\.[0-9]+$'

fail() {
    echo "::error::$1"
    exit 1
}

read_version() {
    local file="$1" count
    [ -f "$file" ] && [ ! -L "$file" ] || fail "일반 버전 파일이 아니다: '$file'"
    count="$(grep -c '^version=' "$file" || true)"
    [ "$count" -eq 1 ] || fail "'$file'에는 version 선언이 정확히 하나 있어야 한다."
    sed -n 's/^version=//p' "$file" | tr -d ' \t\r'
}

require_semver() {
    echo "$2" | grep -Eq "$SEMVER" || fail "$1 버전이 SemVer(MAJOR.MINOR.PATCH) 형식이 아니다: '$2'"
}

next_version() {
    local version="$1" bump="$2" major minor patch
    IFS=. read -r major minor patch <<< "$version"

    case "$bump" in
        major) printf '%s.0.0\n' "$((major + 1))" ;;
        minor) printf '%s.%s.0\n' "$major" "$((minor + 1))" ;;
        patch) printf '%s.%s.%s\n' "$major" "$minor" "$((patch + 1))" ;;
        none) printf '%s\n' "$version" ;;
        *) fail "알 수 없는 백엔드 버전 증가 유형이다: '$bump'" ;;
    esac
}

replace_version() {
    local file="$1" version="$2" temporary
    read_version "$file" > /dev/null
    temporary="$(mktemp)"
    sed "s/^version=.*/version=${version}/" "$file" > "$temporary"
    cat "$temporary" > "$file"
    rm "$temporary"
}

command="${1:-}"
case "$command" in
    next)
        current="${2:-}"
        bump="${3:-}"
        require_semver '현재 백엔드' "$current"
        next_version "$current" "$bump"
        ;;
    set)
        file="${2:-backend/gradle.properties}"
        base_version="${3:-}"
        bump="${4:-}"
        require_semver '기준 백엔드' "$base_version"
        next="$(next_version "$base_version" "$bump")"
        replace_version "$file" "$next"
        echo "$next"
        ;;
    check)
        base_file="${2:-}"
        head_file="${3:-backend/gradle.properties}"
        bump="${4:-}"
        base_version="$(read_version "$base_file")"
        head_version="$(read_version "$head_file")"
        require_semver '기준 백엔드' "$base_version"
        require_semver '현재 백엔드' "$head_version"
        expected="$(next_version "$base_version" "$bump")"
        [ "$head_version" = "$expected" ] ||
            fail "백엔드 $bump 변경에는 버전이 $base_version -> $expected 이어야 한다 (현재 $head_version)."
        echo "백엔드 버전 확인: $base_version -> $head_version ($bump)"
        ;;
    *)
        echo "사용법: $0 next <version> <major|minor|patch|none>" >&2
        echo "        $0 set <gradle.properties> <base-version> <major|minor|patch|none>" >&2
        echo "        $0 check <base-gradle.properties> <head-gradle.properties> <major|minor|patch|none>" >&2
        exit 2
        ;;
esac
