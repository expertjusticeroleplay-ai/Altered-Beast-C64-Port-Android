# GZBeast

GZBeast is a native Android application for the Commodore 64 version of [Altered Beast](https://en.wikipedia.org/wiki/Altered_Beast) Built with Kotlin, it assembles and runs the bundled 6502/PDS game modules through a purpose-built C64 execution core, with VIC-II-style video rendering and SID-inspired audio synthesis.

The app combines the game with a LuaJ-based mod system for scripts, runtime hooks, asset overrides, and graphics changes. Mods are discovered from shared storage and can interact with gameplay, memory, levels, and the 320 × 200 framebuffer.

## Features

- Landscape Android interface with on-screen directional and action controls, plus hardware-keyboard input.
- C64 execution core with assembled intro and game code, level loading, joystick input, and PAL-frame stepping.
- 320 × 200 rendering with a C64 palette, character and bitmap display modes, and hardware sprites.
- Three-voice SID-style audio synthesis with an in-app sound toggle.
- LuaJ mod loading, callbacks, memory and player APIs, PDS assembly patches, program-counter hooks, asset overrides, palette and level edits, and canvas drawing.
- Shared-storage mod discovery and session logging.

## Modding

On first launch, GZBeast creates its mod directory and Lua API guide in shared storage:

```text
/storage/emulated/0/GZBeast/
└── mods/
    ├── guide.txt
    ├── session.log
    └── my_mod.lua
```

Place `.lua` scripts in `mods/`. Scripts in subdirectories are discovered as well. The engine loads discovered scripts at startup and refreshes them when it detects storage changes. A session log is written to `mods/session.log` when the application exits.

A minimal mod can use the `gz` host API:

```lua
function on_init()
    gz.log("My mod is ready: " .. gz.version())
end

function on_frame(frame, in_game, level)
    if in_game then
        gz.poke(0xCFFF, frame % 256)
    end
end

function on_draw(frame, in_game, level)
    gz.canvas.text(4, 4, "MY MOD", 0xFFFFCC00, 1)
end
```

Global lifecycle hooks include `on_init`, `on_before_frame`, `on_frame`, `on_scanline`, `on_draw`, `on_level_load`, and `on_exit`. Mods can also register program-counter hooks with `gz.on_pc(address_or_symbol, callback)`. The `gz` API includes logging/version helpers, C64 memory and I/O access, CPU and symbolic-variable access, player/enemy/level controls, palette and canvas operations, asset read/write and override functions, and runtime assembly patching. See the generated `mods/guide.txt` for the complete API reference and examples.

### Mod security

Lua mods are executable code, not passive data. GZBeast initializes LuaJ with standard JSE globals and exposes powerful game and asset APIs; mods are [not an operating-system security sandbox](https://en.wikipedia.org/wiki/Sandbox_(computer_security)) Only install scripts you trust. Review a mod's source before running it, and avoid granting storage access when you do not trust its publisher.

## Build

Open the project in Android Studio with Android SDK Platform 36 and a compatible JDK/Android Gradle Plugin toolchain. The Android application module is `app`; the minimum supported Android API level is 24. The project configures Android Gradle Plugin 9.1.1, Kotlin 2.2.10, and Gradle 9.3.1. JDK 17 is required to run Gradle; Java source compatibility is set to 11.

With Gradle 9.3.1 installed, build a debug APK from the project root:

```bash
gradle assembleDebug
```

Run the local unit and Robolectric tests with:

```bash
gradle testDebugUnitTest
```

The supplied archive includes `gradle/wrapper/gradle-wrapper.properties`, but does not include the wrapper launcher scripts or wrapper JAR. Use an installed compatible Gradle distribution, or generate a wrapper locally before using `./gradlew`.

## Release signing

The release build uses a signing configuration with key alias `upload`. Provide a keystore and credentials through environment variables:

```bash
export KEYSTORE_PATH="/secure/location/upload-key.jks"
export STORE_PASSWORD="<store-password>"
export KEY_PASSWORD="<key-password>"
gradle assembleRelease
```

If `KEYSTORE_PATH` is unset, the build looks for `my-upload-key.jks` in the project root. Keep the keystore and passwords secure; do not commit them to the repository.

## Shared storage

GZBeast reads and writes mods under the shared `GZBeast` directory. On Android 11 and later, the app requests [All files access](https://developer.android.com/training/data-storage/manage-all-files?hl=en) to discover and manage mod files. Grant this permission only if you intend to use the shared-storage mod workflow. Android distribution channels may impose additional requirements on apps that request broad file access.

## Project relationship and licensing

GZBeast is an Android C64 game project. The supplied source archive contains Kotlin/Android code and LuaJ, but does not contain GZDoom source code or declare a GZDoom dependency. It should not be described as a GZDoom port or as being licensed under GZDoom's license on that basis. GZDoom is a separate project; its official repository identifies its code as GPL-3.0: [GZDoom on GitHub](https://github.com/ZDoom/gzdoom) · [GZDoom license](https://github.com/ZDoom/gzdoom/blob/master/LICENSE).

The supplied archive does not include a project-wide license or documentation establishing redistribution rights for the included [Altered Beast](https://en.wikipedia.org/wiki/Altered_Beast) source and game assets. The embedded asset documentation credits Michael J. Archer and gives a 1989 development date. No blanket open-source license is claimed here for the application, game code, or bundled assets. Before redistribution or publication, identify the rights holders and obtain the permissions or licenses required for each component; review third-party dependency licenses separately. This README is not legal advice.

## Acknowledgements

- The embedded Altered Beast source documentation credits [Michael J. Archer](https://github.com/milkeybabes/Altered-Beast)
- GZBeast uses [LuaJ](https://github.com/luaj/luaj), a Lua implementation for Java.
- GZBeast is distinct from [GZDoom](https://github.com/ZDoom/gzdoom); the links above refer to that separate project.
