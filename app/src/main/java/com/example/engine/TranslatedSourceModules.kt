package com.example.engine

data class SourceModuleDescriptor(
    val modulePath: String,
    val subsystem: String,
    val baseOrgHex: Int,
    val entrySymbols: List<String>,
    val binaryDependencies: List<String>
)

object TranslatedSourceModules {

    val introModules: List<SourceModuleDescriptor> = listOf(
        SourceModuleDescriptor(
            modulePath = "INTRO/X0.DIS",
            subsystem = "INTRO_HARDWARE_CONSTANTS_AND_ZERO_PAGE",
            baseOrgHex = 0x0002,
            entrySymbols = listOf("VIC", "SID", "NYBBLE", "CIA1", "CIA2", "TRACKINIT", "INTMUSIC"),
            binaryDependencies = listOf("INTRO/MUSIC.DAT")
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X1.DIS",
            subsystem = "INTRO_RASTER_IRQ_AND_MULTIPLEXER",
            baseOrgHex = 0x9000,
            entrySymbols = listOf("NMI", "IRQ", "MYSORT", "IRQ1", "SYNC"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X2.DIS",
            subsystem = "INTRO_JOYGET_AND_TEXT_PLOTTER",
            baseOrgHex = 0x9300,
            entrySymbols = listOf("JOYGET", "FIREBUTTON", "PLOTTEXT", "CLRSCRN", "CLRPAN"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X3.DIS",
            subsystem = "INTRO_SCROLLER_CREDITS_AND_BEAST_EYES",
            baseOrgHex = 0x9500,
            entrySymbols = listOf("SCROLLING", "SCROLL", "PLOTEYES", "ANIMATE", "TEXT1", "TEXT2"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X4.DIS",
            subsystem = "INTRO_MAIN_LOOP_AND_BITMAP_TRANSITIONS",
            baseOrgHex = 0x9839,
            entrySymbols = listOf("START", "MLOOP", "STARTGAME", "SETUP1", "SETUP2", "DOALTERED", "DOSTONE"),
            binaryDependencies = listOf("INTRO/GRAPHICS/ALTERED.PIC", "INTRO/GRAPHICS/STONE.PIC")
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X5.DIS",
            subsystem = "INTRO_BEAST_EYES_CHARSETS",
            baseOrgHex = 0x4800,
            entrySymbols = listOf("LOADSC"),
            binaryDependencies = listOf("INTRO/SPRITES/SPRITES.SPR", "INTRO/GRAPHICS/PANNEL.CHR")
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X6.DIS",
            subsystem = "INTRO_LOADER_AND_CRUNCH_BUFFERS",
            baseOrgHex = 0x9B00,
            entrySymbols = listOf("LOADSTART", "CRUNCH1", "CRUNCH2"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "INTRO/X7.DIS",
            subsystem = "INTRO_TURBO_LOADER_STUB",
            baseOrgHex = 0x9D00,
            entrySymbols = listOf("SEND"),
            binaryDependencies = emptyList()
        )
    )

    val gameCoreModules: List<SourceModuleDescriptor> = listOf(
        SourceModuleDescriptor(
            modulePath = "GAME/X0.DIS",
            subsystem = "GAME_ZERO_PAGE_CONSTANTS_AND_MACROS",
            baseOrgHex = 0x0200,
            entrySymbols = listOf("INITSFX", "INTSFX", "FXCHAN1", "FXCHAN2", "FXCHAN3", "NOB", "NOW"),
            binaryDependencies = listOf("GAME/MUSIC.DAT")
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/X1.DIS",
            subsystem = "GAME_NMI_IRQ_MULTIPLEXER_AND_SCROLL_ENGINE",
            baseOrgHex = 0x222C,
            entrySymbols = listOf("NMI", "TIMERA", "TIMERB", "IRQ", "SCROLL", "PLOTSTRIP", "MYSORT"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/X2.DIS",
            subsystem = "GAME_MAIN_LOOP_PLAYER_SELECTION_AND_INTERMISSION",
            baseOrgHex = 0x2ED7,
            entrySymbols = listOf("START", "RESTART", "GAMELOOP", "MLOOP", "WHATPLAYER", "INTERMISSION", "ENDOFGAME"),
            binaryDependencies = listOf(
                "GAME/END/SCREEN11.CHR", "GAME/END/SCREEN11.DAT",
                "GAME/END/SCREEN12.CHR", "GAME/END/SCREEN12.DAT",
                "GAME/END/SCREEN21.CHR", "GAME/END/SCREEN21.DAT",
                "GAME/END/SCREEN22.CHR", "GAME/END/SCREEN22.DAT"
            )
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/X3.DIS",
            subsystem = "GAME_SPRITE_ENGINE_AND_COLLISION_DETECTION",
            baseOrgHex = 0x3800,
            entrySymbols = listOf("DOSPRITES", "CREATE", "KILL", "DOBANKS", "COLLISIONS", "MOVE"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/X4.DIS",
            subsystem = "GAME_PLAYER_PHYSICS_COMBAT_AND_HUD_PANEL",
            baseOrgHex = 0x4500,
            entrySymbols = listOf("DOPLAYERS", "TRANSFORM", "PUNCH", "KICK", "FIRE", "ADDSCORE", "JOYGET"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/SCOPY2.PDS",
            subsystem = "GAME_SPRITE_UNPACK_AND_HARDWARE_MIRRORING",
            baseOrgHex = 0x5600,
            entrySymbols = listOf("UNPACK", "PUTIN", "PUTIN2", "MIRROR"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/LOADER.PDS",
            subsystem = "GAME_LEVEL_AND_ASSET_LOADER_DISPATCH",
            baseOrgHex = 0x5E27,
            entrySymbols = listOf("LOAD", "LOADINGSC", "LOADEND", "FILENAME"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/TLOADER.PDS",
            subsystem = "GAME_TAPE_PULSE_DECODER",
            baseOrgHex = 0x5F00,
            entrySymbols = listOf("LOADTAPE"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/X5.DIS",
            subsystem = "GAME_SHARED_SPRITES_HUD_AND_LEVEL_DISPATCH",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("NEWPC", "PSCREEN", "PANNEL", "MANSPRITES", "EXPSPRITES"),
            binaryDependencies = listOf(
                "GAME/GRAPHICS/PANNEL.CHR",
                "GAME/SPRITES/MAN.SPR",
                "GAME/SPRITES/EXPLOS.EXP"
            )
        )
    )

    val gameLevelModules: List<SourceModuleDescriptor> = listOf(
        SourceModuleDescriptor(
            modulePath = "GAME/LEVEL1.PDS",
            subsystem = "LEVEL_1_WEREWOLF_GRAVEYARD_ASSETS_AND_AGGAR_BOSS",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("doboss", "DOHEADS", "SPRITES1", "SPRITES2", "BEASTSPRITES", "BLOCKS", "MAP"),
            binaryDependencies = listOf(
                "GAME/LEVEL1/LEVEL11.SPR", "GAME/LEVEL1/LEVEL12.SPR", "GAME/SPRITES/WOLF.SPR",
                "GAME/LEVEL1/LEVEL1.CHR", "GAME/LEVEL1/LEVEL1.COL", "GAME/LEVEL1/LEVEL1.BLK",
                "GAME/LEVEL1/LEVEL1.MAP", "GAME/LEVEL1/LEVEL1.FAC", "GAME/LEVEL1/LEVEL1IN.CHR"
            )
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/LEVEL2.PDS",
            subsystem = "LEVEL_2_THUNDER_DRAGON_CAVE_ASSETS_AND_OCTOEYES_BOSS",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("DOBOSS", "CREATEEYE", "MOVEALL", "SPRITES1", "SPRITES2", "BEASTSPRITES"),
            binaryDependencies = listOf(
                "GAME/LEVEL2/LEVEL21.SPR", "GAME/LEVEL2/LEVEL22.SPR", "GAME/SPRITES/DRAGON.SPR",
                "GAME/LEVEL2/LEVEL2.CHR", "GAME/LEVEL2/LEVEL2.COL", "GAME/LEVEL2/LEVEL2.BLK",
                "GAME/LEVEL2/LEVEL2.MAP", "GAME/LEVEL2/LEVEL2.FAC", "GAME/LEVEL2/LEVEL2IN.CHR"
            )
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/LEVEL3.PDS",
            subsystem = "LEVEL_3_BEAR_CAVERN_ASSETS_AND_MOLE_BOSS",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("DOBOSS", "SPRITES1", "SPRITES2", "BEASTSPRITES", "BLOCKS", "MAP"),
            binaryDependencies = listOf(
                "GAME/LEVEL3/LEVEL31.SPR", "GAME/LEVEL3/LEVEL32.SPR", "GAME/SPRITES/BEAR.SPR",
                "GAME/LEVEL3/LEVEL3.CHR", "GAME/LEVEL3/LEVEL3.COL", "GAME/LEVEL3/LEVEL3.BLK",
                "GAME/LEVEL3/LEVEL3.MAP", "GAME/LEVEL3/LEVEL3.FAC", "GAME/LEVEL3/LEVEL3IN.CHR"
            )
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/LEVEL4.PDS",
            subsystem = "LEVEL_4_TIGER_PALACE_ASSETS_AND_CROCODILE_BOSS",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("DOBOSS", "DODRAGON", "SPRITES1", "SPRITES2", "BEASTSPRITES"),
            binaryDependencies = listOf(
                "GAME/LEVEL4/LEVEL41.SPR", "GAME/LEVEL4/LEVEL42.SPR", "GAME/SPRITES/TIGER.SPR",
                "GAME/LEVEL4/LEVEL4.CHR", "GAME/LEVEL4/LEVEL4.COL", "GAME/LEVEL4/LEVEL4.BLK",
                "GAME/LEVEL4/LEVEL4.MAP", "GAME/LEVEL4/LEVEL4.FAC", "GAME/LEVEL4/LEVEL4IN.CHR",
                "GAME/SPRITES/LEVEL4.PDS"
            )
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/LEVEL5.PDS",
            subsystem = "LEVEL_5_GOLDEN_WOLF_SANCTUM_ASSETS_AND_RHINO_BOSS",
            baseOrgHex = 0x6300,
            entrySymbols = listOf("DOBOSS", "SPRITES1", "SPRITES2", "BEASTSPRITES", "BLOCKS", "MAP"),
            binaryDependencies = listOf(
                "GAME/LEVEL5/LEVEL51.SPR", "GAME/LEVEL5/LEVEL52.SPR", "GAME/SPRITES/WOLF.SPR",
                "GAME/LEVEL5/LEVEL5.CHR", "GAME/LEVEL5/LEVEL5.COL", "GAME/LEVEL5/LEVEL5.BLK",
                "GAME/LEVEL5/LEVEL5.MAP", "GAME/LEVEL1/LEVEL1.FAC", "GAME/LEVEL5/LEVEL5IN.CHR",
                "GAME/SPRITES/LEVEL5.PDS"
            )
        )
    )

    val gameEnemyModules: List<SourceModuleDescriptor> = listOf(
        SourceModuleDescriptor(
            modulePath = "GAME/ENEMY1.PDS",
            subsystem = "ENEMY_AI_WAVE_TABLES_AND_WEREWOLF_BREATH_ATTACKS",
            baseOrgHex = 0x6800,
            entrySymbols = listOf("DOENEMYS", "NEXTWAVE", "ZOMBIE", "WOLF", "PIG", "ANGEL", "SERPENT", "DEVIL"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/ENEMY2.PDS",
            subsystem = "ENEMY_AI_WAVE_TABLES_AND_DRAGON_LIGHTNING_BARRIER",
            baseOrgHex = 0x6800,
            entrySymbols = listOf("DOENEMYS", "NEXTWAVE", "POD", "SNAKE", "CHICKEN", "WOLF"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/ENEMY3.PDS",
            subsystem = "ENEMY_AI_WAVE_TABLES_AND_BEAR_PETRIFYING_BREATH",
            baseOrgHex = 0x6800,
            entrySymbols = listOf("DOENEMYS", "NEXTWAVE", "TURTLE", "FROG", "WOLF", "HAMMER"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/ENEMY4.PDS",
            subsystem = "ENEMY_AI_WAVE_TABLES_AND_TIGER_FLAME_PILLAR",
            baseOrgHex = 0x6800,
            entrySymbols = listOf("DOENEMYS", "NEXTWAVE", "WOLF", "SERPENT", "ANGEL"),
            binaryDependencies = emptyList()
        ),
        SourceModuleDescriptor(
            modulePath = "GAME/ENEMY5.PDS",
            subsystem = "ENEMY_AI_WAVE_TABLES_AND_GAUNTLET_SPAWNER",
            baseOrgHex = 0x6800,
            entrySymbols = listOf("DOENEMYS", "NEXTWAVE", "GOAT", "UNICORN", "WOLF", "SAW"),
            binaryDependencies = emptyList()
        )
    )

    fun allSourceDescriptors(): List<SourceModuleDescriptor> {
        return introModules + gameCoreModules + gameLevelModules + gameEnemyModules
    }
}
