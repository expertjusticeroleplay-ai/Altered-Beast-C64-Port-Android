package com.example

import com.example.engine.BinaryAssetProvider
import com.example.engine.C64ExecutionCore
import com.example.engine.PdsNativeAssembler
import com.example.engine.SidAudioSynthesizer
import com.example.engine.TranslatedSourceModules
import com.example.engine.VicIIVideoRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
}
