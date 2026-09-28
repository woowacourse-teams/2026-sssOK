#!/usr/bin/env bash
set -euo pipefail

base_sha="${1:-}"
[ -n "$base_sha" ] || {
    echo "사용법: $0 <base-sha>" >&2
    exit 2
}

migration_pattern='backend/src/main/resources/db/migration/V*.sql'
changed_migrations="$(git diff --name-status --diff-filter=MDR "$base_sha" HEAD -- "$migration_pattern")"

[ -z "$changed_migrations" ] || {
    echo '::error::기존 Flyway 마이그레이션 파일은 수정, 삭제 또는 이름을 변경할 수 없다.'
    echo '문제가 된 파일:'
    printf '%s\n' "$changed_migrations"
    exit 1
}

echo '기존 Flyway 마이그레이션 파일 변경 없음'
