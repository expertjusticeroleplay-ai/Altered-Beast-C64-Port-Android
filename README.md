# Altered Beast — Commodore 64 Android Port

A native Android port of the Commodore 64 (C64) version of *Altered Beast*, powered by an execution engine and renderer written in Kotlin. The app runs the game materials from the included C64 assets, displays a VIC-II-style screen, and provides touch controls for playing in landscape orientation.

> **Project status:** The repository contains the app implementation and a set of tests for the engine and gameplay flow. This README describes what is evident from the included code and configuration; complete behavior on real devices still needs to be verified through builds and hands-on testing.

## Features

- Native Android app with a Jetpack Compose UI, landscape orientation, and immersive full-screen display.
- Kotlin-based C64 engine, including a PDS assembler, execution core, VIC-II video renderer, and SID audio synthesizer.
- C64 game assets stored in `app/src/main/assets/`, including INTRO files, levels 1–5 data, sprites, graphics, music, and program data in `.PDS`, `.DIS`, and `.TAP` formats.
- A 320 × 200 game screen scaled to the device canvas with nearest-neighbor filtering to preserve sharp pixels.
- Touch controls: eight-way D-pad, FIRE, F1 (1 player), F7 (2 players), pause, restore, cheat, joystick port selection, audio, and HUD toggle.
- App-handled keyboard/gamepad input, including arrow keys/WASD, Space/Enter, F1/F7, P, and Escape/Delete for restore.
- 50 Hz PAL simulation loop, with render pacing that follows the detected display refresh rate and an FPS indicator in the HUD.
- Unit tests for module assembly, basic rendering and audio, gameplay flow and levels, plus Robolectric UI tests for the main controls.

## Technology

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| Platform | Android, `minSdk 24`, `targetSdk 36` |
| UI | Jetpack Compose and Material 3 |
| Build | Gradle Kotlin DSL, Android Gradle Plugin 9.1.1, Kotlin 2.2.10 |
| Engine | Kotlin implementations: `C64ExecutionCore`, `PdsNativeAssembler`, `VicIIVideoRenderer`, `SidAudioSynthesizer` |
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
- Android SDK Platform 36 (including the project-configured API minor level) and compatible Android SDK Build-Tools.
- An internet connection on the first build if dependencies or the Gradle distribution are not already available locally.

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

Install it on a connected device/emulator:

```bash
./gradlew installDebug
```

## Running tests

Local unit tests, including Robolectric tests in the `test` source set, can be run with:

```bash
./gradlew testDebugUnitTest
```

Instrumentation tests on a device or emulator can be run with:

```bash
./gradlew connectedDebugAndroidTest
```

Engine tests check assembly of the INTRO module and five levels, visual and audio output, input, and several gameplay transitions. Compose tests check that the canvas and main controls are displayed. SDK, emulator, and host configuration availability may affect test results.

## Creating a release build

The project's `release` build type uses a signing configuration named `release`. Before building a release APK/AAB, prepare your own keystore and provide the credentials through these environment variables:

- `KEYSTORE_PATH` — keystore location; if unset, the build looks for `my-upload-key.jks` in the project root directory.
- `STORE_PASSWORD` — keystore password.
- `KEY_PASSWORD` — password for the key with alias `upload`.

Example (use a secret manager or secure local environment; do not commit credentials):

```bash
export KEYSTORE_PATH="/secure/location/upload-key.jks"
export STORE_PASSWORD="<store-password>"
export KEY_PASSWORD="<key-password>"
./gradlew assembleRelease
```

The keystore and signing secrets are not included in the archive. A release build requires valid signing configuration.

## Asset and attribution notes

The archive includes assets and source materials identified as the C64 version of *Altered Beast* by Michael J. Archer; the README included with the assets states copyright `(c) Michael J Archer` and a development period in 1989. The project archive does not include a license file defining distribution or usage rights for these assets. Confirm applicable permissions and copyright terms before distributing the app, ROM/data, or game assets.

Some C64 source files have an older PDS data header, as explained in the included source notes; do not assume these files are corrupted simply because they are not ordinary assembly text.

## Limitations and development notes

- The current `applicationId` is `com.alteredbeast.port`; the Android source namespace is `com.example`. Update both consistently before publication if needed.
- `targetSdk` is set to 36. Recheck platform policies, screen behavior, and distribution requirements before release.
- Cheat, audio, port selection, and other controls are operated through the available UI; their final behavior depends on the engine implementation and should be tested on a device.
- `.env.example` contains a Gemini placeholder example, but no Gemini API usage is evident in the core game flow. This README does not require an API key to run the game emulator.
- The archive includes proprietary/copyrighted assets and an additional archive under `public/assets/aistudio/`; review the provenance and permissions for these assets before reuse or publication.

## Contributing

Contributions can be submitted as focused changes to the engine, renderer, audio, UI, or tests. Include context for the change and relevant test results. Do not include secrets, keystores, or assets without distribution rights.
