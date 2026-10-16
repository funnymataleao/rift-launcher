# Rift fork tooling

Rift Launcher is a fork of [GameNative](https://github.com/utkarshdalal/GameNative).
GameNative's engine, stores and launch flow stay as they are; Rift's own UI lives
in `app/src/main/java/dev/rift/launcher/` so upstream merges stay clean.

## Proprietary blobs

A few GameNative binaries are "all rights reserved" by its maintainers (see
`THIRD_PARTY_NOTICES` and `proprietary-blobs.txt`). They were removed from every
commit of this repository's history and must never be committed here.

- CI (`.github/workflows/rift-build.yml`) builds without them; that APK is safe to
  publish, but Windows games will not start on Android 15+.
- For a private test build, run `tools/rift/fetch-proprietary-blobs.sh` and build
  locally. Do not publish that APK.

## Syncing upstream

`tools/rift/sync-upstream.sh` clones GameNative, applies the same
`git filter-repo` rewrite (`filter-args.txt`) and fetches the result into a local
`upstream-gamenative` branch. The rewrite is deterministic, so commits we already
have keep their ids and the branch merges like a normal upstream. Upstream commit
in use: `UPSTREAM_COMMIT`.

The "Sync upstream GameNative" workflow (`.github/workflows/rift-sync-upstream.yml`)
does this every day: when GameNative has new commits it opens or refreshes the
PR "Sync upstream GameNative" from `rift/sync-upstream`, builds the APK and posts the
result there. A merge with conflicts gets the `sync-conflict` label and the list of
conflicting files. It needs "Allow GitHub Actions to create and approve pull
requests" in the repository settings, and a `RIFT_SYNC_TOKEN` secret with the
`workflow` scope for upstream commits that change `.github/workflows`.
