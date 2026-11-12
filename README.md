# GZBeast

GZBeast is an Android player for the Commodore 64 version of *Altered Beast*. It brings the original C64 game data into a Kotlin-based runtime with touch controls, C64-style video and audio components, and Lua scripting for gameplay and asset modifications.

## What’s inside

- **Android app** built with Kotlin and Jetpack Compose, designed for landscape play.

- **C64 runtime components**: execution core, PDS-oriented assembler/loader, VIC-II video renderer, and SID audio synthesizer.

- **Bundled C64 game data**: intro and gameplay files, including level data, sprites, graphics, and music.

- **Lua mod support**: scripts can hook into engine and gameplay events and use APIs for input, memory, game state, graphics, and assets.

- **On-screen controls** alongside keyboard and controller input handling.

## Requirements

- Android Studio and Android SDK Platform 36.1 (compile SDK 36, minor API level 1).

- JDK 11-compatible toolchain.

- Android 7.0 / API 24 or later.

- Gradle 9.3.1 for command-line builds.

The supplied archive does not contain `gradlew` or the Gradle wrapper JAR. Open the project in Android Studio to sync and build, or install Gradle 9.3.1 locally.

## Build

Open the directory containing `settings.gradle.kts` in Android Studio, install any requested SDK components, sync the project, and run the `app` configuration on an Android device or emulator. The app is configured for landscape orientation.

With Gradle 9.3.1 installed, build and run the unit tests from the project root:

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release signing reads the keystore path and passwords from environment variables. The signing alias is `upload`; the default keystore path is `my-upload-key.jks` in the project root.

```bash
export KEYSTORE_PATH="/secure/path/upload-key.jks"
export STORE_PASSWORD="<keystore-password>"
export KEY_PASSWORD="<key-password>"
gradle assembleRelease
```

Keep signing credentials out of source control. The app requests external-storage access for its Lua mod workflow; verify permissions and storage behavior on the Android versions you intend to support.

## Playing and modding

GZBeast creates its working directory on external storage:

```
/storage/emulated/0/GZBeast/
└── mods/
    ├── guide.txt
    ├── session.log
    └── *.lua
```

Add Lua scripts to `mods/` or a subdirectory. The app finds scripts recursively and loads them in alphabetical order. On first launch, it creates the directories and writes `guide.txt`, which contains the scripting API reference and examples. Runtime messages are recorded in `session.log`.

The Lua API exposes hooks for initialization, frames, drawing, scanlines, level loading, CPU program-counter events, and application exit. It also provides functions for game state, memory and hardware access, input, graphics, asset files, and assembly patches. Because scripts can change game memory and assets, only run mods from sources you trust.

The interface includes directional controls, FIRE, one- and two-player keys, pause, restore, cheat, port selection, and SID/audio controls. Keyboard and controller shortcuts are also handled by the activity; available actions depend on the connected input device and current game state.

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt             # Android entry point and input/UI wiring
├── engine/                     # C64 core, PDS assembler, VIC-II renderer, SID audio
├── modding/                    # Lua engine, host APIs, guide, and mod storage
└── ui/                          # ViewModel, controls, and Compose theme

app/src/main/assets/
├── GAME/                        # C64 gameplay files and data
└── INTRO/                       # C64 intro files and data
```

## GZDoom

GZBeast is **not a GZDoom port**. The project implements a runtime for the Commodore 64 version of *Altered Beast*; its supplied Android build files do not declare GZDoom as a dependency. GZDoom is a separate Doom-engine source port, distributed under GPL-3.0. That license applies to GZDoom's own code and does not license GZBeast or the *Altered Beast* game data.

## Game data and licensing

The repository bundles data from the C64 *Altered Beast* source release. The [upstream Altered Beast repository](https://github.com/milkeybabes/Altered-Beast) describes the material as C64 source code and credits Michael J. Archer, but it does not provide an explicit open-source license in its repository overview. The game, its characters, and associated audiovisual material are SEGA intellectual property or material used under the relevant rights.

SEGA's [Terms of Use / End User License Agreement](https://games.sega.com/eula/) reserve rights in the game and related intellectual property. The terms describe a limited personal, non-commercial license and restrict copying, distribution, and commercial exploitation except where permission or applicable law allows. Accordingly, the presence of these files in a public source archive should not be taken as permission to redistribute the game or its assets. Anyone distributing a build or repository containing them needs to establish the applicable rights or obtain permission from the relevant rights holders.

No license file in this project grants rights to the bundled *Altered Beast* material. The GZDoom GPL-3.0 license does not extend to it. Keep the code, game data, and third-party dependencies' licensing separate when reviewing or distributing this project.

## Credits

The C64 source repository credits Michael J. Archer. *Altered Beast* and related marks and game content belong to their respective rights holders. GZBeast is an independent project and is not affiliated with SEGA or the GZDoom project.
