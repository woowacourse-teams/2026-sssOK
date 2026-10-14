#!/bin/sh
# SSE 시나리오용 k6 를 bin/k6 로 빌드한다. xk6-sse 는 k6 자동 확장 받기 대상이 아니라 직접 빌드해야 한다.
# Go 없이 Docker 의 grafana/xk6 이미지로 빌드한다. 다른 OS 용은 GOOS·GOARCH 를 넘긴다 (예: 부하 발생용 EC2 → GOOS=linux GOARCH=amd64).
set -eu

K6_VERSION="${K6_VERSION:-v2.3.0}"
XK6_SSE_VERSION="${XK6_SSE_VERSION:-v0.2.0}"
GOOS="${GOOS:-$(uname -s | tr '[:upper:]' '[:lower:]')}"
case "$(uname -m)" in
  arm64 | aarch64) DEFAULT_ARCH=arm64 ;;
  *) DEFAULT_ARCH=amd64 ;;
esac
GOARCH="${GOARCH:-$DEFAULT_ARCH}"

cd "$(dirname "$0")/.."
mkdir -p bin
docker run --rm -e GOOS="$GOOS" -e GOARCH="$GOARCH" -u "$(id -u):$(id -g)" -v "$PWD/bin:/xk6" \
  grafana/xk6 build "$K6_VERSION" --with "github.com/phymbert/xk6-sse@$XK6_SSE_VERSION" --output /xk6/k6

echo "빌드 완료: bin/k6 ($GOOS/$GOARCH)"
if [ "$GOOS" = "$(uname -s | tr '[:upper:]' '[:lower:]')" ]; then
  ./bin/k6 version
fi
