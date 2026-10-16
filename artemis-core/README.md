# artemis-core

Artemis' streaming core as an Android library module for Rift.

Source: [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android),
branch `moonlight-noir`, commit `c5cf27f4dc822db0e863c4691e7a70c74bea977a`. License: GPL-3.0.
Java packages stay `com.limelight.*` so upstream fixes can be diffed in.

Contents:
- `nvstream/**`: connection, HTTP pairing/serverinfo/applist/launch, mDNS, WoL, JNI bridge (`MoonBridge`).
- `binding/{video,audio,crypto}`, `binding/PlatformBinding`: MediaCodec decoder, audio renderer, client certificate.
- `computers/**`, `discovery/**`: PC database and polling service, mDNS discovery service.
- `utils/{CacheHelper,NetHelper,TrafficStatsHelper,DeviceUtils}`, `LimeLog`.
- `src/main/jni/moonlight-core`: moonlight-common-c (+ enet, reedsolomon), prebuilt opus and OpenSSL.

Rift changes against Artemis:
- `binding/video/DecoderConfiguration` replaces `preferences.PreferenceConfiguration` in the decoder
  (same field names and constants, no SharedPreferences, no profiles).
- Stereo3D/AI-3D lines removed from the decoder's perf overlay (`utils.Stereo3DRenderer` pulls LiteRT/OpenCV).
- `ComputerManagerService` no longer uses `utils.ServerHelper` (one helper inlined).
- `R`/`BuildConfig` come from `com.limelight.core`; only the perf overlay strings were copied.
- Not included: `binding/input/**` (controller handler, keyboard, touch contexts, USB drivers,
  on-screen controller), `evdev_reader` (root only), all Artemis activities and UI.

Rift's UI on top of it lives in `app/src/main/java/dev/rift/launcher/stream/`.
