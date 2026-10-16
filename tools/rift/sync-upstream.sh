#!/usr/bin/env bash
# Brings GameNative upstream into a local branch, with the same history
# rewrite that produced this repository's import. git-filter-repo is
# deterministic, so commits we already have keep their ids and the result
# merges like an ordinary upstream branch.
#
# Usage: tools/rift/sync-upstream.sh [upstream-ref]   (default: master)
# Needs git-filter-repo on PATH (https://github.com/newren/git-filter-repo).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
ref="${1:-master}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git clone --single-branch --branch "$ref" https://github.com/utkarshdalal/GameNative.git "$work/gn"
upstream_commit="$(git -C "$work/gn" rev-parse HEAD)"
mapfile -t args < "$root/tools/rift/filter-args.txt"
(cd "$work/gn" && git filter-repo "${args[@]}" --force)

git -C "$root" fetch "$work/gn" "HEAD:refs/heads/upstream-gamenative"
echo "$upstream_commit" > "$root/tools/rift/UPSTREAM_COMMIT"
echo
echo "Filtered upstream $upstream_commit is in branch upstream-gamenative."
echo "Next: git merge upstream-gamenative, then commit tools/rift/UPSTREAM_COMMIT."
