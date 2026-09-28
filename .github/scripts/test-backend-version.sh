#!/usr/bin/env bash
set -euo pipefail

script='.github/scripts/backend-version.sh'

expect_pass() {
    bash "$script" at-least "$1" "$2" || {
        echo "::error::$1 이 최소 버전 $2 이상이어야 한다."
        exit 1
    }
}

expect_fail() {
    if bash "$script" at-least "$1" "$2"; then
        echo "::error::$1 이 최소 버전 $2 미만이어야 한다."
        exit 1
    fi
}

expect_pass 1.0.1 1.0.1
expect_pass 1.1.0 1.0.1
expect_pass 2.0.0 1.0.1
expect_pass 2.0.0 1.99.99
expect_fail 1.0.0 1.0.1
expect_fail 1.1.9 1.2.0
expect_fail 1.99.99 2.0.0

echo '백엔드 최소 버전 비교 테스트 통과'
