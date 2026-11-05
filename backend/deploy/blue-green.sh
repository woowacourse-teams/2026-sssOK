#!/usr/bin/env bash
# 운영 블루-그린 배포. 배포 경로(DEPLOY_PATH)에서 실행한다.
#
#   bash deploy/blue-green.sh deploy     새 이미지를 쉬는 칸에 띄우고 트래픽을 넘긴 뒤 기존 칸을 끈다
#   bash deploy/blue-green.sh rollback   직전 칸을 다시 띄우고 트래픽을 되돌린다
#   bash deploy/blue-green.sh status     활성 칸과 컨테이너 상태를 보여 준다
#
# 활성 칸의 기준은 nginx/upstream.conf 다 (nginx 가 실제로 트래픽을 보내는 곳). 배포가 중간에 끊겨도
# 다시 실행하면 이 파일과 실행 중인 컨테이너를 보고 이어서 맞춘다.
#
# deploy 에 필요한 환경변수: NEW_IMAGE, APP_RELEASE_VERSION, APP_BACKEND_VERSION, APP_GIT_SHA
# 아래 기본값(COMPOSE_FILES·NGINX_URL·SKIP_PULL 등)은 로컬 리허설에서만 바꾼다.
set -euo pipefail

cd "${DEPLOY_PATH:-$(dirname "$0")/..}"

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.prod.yml}"
NGINX_URL="${NGINX_URL:-http://localhost}"
NGINX_CONTAINER="${NGINX_CONTAINER:-sssok-nginx}"
LEGACY_CONTAINER="${LEGACY_CONTAINER:-sssok-app}"
MIN_AVAILABLE_MB="${MIN_AVAILABLE_MB:-600}"
MEMORY_WAIT_SECONDS="${MEMORY_WAIT_SECONDS:-300}"
MEMINFO_FILE="${MEMINFO_FILE:-/proc/meminfo}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-180}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-50}"
AUX_SERVICES="${AUX_SERVICES-alloy node-exporter docker-socket-proxy}"

# 함수 출력(echo)을 값으로 쓰는 곳이 있어 로그는 stderr 로 보낸다.
log() { echo "[blue-green] $*" >&2; }
fail() { echo "[blue-green] 실패: $*" >&2; exit 1; }

compose() {
  # shellcheck disable=SC2086
  docker compose --env-file .env --env-file blue.env --env-file green.env $COMPOSE_FILES "$@"
}

container_of() {
  case "$1" in
    legacy) echo "$LEGACY_CONTAINER" ;;
    *) echo "sssok-app-$1" ;;
  esac
}

host_of() {
  case "$1" in
    legacy) echo app ;;
    *) echo "app-$1" ;;
  esac
}

other_of() {
  case "$1" in
    blue) echo green ;;
    *) echo blue ;;
  esac
}

is_running() {
  [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ]
}

exists() {
  docker inspect "$1" > /dev/null 2>&1
}

nginx_exec() {
  docker exec "$NGINX_CONTAINER" "$@"
}

# nginx/upstream.conf 가 가리키는 칸. 파일이 없으면 기존 단일 앱(legacy)이 떠 있는지로 판단한다.
active_color() {
  if [ -f nginx/upstream.conf ]; then
    case "$(grep -oE 'server app(-blue|-green)?:8080' nginx/upstream.conf | head -n 1)" in
      'server app-blue:8080') echo blue ;;
      'server app-green:8080') echo green ;;
      'server app:8080') echo legacy ;;
      *) echo none ;;
    esac
  elif is_running "$LEGACY_CONTAINER"; then
    echo legacy
  else
    echo none
  fi
}

write_upstream() {
  local host="$1"
  cat > nginx/upstream.conf.tmp <<EOF
# 배포 스크립트(deploy/blue-green.sh)가 만든다. 직접 고치지 않는다.
upstream sssok_app {
    zone sssok_app 64k;
    server $host:8080 resolve;
}

upstream sssok_app_management {
    zone sssok_app_management 64k;
    server $host:8081 resolve;
}
EOF
  mv nginx/upstream.conf.tmp nginx/upstream.conf
}

reload_nginx() {
  nginx_exec nginx -t && nginx_exec nginx -s reload
}

# nginx 가 nginx/ 디렉터리를 conf.d 로 마운트하고 있어야 upstream.conf 를 읽는다. 기존 단일 파일 마운트에서
# 처음 넘어올 때만 nginx 를 다시 만든다(1~2초 끊김). 그 뒤로는 reload 만 한다.
ensure_nginx() {
  mkdir -p nginx
  [ -f nginx/default.conf ] || fail "nginx/default.conf 가 없다 (저장소 nginx.conf 를 복사해야 한다)"
  if [ ! -f nginx/upstream.conf ]; then
    local current
    current="$(active_color)"
    [ "$current" = none ] && current=blue
    write_upstream "$(host_of "$current")"
    log "upstream.conf 생성: $(host_of "$current")"
  fi

  local mounts
  mounts="$(docker inspect -f '{{range .Mounts}}{{.Destination}};{{end}}' "$NGINX_CONTAINER" 2>/dev/null || true)"
  if ! is_running "$NGINX_CONTAINER" || ! printf '%s' "$mounts" | grep -q '/etc/nginx/conf.d;'; then
    check_same_project
    [ "${SKIP_PULL:-0}" = 1 ] || compose pull nginx || fail "nginx 이미지를 받지 못했다. 기존 nginx 는 그대로 둔다"
    log "nginx 를 conf.d 디렉터리 마운트로 다시 만든다"
    compose up -d --no-deps --force-recreate nginx
    wait_nginx_up
  fi
  reload_nginx || fail "nginx 설정 검사 실패. 실행 중인 nginx 는 기존 설정을 유지한다"
}

# 다시 만든 nginx 는 이 compose 프로젝트의 네트워크에 붙는다. 기존 단일 앱이 다른 프로젝트로 떠 있으면
# nginx 가 그 앱에 닿지 못해 새 칸이 준비될 때까지 끊기므로, 그 경우 아무것도 바꾸지 않고 멈춘다.
check_same_project() {
  is_running "$LEGACY_CONTAINER" || return 0
  local expected actual
  expected="$(compose config 2>/dev/null | sed -n 's/^name: *//p' | head -n 1)"
  actual="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$LEGACY_CONTAINER")"
  [ "$expected" = "$actual" ] ||
    fail "$LEGACY_CONTAINER 의 compose 프로젝트($actual)가 배포 경로의 프로젝트($expected)와 다르다. nginx 를 다시 만들면 끊기므로 멈춘다"
}

wait_nginx_up() {
  local i
  for i in $(seq 1 30); do
    nginx_exec nginx -t > /dev/null 2>&1 && return 0
    sleep 1
  done
  fail "nginx 가 30초 안에 뜨지 않았다"
}

wait_memory() {
  [ -r "$MEMINFO_FILE" ] || { log "메모리 정보($MEMINFO_FILE)가 없어 확인을 건너뛴다"; return 0; }
  local waited=0 available
  while :; do
    available="$(awk '/^MemAvailable:/ {print int($2 / 1024)}' "$MEMINFO_FILE")"
    if [ "$available" -ge "$MIN_AVAILABLE_MB" ]; then
      log "가용 메모리 ${available}MiB (기준 ${MIN_AVAILABLE_MB}MiB)"
      return 0
    fi
    [ "$waited" -ge "$MEMORY_WAIT_SECONDS" ] &&
      fail "가용 메모리 ${available}MiB 가 기준 ${MIN_AVAILABLE_MB}MiB 보다 적다. 기존 앱은 그대로 둔다"
    log "가용 메모리 ${available}MiB, 기다린다 (${waited}s)"
    sleep 15
    waited=$((waited + 15))
  done
}

restart_count() {
  docker inspect -f '{{.RestartCount}}' "$1" 2>/dev/null || echo 0
}

# DB 연결까지 보는 readiness 로 확인한다. 컨테이너가 죽거나 확인 도중 재시작되면 기다리지 않고 바로 실패한다.
wait_ready() {
  local color="$1" host container waited=0 restarts
  host="$(host_of "$color")"
  container="$(container_of "$color")"
  restarts="$(restart_count "$container")"
  while [ "$waited" -lt "$READY_TIMEOUT_SECONDS" ]; do
    if [ "$(restart_count "$container")" -gt "$restarts" ]; then
      log "$container 이 기동 중 재시작됐다"
      return 1
    fi
    is_running "$container" || return 1
    if nginx_exec wget -q -T 3 -O - "http://$host:8081/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
      log "$container 준비 완료 (${waited}s)"
      return 0
    fi
    sleep 5
    waited=$((waited + 5))
  done
  return 1
}

running_sha() {
  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$1" | sed -n 's/^APP_GIT_SHA=//p'
}

verify_via_nginx() {
  local i ok=0
  for i in $(seq 1 20); do
    if curl -fsS --max-time 3 "$NGINX_URL/health" > /dev/null 2>&1; then
      ok=$((ok + 1))
      [ "$ok" -ge 3 ] && return 0
    else
      ok=0
    fi
    sleep 1
  done
  return 1
}

switch_to() {
  local color="$1"
  cp nginx/upstream.conf nginx/upstream.conf.prev
  write_upstream "$(host_of "$color")"
  if ! reload_nginx; then
    mv nginx/upstream.conf.prev nginx/upstream.conf
    return 1
  fi
  log "트래픽 전환: $(host_of "$color")"
}

switch_back() {
  [ -f nginx/upstream.conf.prev ] || return 1
  mv nginx/upstream.conf.prev nginx/upstream.conf
  reload_nginx
}

retire() {
  local color="$1" container
  container="$(container_of "$color")"
  is_running "$container" || return 0
  log "$container 종료 (진행 중인 작업을 마칠 때까지 최대 ${STOP_TIMEOUT_SECONDS}s)"
  docker stop -t "$STOP_TIMEOUT_SECONDS" "$container" > /dev/null
  # 기존 단일 앱은 다시 쓰지 않으므로 지운다. 색 칸은 롤백용으로 남겨 둔다.
  [ "$color" = legacy ] && docker rm "$container" > /dev/null
  return 0
}

discard() {
  local color="$1"
  compose logs --tail 100 "app-$color" || true
  compose rm -fsv "app-$color" > /dev/null 2>&1 || true
}

write_color_env() {
  local color="$1" prefix
  prefix="$(printf '%s' "$color" | tr '[:lower:]' '[:upper:]')"
  {
    echo "${prefix}_IMAGE=$NEW_IMAGE"
    echo "${prefix}_RELEASE_VERSION=$APP_RELEASE_VERSION"
    echo "${prefix}_BACKEND_VERSION=$APP_BACKEND_VERSION"
    echo "${prefix}_GIT_SHA=$APP_GIT_SHA"
  } > "$color.env"
}

# 배포할 이미지, 두 칸의 이미지(롤백용 포함), 실행 중인 컨테이너의 이미지는 남긴다.
cleanup_images() {
  local repository="${NEW_IMAGE%:*}" keep image
  keep="$NEW_IMAGE
$(grep -hE '^(BLUE|GREEN)_IMAGE=' blue.env green.env 2>/dev/null | cut -d= -f2- || true)
$(docker ps --format '{{.Image}}')"
  docker image ls "$repository" --format '{{.Repository}}:{{.Tag}}' | while IFS= read -r image; do
    [ "${image##*:}" = '<none>' ] && continue
    printf '%s\n' "$keep" | grep -qxF "$image" && continue
    docker image rm "$image" > /dev/null 2>&1 || true
  done
  docker image prune -f > /dev/null 2>&1 || true
}

# 활성 칸 컨테이너가 꺼져 있으면(서버 재시작 등) 다른 칸이 살아 있을 때 그쪽으로 먼저 맞춘다.
reconcile() {
  local active="$1" other
  case "$active" in
    blue|green) ;;
    *) echo "$active"; return 0 ;;
  esac
  if is_running "$(container_of "$active")"; then
    echo "$active"
    return 0
  fi
  other="$(other_of "$active")"
  if is_running "$(container_of "$other")" && wait_ready "$other" && switch_to "$other"; then
    log "활성 칸 $active 가 꺼져 있어 실행 중인 $other 로 맞췄다"
    echo "$other"
    return 0
  fi
  log "실행 중인 칸이 없다. 새로 띄운다"
  echo none
}

deploy() {
  : "${NEW_IMAGE:?NEW_IMAGE 가 필요하다}" "${APP_GIT_SHA:?APP_GIT_SHA 가 필요하다}"
  : "${APP_RELEASE_VERSION:=unknown}" "${APP_BACKEND_VERSION:=unknown}"
  touch blue.env green.env

  ensure_nginx
  local active target
  active="$(reconcile "$(active_color)")"
  case "$active" in
    blue) target=green ;;
    *) target=blue ;;
  esac
  log "활성: $active → 대상: $target ($NEW_IMAGE)"

  wait_memory
  cleanup_images

  write_color_env "$target"
  [ "${SKIP_PULL:-0}" = 1 ] || compose pull "app-$target" || fail "이미지를 받지 못했다. 트래픽은 $active 에 그대로 있다"
  if ! compose up -d --no-deps --force-recreate "app-$target"; then
    discard "$target"
    fail "app-$target 을 띄우지 못했다. 트래픽은 $active 에 그대로 있다"
  fi

  if ! wait_ready "$target"; then
    discard "$target"
    fail "app-$target 이 준비되지 않았다. 트래픽은 $active 에 그대로 있다"
  fi
  if [ "$(running_sha "$(container_of "$target")")" != "$APP_GIT_SHA" ]; then
    discard "$target"
    fail "app-$target 의 커밋이 배포 커밋($APP_GIT_SHA)과 다르다"
  fi
  if ! switch_to "$target"; then
    discard "$target"
    fail "nginx 전환 실패. 트래픽은 $active 에 그대로 있다"
  fi
  if ! verify_via_nginx; then
    switch_back || true
    discard "$target"
    fail "전환 후 nginx 경유 헬스체크 실패. 트래픽을 $active 로 되돌렸다"
  fi

  # 기존 활성 칸과, 중간에 끊긴 이전 배포가 남긴 칸까지 대상 칸 말고는 모두 끈다.
  local color
  for color in blue green legacy; do
    [ "$color" = "$target" ] || retire "$color"
  done
  echo "$target" > active-color
  rm -f nginx/upstream.conf.prev

  if [ -n "$AUX_SERVICES" ]; then
    # shellcheck disable=SC2086
    compose up -d --no-deps $AUX_SERVICES || log "일부 보조 서비스 기동 실패 - 앱 배포는 유지한다"
  fi
  cleanup_images
  log "배포 완료: app-$target"
}

rollback() {
  local active previous
  active="$(active_color)"
  case "$active" in
    blue|green) ;;
    *) fail "롤백할 수 있는 칸 구성이 아니다 (활성: $active)" ;;
  esac
  previous="$(other_of "$active")"
  exists "$(container_of "$previous")" || fail "$(container_of "$previous") 컨테이너가 없어 되돌릴 수 없다"

  wait_memory
  docker start "$(container_of "$previous")" > /dev/null
  wait_ready "$previous" || fail "$(container_of "$previous") 이 준비되지 않았다. 트래픽은 $active 에 그대로 있다"
  switch_to "$previous" || fail "nginx 전환 실패. 트래픽은 $active 에 그대로 있다"
  if ! verify_via_nginx; then
    switch_back || true
    fail "전환 후 nginx 경유 헬스체크 실패. 트래픽을 $active 로 되돌렸다"
  fi
  retire "$active"
  echo "$previous" > active-color
  rm -f nginx/upstream.conf.prev
  log "롤백 완료: app-$previous"
}

status() {
  local color
  echo "활성 칸: $(active_color)"
  for color in blue green legacy; do
    if exists "$(container_of "$color")"; then
      printf '%-18s %-8s %s\n' "$(container_of "$color")" \
        "$(docker inspect -f '{{.State.Status}}' "$(container_of "$color")")" \
        "$(docker inspect -f '{{.Config.Image}}' "$(container_of "$color")")"
    fi
  done
}

case "${1:-}" in
  deploy) deploy ;;
  rollback) rollback ;;
  status) status ;;
  *) echo "사용법: $0 {deploy|rollback|status}" >&2; exit 2 ;;
esac
