package com.example.modding

object GuideDocumentation {

    val CANONICAL_GUIDE_TEXT: String = """
================================================================================
GZBEAST - COMPLETE OFFICIAL LUA MODDING SPECIFICATION AND DEVELOPER GUIDE
================================================================================

1. OVERVIEW & DIRECTORY ARCHITECTURE
--------------------------------------------------------------------------------
Welcome to the official GZBeast Modding System. GZBeast features an unrestricted
Lua 5.2 scripting and asset-overriding engine connected directly to the native
6502 CPU, VIC-II Video Controller, SID 6581/8580 Sound Synthesizer, Level
Assembler, and Sprite/Map Pipeline.

Root Directory      : /storage/emulated/0/GZBeast
Mods Directory      : /storage/emulated/0/GZBeast/mods
Documentation File  : /storage/emulated/0/GZBeast/mods/guide.txt
Session Log File    : /storage/emulated/0/GZBeast/mods/session.log

Startup Verification Rules:
1. On application launch, GZBeast checks if /storage/emulated/0/GZBeast exists.
   If missing, it creates the directory automatically.
2. GZBeast checks if /storage/emulated/0/GZBeast/mods exists. If missing, it
   creates the directory automatically.
3. GZBeast checks /storage/emulated/0/GZBeast/mods/guide.txt. If the file does
   not exist, or if its exact byte length differs from the official specification
   size, GZBeast automatically writes or replaces guide.txt. If the byte size
   matches, no disk write is performed.
4. GZBeast automatically discovers and loads all .lua script files inside the
   mods directory (including any subdirectories) in alphabetical order. You can
   place as many .lua mods and companion asset files as you want.
5. Runtime logs and mod messages recorded via gz.log(...) are buffered in RAM
   with zero disk I/O overhead during gameplay and flushed to session.log only
   when the application closes or terminates.


2. MOD EXECUTION LIFECYCLE & GLOBAL CALLBACK HOOKS
--------------------------------------------------------------------------------
Every .lua file in the mods folder is evaluated once at startup in its own
script environment sharing the global `gz` engine API. A mod can define any
combination of the following global functions. Only the callbacks you define
are invoked by the engine:

function on_init()
    Called once immediately after the engine and base game programs are
    assembled and initialized. Ideal for registering asset overrides, patching
    6502 assembly, customizing palettes, or setting up PC traps.
end

function on_before_frame(frame_count, in_game, current_level)
    Called at the beginning of each 50Hz PAL frame before CPU scanlines run.
    - frame_count   : Monotonically increasing integer frame counter (1, 2, ...).
    - in_game       : Boolean (true when in active game/title mode, false in intro).
    - current_level : Integer (1..5 for standard levels, or custom level ID).
end

function on_frame(frame_count, in_game, current_level)
    Called once per 50Hz PAL frame after all 312 raster scanlines and CPU
    instructions for that frame have completed. Ideal for modifying player
    physics, enemy behaviors, boss logic, custom abilities, and level state.
end

function on_level_load(level_id, file_id)
    Called whenever the native level/intermission loader executes.
    - file_id 1..5   : Main playable levels 1 through 5 (level_id = 1..5).
    - file_id 6..10  : Intermission artwork screens for levels 1 through 5.
    - file_id 11..18 : End-of-game screens after defeating Level 5.
    - file_id > 18   : Custom mod-defined level or data loads.
    Ideal for injecting custom sprites, custom maps, custom blocks, custom
    charsets, or custom boss scripts for a specific level.
end

function on_scanline(raster_y, in_game, current_level)
    Called on each raster line (0..311) before visible rendering. Use only when
    creating custom raster split effects (such as mid-screen palette, scroll,
    or sprite multiplexer changes). If omitted, zero scanline overhead occurs.
end

function on_draw(frame_count, in_game, current_level)
    Called at the end of every frame after VIC-II rendering completes. Use the
    `gz.canvas.*` functions inside `on_draw` to draw custom HUDs, pixel art,
    rectangles, lines, text, or external .png images onto the 320x200 screen.
end

function on_exit()
    Called when the application is closing before session.log is written to disk.
    Ideal for saving custom mod state files via `gz.asset.write_text(...)`.
end


3. COMPLETE `gz` API REFERENCE
--------------------------------------------------------------------------------

3.1 LOGGING & DIAGNOSTICS
-------------------------
gz.log(message)
    Appends a timestamped message tagged with your mod filename to the in-memory
    session log buffer. Written to /storage/emulated/0/GZBeast/mods/session.log
    when the application exits.

gz.version()
    Returns the GZBeast modding engine version string ("GZBeast-Lua-1.0").


3.2 RAW MEMORY, COLOR RAM, VIC-II & SID HARDWARE ACCESS
-------------------------------------------------------
The Commodore 64 core has 64KB of main RAM (0x0000..0xFFFF), 1024 bytes of
Color RAM (0x0000..0x03FF, mapped at 0xD800..0xDBFF), 64 VIC-II registers
(0x00..0x3F), and 32 SID audio registers (0x00..0x1F).

gz.peek(address) -> int (0..255)
    Reads a single unsigned byte from 64KB RAM at `address` (0x0000..0xFFFF).

gz.poke(address, byte_value)
    Writes a single byte (0..255) directly into 64KB RAM at `address`.

gz.peek16(address) -> int (0..65535)
    Reads a 16-bit little-endian word from `address` and `address + 1`.

gz.poke16(address, word_value)
    Writes a 16-bit little-endian word to `address` (low) and `address + 1` (high).

gz.peek_bytes(address, length) -> table (1-indexed array of bytes)
    Reads `length` bytes starting at `address` and returns a Lua table.

gz.poke_bytes(address, byte_table)
    Writes an array table of byte integers (1-indexed) into RAM starting at
    `address`.

gz.read_io(address) -> int (0..255)
    Reads through the CPU memory-mapped I/O bus (handles 0xD000..0xDFFF VIC/SID/
    ColorRAM/CIA registers when I/O is banked in).

gz.write_io(address, byte_value)
    Writes through the CPU memory-mapped I/O bus.

gz.color_peek(offset) -> int (0..15)
    Reads a 4-bit color nybble from Color RAM at `offset` (0..1023).

gz.color_poke(offset, color_nybble)
    Writes a 4-bit color nybble (0..15) to Color RAM at `offset` (0..1023).

gz.vic_get(register_index) -> int (0..255)
    Reads VIC-II hardware register `register_index` (0x00..0x3F).
    Key VIC-II Registers:
    - 0x00..0x0F : Sprite 0..7 X and Y coordinates (sp*2 = X low, sp*2+1 = Y)
    - 0x10       : Sprites 0..7 X coordinate MSB (bit 8)
    - 0x11       : Screen Control Register 1 (Y-scroll bits 0..2, RSEL bit 3,
                   DEN screen enable bit 4, BMM bitmap mode bit 5)
    - 0x12       : Current Raster Line
    - 0x15       : Sprite Enable Mask (bits 0..7 for Sprites 0..7)
    - 0x16       : Screen Control Register 2 (X-scroll bits 0..2, CSEL bit 3,
                   MCM multicolor mode bit 4)
    - 0x17       : Sprite Y-Expansion Mask
    - 0x18       : Memory Pointers (Screen Base bits 4..7, Char/Bitmap bits 1..3)
    - 0x1B       : Sprite-to-Background Priority Mask
    - 0x1C       : Sprite Multicolor Mode Mask
    - 0x1D       : Sprite X-Expansion Mask
    - 0x20       : Border Color (0..15)
    - 0x21       : Background Color 0 (0..15)
    - 0x22       : Background Multicolor 1 (0..15)
    - 0x23       : Background Multicolor 2 (0..15)
    - 0x25       : Sprite Shared Multicolor 0 (0..15)
    - 0x26       : Sprite Shared Multicolor 1 (0..15)
    - 0x27..0x2E : Individual Sprite 0..7 Colors (0..15)

gz.vic_set(register_index, byte_value)
    Writes `byte_value` (0..255) to VIC-II register `register_index` (0x00..0x3F).

gz.sid_get(register_index) -> int (0..255)
    Reads SID audio register `register_index` (0x00..0x1F).

gz.sid_poke(register_index, byte_value)
    Writes `byte_value` (0..255) to SID register `register_index` (0x00..0x1F).
    Voice 0 uses registers 0..6, Voice 1 uses 7..13, Voice 2 uses 14..20, and
    register 24 (0x18) controls Master Volume (bits 0..3, 0..15).

gz.sid_note(voice, freq_hz, waveform, attack_decay, sustain_release, pulse_width)
    High-level helper to trigger a synthesized note on SID `voice` (0, 1, or 2):
    - freq_hz         : Frequency in Hz (e.g. 440.0 for A4, 0 to gate off).
    - waveform        : 0x11 = Triangle, 0x21 = Sawtooth, 0x41 = Pulse, 0x81 = Noise.
    - attack_decay    : Byte (0x00..0xFF, default 0x19).
    - sustain_release : Byte (0x00..0xFF, default 0xF5).
    - pulse_width     : Integer (64..4032, default 2048).


3.3 6502 CPU REGISTERS, SYMBOLS & PROGRAM COUNTER (PC) HOOKS
------------------------------------------------------------
gz.cpu_get() -> table
    Returns a table containing current 6502 CPU state:
    `{ a, x, y, sp, pc, raster, n, v, d, i, z, c }`

gz.cpu_set(state_table)
    Updates any specified 6502 CPU registers or flags (`a`, `x`, `y`, `sp`, `pc`,
    `n`, `v`, `d`, `i`, `z`, `c`).

gz.sym(symbol_name, optional_level) -> int or nil
    Resolves any symbolic label from the assembled C64 source code (`GAME` or
    `INTRO`) into its exact 16-bit memory address. If `optional_level` (1..5)
    is omitted, resolves from the currently active level program.

gz.sym_peek(symbol_name, optional_offset) -> int (0..255)
    Reads the byte at `gz.sym(symbol_name) + (optional_offset or 0)`.

gz.sym_poke(symbol_name, optional_offset_or_val, optional_val)
    Writes a byte to a symbolic variable.
    - 2-arg form: `gz.sym_poke("SCROLL", 1)` writes 1 to `SCROLL + 0`.
    - 3-arg form: `gz.sym_poke("X", 1, 80)` writes 80 to `X + 1` (Player 2 X).

gz.on_pc(address_or_symbol, callback_function)
    Registers a zero-overhead hardware execution breakpoint at `address_or_symbol`
    (can be a 16-bit integer address like `0x4500` or a symbol string like
    `"DOPLAYERS"`, `"PUNCH"`, `"KICK"`, `"FIREBALL"`, `"DOBOSS"`).
    When the 6502 Program Counter reaches that address, `callback_function(pc)`
    is invoked before the instruction executes. If your callback modifies `pc`
    via `gz.cpu_set({ pc = new_addr })` or returns `true`, the original
    instruction is skipped.

gz.clear_pc(address_or_symbol)
    Removes all PC hooks registered at `address_or_symbol`.


3.4 COMPLETE SYMBOLIC MEMORY MAP OF ALTERED BEAST (C64)
-------------------------------------------------------
All variables below can be accessed via `gz.sym(name)`, `gz.sym_peek(name, idx)`,
and `gz.sym_poke(name, idx, val)`:

Core Entity Arrays (8 Slots: Index 0 = Player 1, Index 1 = Player 2,
Indices 2..7 = Active Enemies / Bosses / Projectiles / Spirit Balls):
- "X"           : Horizontal coordinate (0..255, visible playfield is 6..150).
- "Y"           : Vertical coordinate (0..255, standard ground level is 138).
- "DIR"         : Facing direction (0 = Left, 1 or 255 = Right).
- "ACTION"      : Current entity action state ID (see Action ID table below).
- "FRAME"       : Current animation frame index within the active action.
- "UP"          : Vertical velocity / jump arc counter.
- "ATTACHED"    : Platform attachment state (255 = on ground/air, <255 = platform).
- "BLANKING"    : Invulnerability blink timer (0 = visible, >0 = blinking).

Player-Specific Arrays (Index 0 = Player 1, Index 1 = Player 2):
- "LIVES"       : Remaining lives count (1..9).
- "HITS"        : Remaining health bar segments (9 = full health, 0 = dead).
- "BALLS"       : Spirit balls collected (0 = Human, 1 = Powered Up,
                  2 = Super Muscular, 3 = Transformed into Altered Beast).
- "ALTERED"     : Beast transformation state (0 = Human form, 255 = Altered Beast).
- "FACEFLASH"   : Transformation face flashing countdown timer.

Enemy & Boss Arrays (Indices 2..7):
- "ENEMY"       : Enemy type ID in slots 0..7 (slots 2..7 are enemies/bosses).
                  Special values in ENEMY+2:
                  0 = Empty
                  1 = Active Level Boss (MONSTER_ACT)
                  2 = Pre-Boss Explosion Sequence (EXPLODE)
                  3 = Start/End Level Cutscene Sequence (ENDSEQ)
                  4 = Spirit Ball Power-Up (BALL)
                  5 = Two-Headed Wolf (WOLF)
                  8 = Walking Zombie (ZOMBIE1)
- "EHITS"       : Enemy/Boss hit points remaining.

World, Scroll & Level State Variables:
- "LEVEL"       : Current level number (1..5).
- "GLEVEL"      : Assembled level constant (1..5).
- "SCROLL"      : Horizontal scrolling active flag (0 = stopped, 1 = scrolling).
- "ENDSCROLL"   : Reached end of level map flag (0 = scrolling, 1 = at boss).
- "KILLED"      : Boss defeated flag (0 = alive, 255 = boss defeated).
- "CHEATBIT"    : Cheat flag (`CHEATBIT + 1` = 255 enables native invincibility).
- "SPRITETHERE" : `SPRITETHERE + 1` = 255 enables playfield entity sprites.

Player Action IDs (`ACTION` + 0 for Player 1, `ACTION` + 1 for Player 2):
- 0  : STAND_ACT    (Standing / Walking)
- 1  : CROUCH_ACT   (Crouching)
- 2  : JUMP_ACT     (Jumping upward)
- 3  : DROP_ACT     (Dropping from jump)
- 4  : FALL_ACT     (Falling down pit or hole)
- 5  : PUNCH_ACT    (Punching / Beast Primary Attack)
- 6  : KICK_ACT     (Kicking / Beast Secondary Attack)
- 7  : CPUNCH_ACT   (Crouch Punch)
- 8  : CKICK_ACT    (Crouch Kick)
- 9  : ALTERED_ACT  (Special Beast Attack: Bear Spin / Tiger Pillar)
- 10 : STREAK_ACT   (Werewolf Flame Streak Dash)
- 11 : HIT_ACT      (Taking damage knockback)
- 12 : DIE_ACT      (Dying sequence)
- 16 : NOTINUSE_ACT (Cutscene / Inactive state)

Memory Layout of Level Graphics & Code (when a level is loaded):
- 0x0200..0x5FFF : Core Game Engine (IRQ, Multiplexer, Sprite Engine, Physics)
- 0x6300..0xB3FF : Level Code, Boss AI, Enemy AI, Level Map (`MAP`),
                   Block Definitions (`BLOCKMEM`), Color Tables (`COLTABLE`),
                   and Unpacked Sprite Buffers (`SPRITES1`, `SPRITES2`,
                   `BEASTSPRITES`, `MANSPRITES`, `EXPSPRITES`).
- 0xC000..0xFFFF : VIC-II Hardware Bank 3:
  - 0xCC00..0xCFFF : Screen Matrix 1
  - 0xD000..0xDFFF : Hardware I/O (or Character/Sprite RAM when unbanked)
  - 0xF000..0xF3FF : Screen Matrix 2
  - 0xF800..0xFFFF : Active Level 8x8 Multicolor Character Set (`CHARSET`)


3.5 HIGH-LEVEL PLAYER & BEAST API (`gz.player.*`)
-------------------------------------------------
gz.player.get(player_index) -> table
    `player_index` is 1 (Player 1) or 2 (Player 2). Defaults to 1.
    Returns:
    `{ x, y, dir, action, frame, up, lives, hits, balls, altered, blanking, attached }`

gz.player.set(player_index, properties_table)
    Updates any subset of player fields (`x`, `y`, `dir`, `action`, `frame`,
    `up`, `lives`, `hits`, `balls`, `altered`, `blanking`, `attached`).
    Example:
    `gz.player.set(1, { lives = 9, hits = 9, altered = 255, balls = 3 })`

gz.player.transform(player_index, enable_beast)
    Immediately transforms the player into the current level's Altered Beast
    (`enable_beast = true`) or reverts to human form (`enable_beast = false`).


3.6 HIGH-LEVEL ENEMY & BOSS API (`gz.enemy.*` and `gz.boss.*`)
--------------------------------------------------------------
Enemy slots are indexed 2 through 7 (corresponding to C64 entity slots 2..7).

gz.enemy.get(slot) -> table
    Returns `{ slot, id, x, y, dir, action, frame, up, hits }` for `slot` (2..7).

gz.enemy.set(slot, properties_table)
    Modifies any field (`id`, `x`, `y`, `dir`, `action`, `frame`, `up`, `hits`)
    for enemy `slot` (2..7).

gz.enemy.spawn(slot, enemy_id, x, y, action, hits)
    Spawns an enemy or power-up in `slot` (2..7) at (`x`, `y`).
    Example: `gz.enemy.spawn(3, 4, 100, 100, 0, 1)` spawns a Spirit Ball!

gz.enemy.kill_all()
    Clears all active regular enemies in slots 2..7.

gz.boss.spawn()
    Immediately stops level scrolling and triggers the current level's Boss
    entrance sequence (`ENDSCROLL = 1`).

gz.boss.kill()
    Immediately defeats the active Boss (`KILLED = 255`) and starts the end-of-
    level victory sequence.


3.7 LEVEL, MAP, BLOCK & CHARSET API (`gz.level.*`)
--------------------------------------------------
gz.level.get() -> table
    Returns `{ level, in_game, scroll, end_scroll, boss_killed }`.

gz.level.load(level_number)
    Immediately loads and switches to `level_number` (1..5) and initializes its
    memory banks, sprites, charset, and map.

gz.level.set_char(char_index, eight_bytes_table)
    Writes an 8-byte 8x8 character glyph (0..255) into the active level's
    character set at `CHARSET + char_index * 8` (`0xF800 + char_index * 8`).

gz.level.get_char(char_index) -> table (8 bytes)
    Reads the 8-byte glyph for `char_index` (0..255) from `CHARSET`.

gz.level.set_block(block_index, sixteen_bytes_table, optional_color_byte)
    Modifies a 4x4 character tile block (`16` character indices) in `BLOCKMEM`
    and optionally updates its color attribute in `COLTABLE`.

gz.level.set_map_byte(offset, value)
    Writes a byte at `MAP + offset` to customize level layout strips dynamically.


3.8 SPRITE, PNG CONVERTER, PALETTE & CANVAS OVERLAY API
-------------------------------------------------------
gz.palette.get(color_index) -> int (0xRRGGBB)
    Returns the 24-bit RGB color of C64 palette slot `color_index` (0..15).
    Default C64 Color Indices:
    0=Black, 1=White, 2=Red, 3=Cyan, 4=Purple, 5=Green, 6=Blue, 7=Yellow,
    8=Orange, 9=Brown, 10=LightRed, 11=DarkGrey, 12=Grey, 13=LightGreen,
    14=LightBlue, 15=LightGrey.

gz.palette.set(color_index, rgb_hex)
    Overrides C64 hardware palette color `color_index` (0..15) with any custom
    RGB hex integer (e.g. `0xFF2266` or `0xFF00FF88`).

gz.palette.reset()
    Restores all 16 C64 palette colors to their factory defaults.

gz.sprite.read(address, byte_count) -> table
    Reads `byte_count` (default 64) raw sprite bytes from `address`.

gz.sprite.write(address, sprite_index, bytes_table)
    Writes a 63-byte or 64-byte C64 hardware sprite (24x21 pixels) to
    `address + sprite_index * 64`.

gz.sprite.from_png(mod_png_path, bg_rgb, mc0_rgb, sprite_rgb, mc1_rgb) -> table
    Loads an external `.png` file from `/storage/emulated/0/GZBeast/mods/` and
    slices it into 24x21 C64 multicolor sprites (left-to-right, top-to-bottom).
    Each pixel in every 24x21 cell (stepping 2 horizontal pixels per multicolor
    pair) is matched to the closest RGB color among:
    - `bg_rgb`     -> bit-pair 00 (transparent)
    - `mc0_rgb`    -> bit-pair 01 (shared multicolor 0)
    - `sprite_rgb` -> bit-pair 10 (individual sprite color)
    - `mc1_rgb`    -> bit-pair 11 (shared multicolor 1)
    Returns a 1-indexed table of 64-byte sprite tables. You can write them into
    any sprite bank (`MANSPRITES`, `BEASTSPRITES`, `SPRITES1`, `SPRITES2`, or
    VRAM `0xC000..0xFFFF`) using `gz.sprite.write(...)` or `gz.poke_bytes(...)`!

Direct 320x200 Framebuffer Canvas API (Call inside `on_draw()`):
gz.canvas.pixel(x, y, argb_hex)
    Draws a pixel at (`x`, `y`) on the 320x200 screen (`0xAARRGGBB` or `0xRRGGBB`).

gz.canvas.rect(x, y, width, height, argb_hex, filled)
    Draws a filled (`filled = true`) or outlined (`filled = false`) rectangle.

gz.canvas.line(x1, y1, x2, y2, argb_hex)
    Draws a line between (`x1`, `y1`) and (`x2`, `y2`).

gz.canvas.text(x, y, text_string, argb_hex, scale)
    Renders crisp built-in 8x8 arcade bitmap text at (`x`, `y`) with integer
    `scale` (default 1).

gz.canvas.blit_png(mod_png_path, dst_x, dst_y, src_x, src_y, width, height, scale)
    Draws a `.png` image (or sub-rectangle of a PNG sprite sheet) from the
    `mods/` folder directly onto the 320x200 framebuffer with full alpha
    blending! You can use this to render full-color 32-bit custom player sprites,
    custom bosses, custom backgrounds, or custom UI overlays on top of the game.


3.9 EXTERNAL ASSET OVERRIDING & FILE I/O (`gz.asset.*`)
-------------------------------------------------------
You can replace ANY file used by the game (sprites, character sets, blocks,
color tables, maps, faces, explosions, pictures, music data, or 6502 `.PDS`/
`.DIS` source code) either by calling `gz.asset.override(...)` in Lua OR simply
by placing a replacement file at the matching relative path inside `mods/`
(for example: `/storage/emulated/0/GZBeast/mods/GAME/LEVEL1/LEVEL1.MAP`).

gz.asset.override(internal_asset_path, mod_relative_path)
    Redirects all engine reads of `internal_asset_path` to `mod_relative_path`
    inside `/storage/emulated/0/GZBeast/mods/` and reassembles affected level
    banks automatically.

gz.asset.override_bytes(internal_asset_path, byte_table)
    Redirects all engine reads of `internal_asset_path` to an in-memory byte
    array generated directly by your Lua script!

gz.asset.clear_overrides()
    Removes all registered asset overrides and restores original game assets.

gz.asset.rebuild_level(level_number)
    Re-runs the native 6502 PDS assembler for `level_number` (1..5) so any new
    asset overrides (`.SPR`, `.CHR`, `.COL`, `.BLK`, `.MAP`, `.PDS`, `.DIS`)
    take effect immediately.

gz.asset.exists(mod_relative_path) -> boolean
    Returns `true` if `mod_relative_path` exists inside `mods/`.

gz.asset.read_bytes(mod_relative_path) -> table or nil
    Reads a binary file from `mods/` and returns a 1-indexed table of bytes.

gz.asset.write_bytes(mod_relative_path, byte_table) -> boolean
    Writes a 1-indexed table of bytes to a file inside `mods/`.

gz.asset.read_text(mod_relative_path) -> string or nil
    Reads a UTF-8 text file from `mods/`.

gz.asset.write_text(mod_relative_path, text_content) -> boolean
    Writes a UTF-8 text file inside `mods/`.

gz.asset.list(mod_relative_subdir) -> table of filenames
    Lists all files inside `mods/` (or a subdirectory of `mods/`).

Complete Catalog of Internal Game Asset Paths:
- Intro Assets:
  - "INTRO/MUSIC.DAT"
  - "INTRO/GRAPHICS/ALTERED.PIC"
  - "INTRO/GRAPHICS/STONE.PIC"
  - "INTRO/GRAPHICS/PANNEL.CHR"
  - "INTRO/SPRITES/SPRITES.SPR"
  - "INTRO/X0.DIS" .. "INTRO/X7.DIS"
- Shared Game Assets:
  - "GAME/MUSIC.DAT"
  - "GAME/GRAPHICS/PANNEL.CHR"
  - "GAME/SPRITES/MAN.SPR"
  - "GAME/SPRITES/WOLF.SPR"
  - "GAME/SPRITES/DRAGON.SPR"
  - "GAME/SPRITES/BEAR.SPR"
  - "GAME/SPRITES/TIGER.SPR"
  - "GAME/SPRITES/EXPLOS.EXP"
  - "GAME/X0.DIS" .. "GAME/X5.DIS", "GAME/SCOPY2.PDS", "GAME/LOADER.PDS"
- Level 1..5 Assets (Replace `N` with 1, 2, 3, 4, or 5):
  - "GAME/LEVELN.PDS"            (Level 6502 code, boss AI & asset layout)
  - "GAME/ENEMYN.PDS"            (Level enemy AI & wave spawn tables)
  - "GAME/LEVELN/LEVELN1.SPR"    (Level enemy/boss sprite bank 1)
  - "GAME/LEVELN/LEVELN2.SPR"    (Level enemy/boss sprite bank 2)
  - "GAME/LEVELN/LEVELN.CHR"     (Level 8x8 background character set, 2048 bytes)
  - "GAME/LEVELN/LEVELN.COL"     (Level block color attribute table)
  - "GAME/LEVELN/LEVELN.BLK"     (Level 4x4 tile block definitions)
  - "GAME/LEVELN/LEVELN.MAP"     (Level horizontal scrolling map data)
  - "GAME/LEVELN/LEVELN.FAC"     (HUD Beast transformation face icon)
  - "GAME/LEVELN/LEVELNIN.CHR"   (Intermission screen 15x15 character artwork)


3.10 RUNTIME 6502 ASSEMBLY PATCHING (`gz.asm.*`)
------------------------------------------------
gz.asm.patch(org_address, pds_assembly_string) -> int (bytes_written)
    Assembles 6502 instructions written in PDS syntax starting at `org_address`
    with full access to the current level's symbol table, and writes the machine
    code directly into C64 RAM!
    Example:
    ```lua
    gz.asm.patch(gz.sym("ADDSCORE"), [[
        RTS
    ]])
    ```


3.11 INPUT & CONTROLLER OVERRIDE API (`gz.input.*`)
---------------------------------------------------
gz.input.get() -> table
    Returns `{ up, down, left, right, fire, f1, f7, pause, cheat, port }`.

gz.input.set(input_table)
    Programmatically presses or releases virtual controls (`up`, `down`, `left`,
    `right`, `fire`, `f1`, `f7`, `pause`, `cheat`).


4. COMPLETE PRACTICAL MOD EXAMPLES
--------------------------------------------------------------------------------

EXAMPLE 1: CUSTOM CHARACTER / BEAST SPRITES & FULL-COLOR PNG OVERLAY
Save as `/storage/emulated/0/GZBeast/mods/custom_character.lua`:
```lua
function on_init()
    gz.log("Custom Character Mod initialized")
    if gz.asset.exists("my_hero.spr") then
        gz.asset.override("GAME/SPRITES/MAN.SPR", "my_hero.spr")
        gz.asset.rebuild_level(1)
    end
end

function on_draw(frame, in_game, level)
    if not in_game then return end
    local p1 = gz.player.get(1)
    if p1.lives > 0 and gz.asset.exists("custom_hero_sheet.png") then
        local screen_x = p1.x * 2 - 12
        local screen_y = p1.y - 42
        local anim_col = (p1.frame % 4) * 24
        gz.canvas.blit_png("custom_hero_sheet.png", screen_x, screen_y, anim_col, 0, 24, 42, 1)
    end
end
```

EXAMPLE 2: COMPLETE CUSTOM LEVEL & PROCEDURAL MAP MOD
Save as `/storage/emulated/0/GZBeast/mods/custom_level1.lua`:
```lua
function on_init()
    if gz.asset.exists("custom_lvl1.map") then
        gz.asset.override("GAME/LEVEL1/LEVEL1.MAP", "custom_lvl1.map")
        gz.asset.rebuild_level(1)
    end
end

function on_level_load(level, file_id)
    if file_id == 1 then
        gz.palette.set(0, 0x0A0818)
        gz.palette.set(12, 0x483B8A)
        gz.palette.set(15, 0x8F7CE6)
        gz.log("Applied custom night-citadel palette to Level 1")
    else
        gz.palette.reset()
    end
end
```

EXAMPLE 3: SUPER BEAST MECHANICS, FLIGHT ON ANY LEVEL & CUSTOM HUD
Save as `/storage/emulated/0/GZBeast/mods/omega_beast.lua`:
```lua
function on_frame(frame, in_game, level)
    if not in_game then return end
    local st = gz.level.get()
    if st.scroll == 0 and st.end_scroll == 0 then return end

    local p1 = gz.player.get(1)
    if p1.lives > 0 then
        if p1.hits < 9 then
            gz.player.set(1, { hits = 9 })
        end
        local inp = gz.input.get()
        if inp.up and inp.fire and p1.y > 28 then
            gz.player.set(1, { y = p1.y - 2 })
        end
    end
end

function on_draw(frame, in_game, level)
    if not in_game then return end
    local p1 = gz.player.get(1)
    gz.canvas.rect(4, 4, 108, 12, 0xAA000000, true)
    gz.canvas.text(8, 6, "LV:" .. tostring(level) .. " HP:" .. tostring(p1.hits), 0xFFFFD700, 1)
end
```
================================================================================
END OF GZBEAST OFFICIAL LUA MODDING GUIDE
================================================================================
""".trimIndent() + "\n"

    val CANONICAL_GUIDE_BYTES: ByteArray = CANONICAL_GUIDE_TEXT.toByteArray(Charsets.UTF_8)

    val EXPECTED_BYTE_SIZE: Long = CANONICAL_GUIDE_BYTES.size.toLong()
}
