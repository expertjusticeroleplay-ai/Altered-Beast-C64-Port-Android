package com.example.engine

class C64ExecutionCore(
    private val assembler: PdsNativeAssembler,
    private val assetProvider: BinaryAssetProvider,
    val videoRenderer: VicIIVideoRenderer,
    val sidSynthesizer: SidAudioSynthesizer
) {

    val ram = ByteArray(65536)
    val colorRam = ByteArray(1024)
    val vicRegs = IntArray(64)

    var regA = 0
    var regX = 0
    var regY = 0
    var regSP = 0xFF
    var regPC = 0
    var flagN = false
    var flagV = false
    var flagB = false
    var flagD = false
    var flagI = true
    var flagZ = false
    var flagC = false

    var currentRaster = 0
    var irqRasterLatch = 0
    private var cycleRemainder = 0

    var cia1PortA = 0xFF
    var cia1DdrA = 0xFF
    var cia2PortA = 0x00
    var cia2IcrFlags = 0x00
    var cia2IcrMask = 0x00

    private var timerALatch = 0xFFFF
    private var timerACounter = 0xFFFF
    private var timerARunning = false
    private var timerAOneShot = false

    private var timerBLatch = 0xFFFF
    private var timerBCounter = 0xFFFF
    private var timerBRunning = false
    private var timerBOneShot = false

    @Volatile
    var joyUp = false
    @Volatile
    var joyDown = false
    @Volatile
    var joyLeft = false
    @Volatile
    var joyRight = false
    @Volatile
    var joyFire = false
    @Volatile
    var joystickPort = 2

    @Volatile
    var keyF1Pressed = false
    @Volatile
    var keyF7Pressed = false
    @Volatile
    var keyPausePressed = false
    @Volatile
    var keyCheatPressed = false
    @Volatile
    var cheatEnabled = false
    @Volatile
    var pendingRestoreNmi = false

    @Volatile
    var inGameMode = false
        private set

    @Volatile
    var currentLoadedLevel = 1
        private set

    private var introProgram: AssembledProgram? = null
    private var gameBaseProgram: AssembledProgram? = null
    private val levelPrograms = HashMap<Int, AssembledProgram>()

    private var introStartAddr = 0x9839
    private var introLoadingScAddr = -1
    private var introLoadStartAddr = -1
    private var introLoadEndAddr = -1
    private var introLoadAddr = -1
    private var introStartGameAddr = -1

    private var gameStartAddr = 0x2ED7
    private var gameLoadAddr = 0x5E27
    private var gameNewPcAddr = 0x6300
    private var gameCharsetAddr = 0xF800
    private var zpScreenEnable = -1
    private var zpScreenTest = -1

    private var gameMainIrqAddr = -1
    private var gameIrq1Addr = -1
    private var gameRetFAddr = -1
    private var symX = -1
    private var symY = -1
    private var symDir = -1
    private var symAction = -1
    private var symUp = -1
    private var symLives = -1
    private var symHits = -1
    private var symBalls = -1
    private var symAltered = -1
    private var symFaceFlash = -1
    private var symBlanking = -1
    private var symCheatBit = -1
    private var symSpriteThere = -1
    private var symEnemy = -1
    private var symAttached = -1
    private var symScroll = -1
    private var symEndScroll = -1

    private var jumpUpLatch = false

    fun initializeEngine() {
        val intro = assembler.assembleIntro()
        introProgram = intro
        introStartAddr = intro.symbols["START"] ?: 0x9839
        introLoadingScAddr = intro.symbols["LOADINGSC"] ?: -1
        introLoadStartAddr = intro.symbols["LOADSTART"] ?: -1
        introLoadEndAddr = intro.symbols["LOADEND"] ?: -1
        introLoadAddr = intro.symbols["LOAD"] ?: -1
        introStartGameAddr = intro.symbols["STARTGAME"] ?: -1

        val level1 = assembler.assembleGameLevel(1)
        gameBaseProgram = level1
        levelPrograms[1] = level1
        gameStartAddr = level1.symbols["START"] ?: 0x2ED7
        gameLoadAddr = level1.symbols["LOAD"] ?: 0x5E27
        gameNewPcAddr = level1.symbols["NEWPC"] ?: 0x6300
        gameCharsetAddr = level1.symbols["CHARSET"] ?: 0xF800
        zpScreenEnable = level1.symbols["SCREENENABLE"] ?: -1
        zpScreenTest = level1.symbols["SCREENTEST"] ?: -1

        gameMainIrqAddr = level1.symbols["IRQ"] ?: -1
        gameIrq1Addr = level1.symbols["IRQ1"] ?: -1
        gameRetFAddr = level1.symbols["RETF"] ?: -1
        symX = level1.symbols["X"] ?: -1
        symY = level1.symbols["Y"] ?: -1
        symDir = level1.symbols["DIR"] ?: -1
        symAction = level1.symbols["ACTION"] ?: -1
        symUp = level1.symbols["UP"] ?: -1
        symLives = level1.symbols["LIVES"] ?: -1
        symHits = level1.symbols["HITS"] ?: -1
        symBalls = level1.symbols["BALLS"] ?: -1
        symAltered = level1.symbols["ALTERED"] ?: -1
        symFaceFlash = level1.symbols["FACEFLASH"] ?: -1
        symBlanking = level1.symbols["BLANKING"] ?: -1
        symCheatBit = level1.symbols["CHEATBIT"] ?: -1
        symSpriteThere = level1.symbols["SPRITETHERE"] ?: -1
        symEnemy = level1.symbols["ENEMY"] ?: -1
        symAttached = level1.symbols["ATTACHED"] ?: -1
        symScroll = level1.symbols["SCROLL"] ?: -1
        symEndScroll = level1.symbols["ENDSCROLL"] ?: -1

        for (lvl in 2..5) {
            levelPrograms[lvl] = assembler.assembleGameLevel(lvl)
        }

        bootIntroProgram()
    }

    fun bootIntroProgram() {
        val intro = introProgram ?: return
        inGameMode = false
        ram.fill(0)
        colorRam.fill(0)
        vicRegs.fill(0)
        videoRenderer.resetFrameState()
        sidSynthesizer.reset()

        System.arraycopy(intro.memory, 0, ram, 0, 65536)
        ram[0] = 0x2F
        ram[1] = 0x35
        vicRegs[0x11] = 0x1B
        vicRegs[0x16] = 0x08
        vicRegs[0x18] = 0x14
        currentRaster = 0
        irqRasterLatch = 0
        cycleRemainder = 0
        cia1PortA = 0xFF
        cia1DdrA = 0xFF
        cia2PortA = 0x00
        cia2IcrFlags = 0x00
        cia2IcrMask = 0x00
        timerARunning = false
        timerBRunning = false

        regA = 0
        regX = 0
        regY = 0
        regSP = 0xFF
        flagN = false
        flagV = false
        flagD = false
        flagI = true
        flagZ = false
        flagC = false
        regPC = introStartAddr

        zpScreenEnable = intro.symbols["SCREENENABLE"] ?: -1
        zpScreenTest = intro.symbols["SCREENTEST"] ?: -1
    }

    fun bootGameProgram() {
        val base = gameBaseProgram ?: return
        inGameMode = true
        currentLoadedLevel = 1
        ram.fill(0)
        colorRam.fill(0)
        vicRegs.fill(0)
        videoRenderer.resetFrameState()
        sidSynthesizer.reset()

        System.arraycopy(base.memory, 0, ram, 0, 65536)
        ram[0] = 0x2F
        ram[1] = 0x35
        vicRegs[0x11] = 0x1B
        vicRegs[0x16] = 0x08
        vicRegs[0x18] = 0xC2
        currentRaster = 0
        irqRasterLatch = 0
        cycleRemainder = 0
        cia1PortA = 0xFF
        cia1DdrA = 0xFF
        cia2PortA = 0x00
        cia2IcrFlags = 0x00
        cia2IcrMask = 0x00
        timerARunning = false
        timerBRunning = false

        regA = 0
        regX = 0
        regY = 0
        regSP = 0xFF
        flagN = false
        flagV = false
        flagD = false
        flagI = true
        flagZ = false
        flagC = false
        regPC = gameStartAddr

        zpScreenEnable = base.symbols["SCREENENABLE"] ?: -1
        zpScreenTest = base.symbols["SCREENTEST"] ?: -1
    }

    fun toggleCheatMode(): Boolean {
        val next = !cheatEnabled
        cheatEnabled = next
        keyCheatPressed = next
        if (inGameMode) {
            if (next) {
                if (symCheatBit >= 0) {
                    ram[(symCheatBit + 1) and 0xFFFF] = 255.toByte()
                }
                if (symSpriteThere >= 0) {
                    ram[(symSpriteThere + 1) and 0xFFFF] = 255.toByte()
                }
                for (p in 0..1) {
                    val lives = if (symLives >= 0) ram[symLives + p].toInt() and 0xFF else 0
                    if (lives > 0) {
                        if (lives < 3 && symLives >= 0) {
                            ram[symLives + p] = 3
                        }
                        if (symHits >= 0) {
                            ram[symHits + p] = 9
                        }
                        if (symBalls >= 0) {
                            ram[symBalls + p] = 3
                        }
                        if (symAltered >= 0) {
                            ram[symAltered + p] = 255.toByte()
                        }
                        if (symFaceFlash >= 0) {
                            ram[symFaceFlash + p] = 50
                        }
                        if (symBlanking >= 0) {
                            ram[symBlanking + p] = 0
                        }
                    }
                }
            } else {
                if (symCheatBit >= 0) {
                    ram[(symCheatBit + 1) and 0xFFFF] = 0
                }
                if (symSpriteThere >= 0) {
                    ram[(symSpriteThere + 1) and 0xFFFF] = 255.toByte()
                }
                for (p in 0..1) {
                    if (symBlanking >= 0) {
                        ram[symBlanking + p] = 0
                    }
                }
            }
        }
        return next
    }

    fun triggerRestoreKey() {
        pendingRestoreNmi = true
    }

    fun runSinglePalFrame() {
        videoRenderer.resetFrameState()

        if (pendingRestoreNmi) {
            pendingRestoreNmi = false
            cia2IcrFlags = 0x00
            triggerNmi()
        }

        if (!joyUp) {
            jumpUpLatch = false
        }

        for (raster in 0 until 312) {
            currentRaster = raster

            if (inGameMode && raster == 209 && gameMainIrqAddr > 0) {
                val vec = (ram[0xFFFE].toInt() and 0xFF) or ((ram[0xFFFF].toInt() and 0xFF) shl 8)
                if (gameIrq1Addr > 0 && gameRetFAddr > 0 && vec in gameIrq1Addr..(gameRetFAddr + 32)) {
                    ram[0xFFFE] = (gameMainIrqAddr and 0xFF).toByte()
                    ram[0xFFFF] = ((gameMainIrqAddr ushr 8) and 0xFF).toByte()
                    irqRasterLatch = 209
                }
            }

            if (raster == irqRasterLatch) {
                vicRegs[0x19] = vicRegs[0x19] or 0x81
            }

            if ((vicRegs[0x19] and 0x81) != 0 && (vicRegs[0x1A] and 0x01) != 0 && !flagI) {
                triggerIrq()
            }

            videoRenderer.beginScanlineSprites(raster, vicRegs)

            stepScanlineCycles(63)

            applyRuntimeGuardsAndCheats()

            if (raster in 50 until 250) {
                videoRenderer.renderScanline(
                    displayY = raster - 50,
                    ram = ram,
                    colorRam = colorRam,
                    vicRegs = vicRegs,
                    cia2PortA = cia2PortA
                )
            }

            videoRenderer.endScanlineSprites(vicRegs)
        }

        sidSynthesizer.renderFrameSamples()
    }

    private fun applyRuntimeGuardsAndCheats() {
        if (!inGameMode) return

        val enemy2 = if (symEnemy >= 0) ram[symEnemy + 2].toInt() and 0xFF else 0
        val scroll = if (symScroll >= 0) ram[symScroll].toInt() and 0xFF else 0
        val endScroll = if (symEndScroll >= 0) ram[symEndScroll].toInt() and 0xFF else 0

        if (cheatEnabled) {
            if (symCheatBit >= 0) {
                ram[(symCheatBit + 1) and 0xFFFF] = 255.toByte()
            }
            for (p in 0..1) {
                val lives = if (symLives >= 0) ram[symLives + p].toInt() and 0xFF else 0
                if (lives > 0) {
                    if (lives < 3 && symLives >= 0) {
                        ram[symLives + p] = 3
                    }
                    if (symHits >= 0) {
                        ram[symHits + p] = 9
                    }
                    if (symBlanking >= 0) {
                        ram[symBlanking + p] = 0
                    }
                    if (enemy2 != 2 && enemy2 != 3 && (scroll != 0 || endScroll != 0)) {
                        val act = if (symAction >= 0) ram[symAction + p].toInt() and 0xFF else 0
                        val curAltered = if (symAltered >= 0) ram[symAltered + p].toInt() and 0xFF else 0
                        if (act < 11 && curAltered == 0) {
                            if (symBalls >= 0) {
                                ram[symBalls + p] = 3
                            }
                            if (symAltered >= 0) {
                                ram[symAltered + p] = 255.toByte()
                            }
                            if (symFaceFlash >= 0) {
                                ram[symFaceFlash + p] = 50
                            }
                        }
                    }
                }
            }
        }

        if (symX < 0 || symY < 0 || symDir < 0 || symAction < 0 || symLives < 0) return
        if (enemy2 == 3) {
            for (p in 0..1) {
                val lives = ram[symLives + p].toInt() and 0xFF
                val act = ram[symAction + p].toInt() and 0xFF
                if (lives > 0 && act == 16) {
                    ram[symY + p] = 138.toByte()
                }
            }
            return
        }

        for (p in 0..1) {
            val lives = ram[symLives + p].toInt() and 0xFF
            val act = ram[symAction + p].toInt() and 0xFF
            if (lives == 0 || act >= 14) continue

            val dir = ram[symDir + p].toInt() and 0xFF
            val minX = if (dir != 0) (if (p == 0) 8 else 6) else (if (p == 0) 14 else 12)
            val maxX = 0x96
            val curX = ram[symX + p].toInt() and 0xFF
            if (curX >= 0xD0 || curX < minX) {
                ram[symX + p] = minX.toByte()
            } else if (curX > maxX) {
                ram[symX + p] = maxX.toByte()
            }

            val curY = ram[symY + p].toInt() and 0xFF
            val attached = if (symAttached >= 0) ram[symAttached + p].toInt() and 0xFF else 255
            val altered = if (symAltered >= 0) ram[symAltered + p].toInt() and 0xFF else 0

            if (currentLoadedLevel == 2 && altered != 0) {
                if (curY < 18 || curY > 200) {
                    ram[symY + p] = 42.toByte()
                } else if (curY in 152..186) {
                    ram[symY + p] = 144.toByte()
                }
            } else if (act == 2 || act == 9) {
                if (act == 2) {
                    jumpUpLatch = true
                }
                if (curY < 18 || curY > 200) {
                    ram[symY + p] = 18.toByte()
                }
            } else {
                if (jumpUpLatch && symUp >= 0) {
                    ram[symUp + p] = 0
                }
                if (attached == 255) {
                    if (curY > 200) {
                        ram[symY + p] = 18.toByte()
                    } else if ((act == 0 || act == 1 || act == 5 || act == 6 || act == 7 || act == 8) && curY in 139..186) {
                        ram[symY + p] = 138.toByte()
                    }
                }
            }
        }
    }

    private fun stepScanlineCycles(targetCycles: Int) {
        var cyclesRun = cycleRemainder
        while (cyclesRun < targetCycles) {
            if (!inGameMode) {
                if (regPC == introLoadingScAddr || regPC == introLoadStartAddr || regPC == introLoadAddr) {
                    val lo = pullByte()
                    val hi = pullByte()
                    regPC = (((hi shl 8) or lo) + 1) and 0xFFFF
                    cyclesRun += 6
                    continue
                }
                if (regPC == introStartGameAddr || regPC == introLoadEndAddr || regPC == 0x2F3D) {
                    bootGameProgram()
                    cycleRemainder = 0
                    return
                }
            }

            if (inGameMode && regPC == gameLoadAddr) {
                handleNativeGameLoad(regA)
                val lo = pullByte()
                val hi = pullByte()
                regPC = (((hi shl 8) or lo) + 1) and 0xFFFF
                cyclesRun += 6
                continue
            }

            val stepCycles = executeSingleInstruction()
            cyclesRun += stepCycles

            if ((vicRegs[0x19] and 0x81) != 0 && (vicRegs[0x1A] and 0x01) != 0 && !flagI) {
                triggerIrq()
            }

            if (timerARunning) {
                timerACounter -= stepCycles
                if (timerACounter <= 0) {
                    cia2IcrFlags = cia2IcrFlags or 0x01
                    if (timerAOneShot) {
                        timerARunning = false
                    } else {
                        timerACounter += timerALatch.coerceAtLeast(1)
                    }
                    if ((cia2IcrMask and 0x01) != 0) {
                        cia2IcrFlags = cia2IcrFlags or 0x80
                        triggerNmi()
                    }
                }
            }

            if (timerBRunning) {
                timerBCounter -= stepCycles
                if (timerBCounter <= 0) {
                    cia2IcrFlags = cia2IcrFlags or 0x02
                    if (timerBOneShot) {
                        timerBRunning = false
                    } else {
                        timerBCounter += timerBLatch.coerceAtLeast(1)
                    }
                    if ((cia2IcrMask and 0x02) != 0) {
                        cia2IcrFlags = cia2IcrFlags or 0x80
                        triggerNmi()
                    }
                }
            }
        }
        cycleRemainder = cyclesRun - targetCycles
    }

    private fun handleNativeGameLoad(fileId: Int) {
        when (fileId) {
            in 1..5 -> {
                val lvlProg = levelPrograms[fileId] ?: return
                currentLoadedLevel = fileId
                val src = lvlProg.memory
                System.arraycopy(src, gameNewPcAddr, ram, gameNewPcAddr, 0xB400 - gameNewPcAddr)
                System.arraycopy(src, 0xD240, ram, 0xD240, 0xF000 - 0xD240)
                System.arraycopy(src, 0xF340, ram, 0xF340, 0xFFFA - 0xF340)
            }
            in 6..10 -> {
                val interIdx = fileId - 5
                val data = assetProvider.readBytes("GAME/LEVEL$interIdx/LEVEL${interIdx}IN.CHR")
                val copyLen = data.size.coerceAtMost(0x10000 - gameCharsetAddr)
                System.arraycopy(data, 0, ram, gameCharsetAddr, copyLen)
            }
            in 11..18 -> {
                val interIdx = ((fileId - 11) % 5) + 1
                val data = assetProvider.readBytes("GAME/LEVEL$interIdx/LEVEL${interIdx}IN.CHR")
                ram.fill(0, 0x8000, 0x8000 + 2160)
                for (row in 0 until 15) {
                    for (col in 0 until 15) {
                        val srcOff = (row * 15 + col) * 8
                        val dstOff = 0x8000 + (row * 18 + (col + 1)) * 8
                        if (srcOff + 8 <= data.size) {
                            System.arraycopy(data, srcOff, ram, dstOff, 8)
                        }
                    }
                }
                for (i in 0 until 270) {
                    ram[0x8870 + i] = 0xB4.toByte()
                    ram[0x897E + i] = 0x09.toByte()
                }
            }
        }
    }

    private fun isIoMapped(): Boolean {
        val port = ram[1].toInt() and 0x07
        return port >= 5
    }

    fun readMem(address: Int): Int {
        val addr = address and 0xFFFF
        if (addr in 0xD000..0xDFFF && isIoMapped()) {
            return when (addr) {
                in 0xD000..0xD3FF -> {
                    val reg = addr and 0x3F
                    if (reg == 0x12) {
                        currentRaster and 0xFF
                    } else if (reg == 0x11) {
                        (vicRegs[0x11] and 0x7F) or (if (currentRaster >= 256) 0x80 else 0)
                    } else {
                        vicRegs[reg] and 0xFF
                    }
                }
                in 0xD400..0xD7FF -> 0x00
                in 0xD800..0xDBFF -> colorRam[addr - 0xD800].toInt() and 0x0F
                in 0xDC00..0xDCFF -> readCia1(addr and 0x0F)
                in 0xDD00..0xDDFF -> readCia2(addr and 0x0F)
                else -> ram[addr].toInt() and 0xFF
            }
        }
        return ram[addr].toInt() and 0xFF
    }

    fun writeMem(address: Int, value: Int) {
        val addr = address and 0xFFFF
        val byteVal = value and 0xFF

        if (addr == zpScreenEnable && zpScreenTest >= 0) {
            ram[addr] = byteVal.toByte()
            if (flagI || (vicRegs[0x1A] and 0x01) == 0) {
                ram[zpScreenTest] = byteVal.toByte()
                if (byteVal == 0) {
                    vicRegs[0x11] = vicRegs[0x11] and 0xEF
                } else {
                    vicRegs[0x11] = (vicRegs[0x11] and 0xEF) or 0x10
                }
            }
            return
        }

        if (addr in 0xD000..0xDFFF && isIoMapped()) {
            when (addr) {
                in 0xD000..0xD3FF -> {
                    val reg = addr and 0x3F
                    when {
                        reg < 16 && (reg and 1) == 1 -> {
                            vicRegs[reg] = byteVal
                            videoRenderer.onSpriteYWrite(reg ushr 1, byteVal, currentRaster, vicRegs)
                        }
                        reg == 0x11 -> {
                            irqRasterLatch = (irqRasterLatch and 0x0FF) or ((byteVal and 0x80) shl 1)
                            vicRegs[0x11] = byteVal
                        }
                        reg == 0x12 -> {
                            irqRasterLatch = (irqRasterLatch and 0x100) or byteVal
                            vicRegs[0x12] = byteVal
                            if (inGameMode && gameIrq1Addr > 0 && gameRetFAddr > 0) {
                                val vec = (ram[0xFFFE].toInt() and 0xFF) or ((ram[0xFFFF].toInt() and 0xFF) shl 8)
                                if (vec in gameIrq1Addr..(gameRetFAddr + 32) &&
                                    irqRasterLatch in 18..209 &&
                                    irqRasterLatch <= currentRaster &&
                                    (currentRaster - irqRasterLatch) <= 8
                                ) {
                                    vicRegs[0x19] = vicRegs[0x19] or 0x81
                                }
                            }
                        }
                        reg == 0x19 -> {
                            vicRegs[0x19] = vicRegs[0x19] and (byteVal.inv() and 0x0F)
                        }
                        else -> {
                            vicRegs[reg] = byteVal
                        }
                    }
                }
                in 0xD400..0xD7FF -> {
                    sidSynthesizer.writeRegister(addr and 0x1F, byteVal)
                }
                in 0xD800..0xDBFF -> {
                    colorRam[addr - 0xD800] = (byteVal and 0x0F).toByte()
                }
                in 0xDC00..0xDCFF -> {
                    writeCia1(addr and 0x0F, byteVal)
                }
                in 0xDD00..0xDDFF -> {
                    writeCia2(addr and 0x0F, byteVal)
                }
                else -> {
                    ram[addr] = byteVal.toByte()
                }
            }
            return
        }

        ram[addr] = byteVal.toByte()
    }

    private fun readCia1(reg: Int): Int {
        val activeRowMask = (cia1PortA or (cia1DdrA.inv() and 0xFF)) and 0xFF
        if (reg == 0x00) {
            var v = activeRowMask
            if (joystickPort == 2) {
                v = v and buildJoystickMask()
            }
            if (!inGameMode && (keyF1Pressed || keyF7Pressed)) {
                v = v and 0xEF
            }
            return v
        }
        if (reg == 0x01) {
            var v = 0xFF
            if (joystickPort == 1) {
                v = v and buildJoystickMask()
            }
            if ((activeRowMask and 0x01) == 0) {
                if (keyF1Pressed) v = v and 0xEF
                if (keyF7Pressed) v = v and 0xF7
            }
            if ((activeRowMask and 0x02) == 0) {
                if (keyPausePressed) v = v and 0x7F
                if (keyCheatPressed) v = v and 0xFB
            }
            if ((activeRowMask and 0x08) == 0) {
                if (keyCheatPressed) v = v and 0xEF
            }
            if ((activeRowMask and 0x80) == 0) {
                if (keyCheatPressed) v = v and 0xEF
            }
            return v
        }
        if (reg == 0x02) {
            return cia1DdrA
        }
        return 0xFF
    }

    private fun writeCia1(reg: Int, value: Int) {
        if (reg == 0x00) {
            cia1PortA = value and 0xFF
        } else if (reg == 0x02) {
            cia1DdrA = value and 0xFF
        }
    }

    private fun buildJoystickMask(): Int {
        var mask = 0xFF
        val allowUp = if (inGameMode && jumpUpLatch) {
            val pIdx = if (joystickPort == 2) 0 else 1
            val altered = if (symAltered >= 0) ram[symAltered + pIdx].toInt() and 0xFF else 0
            val act = if (symAction >= 0) ram[symAction + pIdx].toInt() and 0xFF else 0
            act == 2 || (currentLoadedLevel == 2 && altered != 0)
        } else {
            true
        }
        if (joyUp && allowUp) mask = mask and 0xFE
        if (joyDown) mask = mask and 0xFD
        if (joyLeft) mask = mask and 0xFB
        if (joyRight) mask = mask and 0xF7
        if (joyFire) mask = mask and 0xEF
        return mask
    }

    private fun readCia2(reg: Int): Int {
        return when (reg) {
            0x00 -> cia2PortA
            0x04 -> timerACounter and 0xFF
            0x05 -> (timerACounter ushr 8) and 0xFF
            0x06 -> timerBCounter and 0xFF
            0x07 -> (timerBCounter ushr 8) and 0xFF
            0x0D -> {
                val flags = cia2IcrFlags
                cia2IcrFlags = 0x00
                flags
            }
            else -> 0x00
        }
    }

    private fun writeCia2(reg: Int, value: Int) {
        when (reg) {
            0x00 -> cia2PortA = value
            0x04 -> timerALatch = (timerALatch and 0xFF00) or value
            0x05 -> timerALatch = (timerALatch and 0x00FF) or (value shl 8)
            0x06 -> timerBLatch = (timerBLatch and 0xFF00) or value
            0x07 -> timerBLatch = (timerBLatch and 0x00FF) or (value shl 8)
            0x0D -> {
                if ((value and 0x80) != 0) {
                    cia2IcrMask = cia2IcrMask or (value and 0x1F)
                } else {
                    cia2IcrMask = cia2IcrMask and (value and 0x1F).inv()
                }
            }
            0x0E -> {
                if ((value and 0x10) != 0) {
                    timerACounter = timerALatch
                }
                timerARunning = (value and 0x01) != 0
                timerAOneShot = (value and 0x08) != 0
            }
            0x0F -> {
                if ((value and 0x10) != 0) {
                    timerBCounter = timerBLatch
                }
                timerBRunning = (value and 0x01) != 0
                timerBOneShot = (value and 0x08) != 0
            }
        }
    }

    private fun pushByte(value: Int) {
        ram[0x0100 + (regSP and 0xFF)] = (value and 0xFF).toByte()
        regSP = (regSP - 1) and 0xFF
    }

    private fun pullByte(): Int {
        regSP = (regSP + 1) and 0xFF
        return ram[0x0100 + (regSP and 0xFF)].toInt() and 0xFF
    }

    private fun packStatusFlags(brkBit: Boolean): Int {
        var p = 0x20
        if (flagN) p = p or 0x80
        if (flagV) p = p or 0x40
        if (brkBit) p = p or 0x10
        if (flagD) p = p or 0x08
        if (flagI) p = p or 0x04
        if (flagZ) p = p or 0x02
        if (flagC) p = p or 0x01
        return p
    }

    private fun unpackStatusFlags(p: Int) {
        flagN = (p and 0x80) != 0
        flagV = (p and 0x40) != 0
        flagB = (p and 0x10) != 0
        flagD = (p and 0x08) != 0
        flagI = (p and 0x04) != 0
        flagZ = (p and 0x02) != 0
        flagC = (p and 0x01) != 0
    }

    private fun setNZ(value: Int): Int {
        val v = value and 0xFF
        flagZ = v == 0
        flagN = (v and 0x80) != 0
        return v
    }

    fun triggerIrq() {
        if (flagI) return
        pushByte((regPC ushr 8) and 0xFF)
        pushByte(regPC and 0xFF)
        pushByte(packStatusFlags(false))
        flagI = true
        val lo = ram[0xFFFE].toInt() and 0xFF
        val hi = ram[0xFFFF].toInt() and 0xFF
        regPC = (hi shl 8) or lo
    }

    fun triggerNmi() {
        pushByte((regPC ushr 8) and 0xFF)
        pushByte(regPC and 0xFF)
        pushByte(packStatusFlags(false))
        flagI = true
        val lo = ram[0xFFFA].toInt() and 0xFF
        val hi = ram[0xFFFB].toInt() and 0xFF
        regPC = (hi shl 8) or lo
    }

    private fun fetchByte(): Int {
        val b = readMem(regPC)
        regPC = (regPC + 1) and 0xFFFF
        return b
    }

    private fun fetchWord(): Int {
        val lo = fetchByte()
        val hi = fetchByte()
        return (hi shl 8) or lo
    }

    private fun addrZp(): Int = fetchByte()
    private fun addrZpX(): Int = (fetchByte() + regX) and 0xFF
    private fun addrZpY(): Int = (fetchByte() + regY) and 0xFF
    private fun addrAbs(): Int = fetchWord()
    private fun addrAbsX(): Int = (fetchWord() + regX) and 0xFFFF
    private fun addrAbsY(): Int = (fetchWord() + regY) and 0xFFFF

    private fun addrIndX(): Int {
        val zp = (fetchByte() + regX) and 0xFF
        val lo = ram[zp].toInt() and 0xFF
        val hi = ram[(zp + 1) and 0xFF].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    private fun addrIndY(): Int {
        val zp = fetchByte()
        val lo = ram[zp].toInt() and 0xFF
        val hi = ram[(zp + 1) and 0xFF].toInt() and 0xFF
        return (((hi shl 8) or lo) + regY) and 0xFFFF
    }

    private fun doAdc(value: Int) {
        val v = value and 0xFF
        val carryIn = if (flagC) 1 else 0
        if (flagD) {
            var lo = (regA and 0x0F) + (v and 0x0F) + carryIn
            var hi = (regA and 0xF0) + (v and 0xF0)
            if (lo > 0x09) {
                hi += 0x10
                lo += 0x06
            }
            val binSum = regA + v + carryIn
            flagZ = (binSum and 0xFF) == 0
            flagN = (hi and 0x80) != 0
            flagV = (((regA xor binSum) and (v xor binSum) and 0x80) != 0)
            if (hi > 0x90) {
                hi += 0x60
            }
            flagC = hi > 0xFF
            regA = (hi and 0xF0) or (lo and 0x0F)
        } else {
            val sum = regA + v + carryIn
            flagC = sum > 0xFF
            flagV = ((regA xor sum) and (v xor sum) and 0x80) != 0
            regA = setNZ(sum)
        }
    }

    private fun doSbc(value: Int) {
        val v = value and 0xFF
        val borrowIn = if (flagC) 0 else 1
        if (flagD) {
            val binDiff = regA - v - borrowIn
            var lo = (regA and 0x0F) - (v and 0x0F) - borrowIn
            var hi = (regA and 0xF0) - (v and 0xF0)
            if ((lo and 0x10) != 0) {
                lo -= 0x06
                hi -= 0x10
            }
            if ((hi and 0x100) != 0) {
                hi -= 0x60
            }
            flagC = binDiff >= 0
            flagV = ((regA xor binDiff) and (regA xor v) and 0x80) != 0
            setNZ(binDiff)
            regA = (hi and 0xF0) or (lo and 0x0F)
        } else {
            doAdc(v xor 0xFF)
        }
    }

    private fun doCmp(reg: Int, value: Int) {
        val v = value and 0xFF
        val diff = reg - v
        flagC = reg >= v
        setNZ(diff)
    }

    private fun doBranch(condition: Boolean): Int {
        val offset = fetchByte().toByte().toInt()
        return if (condition) {
            regPC = (regPC + offset) and 0xFFFF
            3
        } else {
            2
        }
    }

    private fun executeSingleInstruction(): Int {
        val opcode = fetchByte()
        when (opcode) {
            0x00 -> {
                regPC = (regPC + 1) and 0xFFFF
                pushByte((regPC ushr 8) and 0xFF)
                pushByte(regPC and 0xFF)
                pushByte(packStatusFlags(true))
                flagI = true
                val lo = ram[0xFFFE].toInt() and 0xFF
                val hi = ram[0xFFFF].toInt() and 0xFF
                regPC = (hi shl 8) or lo
                return 7
            }
            0x01 -> { regA = setNZ(regA or readMem(addrIndX())); return 6 }
            0x05 -> { regA = setNZ(regA or readMem(addrZp())); return 3 }
            0x06 -> {
                val a = addrZp()
                val v = readMem(a)
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ(v shl 1))
                return 5
            }
            0x08 -> { pushByte(packStatusFlags(true)); return 3 }
            0x09 -> { regA = setNZ(regA or fetchByte()); return 2 }
            0x0A -> {
                flagC = (regA and 0x80) != 0
                regA = setNZ(regA shl 1)
                return 2
            }
            0x0D -> { regA = setNZ(regA or readMem(addrAbs())); return 4 }
            0x0E -> {
                val a = addrAbs()
                val v = readMem(a)
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ(v shl 1))
                return 6
            }
            0x10 -> return doBranch(!flagN)
            0x11 -> { regA = setNZ(regA or readMem(addrIndY())); return 5 }
            0x15 -> { regA = setNZ(regA or readMem(addrZpX())); return 4 }
            0x16 -> {
                val a = addrZpX()
                val v = readMem(a)
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ(v shl 1))
                return 6
            }
            0x18 -> { flagC = false; return 2 }
            0x19 -> { regA = setNZ(regA or readMem(addrAbsY())); return 4 }
            0x1D -> { regA = setNZ(regA or readMem(addrAbsX())); return 4 }
            0x1E -> {
                val a = addrAbsX()
                val v = readMem(a)
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ(v shl 1))
                return 7
            }
            0x20 -> {
                val target = addrAbs()
                val ret = (regPC - 1) and 0xFFFF
                pushByte((ret ushr 8) and 0xFF)
                pushByte(ret and 0xFF)
                regPC = target
                return 6
            }
            0x21 -> { regA = setNZ(regA and readMem(addrIndX())); return 6 }
            0x24 -> {
                val v = readMem(addrZp())
                flagZ = (regA and v) == 0
                flagN = (v and 0x80) != 0
                flagV = (v and 0x40) != 0
                return 3
            }
            0x25 -> { regA = setNZ(regA and readMem(addrZp())); return 3 }
            0x26 -> {
                val a = addrZp()
                val v = readMem(a)
                val c = if (flagC) 1 else 0
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ((v shl 1) or c))
                return 5
            }
            0x28 -> { unpackStatusFlags(pullByte()); return 4 }
            0x29 -> { regA = setNZ(regA and fetchByte()); return 2 }
            0x2A -> {
                val c = if (flagC) 1 else 0
                flagC = (regA and 0x80) != 0
                regA = setNZ((regA shl 1) or c)
                return 2
            }
            0x2C -> {
                val v = readMem(addrAbs())
                flagZ = (regA and v) == 0
                flagN = (v and 0x80) != 0
                flagV = (v and 0x40) != 0
                return 4
            }
            0x2D -> { regA = setNZ(regA and readMem(addrAbs())); return 4 }
            0x2E -> {
                val a = addrAbs()
                val v = readMem(a)
                val c = if (flagC) 1 else 0
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ((v shl 1) or c))
                return 6
            }
            0x30 -> return doBranch(flagN)
            0x31 -> { regA = setNZ(regA and readMem(addrIndY())); return 5 }
            0x35 -> { regA = setNZ(regA and readMem(addrZpX())); return 4 }
            0x36 -> {
                val a = addrZpX()
                val v = readMem(a)
                val c = if (flagC) 1 else 0
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ((v shl 1) or c))
                return 6
            }
            0x38 -> { flagC = true; return 2 }
            0x39 -> { regA = setNZ(regA and readMem(addrAbsY())); return 4 }
            0x3D -> { regA = setNZ(regA and readMem(addrAbsX())); return 4 }
            0x3E -> {
                val a = addrAbsX()
                val v = readMem(a)
                val c = if (flagC) 1 else 0
                flagC = (v and 0x80) != 0
                writeMem(a, setNZ((v shl 1) or c))
                return 7
            }
            0x40 -> {
                unpackStatusFlags(pullByte())
                val lo = pullByte()
                val hi = pullByte()
                regPC = (hi shl 8) or lo
                return 6
            }
            0x41 -> { regA = setNZ(regA xor readMem(addrIndX())); return 6 }
            0x45 -> { regA = setNZ(regA xor readMem(addrZp())); return 3 }
            0x46 -> {
                val a = addrZp()
                val v = readMem(a)
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ(v ushr 1))
                return 5
            }
            0x48 -> { pushByte(regA); return 3 }
            0x49 -> { regA = setNZ(regA xor fetchByte()); return 2 }
            0x4A -> {
                flagC = (regA and 0x01) != 0
                regA = setNZ(regA ushr 1)
                return 2
            }
            0x4C -> { regPC = addrAbs(); return 3 }
            0x4D -> { regA = setNZ(regA xor readMem(addrAbs())); return 4 }
            0x4E -> {
                val a = addrAbs()
                val v = readMem(a)
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ(v ushr 1))
                return 6
            }
            0x50 -> return doBranch(!flagV)
            0x51 -> { regA = setNZ(regA xor readMem(addrIndY())); return 5 }
            0x55 -> { regA = setNZ(regA xor readMem(addrZpX())); return 4 }
            0x56 -> {
                val a = addrZpX()
                val v = readMem(a)
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ(v ushr 1))
                return 6
            }
            0x58 -> { flagI = false; return 2 }
            0x59 -> { regA = setNZ(regA xor readMem(addrAbsY())); return 4 }
            0x5D -> { regA = setNZ(regA xor readMem(addrAbsX())); return 4 }
            0x5E -> {
                val a = addrAbsX()
                val v = readMem(a)
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ(v ushr 1))
                return 7
            }
            0x60 -> {
                val lo = pullByte()
                val hi = pullByte()
                regPC = (((hi shl 8) or lo) + 1) and 0xFFFF
                return 6
            }
            0x61 -> { doAdc(readMem(addrIndX())); return 6 }
            0x65 -> { doAdc(readMem(addrZp())); return 3 }
            0x66 -> {
                val a = addrZp()
                val v = readMem(a)
                val c = if (flagC) 0x80 else 0
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ((v ushr 1) or c))
                return 5
            }
            0x68 -> { regA = setNZ(pullByte()); return 4 }
            0x69 -> { doAdc(fetchByte()); return 2 }
            0x6A -> {
                val c = if (flagC) 0x80 else 0
                flagC = (regA and 0x01) != 0
                regA = setNZ((regA ushr 1) or c)
                return 2
            }
            0x6C -> {
                val ptr = addrAbs()
                val lo = readMem(ptr)
                val hiAddr = (ptr and 0xFF00) or ((ptr + 1) and 0x00FF)
                val hi = readMem(hiAddr)
                regPC = (hi shl 8) or lo
                return 5
            }
            0x6D -> { doAdc(readMem(addrAbs())); return 4 }
            0x6E -> {
                val a = addrAbs()
                val v = readMem(a)
                val c = if (flagC) 0x80 else 0
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ((v ushr 1) or c))
                return 6
            }
            0x70 -> return doBranch(flagV)
            0x71 -> { doAdc(readMem(addrIndY())); return 5 }
            0x75 -> { doAdc(readMem(addrZpX())); return 4 }
            0x76 -> {
                val a = addrZpX()
                val v = readMem(a)
                val c = if (flagC) 0x80 else 0
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ((v ushr 1) or c))
                return 6
            }
            0x78 -> { flagI = true; return 2 }
            0x79 -> { doAdc(readMem(addrAbsY())); return 4 }
            0x7D -> { doAdc(readMem(addrAbsX())); return 4 }
            0x7E -> {
                val a = addrAbsX()
                val v = readMem(a)
                val c = if (flagC) 0x80 else 0
                flagC = (v and 0x01) != 0
                writeMem(a, setNZ((v ushr 1) or c))
                return 7
            }
            0x81 -> { writeMem(addrIndX(), regA); return 6 }
            0x84 -> { writeMem(addrZp(), regY); return 3 }
            0x85 -> { writeMem(addrZp(), regA); return 3 }
            0x86 -> { writeMem(addrZp(), regX); return 3 }
            0x88 -> { regY = setNZ(regY - 1); return 2 }
            0x8A -> { regA = setNZ(regX); return 2 }
            0x8C -> { writeMem(addrAbs(), regY); return 4 }
            0x8D -> { writeMem(addrAbs(), regA); return 4 }
            0x8E -> { writeMem(addrAbs(), regX); return 4 }
            0x90 -> return doBranch(!flagC)
            0x91 -> { writeMem(addrIndY(), regA); return 6 }
            0x94 -> { writeMem(addrZpX(), regY); return 4 }
            0x95 -> { writeMem(addrZpX(), regA); return 4 }
            0x96 -> { writeMem(addrZpY(), regX); return 4 }
            0x98 -> { regA = setNZ(regY); return 2 }
            0x99 -> { writeMem(addrAbsY(), regA); return 5 }
            0x9A -> { regSP = regX and 0xFF; return 2 }
            0x9D -> { writeMem(addrAbsX(), regA); return 5 }
            0xA0 -> { regY = setNZ(fetchByte()); return 2 }
            0xA1 -> { regA = setNZ(readMem(addrIndX())); return 6 }
            0xA2 -> { regX = setNZ(fetchByte()); return 2 }
            0xA4 -> { regY = setNZ(readMem(addrZp())); return 3 }
            0xA5 -> { regA = setNZ(readMem(addrZp())); return 3 }
            0xA6 -> { regX = setNZ(readMem(addrZp())); return 3 }
            0xA8 -> { regY = setNZ(regA); return 2 }
            0xA9 -> { regA = setNZ(fetchByte()); return 2 }
            0xAA -> { regX = setNZ(regA); return 2 }
            0xAC -> { regY = setNZ(readMem(addrAbs())); return 4 }
            0xAD -> { regA = setNZ(readMem(addrAbs())); return 4 }
            0xAE -> { regX = setNZ(readMem(addrAbs())); return 4 }
            0xB0 -> return doBranch(flagC)
            0xB1 -> { regA = setNZ(readMem(addrIndY())); return 5 }
            0xB4 -> { regY = setNZ(readMem(addrZpX())); return 4 }
            0xB5 -> { regA = setNZ(readMem(addrZpX())); return 4 }
            0xB6 -> { regX = setNZ(readMem(addrZpY())); return 4 }
            0xB8 -> { flagV = false; return 2 }
            0xB9 -> { regA = setNZ(readMem(addrAbsY())); return 4 }
            0xBA -> { regX = setNZ(regSP); return 2 }
            0xBC -> { regY = setNZ(readMem(addrAbsX())); return 4 }
            0xBD -> { regA = setNZ(readMem(addrAbsX())); return 4 }
            0xBE -> { regX = setNZ(readMem(addrAbsY())); return 4 }
            0xC0 -> { doCmp(regY, fetchByte()); return 2 }
            0xC1 -> { doCmp(regA, readMem(addrIndX())); return 6 }
            0xC4 -> { doCmp(regY, readMem(addrZp())); return 3 }
            0xC5 -> { doCmp(regA, readMem(addrZp())); return 3 }
            0xC6 -> {
                val a = addrZp()
                writeMem(a, setNZ(readMem(a) - 1))
                return 5
            }
            0xC8 -> { regY = setNZ(regY + 1); return 2 }
            0xC9 -> { doCmp(regA, fetchByte()); return 2 }
            0xCA -> { regX = setNZ(regX - 1); return 2 }
            0xCC -> { doCmp(regY, readMem(addrAbs())); return 4 }
            0xCD -> { doCmp(regA, readMem(addrAbs())); return 4 }
            0xCE -> {
                val a = addrAbs()
                writeMem(a, setNZ(readMem(a) - 1))
                return 6
            }
            0xD0 -> return doBranch(!flagZ)
            0xD1 -> { doCmp(regA, readMem(addrIndY())); return 5 }
            0xD5 -> { doCmp(regA, readMem(addrZpX())); return 4 }
            0xD6 -> {
                val a = addrZpX()
                writeMem(a, setNZ(readMem(a) - 1))
                return 6
            }
            0xD8 -> { flagD = false; return 2 }
            0xD9 -> { doCmp(regA, readMem(addrAbsY())); return 4 }
            0xDD -> { doCmp(regA, readMem(addrAbsX())); return 4 }
            0xDE -> {
                val a = addrAbsX()
                writeMem(a, setNZ(readMem(a) - 1))
                return 7
            }
            0xE0 -> { doCmp(regX, fetchByte()); return 2 }
            0xE1 -> { doSbc(readMem(addrIndX())); return 6 }
            0xE4 -> { doCmp(regX, readMem(addrZp())); return 3 }
            0xE5 -> { doSbc(readMem(addrZp())); return 3 }
            0xE6 -> {
                val a = addrZp()
                writeMem(a, setNZ(readMem(a) + 1))
                return 5
            }
            0xE8 -> { regX = setNZ(regX + 1); return 2 }
            0xE9 -> { doSbc(fetchByte()); return 2 }
            0xEA -> return 2
            0xEC -> { doCmp(regX, readMem(addrAbs())); return 4 }
            0xED -> { doSbc(readMem(addrAbs())); return 4 }
            0xEE -> {
                val a = addrAbs()
                writeMem(a, setNZ(readMem(a) + 1))
                return 6
            }
            0xF0 -> return doBranch(flagZ)
            0xF1 -> { doSbc(readMem(addrIndY())); return 5 }
            0xF5 -> { doSbc(readMem(addrZpX())); return 4 }
            0xF6 -> {
                val a = addrZpX()
                writeMem(a, setNZ(readMem(a) + 1))
                return 6
            }
            0xF8 -> { flagD = true; return 2 }
            0xF9 -> { doSbc(readMem(addrAbsY())); return 4 }
            0xFD -> { doSbc(readMem(addrAbsX())); return 4 }
            0xFE -> {
                val a = addrAbsX()
                writeMem(a, setNZ(readMem(a) + 1))
                return 7
            }
            0x04, 0x44, 0x64 -> { fetchByte(); return 3 }
            0x0C -> { fetchWord(); return 4 }
            else -> return 2
        }
    }
}
