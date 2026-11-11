package com.example

import com.example.engine.BinaryAssetProvider
import com.example.engine.C64ExecutionCore
import com.example.engine.PdsNativeAssembler
import com.example.engine.SidAudioSynthesizer
import com.example.engine.TranslatedSourceModules
import com.example.engine.VicIIVideoRenderer
import com.example.modding.GuideDocumentation
import com.example.modding.GuideVerificationResult
import com.example.modding.LuaModEngine
import com.example.modding.ModAssetRouter
import com.example.modding.ModStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    private val fileSystemAssetProvider = object : BinaryAssetProvider {
        override fun readBytes(assetPath: String): ByteArray {
            val normalized = assetPath.trimStart('/')
            val candidates = listOf(
                File("src/main/assets/$normalized"),
                File("app/src/main/assets/$normalized")
            )
            val found = candidates.firstOrNull { it.exists() }
                ?: throw IllegalArgumentException("Missing asset: $normalized")
            return found.readBytes()
        }
    }

    @Test
    fun allOriginalC64SourceFilesAndLevelsAssembleAndExecuteCleanly() {
        val assembler = PdsNativeAssembler(fileSystemAssetProvider)
        val allModules = TranslatedSourceModules.allSourceDescriptors()
        assertEquals(27, allModules.size)

        val introProg = assembler.assembleIntro()
        assertNotNull(introProg.symbols["START"])
        assertNotNull(introProg.symbols["IRQ"])
        assertEquals(0x9000, introProg.symbols["NMI"])
        assertTrue((introProg.symbols["SYNC"] ?: 0) in 1..0xFF)

        for (level in 1..5) {
            val gameProg = assembler.assembleGameLevel(level)
            assertEquals(level, gameProg.symbols["GLEVEL"])
            assertTrue((gameProg.symbols["SORTTABLE"] ?: 0) in 1..0xFF)
            assertTrue((gameProg.symbols["SYNC"] ?: 0) in 1..0xFF)
            assertTrue((gameProg.symbols["SCREENENABLE"] ?: 0) in 1..0xFF)
            assertTrue((gameProg.symbols["START"] ?: 0) > 0x1000)
            assertTrue((gameProg.symbols["IRQ"] ?: 0) > 0x1000)
            assertTrue((gameProg.symbols["NMI"] ?: 0) > 0x1000)
            assertTrue((gameProg.symbols["LOAD"] ?: 0) > 0x4000)
            val newPc = gameProg.symbols["NEWPC"] ?: 0
            assertTrue(newPc > 0x5000 && (newPc and 0xFF) == 0)
            assertNotNull(gameProg.symbols["MAP"])
            assertNotNull(gameProg.symbols["COLTABLE"])
            assertNotNull(gameProg.symbols["BLOCKMEM"])
            assertNotNull(gameProg.symbols["EMOVE"])
            assertNotNull(gameProg.symbols["DETECT"])
        }

        val renderer = VicIIVideoRenderer()
        val sid = SidAudioSynthesizer()
        val core = C64ExecutionCore(
            assembler = assembler,
            assetProvider = fileSystemAssetProvider,
            videoRenderer = renderer,
            sidSynthesizer = sid
        )
        core.initializeEngine()

        for (frame in 0 until 30) {
            core.runSinglePalFrame()
        }
        val introDistinctColors = renderer.framePixels.map { it and 0xFFFFFF }.distinct()
        assertTrue(
            "Expected INTRO to render multiple colors, got: $introDistinctColors (PC=${Integer.toHexString(core.regPC)}, VIC11=${Integer.toHexString(core.vicRegs[0x11])})",
            introDistinctColors.size >= 3
        )
        assertTrue(
            "Expected INTRO SID music to produce non-zero PCM audio samples",
            sid.renderFrameSamples().any { it.toInt() != 0 }
        )

        core.joyFire = true
        for (frame in 0 until 100) {
            core.runSinglePalFrame()
            if (core.inGameMode) break
        }
        core.joyFire = false
        assertTrue("Expected pressing FIRE in INTRO to transition to GAME mode", core.inGameMode)

        for (frame in 0 until 60) {
            core.runSinglePalFrame()
        }
        val titleDistinctColors = renderer.framePixels.map { it and 0xFFFFFF }.distinct()
        assertTrue(
            "Expected GAME title screen to render multiple colors, got: $titleDistinctColors (PC=${Integer.toHexString(core.regPC)}, VIC11=${Integer.toHexString(core.vicRegs[0x11])})",
            titleDistinctColors.size >= 3
        )

        core.keyF1Pressed = true
        for (frame in 0 until 120) {
            core.runSinglePalFrame()
        }
        core.keyF1Pressed = false
        val gameDistinctColors = renderer.framePixels.map { it and 0xFFFFFF }.distinct()
        assertTrue(
            "Expected active GAME level 1 to render multiple colors, got: $gameDistinctColors",
            gameDistinctColors.size >= 4
        )

        val level1Prog = assembler.assembleGameLevel(1)
        val screenOnAddr = level1Prog.symbols["SCREENON"] ?: 0
        val screenOffAddr = level1Prog.symbols["SCREENOFF"] ?: 0
        assertTrue(screenOnAddr > 0 && screenOffAddr > screenOnAddr)
        val secLabelAddr = level1Prog.symbols["SEC"] ?: 0
        assertTrue("Expected SEC label in LEVEL1.PDS to be defined", secLabelAddr > 0x6000)

        for (frame in 0 until 120) {
            core.runSinglePalFrame()
        }

        val symX = level1Prog.symbols["X"]!!
        val symY = level1Prog.symbols["Y"]!!
        val symAction = level1Prog.symbols["ACTION"]!!
        val symHits = level1Prog.symbols["HITS"]!!
        val symAltered = level1Prog.symbols["ALTERED"]!!
        val symCheatBit = level1Prog.symbols["CHEATBIT"]!!

        val playerY = core.ram[symY].toInt() and 0xFF
        assertEquals(138, playerY)

        val spriteRowsWithPlayerColor = (0 until 48).count { dy ->
            val displayY = (playerY - 50 + dy).coerceIn(0, 199)
            val rowStart = displayY * 320
            (0 until 320).any { x ->
                val c = renderer.framePixels[rowStart + x] and 0xFFFFFF
                c == 0xB86962 || c == 0x9F9F9F || c == 0x000000
            }
        }
        assertTrue(
            "Expected player sprite to render across upper, middle, and lower body rows, got $spriteRowsWithPlayerColor/48",
            spriteRowsWithPlayerColor >= 42
        )

        val cheatState = core.toggleCheatMode()
        assertTrue(cheatState)
        assertEquals(255, core.ram[symCheatBit + 1].toInt() and 0xFF)
        assertEquals(9, core.ram[symHits].toInt() and 0xFF)
        assertEquals(255, core.ram[symAltered].toInt() and 0xFF)

        core.ram[symY] = 66.toByte()
        core.joyUp = true
        for (frame in 0 until 70) {
            core.runSinglePalFrame()
            val curY = core.ram[symY].toInt() and 0xFF
            assertTrue("Expected player Y ($curY) to never underflow to ground/sky (>186)", curY in 18..140)
        }
        core.joyUp = false

        core.ram[symX] = 250.toByte()
        core.runSinglePalFrame()
        val clampedLeftX = core.ram[symX].toInt() and 0xFF
        assertTrue("Expected off-screen left X to be clamped in-bounds, got $clampedLeftX", clampedLeftX in 6..150)

        core.ram[symX] = 175.toByte()
        core.runSinglePalFrame()
        val clampedRightX = core.ram[symX].toInt() and 0xFF
        assertTrue("Expected off-screen right X to be clamped in-bounds, got $clampedRightX", clampedRightX in 6..150)
        assertTrue("Expected player action to remain valid", (core.ram[symAction].toInt() and 0xFF) < 14)

        val symEndScroll = level1Prog.symbols["ENDSCROLL"]!!
        val symKilled = level1Prog.symbols["KILLED"]!!
        val symEnemy = level1Prog.symbols["ENEMY"]!!
        val symScroll = level1Prog.symbols["SCROLL"]!!
        val symLevel = level1Prog.symbols["LEVEL"]!!

        for (targetLevel in 2..5) {
            core.ram[symEndScroll] = 1
            for (frame in 0 until 300) {
                core.runSinglePalFrame()
                if ((core.ram[symEnemy + 2].toInt() and 0xFF) == 1) break
            }
            assertEquals("Expected boss active on level ${targetLevel - 1}", 1, core.ram[symEnemy + 2].toInt() and 0xFF)

            core.ram[symKilled] = 255.toByte()
            var enteredEndSeq = false
            var reachedNextLevel = false
            for (frame in 0 until 3000) {
                if ((core.ram[symEnemy + 2].toInt() and 0xFF) == 3) {
                    enteredEndSeq = true
                }
                if (core.currentLoadedLevel == targetLevel) {
                    val act2 = core.ram[symAction + 2].toInt() and 0xFF
                    val en2 = core.ram[symEnemy + 2].toInt() and 0xFF
                    core.joyFire = !(en2 == 3 && act2 in 9..15) && ((frame and 1) == 0)
                    if ((core.ram[symScroll].toInt() and 0xFF) == 1 && en2 != 3) {
                        reachedNextLevel = true
                        break
                    }
                }
                core.runSinglePalFrame()
            }
            core.joyFire = false

            assertTrue("Expected level ${targetLevel - 1} to enter ENDSEQ cutscene (ENEMY+2 == 3)", enteredEndSeq)
            assertEquals("Expected loader to advance to level $targetLevel", targetLevel, core.currentLoadedLevel)
            assertEquals("Expected C64 LEVEL variable to be $targetLevel", targetLevel, core.ram[symLevel].toInt() and 0xFF)
            assertTrue(
                "Expected level $targetLevel to finish STARTSEQ and begin scrolling gameplay (scroll=${core.ram[symScroll].toInt() and 0xFF}, enemy2=${core.ram[symEnemy + 2].toInt() and 0xFF}, act0=${core.ram[symAction].toInt() and 0xFF}, act2=${core.ram[symAction + 2].toInt() and 0xFF}, x0=${core.ram[symX].toInt() and 0xFF}, y0=${core.ram[symY].toInt() and 0xFF}, pc=${Integer.toHexString(core.regPC)})",
                reachedNextLevel
            )

            for (frame in 0 until 10) {
                core.runSinglePalFrame()
            }
            assertEquals(
                "Expected CHEAT ON to restore Altered Beast form on level $targetLevel after STARTSEQ",
                255,
                core.ram[symAltered].toInt() and 0xFF
            )

            core.joyFire = true
            core.joyRight = true
            for (frame in 0 until 45) {
                core.runSinglePalFrame()
                val curY = core.ram[symY].toInt() and 0xFF
                assertTrue("Expected valid player Y ($curY) during level $targetLevel Altered Beast attack", curY in 18..187)
            }
            core.joyFire = false
            core.joyRight = false
            for (frame in 0 until 30) {
                core.runSinglePalFrame()
            }
        }

        core.ram[symEndScroll] = 1
        for (frame in 0 until 300) {
            core.runSinglePalFrame()
            if ((core.ram[symEnemy + 2].toInt() and 0xFF) == 1) break
        }
        assertEquals("Expected boss active on level 5", 1, core.ram[symEnemy + 2].toInt() and 0xFF)
        core.ram[symKilled] = 255.toByte()
        var completedLevel5EndSeq = false
        for (frame in 0 until 3500) {
            core.joyFire = (frame and 1) == 0
            core.runSinglePalFrame()
            if ((core.ram[symEnemy + 2].toInt() and 0xFF) == 3 &&
                (core.ram[symAction + 2].toInt() and 0xFF) == 0 &&
                (core.ram[symAction + 3].toInt() and 0xFF) == 0
            ) {
                completedLevel5EndSeq = true
            }
        }
        core.joyFire = false
        assertTrue("Expected Level 5 ending sequence and gallery to execute cleanly without crashing", completedLevel5EndSeq)
    }

    @Test
    fun gzBeastStorageGuideVerificationAndUnrestrictedLuaModdingEngineWorksEndToEnd() {
        val tempRoot = Files.createTempDirectory("gzbeast_storage_test").toFile()
        try {
            val router = ModAssetRouter(fileSystemAssetProvider)
            val assembler = PdsNativeAssembler(router)
            val renderer = VicIIVideoRenderer()
            val sid = SidAudioSynthesizer()
            val core = C64ExecutionCore(
                assembler = assembler,
                assetProvider = router,
                videoRenderer = renderer,
                sidSynthesizer = sid
            )
            val luaEngine = LuaModEngine(core, router)

            val report1 = luaEngine.initializeFromStorage(tempRoot, resetLogSession = true)
            assertTrue(report1.gzBeastCreated)
            assertTrue(report1.modsCreated)
            assertTrue(report1.gzBeastDir.exists())
            assertTrue(report1.modsDir.exists())
            assertTrue(report1.guideFile.exists())
            assertEquals(GuideVerificationResult.CREATED_NEW, report1.guideResult)
            assertEquals(GuideDocumentation.EXPECTED_BYTE_SIZE, report1.actualGuideSize)
            assertEquals(GuideDocumentation.CANONICAL_GUIDE_TEXT, report1.guideFile.readText(Charsets.UTF_8))
            assertEquals(0, report1.discoveredLuaFiles.size)

            assertFalse("Unchanged storage should not re-initialize", luaEngine.refreshFromStorageIfChanged(tempRoot))

            core.initializeEngine()
            for (lvl in 1..5) {
                core.loadLevelDirect(lvl)
                core.runSinglePalFrame()
            }

            val report2 = ModStorageManager.initializeAndVerifyStorage(tempRoot)
            assertFalse(report2.gzBeastCreated)
            assertFalse(report2.modsCreated)
            assertEquals(GuideVerificationResult.UNCHANGED_SIZE_MATCH, report2.guideResult)
            assertEquals(GuideDocumentation.EXPECTED_BYTE_SIZE, report2.actualGuideSize)

            report1.guideFile.writeText("corrupted_or_outdated_guide_content", Charsets.UTF_8)
            assertTrue(report1.guideFile.length() != GuideDocumentation.EXPECTED_BYTE_SIZE)

            val report3 = ModStorageManager.initializeAndVerifyStorage(tempRoot)
            assertEquals(GuideVerificationResult.REPLACED_SIZE_MISMATCH, report3.guideResult)
            assertEquals(GuideDocumentation.EXPECTED_BYTE_SIZE, report3.guideFile.length())
            assertEquals(GuideDocumentation.CANONICAL_GUIDE_TEXT, report3.guideFile.readText(Charsets.UTF_8))

            val mod1File = File(report1.modsDir, "01_custom_level_and_sprites.lua")
            mod1File.writeText(
                """
                function on_init()
                    gz.log("Mod 1 initialized with version " .. gz.version())
                    local custom_char = { 0xFF, 0x81, 0x81, 0xFF, 0xFF, 0x81, 0x81, 0xFF }
                    gz.level.set_char(0, custom_char)
                    gz.palette.set(0, 0x112233)
                    gz.asset.write_text("custom_state.txt", "mod1_ready")
                end

                function on_level_load(lvl, file_id)
                    gz.log("Level loaded: " .. tostring(lvl) .. " file_id=" .. tostring(file_id))
                    gz.poke(0xCFFE, lvl)
                end

                function on_frame(frame, in_game, lvl)
                    if in_game then
                        local p1 = gz.player.get(1)
                        if p1.lives > 0 and p1.hits < 9 then
                            gz.player.set(1, { hits = 9 })
                        end
                    end
                    gz.poke(0xCFFF, frame % 256)
                end

                function on_draw(frame, in_game, lvl)
                    gz.canvas.rect(2, 2, 40, 10, 0xFF102030, true)
                    gz.canvas.text(4, 4, "GZMOD", 0xFFFFCC00, 1)
                end

                function on_exit()
                    gz.log("Mod 1 cleanly exiting")
                end
                """.trimIndent(),
                Charsets.UTF_8
            )

            val subModDir = File(report1.modsDir, "pack_b")
            subModDir.mkdirs()
            val mod2File = File(subModDir, "02_asm_and_pc_hook.lua")
            mod2File.writeText(
                """
                pc_hook_hits = 0
                function on_init()
                    gz.asm.patch(0xCF00, [[
                        LDA #&42
                        STA &CF10
                        RTS
                    ]])
                    gz.on_pc(0xCF00, function(pc)
                        pc_hook_hits = pc_hook_hits + 1
                        gz.poke(0xCF11, pc_hook_hits)
                    end)
                end
                """.trimIndent(),
                Charsets.UTF_8
            )

            val modReport = luaEngine.initializeFromStorage(tempRoot, resetLogSession = true)
            assertEquals(2, modReport.discoveredLuaFiles.size)
            assertEquals(2, luaEngine.loadedModNames.size)
            assertFalse("Second refresh without file changes must be a no-op", luaEngine.refreshFromStorageIfChanged(tempRoot))

            core.initializeEngine()
            assertEquals(0x112233, renderer.getPaletteColor(0))
            assertEquals("mod1_ready", router.readModFileText("custom_state.txt"))
            assertEquals(0xA9, core.ram[0xCF00].toInt() and 0xFF)
            assertEquals(0x42, core.ram[0xCF01].toInt() and 0xFF)

            val sessionLogFile = File(report1.modsDir, "session.log")
            assertFalse("session.log must NOT be written during active runtime before exit", sessionLogFile.exists())

            core.loadLevelDirect(2)
            assertEquals(2, core.currentLoadedLevel)
            assertEquals(2, core.ram[0xCFFE].toInt() and 0xFF)

            val prevFrames = core.totalFramesRun
            for (f in 1..5) {
                core.runSinglePalFrame()
            }
            assertEquals(((prevFrames + 5) % 256).toInt(), core.ram[0xCFFF].toInt() and 0xFF)

            val savedPc = core.regPC
            core.regPC = 0xCF00
            luaEngine.onPcHook(0xCF00)
            core.regPC = savedPc
            assertEquals(1, core.ram[0xCF11].toInt() and 0xFF)

            assertFalse("session.log must still NOT exist before app exit", sessionLogFile.exists())

            luaEngine.onApplicationExit()
            assertTrue("session.log must be written when application exits", sessionLogFile.exists())
            val logContent = sessionLogFile.readText(Charsets.UTF_8)
            val sessionHeaderCount = logContent.lines().count { it.contains("GZBeast session started.") }
            assertEquals("Expected exactly 1 session header in session.log", 1, sessionHeaderCount)
            assertTrue(logContent.contains("Mod 1 initialized with version GZBeast-Lua-1.0"))
            assertTrue(logContent.contains("Level loaded: 2 file_id=2"))
            assertTrue(logContent.contains("Mod 1 cleanly exiting"))
            assertTrue(logContent.contains("Session log flushed on application exit."))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}

