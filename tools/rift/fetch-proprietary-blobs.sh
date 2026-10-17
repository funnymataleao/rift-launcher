#!/usr/bin/env bash
# Downloads GameNative's proprietary blobs into the working tree for a local,
# private test build. These files are gitignored: never commit them and never
# publish an APK that contains them (CI artifacts included) unless the
# GameNative maintainers have granted permission.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
commit="${1:-$(cat "$root/tools/rift/UPSTREAM_COMMIT")}"
base="https://raw.githubusercontent.com/utkarshdalal/GameNative/$commit"

grep -v '^#' "$root/tools/rift/proprietary-blobs.txt" | while read -r path; do
    [ -z "$path" ] && continue
    mkdir -p "$root/$(dirname "$path")"
    echo "fetch $path"
    curl -fsSL -o "$root/$path" "$base/$path"
done
