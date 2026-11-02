#!/usr/bin/env bash
# Run as the dedicated ubuntu runner. No sudo, host build or data deletion.
set -Eeuo pipefail
umask 077
source_dir=$(cd "$(dirname "$0")" && pwd)
root=${MCP_DEPLOY_ROOT:-/home/ubuntu/sssok-mcp}
: "${GALLERY_IMAGE:?Immutable image reference required}"
[[ "$GALLERY_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+@sha256:[a-f0-9]{64}$ ]] || { echo 'Invalid image digest'; exit 1; }
[[ -r "$root/gallery/.env" ]] || { echo 'ubuntu must be able to read gallery/.env'; exit 1; }
mkdir -p "$root/releases"
exec 9>"$root/deploy.lock"
flock -n 9 || { echo 'Another deployment is running'; exit 1; }
release=$(mktemp -d "$root/releases/gallery-XXXXXXXX")
cp "$source_dir/compose.yml" "$source_dir/smoke.py" "$source_dir/control.py" "$release/"
cp "$source_dir/../servers/gallery/model-checksums.json" "$release/"
chmod 644 "$release/model-checksums.json"
printf 'GALLERY_IMAGE=%s\n' "$GALLERY_IMAGE" > "$release/image.env"
# Per-release snapshot means rollback restores configuration as well as image.
cp "$root/gallery/.env" "$release/settings.env"
previous=$(readlink -f "$root/current" 2>/dev/null || true)
changed=false
drained=false
compose() {
  local dir=$1; shift
  # Ignore a legacy GALLERY_IMAGE in the caller environment on rollback.
  env -u GALLERY_IMAGE docker compose --project-name sssok-gallery \
    --env-file "$dir/settings.env" --env-file "$dir/image.env" -f "$dir/compose.yml" "$@"
}
inside() {
  local dir=$1 script=$2; shift 2
  compose "$dir" exec -T gallery-mcp python - "$@" < "$dir/$script"
}
smoke() { inside "$1" smoke.py; }
set_current() {
  ln -sfn "$1" "$root/current.next"
  python3 -c 'import os, sys; os.replace(sys.argv[1], sys.argv[2])' "$root/current.next" "$root/current"
}
finish() {
  local result=$?
  trap - EXIT INT TERM
  if [[ "$result" != 0 && "$changed" == true ]]; then
    if [[ -n "$previous" ]]; then
      echo 'Deployment failed; restoring previous image and configuration'
      if compose "$previous" up -d --wait --wait-timeout 90 && smoke "$previous"; then
        set_current "$previous" || echo 'ERROR: current link recovery failed'
        echo 'Previous release restored and verified'
      else
        echo 'ERROR: rollback failed; manual recovery required'
      fi
    else
      compose "$release" stop || true
      if [[ "$(readlink "$root/current" 2>/dev/null || true)" == "$release" ]]; then
        rm -f "$root/current"
      fi
      echo 'First deployment failed; container stopped, data preserved'
    fi
  fi
  if [[ "$drained" == true ]]; then
    if [[ "$result" == 0 ]]; then
      inside "$release" control.py resume || result=1
    else
      inside "$previous" control.py resume || echo 'ERROR: remove draining marker after recovery'
    fi
  fi
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
compose "$release" config --quiet
compose "$release" pull
# Verify model hashes and secret access under the actual container UID before downtime.
compose "$release" run --rm --no-deps -T \
  -v "$release/model-checksums.json:/tmp/model-checksums.json:ro" \
  --entrypoint python gallery-mcp - verify < "$release/control.py"
if [[ -n "$previous" ]]; then
  drained=true
  inside "$previous" control.py drain
else
  # Do not adopt an unmanaged container and lose its rollback information.
  [[ -z "$(compose "$release" ps -aq)" ]] || { echo 'Existing unmanaged deployment; migration required'; exit 1; }
fi
changed=true
compose "$release" up -d --wait --wait-timeout 90
smoke "$release"
set_current "$release"
if [[ "$drained" == true ]]; then
  inside "$release" control.py resume
  drained=false
fi
printf 'Deployed %s\n' "$GALLERY_IMAGE"
