# GZBeast — Unrestricted Lua Modding Engine & Full Game Customization Architecture

Rebrands the application to **GZBeast** and introduces a high-performance, unrestricted `.lua` modding engine that automatically scans `/storage/emulated/0/GZBeast/mods/` on startup, maintains a size-verified comprehensive English modding specification (`guide.txt`), allows unlimited scripts and external binary/image/source assets to modify any aspect of the game, and writes a complete `session.log` only when the application exits to preserve runtime device resources.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural choices incorporate your confirmed preferences from the clarification phase:

- **Confirmed Decision 1 — Zero-UI Automatic Startup & Deferred Exit Logging**: Mods in `/storage/emulated/0/GZBeast/mods/` are discovered and loaded automatically at startup without cluttering the screen with extra menus. Full engine and mod execution events are buffered in a lightweight ring/memory buffer and flushed to `/storage/emulated/0/GZBeast/mods/session.log` **only when the application closes or stops** (`onStop` / `onDestroy` / `onCleared` / `Runtime.addShutdownHook` / `UncaughtExceptionHandler`), ensuring zero disk I/O overhead during gameplay.
- **Confirmed Decision 2 — Unrestricted Code, Memory, Asset & Level Overriding**: Lua mods have full access to read/write 64KB RAM, Color RAM, VIC-II registers, SID audio synthesis voices, CPU registers/PC hooks, symbolic C64 game variables (`X`, `Y`, `ACTION`, `ENEMY`, `ALTERED`, `BALLS`, `HITS`, `LIVES`, `LEVEL`, `SCROLL`, `ENDSCROLL`, etc.), custom `.PDS`/`.DIS` 6502 assembly injection, custom level binary replacements (`.SPR`, `.CHR`, `.COL`, `.BLK`, `.MAP`, `.FAC`, `.EXP`, `.PIC`, `.DAT`, `.BIN`), and `.PNG` sprite/bitmap conversion.
- **Confirmed Decision 3 — Full English `guide.txt` with Exact Byte-Size Verification**: On startup, the engine checks `/storage/emulated/0/GZBeast/mods/guide.txt`. If the file is missing or its byte size (`File.length()`) differs from the canonical embedded specification byte length, it is atomically replaced so modders always have the complete, up-to-date English documentation.
- **Confirmed Decision 4 — Android 7.0 (`minSdk 24`) to Latest Android (`targetSdk 36`) Storage Permissions**: Declares `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (with `requestLegacyExternalStorage="true"` for Android 10), and `MANAGE_EXTERNAL_STORAGE` in the manifest, paired with a seamless startup permission check flow so `/storage/emulated/0/GZBeast/mods/` can be created, verified, and read across all Android versions from 7.0 onward.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms **GZBeast** into an open, scriptable retro arcade engine where players can drop any number of `.lua` scripts and companion asset files into `/storage/emulated/0/GZBeast/mods/` to customize characters, sprites, animations, physics, enemies, boss behaviors, SID soundtracks, palettes, HUD overlays, or entire levels from scratch.
- **Target Audience / Persona**: Retro arcade players and modders who want complete creative freedom to build custom characters, total-conversion levels, gameplay tweaks, and visual/audio overhauls using standard `.lua` scripts and a self-contained offline reference guide.
- **Key Value**: True unrestricted modding without recompiling the APK, zero frame-time waste during 50Hz PAL execution, and a self-healing `guide.txt` + `session.log` workflow in shared storage.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **App Launch & Storage Verification**:
     - When **GZBeast** launches, it verifies storage access for `/storage/emulated/0/GZBeast/mods/` (requesting runtime storage permission on Android 7.0–10 or All Files Access on Android 11+ if not yet granted).
     - Automatically ensures `/storage/emulated/0/GZBeast` and `/storage/emulated/0/GZBeast/mods` exist.
     - Checks `/storage/emulated/0/GZBeast/mods/guide.txt`. If missing or if its file size in bytes does not match the expected canonical guide size, writes the complete English documentation immediately.
  2. **Automatic Mod Discovery & Execution**:
     - Scans `/storage/emulated/0/GZBeast/mods/` (and any mod subfolders) in deterministic alphabetical order for `.lua` files.
     - Compiles each `.lua` script once into fast bytecode/closures at startup and registers only the lifecycle hooks (`on_init`, `on_frame`, `on_level_load`, `on_scanline`, `on_pc_address`, `on_draw`, `on_input`, `on_exit`) that the script actually defines.
  3. **Gameplay & Custom Assets**:
     - As the player plays through the intro, title screen, or Levels 1–5 (or custom levels beyond 5), registered Lua hooks and asset overrides seamlessly intercept asset loads, sprite rendering, level maps, SID audio, and 6502 memory states at full speed.
  4. **Application Exit & `session.log` Flush**:
     - When the user exits the app (via back navigation, swipe-away from recents, process termination, or an unhandled script/engine error), the in-memory session log buffer is flushed in a single sequential write to `/storage/emulated/0/GZBeast/mods/session.log`.
- **Visual Identity & Theme**:
  - **App Branding**: Renamed to **GZBeast** across the Android launcher label, project identity, and platform metadata while preserving the crisp full-screen Nearest-Neighbor (`FilterQuality.None`) C64 arcade presentation and non-intrusive floating SVG controls.
  - **Custom Canvas Overlay API**: Mods can optionally draw custom pixel primitives, rectangles, text, or external `.png` bitmaps directly onto the 320x200 VIC-II frame buffer during `on_draw`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pre-Compiled Hook Dispatch Table Instead of Per-Frame Script Re-Parsing**
  - *Chosen Approach*: Parse and initialize all `.lua` files once when the engine starts, caching direct function references for active callbacks (`on_frame`, `on_level_load`, `on_pc`, etc.) in flat arrays. If no mod registers a per-scanline or per-PC hook, that hook path executes zero instructions in the inner loop.
  - *Why*: Guarantees 60Hz–240Hz display pacing and rock-solid 50Hz PAL emulation without garbage collection spikes or CPU throttling, even when many mods are installed.
  - *Alternatives Considered*: Re-evaluating Lua strings or polling the filesystem every frame, which would waste battery and cause frame drops.
- **Decision 2: Asset Provider Interceptor & Multi-Format Loader**
  - *Chosen Approach*: Wrap the engine's binary asset loader with a mod-aware asset router. Any internal game asset (`GAME/LEVEL1/LEVEL1.SPR`, `GAME/LEVEL1/LEVEL1.MAP`, `.CHR`, `.COL`, `.BLK`, `.FAC`, `.EXP`, `.PIC`, `.PDS`, `.DIS`, etc.) can be transparently redirected to a file in `/storage/emulated/0/GZBeast/mods/` or dynamically synthesized by a Lua function, plus `.png` images can be automatically quantized into C64 multicolor sprites (`24x21` / 64-byte sprite blocks) or charsets/bitmaps.
  - *Why*: Lets modders replace sprites, tilesets, level maps, or even 6502 assembly modules either by dropping replacement files in `mods/` or by generating them procedurally in Lua.
- **Decision 3: In-Memory Ring Buffer with Single-Write Shutdown Flush for `session.log`**
  - *Chosen Approach*: Record all engine startup events, storage checks, `guide.txt` size verification results, loaded `.lua` scripts, custom `gz.log(...)` messages, and any Lua runtime stack traces in a lock-free/synchronized in-memory buffer, flushing to `/storage/emulated/0/GZBeast/mods/session.log` only on app stop/termination/crash.
  - *Why*: Eliminates flash storage wear and frame stutter during gameplay while ensuring complete diagnostic visibility whenever the app closes.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Android OS (API 24 – API 36)                          │
│         /storage/emulated/0/GZBeast/mods/ (guide.txt, *.lua, assets,         │
│                                            session.log on app exit)          │
└───────────────────┬──────────────────────────────────────┬───────────────────┘
                    │ Startup Check & Asset/Script Read    │ Exit-Only Flush
                    ▼                                      ▲
┌───────────────────────────────────────────┐  ┌───────────────────────────────┐
│        ModStorageInitializer              │  │       SessionLogBuffer        │
│ • Ensures /GZBeast & /GZBeast/mods exist  │  │ • Zero-disk-I/O RAM collector │
│ • Verifies guide.txt byte length == spec  │  │ • Flushes to session.log on   │
│ • Discovers all *.lua mods alphabetically │  │   onStop/onCleared/Shutdown   │
└───────────────────┬───────────────────────┘  └───────────────▲───────────────┘
                    │                                          │
                    ▼                                          │
┌──────────────────────────────────────────────────────────────┴───────────────┐
│                        LuaModEngine & Host API (`gz.*`)                      │
│ • Memory API: peek/poke RAM, ColorRAM, VIC-II, SID, CPU registers & symbols  │
│ • Gameplay API: player/beast state, enemies, bosses, level loader, controls  │
│ • Graphics API: C64 palette override, sprite bank injector, PNG-to-sprite,   │
│                 custom 320x200 framebuffer pixel/rect/text/image drawing     │
│ • Level/Code API: asset file redirection, custom .MAP/.BLK/.CHR/.SPR/.PDS    │
│                   assembly patching, and custom PC breakpoint callbacks      │
└───────────────────┬──────────────────────────────────────────┬───────────────┘
                    │ Direct Hook Dispatch                     │ Asset Override
                    ▼                                          ▼
┌───────────────────────────────────────────┐  ┌───────────────────────────────┐
│           C64ExecutionCore                │  │    ModAwareAssetProvider      │
│ • 6502 CPU + VIC-II + SID 50Hz PAL Core   │  │ • Routes internal asset reads │
│ • Invokes registered Lua hooks at init,   │  │   to mods/ overrides or Lua   │
│   level load, frame, scanline, & PC traps │  │   dynamic byte generators     │
└───────────────────────────────────────────┘  └───────────────────────────────┘
```

### Data Model & Lua API Surface (`gz` Global Namespace)

Every `.lua` file placed in `/storage/emulated/0/GZBeast/mods/` has access to the standard Lua library (`math`, `table`, `string`, `bit32`) plus the comprehensive `gz` modding API documented in `guide.txt`:

1. **Lifecycle Callbacks**:
   - `on_init()` — Called once when the mod is loaded after engine initialization.
   - `on_frame(frame_count, in_game, level)` — Called once per 50Hz PAL frame after CPU/VIC execution.
   - `on_before_frame(frame_count, in_game, level)` — Called before each PAL frame begins.
   - `on_level_load(level_id, file_id)` — Called whenever a level (`1..5`), intermission (`6..10`), or ending (`11..18`) loads, allowing custom level setup or complete level replacement.
   - `on_draw()` — Called after the 320x200 VIC-II frame is rendered so mods can draw custom HUDs, custom PNG sprites, or post-processing effects directly onto the framebuffer.
   - `gz.on_pc(address_or_symbol, callback)` — Registers a zero-overhead trap when the 6502 Program Counter hits a specific address or symbol (e.g., `"FIREBALL"`, `"MONSTER"`, `"DETECT"`).
2. **Memory, Symbol & Register Manipulation**:
   - `gz.peek(addr)`, `gz.poke(addr, val)`, `gz.peek_range(addr, len)`, `gz.poke_bytes(addr, table_or_bytes)`
   - `gz.color_peek(offset)`, `gz.color_poke(offset, col)`
   - `gz.vic_get(reg)`, `gz.vic_set(reg, val)`
   - `gz.sid_poke(reg, val)`, `gz.sid_play_note(voice, freq_hz, waveform, attack_decay, sustain_release)`
   - `gz.sym(name, level)` — Resolves any original C64 symbol (`"X"`, `"Y"`, `"ACTION"`, `"ENEMY"`, `"ALTERED"`, `"BALLS"`, `"HITS"`, `"LIVES"`, `"SCROLL"`, `"ENDSCROLL"`, `"KILLED"`, `"MAP"`, `"BLOCKMEM"`, `"COLTABLE"`, etc.) to its exact memory address.
   - `gz.sym_peek(name, offset)`, `gz.sym_poke(name, offset, val)`
   - `gz.cpu_get()`, `gz.cpu_set({a=..., x=..., y=..., pc=..., c=..., z=..., n=..., v=...})`
3. **High-Level Player, Beast, Enemy & Level Helpers**:
   - `gz.player.get(p)` / `gz.player.set(p, {x=..., y=..., action=..., dir=..., lives=..., hits=..., balls=..., altered=...})`
   - `gz.enemy.get(slot)` / `gz.enemy.set(slot, {id=..., x=..., y=..., action=..., hits=..., dir=...})`
   - `gz.level.get_current()`, `gz.level.load(level_num)`, `gz.level.set_boss_killed(true)`
   - `gz.input.get()` / `gz.input.set({up=..., down=..., left=..., right=..., fire=..., cheat=...})`
4. **Sprites, Palette, PNG & Full Level Replacement**:
   - `gz.asset.override(internal_path, mod_relative_path_or_bytes)` — Replaces any game file (`GAME/LEVEL1/LEVEL1.SPR`, `LEVEL1.MAP`, `LEVEL1.CHR`, `LEVEL1.BLK`, `LEVEL1.COL`, `LEVEL1.PDS`, etc.) with a custom file in `mods/`.
   - `gz.asset.read_bytes(mod_relative_path)` / `gz.asset.write_bytes(mod_relative_path, bytes)`
   - `gz.sprite.write_c64(bank_addr, sprite_index, bytes_64)` — Writes a 64-byte C64 sprite directly into RAM.
   - `gz.sprite.from_png(mod_png_path, c0_rgb, mc1_rgb, sprite_rgb, mc2_rgb)` — Converts an external `.png` image in `mods/` into C64 multicolor 24x21 sprite blocks (`64` bytes per sprite) and returns them for direct injection into player, beast, enemy, or boss sprite banks.
   - `gz.palette.set(color_index, rgb_hex)` / `gz.palette.reset()` — Customizes any of the 16 VIC-II hardware palette colors.
   - `gz.canvas.pixel(x, y, argb)`, `gz.canvas.rect(x, y, w, h, argb)`, `gz.canvas.text(x, y, str, argb)`, `gz.canvas.blit_png(mod_png_path, dst_x, dst_y, src_x, src_y, w, h)` — Draws custom graphics or PNG sprite sheets directly onto the 320x200 screen buffer.
   - `gz.asm.patch(org_addr, pds_6502_source)` — Assembles custom 6502 PDS assembly instructions at runtime and patches them directly into C64 RAM.
5. **Logging**:
   - `gz.log(message)` — Appends a timestamped entry to the in-memory session buffer, written to `/storage/emulated/0/GZBeast/mods/session.log` upon application exit.

### Interactive Component & Lifecycle Mapping

- **Startup Permission & Directory Flow**: `MainActivity` checks storage access on launch (`Environment.isExternalStorageManager()` on Android 11+ / `READ_EXTERNAL_STORAGE` & `WRITE_EXTERNAL_STORAGE` on Android 7.0–10), requests access if needed, and immediately triggers `ModStorageInitializer.ensureInitialized()`, which creates `/storage/emulated/0/GZBeast`, `/storage/emulated/0/GZBeast/mods`, validates/replaces `guide.txt` by exact byte size, and loads all `.lua` mods.
- **Shutdown Log Guarantee**: `MainActivity.onStop()`, `MainActivity.onDestroy()`, `AlteredBeastViewModel.onCleared()`, a JVM shutdown hook, and the thread default `UncaughtExceptionHandler` all invoke `SessionLogManager.flushToDisk()`, ensuring `session.log` is reliably written whenever the app closes by any method without writing to disk during active gameplay.
