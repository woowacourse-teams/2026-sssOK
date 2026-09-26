#!/usr/bin/env bash
# 릴리스 PR(develop → main, main → deploy)에서 버전 선언을 검증한다.
#
# 검증 항목
#   1. 세 버전이 모두 SemVer MAJOR.MINOR.PATCH 형식인가
#   2. 통합 릴리스 버전이 직전 운영 릴리스보다 올라갔는가
#   3. 같은 릴리스 태그가 이미 있지는 않은가
#   4. 백엔드가 바뀐 릴리스인데 백엔드 버전이 그대로이지는 않은가
#   5. 프론트엔드가 바뀐 릴리스인데 프론트엔드 버전이 그대로이지는 않은가
#
# 백엔드 버전은 작업 PR 자동화가, 나머지 버전은 사람이 올린다. 여기서는 최종 결과를 확인한다.
# 전체 태그 이력이 필요하므로 호출하는 쪽에서 fetch-depth: 0 으로 체크아웃해야 한다.
set -euo pipefail

SEMVER='^[0-9]+\.[0-9]+\.[0-9]+$'

fail() {
    echo "::error::$1"
    exit 1
}

read_release_version() {
    tr -d ' \t\r\n' < VERSION
}

read_backend_version() {
    sed -n 's/^version=//p' backend/gradle.properties | tail -n 1 | tr -d ' \t\r'
}

read_frontend_version() {
    node -p "require('./frontend/package.json').version"
}

# 주어진 커밋(태그)에서 같은 파일을 읽는다. 그 시점에 파일이 없으면 빈 문자열.
version_at() {
    local ref="$1" path="$2"
    case "$path" in
        VERSION) git show "$ref:VERSION" 2>/dev/null | tr -d ' \t\r\n' ;;
        backend) git show "$ref:backend/gradle.properties" 2>/dev/null |
            sed -n 's/^version=//p' | tail -n 1 | tr -d ' \t\r' ;;
        frontend) git show "$ref:frontend/package.json" 2>/dev/null |
            node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>process.stdout.write(JSON.parse(s).version??""))' ;;
    esac
}

# $1 이 $2 보다 확실히 크면 0. 같거나 작으면 1.
is_greater() {
    [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)" = "$1" ]
}

require_semver() {
    echo "$2" | grep -Eq "$SEMVER" || fail "$1 버전이 SemVer(MAJOR.MINOR.PATCH) 형식이 아니다: '$2'"
}

require_bumped() {
    local label="$1" path="$2" current="$3" previous="$4"
    if [ -z "$previous" ]; then
        echo "$label: 직전 릴리스에 버전 선언이 없다. 증가 검사를 건너뛴다 ($current)"
        return
    fi
    if git diff --quiet "$PREVIOUS_RELEASE" HEAD -- "$path"; then
        echo "$label: 직전 릴리스 이후 변경 없음 ($previous 유지)"
        return
    fi
    is_greater "$current" "$previous" ||
        fail "${label}가 바뀐 릴리스인데 버전이 $previous 에서 올라가지 않았다 (현재 $current)."
    echo "$label: $previous -> $current"
}

RELEASE_VERSION="$(read_release_version)"
BACKEND_VERSION="$(read_backend_version)"
FRONTEND_VERSION="$(read_frontend_version)"

require_semver '통합 릴리스' "$RELEASE_VERSION"
require_semver '백엔드' "$BACKEND_VERSION"
require_semver '프론트엔드' "$FRONTEND_VERSION"

RELEASE_TAG="release-v${RELEASE_VERSION}"
if git rev-parse -q --verify "refs/tags/${RELEASE_TAG}" > /dev/null; then
    fail "${RELEASE_TAG} 태그가 이미 있다. 루트 VERSION 을 올려야 한다."
fi

PREVIOUS_RELEASE="$(git tag -l 'release-v*' --sort=-v:refname | head -1)"
if [ -z "$PREVIOUS_RELEASE" ]; then
    echo "직전 운영 릴리스 태그가 없다 (첫 통합 릴리스). 형식 검사만 하고 끝낸다."
    echo "통합 릴리스 $RELEASE_VERSION / 백엔드 $BACKEND_VERSION / 프론트엔드 $FRONTEND_VERSION"
    exit 0
fi

PREVIOUS_RELEASE_VERSION="$(version_at "$PREVIOUS_RELEASE" VERSION)"
if [ -n "$PREVIOUS_RELEASE_VERSION" ]; then
    is_greater "$RELEASE_VERSION" "$PREVIOUS_RELEASE_VERSION" ||
        fail "통합 릴리스 버전이 직전 운영 릴리스($PREVIOUS_RELEASE_VERSION)보다 크지 않다 (현재 $RELEASE_VERSION)."
fi
echo "통합 릴리스: ${PREVIOUS_RELEASE_VERSION:-없음} -> $RELEASE_VERSION (직전 태그 $PREVIOUS_RELEASE)"

require_bumped '백엔드' backend/ "$BACKEND_VERSION" "$(version_at "$PREVIOUS_RELEASE" backend)"
require_bumped '프론트엔드' frontend/ "$FRONTEND_VERSION" "$(version_at "$PREVIOUS_RELEASE" frontend)"
