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

require_bump() {
    case "$1" in
        major|minor|patch) ;;
        *) fail "버전 증가 유형은 major, minor, patch 중 하나여야 한다: '$1'" ;;
    esac
}

next_version() {
    local version="$1" bump="$2" major minor patch
    require_semver '현재 백엔드' "$version"
    require_bump "$bump"
    IFS=. read -r major minor patch <<< "$version"

    case "$bump" in
        major) printf '%s.0.0\n' "$((major + 1))" ;;
        minor) printf '%s.%s.0\n' "$major" "$((minor + 1))" ;;
        patch) printf '%s.%s.%s\n' "$major" "$minor" "$((patch + 1))" ;;
    esac
}

highest_bump() {
    local highest=patch bump
    [ "$#" -gt 0 ] || fail '집계할 백엔드 버전 의도가 없다.'
    for bump in "$@"; do
        require_bump "$bump"
        case "$bump" in
            major) highest=major ;;
            minor) [ "$highest" = major ] || highest=minor ;;
        esac
    done
    printf '%s\n' "$highest"
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
    read) read_version "${2:-backend/gradle.properties}" ;;
    next) next_version "${2:-}" "${3:-}" ;;
    highest) shift; highest_bump "$@" ;;
    set)
        file="${2:-backend/gradle.properties}"
        version="${3:-}"
        require_semver '새 백엔드' "$version"
        replace_version "$file" "$version"
        ;;
    *)
        echo "사용법: $0 read [gradle.properties]" >&2
        echo "        $0 next <version> <major|minor|patch>" >&2
        echo "        $0 highest <major|minor|patch>..." >&2
        echo "        $0 set <gradle.properties> <version>" >&2
        exit 2
        ;;
esac
