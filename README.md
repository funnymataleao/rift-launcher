# Rift Launcher

An open-source game launcher for Android tablets, handhelds and phones, built for gamepads.
One library for everything you play: games streamed from your PC (embedded Artemis),
Windows games run on the device (GameNative engine), Android games, emulators and cloud gaming.

Status: stage 0 skeleton. The app builds and lists the Android games installed on the device.

## Build

Requirements: JDK 17 or newer, Android SDK with platform 37.

```sh
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/
./gradlew test                 # unit tests
```

CI (`.github/workflows/build.yml`) runs the tests and uploads the debug APK as an artifact on every push and pull request.

## Modules

| Module | What it holds |
|---|---|
| `:app` | Activity, navigation, screens. Wires every provider in through Hilt. |
| `:core-model` | Pure Kotlin domain model: `Game`, `LaunchTarget`, `LaunchSource`, `LibraryMerger`. |
| `:core-ui` | Theme (Material 3 Expressive, dark Big Picture palette), shapes, motion, shared components. |
| `:library` | The unified library: Room cache, refresh from providers, launch, user state. |
| `:providers:api` | `GameProvider`, the contract every source implements. |
| `:providers:android` | Android games installed on the device. |
| `:providers:pcstream` | PC streaming through the embedded Artemis core (stub). |
| `:providers:gamenative` | Windows games through the GameNative engine (stub). |
| `:metadata` | Artwork sources (SteamGridDB, libretro). Interface only for now. |
| `:assistant` | Optional bring-your-own-key AI assistant. Interface only for now. |

## How the library works

A `Game` has one or more `LaunchTarget`s, one per way to start it: on the PC, on this device,
in an emulator, in the cloud. The Play button starts `Game.defaultTarget` (the source the user
picked last time, else the highest-priority available one) and its menu lists all targets.

Each provider returns `ProvidedGame`s. `LibraryMerger` joins entries that share an identity
such as `steam:1091500`, or failing that the same normalized title, so a Steam game that is also
set up on the PC host shows once with two targets.

To add a source, create a `:providers:<name>` module, implement `GameProvider` and bind it
with `@Binds @IntoSet` in a Hilt module. Nothing else in the app has to change.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). Rift embeds code from GPL-3.0 projects (Artemis, GameNative).
