#!/usr/bin/env bash
# 직전 통합 릴리스 이후 들어간 변경 내역을 릴리스 노트 마크다운으로 출력한다.
#
# 명령어: release-notes.sh <직전 릴리스 태그 | 빈 문자열> <이번 배포 커밋>
#
# PR 라벨이 아니라 커밋이 실제로 바꾼 경로로 분류한다. 라벨은 빠지거나 틀리게 붙는 일이 잦아서
# 기준으로 삼기 어렵다. develop → main → deploy 승격은 머지 커밋이라 --no-merges 로 빠지고,
# develop 에 스쿼시 머지된 작업 커밋만 남는다.
# 전체 이력이 필요하므로 호출하는 쪽에서 fetch-depth: 0 으로 체크아웃해야 한다.
set -euo pipefail

PREVIOUS_RELEASE="${1:-}"
TARGET="${2:?이번 배포 커밋을 넘겨야 한다}"

if [ -n "$PREVIOUS_RELEASE" ]; then
    RANGE="${PREVIOUS_RELEASE}..${TARGET}"
else
    RANGE="$TARGET"
fi

FRONTEND=""
BACKEND=""
COMMON=""

# 버전 선언 파일을 뺀 나머지 경로를 출력한다.
# frontend/package.json 은 의존성 변경도 담기 때문에 version 줄만 바뀐 경우에만 뺀다.
changed_paths() {
    local sha="$1"
    git diff-tree --no-commit-id --name-only -r "$sha" | while read -r path; do
        case "$path" in
            VERSION | backend/gradle.properties | .github/backend-version-intents/*) continue ;;
            frontend/package.json)
                if ! git diff-tree -p --no-commit-id "$sha" -- "$path" |
                    grep '^[-+] ' | grep -qv '"version":'; then
                    continue
                fi
                ;;
        esac
        echo "$path"
    done
}

while IFS=' ' read -r SHA SUBJECT; do
    [ -z "$SHA" ] && continue
    PATHS="$(changed_paths "$SHA")"
    # 버전 숫자만 올린 릴리스 준비 커밋은 본문 위쪽 버전 표기와 겹치므로 목록에서 뺀다.
    [ -z "$PATHS" ] && continue
    TOUCHES_FRONTEND=0
    TOUCHES_BACKEND=0
    echo "$PATHS" | grep -q '^frontend/' && TOUCHES_FRONTEND=1
    echo "$PATHS" | grep -q '^backend/' && TOUCHES_BACKEND=1

    LINE="- ${SUBJECT}"$'\n'
    if [ "$TOUCHES_FRONTEND" -eq 1 ] && [ "$TOUCHES_BACKEND" -eq 0 ]; then
        FRONTEND+="$LINE"
    elif [ "$TOUCHES_BACKEND" -eq 1 ] && [ "$TOUCHES_FRONTEND" -eq 0 ]; then
        BACKEND+="$LINE"
    else
        # 양쪽을 함께 바꿨거나 버전·워크플로·문서처럼 어느 서비스에도 속하지 않는 변경
        COMMON+="$LINE"
    fi
done < <(git log --no-merges --format='%h %s' "$RANGE")

section() {
    local title="$1" body="$2"
    [ -z "$body" ] && return
    printf '### %s\n%s\n' "$title" "$body"
}

echo "## 변경 내역"
echo
if [ -z "${FRONTEND}${BACKEND}${COMMON}" ]; then
    echo "직전 릴리스 이후 변경된 커밋이 없다."
    echo
else
    section "프론트엔드" "$FRONTEND"
    section "백엔드" "$BACKEND"
    section "공통" "$COMMON"
fi

if [ -n "$PREVIOUS_RELEASE" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
    echo "전체 비교: https://github.com/${GITHUB_REPOSITORY}/compare/${PREVIOUS_RELEASE}...${TARGET}"
fi
