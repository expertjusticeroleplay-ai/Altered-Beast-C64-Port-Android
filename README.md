# Altered Beast — Commodore 64 Android Port

A native Android port of the Commodore 64 (C64) version of *Altered Beast*, with an execution engine and renderer written in Kotlin. The app loads the included C64 materials, displays a VIC-II-style screen, and provides touch controls for landscape gameplay.

## Features

- Native Android app with a Jetpack Compose UI, landscape orientation, and immersive full-screen display.
- Kotlin-based C64 engine: PDS assembler, execution core, VIC-II video renderer, and SID audio synthesizer.
- C64 assets for INTRO and levels 1–5, including sprites, graphics, music, and `.PDS`, `.DIS`, and `.TAP` program data.
- 320 × 200 game screen with nearest-neighbor filtering to preserve sharp pixels.
- Touch controls: eight-way D-pad, FIRE, F1 (1 player), F7 (2 players), pause, restore, cheat, joystick port selection, audio, and HUD toggle.
- Keyboard/gamepad input support, including arrow keys/WASD, Space/Enter, F1/F7, P, and Escape/Delete for restore.
- 50 Hz PAL simulation and render pacing that follows the detected display refresh rate, with an FPS indicator in the HUD.

## Technology

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| Platform | Android, `minSdk 24`, `targetSdk 36` |
| UI | Jetpack Compose and Material 3 |
| Build | Gradle Kotlin DSL, Android Gradle Plugin 9.1.1, Kotlin 2.2.10 |
| Engine | `C64ExecutionCore`, `PdsNativeAssembler`, `VicIIVideoRenderer`, `SidAudioSynthesizer` |
| Testing | JUnit 4, Robolectric, Compose UI tests |

## Project structure

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── assets/
│       │   │   ├── GAME/       # C64 programs, level data, sprites, graphics, and music
│       │   │   └── INTRO/      # Intro materials and related assets
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt
│       │   │   ├── engine/     # C64 execution, assembler, video, and audio
│       │   │   └── ui/         # ViewModel, controls, and theme
│       │   └── AndroidManifest.xml
│       └── test/               # Unit tests and Robolectric UI tests
├── gradle/                     # Version catalog and Gradle wrapper
├── build.gradle.kts
└── settings.gradle.kts
```

## Requirements

- Android Studio supporting Android Gradle Plugin 9.1.1, or an Android command-line environment.
- JDK 17 to run Gradle 9.3.1. The app's source compatibility is set to Java 11.
- Android SDK Platform 36 and compatible Android SDK Build-Tools.
- An internet connection on the first build if dependencies or the Gradle distribution are not available locally.

## Setup and run

1. Clone or extract the repository, then open the project directory in Android Studio.
2. Make sure Android SDK Platform 36 is installed and Gradle is using JDK 17.
3. Sync the Gradle project.
4. Run the `app` configuration on an Android emulator or device running API 24 or later. The app is designed for landscape orientation.

Build a debug version from the terminal:

```bash
./gradlew assembleDebug
```

The debug APK is typically generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator:

```bash
./gradlew installDebug
```

## Testing

Run local unit tests, including Robolectric tests in the `test` source set:

```bash
./gradlew testDebugUnitTest
```

Run instrumentation tests on a device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Engine tests cover assembly of the INTRO module and five levels, visual and audio output, input, and several gameplay transitions. Compose tests check that the canvas and main controls are displayed.

## Release build

The `release` build type uses a signing configuration named `release`. Prepare a keystore and provide credentials through these environment variables:

- `KEYSTORE_PATH` — keystore location. If unset, the build looks for `my-upload-key.jks` in the project root directory.
- `STORE_PASSWORD` — keystore password.
- `KEY_PASSWORD` — password for the key with alias `upload`.

Example:

```bash
export KEYSTORE_PATH="/secure/location/upload-key.jks"
export STORE_PASSWORD="<store-password>"
export KEY_PASSWORD="<key-password>"
./gradlew assembleRelease
```

Store credentials and the keystore securely; do not commit them to the repository.

## Credits and licensing

The included source archive identifies the C64 *Altered Beast* materials with the credit `(c) Michael J Archer` and records a development period in 1989. *Altered Beast* is also a SEGA trademark and copyrighted property; see [SEGA's official Altered Beast page](https://games.sega.com/3dclassics/juouki.html).

The project archive includes no license or evidence of permission granting distribution rights for the game, C64 source code, or assets. Accordingly, this repository grants no license to those materials. Rights to the game and third-party materials remain with their respective owners. Obtain written permission and applicable distribution rights before publishing or distributing a build that includes those materials.

To avoid granting rights that are not established, no open-source license is applied to this entire repository. A license for demonstrably original Android code can only be determined after confirming the ownership and provenance of each part of that code, and must clearly exclude third-party game materials.

## Contributing

Focused contributions to the engine, renderer, audio, UI, or tests are welcome with context for the change and relevant test results. Do not include secrets, keystores, or assets without appropriate distribution rights.
