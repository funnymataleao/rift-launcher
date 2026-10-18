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

## Tablet test builds: fast start-up

Install `assembleModernRelease` on the tablet, not `assembleModernDebug`. The
`release` build type is minified with R8 and signed with the same debug key as
the debug build (`signingConfigs.debug`), so it installs over a debug build with
`adb install -r` and keeps the Steam sign-in and installed games.

A debug build is debuggable: ART interprets it and never compiles it ahead of
time, so Compose runs several times slower and every cold start also re-opens
the dex files straight from the APK (`compilation-filter=run-from-apk`, about
0.4 s on the RedMagic Astra before the first frame). Measured on that build
(2026-10-17): 2.0–2.2 s to the first frame; `cmd package compile -m speed` on a
debug build only reaches `verify` (no AOT for a debuggable APK) and brings it to
about 1.4 s.

After installing, start Rift once, have it write its Baseline Profile and compile
it. Right after `adb install` the profile is empty, and `speed-profile` on an empty
profile silently falls back to `verify`:

```
adb install -r app/build/outputs/apk/modern/release/app-modern-release.apk
adb shell monkey -p dev.rift.launcher 1
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE dev.rift.launcher/androidx.profileinstaller.ProfileInstallReceiver
adb shell am force-stop dev.rift.launcher
adb shell cmd package compile -f -m speed-profile dev.rift.launcher
adb shell dumpsys package dexopt | grep -A3 dev.rift.launcher
```

The broadcast answers `result=1` when the profile was written; `dumpsys` should
then show `status=speed-profile`.

`speed-profile` uses the Baseline Profile in the APK: the Compose and AndroidX
libraries ship their own, and `app/src/main/baseline-prof.txt` adds the app code
on the way from tap to home screen. The profile only applies to non-debuggable
builds.

Cold start time to the first frame:

```
adb shell am start -S -W -n dev.rift.launcher/app.gamenative.MainActivityAliasDefault
```

CI keeps building the debug APK; the release build differs only in the build
type, so the same blobs rule applies (`fetch-proprietary-blobs.sh` for a private
test build).
