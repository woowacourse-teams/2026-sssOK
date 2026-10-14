#!/usr/bin/env bash
set -euo pipefail

base_sha="${1:-}"
[ -n "$base_sha" ] || {
    echo "사용법: $0 <base-sha>" >&2
    exit 2
}

intent_dir='.github/backend-version-intents'
version_file='backend/gradle.properties'
added_intents=()
deleted_intents=()
modified_intents=()

while IFS=$'\t' read -r status path; do
    [ -n "$status" ] || continue
    case "$path" in
        "$intent_dir"/*.txt)
            case "$status" in
                A) added_intents+=("$path") ;;
                D) deleted_intents+=("$path") ;;
                *) modified_intents+=("$path") ;;
            esac
            ;;
    esac
done < <(git diff --name-status "$base_sha" HEAD -- "$intent_dir")

version_changed=false
git diff --quiet "$base_sha" HEAD -- "$version_file" || version_changed=true

backend_changed=false
git diff --quiet "$base_sha" HEAD -- backend/ ":!$version_file" || backend_changed=true

if [ "$version_changed" = false ]; then
    [ "${#deleted_intents[@]}" -eq 0 ] && [ "${#modified_intents[@]}" -eq 0 ] || {
        echo '::error::작업 PR에서는 기존 백엔드 버전 의도를 수정하거나 삭제할 수 없다.'
        exit 1
    }

    if [ "$backend_changed" = true ]; then
        # Actions의 merge ref 대신 PR 원본 브랜치에서 이슈 번호를 확인한다.
        # 로컬 검증에서는 현재 작업 브랜치를 사용한다. 입력은 코드로 실행하지 않는다.
        head_ref="${PR_HEAD_REF:-$(git symbolic-ref --quiet --short HEAD || true)}"
        # 버전 의도 식별에 필요한 번호만 읽는다. 브랜치 네이밍은 검사하지 않는다.
        issue_pattern='#([0-9]+)'
        [[ "$head_ref" =~ $issue_pattern ]] || {
            echo '::error::백엔드 작업 PR의 원본 브랜치에서 이슈 번호를 확인할 수 없다.'
            exit 1
        }
        intent_file="$intent_dir/${BASH_REMATCH[1]}.txt"
        if [ "${#added_intents[@]}" -eq 1 ]; then
            [ "${added_intents[0]}" = "$intent_file" ] || {
                echo '::error::새 버전 의도 파일은 PR 브랜치의 이슈 번호와 같아야 한다.'
                exit 1
            }
        elif [ "${#added_intents[@]}" -eq 0 ]; then
            git cat-file -e "$base_sha:$intent_file" 2>/dev/null || {
                echo '::error::동일 이슈의 기존 버전 의도가 없다. 새 의도 파일이 필요하다.'
                exit 1
            }
            echo "동일 이슈의 기존 버전 의도 재사용: $intent_file"
        else
            echo '::error::백엔드 작업 PR에는 여러 버전 의도를 추가할 수 없다.'
            exit 1
        fi
        [ -f "$intent_file" ] && [ ! -L "$intent_file" ] || {
            echo '::error::버전 의도는 일반 파일이어야 한다.'
            exit 1
        }
        bump="$(tr -d ' \t\r\n' < "$intent_file")"
        bash .github/scripts/backend-version.sh next 0.0.0 "$bump" > /dev/null
        echo "백엔드 작업 버전 의도 확인: $bump ($intent_file)"
    else
        [ "${#added_intents[@]}" -eq 0 ] || {
            echo '::error::백엔드 변경이 없는 PR에는 백엔드 버전 의도를 추가할 수 없다.'
            exit 1
        }
        echo '백엔드 애플리케이션 및 버전 변경 없음'
    fi
    exit 0
fi

[ "$backend_changed" = false ] || {
    echo '::error::릴리스 준비 PR에는 백엔드 애플리케이션 변경을 함께 넣을 수 없다.'
    exit 1
}
[ "${#added_intents[@]}" -eq 0 ] && [ "${#modified_intents[@]}" -eq 0 ] || {
    echo '::error::릴리스 준비 PR에서는 새 버전 의도를 추가하거나 기존 의도를 수정할 수 없다.'
    exit 1
}

base_intents=()
while IFS= read -r path; do
    [ -n "$path" ] || continue
    base_intents+=("$(git show "$base_sha:$path" | tr -d ' \t\r\n')")
done < <(git ls-tree -r --name-only "$base_sha" -- "$intent_dir" | grep '\.txt$' || true)

[ "${#base_intents[@]}" -gt 0 ] || {
    echo '::error::반영할 버전 의도 없이 백엔드 버전을 변경했다.'
    exit 1
}
[ "${#deleted_intents[@]}" -eq "${#base_intents[@]}" ] || {
    echo '::error::릴리스 준비 PR은 누적된 백엔드 버전 의도를 모두 소비해야 한다.'
    exit 1
}

bump="$(bash .github/scripts/backend-version.sh highest "${base_intents[@]}")"
base_version="$(git show "$base_sha:$version_file" | sed -n 's/^version=//p' | tr -d ' \t\r\n')"
expected="$(bash .github/scripts/backend-version.sh next "$base_version" "$bump")"
head_version="$(bash .github/scripts/backend-version.sh read "$version_file")"
bash .github/scripts/backend-version.sh at-least "$head_version" "$expected" || {
    echo "::error::릴리스 준비 결과는 최소 $base_version -> $expected 이어야 한다 (현재 $head_version)."
    exit 1
}

echo "백엔드 릴리스 버전 확인: $base_version -> $head_version (최소 $expected, $bump 의도 ${#base_intents[@]}개 소비)"
