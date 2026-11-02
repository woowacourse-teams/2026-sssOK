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
# A missing current is a first deployment; a broken or malformed current is not.
previous=''
if [[ -e "$root/current" || -L "$root/current" ]]; then
  [[ -L "$root/current" && -d "$root/current" ]] || { echo 'Invalid current release; manual recovery required' >&2; exit 1; }
  previous=$(python3 -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve(strict=True))' "$root/current")
  for required in settings.env image.env compose.yml smoke.py control.py; do
    [[ -f "$previous/$required" && -r "$previous/$required" ]] || { echo "Invalid current release: missing readable $required" >&2; exit 1; }
  done
fi
release=$(mktemp -d "$root/releases/gallery-XXXXXXXX")
printf 'sqlite-v2\n' > "$release/storage-version"
cp "$source_dir/compose.yml" "$source_dir/smoke.py" "$source_dir/control.py" "$release/"
cp "$source_dir/../servers/gallery/model-checksums.json" "$release/"
chmod 644 "$release/model-checksums.json"
printf 'GALLERY_IMAGE=%s\n' "$GALLERY_IMAGE" > "$release/image.env"
# Per-release snapshot means rollback restores configuration as well as image.
cp "$root/gallery/.env" "$release/settings.env"
changed=false
drained=false
compose() (
  local dir=$1; shift
  # Only release snapshots supply GALLERY_* values, including during rollback.
  # The subshell leaves the deployer's environment (and image digest) intact.
  local name
  while IFS= read -r name; do
    unset "$name"
  done < <(compgen -e | LC_ALL=C sort | sed -n '/^GALLERY_/p')
  docker compose --project-name sssok-gallery \
    --env-file "$dir/settings.env" --env-file "$dir/image.env" -f "$dir/compose.yml" "$@"
)
data_directory() {
  compose "$1" config --format json | python3 -c '
import json, sys
from pathlib import Path
volumes = json.load(sys.stdin)["services"]["gallery-mcp"].get("volumes", [])
mounts = [v for v in volumes if v.get("target") == "/data"]
if len(mounts) != 1 or mounts[0].get("type") != "bind":
    sys.exit("Expected exactly one persistent /data bind mount")
source = mounts[0].get("source", "")
if not source or not Path(source).is_absolute():
    sys.exit("Expected an absolute /data source path")
print(Path(source).resolve())'
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
      if [[ "$(cat "$previous/storage-version" 2>/dev/null || true)" != sqlite-v2 && -f "$next_data/executions-v1.sqlite3" ]]; then
        # Older images cannot interpret the migrated schema or similar-photo jobs.
        compose "$release" stop || true
        drained=false
        echo 'ERROR: SQLite storage may be migrated; incompatible rollback refused. Keep data and restore a sqlite-v2 release manually.' >&2
      else
        echo 'Deployment failed; restoring previous image and configuration'
        if compose "$previous" up -d --wait --wait-timeout 90 && smoke "$previous"; then
          set_current "$previous" || echo 'ERROR: current link recovery failed'
          echo 'Previous release restored and verified'
        else
          echo 'ERROR: rollback failed; manual recovery required'
        fi
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
if [[ -n "$previous" ]]; then
  previous_data=$(data_directory "$previous")
  next_data=$(data_directory "$release")
  [[ "$previous_data" == "$next_data" ]] || {
    echo 'Persistent data directory changed; deployment refused. Migrate data separately before deploying.' >&2
    exit 1
  }
  if [[ "$(cat "$previous/storage-version" 2>/dev/null || true)" != sqlite-v1 && "$(cat "$previous/storage-version" 2>/dev/null || true)" != sqlite-v2 && -f "$next_data/executions-v1.sqlite3" ]]; then
    echo 'SQLite data exists but the previous release is JSON-only; manual recovery required.' >&2
    exit 1
  fi
fi
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
