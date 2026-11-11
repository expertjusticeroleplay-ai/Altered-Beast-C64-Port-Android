package com.example.engine

class VicIIVideoRenderer {

    val framePixels = IntArray(320 * 200)

    private val defaultPalette = intArrayOf(
        0xFF000000.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF883932.toInt(),
        0xFF67B6BD.toInt(),
        0xFF8B3F96.toInt(),
        0xFF55A049.toInt(),
        0xFF40318D.toInt(),
        0xFFBFCE72.toInt(),
        0xFF8B5429.toInt(),
        0xFF574200.toInt(),
        0xFFB86962.toInt(),
        0xFF505050.toInt(),
        0xFF787878.toInt(),
        0xFF94E089.toInt(),
        0xFF7869C4.toInt(),
        0xFF9F9F9F.toInt()
    )

    private val c64Palette = defaultPalette.copyOf()

    fun getPaletteColor(index: Int): Int {
        return c64Palette[index and 0x0F] and 0xFFFFFF
    }

    fun setPaletteColor(index: Int, rgbHex: Int) {
        c64Palette[index and 0x0F] = (rgbHex and 0xFFFFFF) or (0xFF shl 24)
    }

    fun resetPalette() {
        System.arraycopy(defaultPalette, 0, c64Palette, 0, 16)
    }

    @Volatile
    var displayAspectRatio: Float = 1.6f

    private val charLinePixels = IntArray(320)
    private val charBgMask = BooleanArray(320)
    private val bgMaskLine = BooleanArray(320)
    private val spriteActive = BooleanArray(8)
    private val spriteMcRow = IntArray(8)
    private val spriteYExpandFlipFlop = BooleanArray(8)

    private val spRenderNow = BooleanArray(8)
    private val spRowVals = IntArray(8)
    private val spRawStartX = IntArray(8)
    private val spRawEndX = IntArray(8)
    private val spIsXExpanded = BooleanArray(8)
    private val spIsMultiColor = BooleanArray(8)
    private val spBehindFg = BooleanArray(8)
    private val spColorVals = IntArray(8)
    private val spRowBitsVals = IntArray(8)
    private val spGroupRawCenter = FloatArray(8)
    private val spGroupMappedCenter = FloatArray(8)

    private var lastPlayfieldBgColor0: Int = defaultPalette[0]

    fun resetFrameState() {
        for (sp in 0 until 8) {
            spriteActive[sp] = false
            spriteMcRow[sp] = 0
            spriteYExpandFlipFlop[sp] = false
        }
    }

    fun beginScanlineSprites(rasterY: Int, vicRegs: IntArray) {
        val spriteEnable = vicRegs[0x15]
        val rY = rasterY and 0xFF
        for (sp in 0 until 8) {
            val mask = 1 shl sp
            if ((spriteEnable and mask) == 0) {
                spriteActive[sp] = false
                continue
            }
            if (!spriteActive[sp]) {
                val spY = vicRegs[sp * 2 + 1] and 0xFF
                if (rY == spY) {
                    spriteActive[sp] = true
                    spriteMcRow[sp] = 0
                    spriteYExpandFlipFlop[sp] = false
                }
            }
        }
    }

    fun onSpriteYWrite(sp: Int, newY: Int, currentRaster: Int, vicRegs: IntArray) {
        if (sp !in 0 until 8) return
        val mask = 1 shl sp
        if ((vicRegs[0x15] and mask) == 0) return
        if (!spriteActive[sp]) {
            val rY = currentRaster and 0xFF
            val ny = newY and 0xFF
            if (rY in ny..(ny + 4)) {
                spriteActive[sp] = true
                spriteMcRow[sp] = rY - ny
                spriteYExpandFlipFlop[sp] = false
            }
        }
    }

    fun endScanlineSprites(vicRegs: IntArray) {
        val yExp = vicRegs[0x17]
        for (sp in 0 until 8) {
            if (!spriteActive[sp]) continue
            val mask = 1 shl sp
            if ((yExp and mask) != 0) {
                spriteYExpandFlipFlop[sp] = !spriteYExpandFlipFlop[sp]
                if (!spriteYExpandFlipFlop[sp]) {
                    spriteMcRow[sp]++
                }
            } else {
                spriteMcRow[sp]++
            }
            if (spriteMcRow[sp] >= 21) {
                spriteActive[sp] = false
                spriteMcRow[sp] = 0
                spriteYExpandFlipFlop[sp] = false
            }
        }
    }

    fun renderScanline(
        displayY: Int,
        ram: ByteArray,
        colorRam: ByteArray,
        vicRegs: IntArray,
        cia2PortA: Int
    ) {
        if (displayY !in 0 until 200) return
        val rowOffset = displayY * 320

        val ctrl1 = vicRegs[0x11]
        val ctrl2 = vicRegs[0x16]
        val memSetup = vicRegs[0x18]
        val borderColor = c64Palette[vicRegs[0x20] and 0x0F]
        val rawBgColor0 = c64Palette[vicRegs[0x21] and 0x0F]
        var bgColor1 = c64Palette[vicRegs[0x22] and 0x0F]
        var bgColor2 = c64Palette[vicRegs[0x23] and 0x0F]

        val screenEnabled = (ctrl1 and 0x10) != 0
        if (!screenEnabled) {
            for (x in 0 until 320) {
                framePixels[rowOffset + x] = borderColor
            }
            return
        }

        val rawBankBase = (3 - (cia2PortA and 0x03)) shl 14
        val rawScreenBase = rawBankBase + (((memSetup ushr 4) and 0x0F) shl 10)
        val rawCharBase = rawBankBase + (((memSetup ushr 1) and 0x07) shl 11)
        val bitmapBase = rawBankBase + (((memSetup ushr 3) and 0x01) shl 13)

        val panelSignaturePresent = (ram[0xB400 + 20 * 40 + 6].toInt() and 0xFF) == 0x61 &&
            (ram[0xB400 + 20 * 40 + 33].toInt() and 0xFF) == 0x7C
        val forceHudSplit = displayY >= 160 && panelSignaturePresent &&
            (rawCharBase == 0xB800 || rawBankBase == 0xC000)

        val bankBase = if (forceHudSplit) 0x8000 else rawBankBase
        val screenBase = if (forceHudSplit) 0xB400 else rawScreenBase
        val charBase = if (forceHudSplit) 0xB800 else rawCharBase

        if (displayY in 0..158 && charBase != 0xB800) {
            lastPlayfieldBgColor0 = rawBgColor0
        }
        val bgColor0 = if (displayY == 159 && charBase != 0xB800 && (vicRegs[0x21] and 0x0F) == 0) {
            lastPlayfieldBgColor0
        } else if (forceHudSplit) {
            c64Palette[0]
        } else {
            rawBgColor0
        }

        if (forceHudSplit) {
            bgColor1 = c64Palette[11]
            bgColor2 = c64Palette[12]
        }

        val yScroll = if (forceHudSplit) 3 else (ctrl1 and 0x07)
        val xScroll = ctrl2 and 0x07
        val is40Cols = (ctrl2 and 0x08) != 0
        val bitmapMode = if (forceHudSplit) false else ((ctrl1 and 0x20) != 0)
        val multiColorMode = if (forceHudSplit) true else ((ctrl2 and 0x10) != 0)

        val adjustedY = (displayY - (yScroll - 3)).coerceIn(0, 199)
        val charRow = adjustedY ushr 3
        val lineInChar = adjustedY and 0x07
        val rowScreenIndex = charRow * 40

        val isHudPanel = !bitmapMode && charBase == 0xB800 && charRow in 20..24
        val effectiveXScroll = if (isHudPanel) 0 else xScroll
        val hudWingBgColor = c64Palette[11]
        val initialLineColor = if (isHudPanel) hudWingBgColor else bgColor0

        for (x in 0 until 320) {
            charLinePixels[x] = initialLineColor
            charBgMask[x] = false
        }

        for (col in 0 until 40) {
            val screenAddr = (screenBase + rowScreenIndex + col) and 0xFFFF
            var screenCode = ram[screenAddr].toInt() and 0xFF
            var colorNibble = colorRam[(rowScreenIndex + col) and 0x03FF].toInt() and 0x0F
            val baseX = col * 8 + effectiveXScroll

            if (isHudPanel) {
                val hudRowIdx = charRow - 20
                if (col == 0 && screenCode == 0x0A) {
                    screenCode = when (hudRowIdx) {
                        0 -> 0x60
                        1 -> 0x80
                        2 -> 0xA0
                        3 -> 0xC0
                        else -> 0xE0
                    }
                    colorNibble = 0x09
                } else if (col in 1..5 && (screenCode == 0x0A || col == 5)) {
                    screenCode = when (hudRowIdx) {
                        0 -> 0x63
                        1 -> 0x83
                        2 -> 0xA3
                        3 -> 0xC3
                        else -> 0xE3
                    }
                    colorNibble = 0x09
                } else if (col in 34..38 && (screenCode == 0x0A || col == 34)) {
                    screenCode = when (hudRowIdx) {
                        0 -> 0x63
                        1 -> 0x83
                        2 -> 0xA3
                        3 -> 0xC3
                        else -> 0xE3
                    }
                    colorNibble = 0x09
                } else if (col == 39 && screenCode == 0x0A) {
                    screenCode = when (hudRowIdx) {
                        0 -> 0x7D
                        1 -> 0x9D
                        2 -> 0xBD
                        3 -> 0xDD
                        else -> 0xFD
                    }
                    colorNibble = 0x09
                }
            }

            if (bitmapMode) {
                val bmpAddr = (bitmapBase + rowScreenIndex * 8 + col * 8 + lineInChar) and 0xFFFF
                val bmpByte = ram[bmpAddr].toInt() and 0xFF
                if (multiColorMode) {
                    val col1 = c64Palette[(screenCode ushr 4) and 0x0F]
                    val col2 = c64Palette[screenCode and 0x0F]
                    val col3 = c64Palette[colorNibble]
                    for (pair in 0 until 4) {
                        val bits = (bmpByte ushr (6 - pair * 2)) and 0x03
                        val pxColor = when (bits) {
                            1 -> col1
                            2 -> col2
                            3 -> col3
                            else -> bgColor0
                        }
                        val isFg = bits >= 2
                        val px0 = baseX + pair * 2
                        val px1 = px0 + 1
                        if (px0 in 0 until 320) {
                            charLinePixels[px0] = pxColor
                            charBgMask[px0] = isFg
                        }
                        if (px1 in 0 until 320) {
                            charLinePixels[px1] = pxColor
                            charBgMask[px1] = isFg
                        }
                    }
                } else {
                    val fgCol = c64Palette[(screenCode ushr 4) and 0x0F]
                    val bgCol = c64Palette[screenCode and 0x0F]
                    for (bit in 0 until 8) {
                        val isSet = ((bmpByte ushr (7 - bit)) and 0x01) != 0
                        val px = baseX + bit
                        if (px in 0 until 320) {
                            charLinePixels[px] = if (isSet) fgCol else bgCol
                            charBgMask[px] = isSet
                        }
                    }
                }
            } else {
                val charAddr = (charBase + screenCode * 8 + lineInChar) and 0xFFFF
                val charByte = ram[charAddr].toInt() and 0xFF
                val cellBgColor0 = if (isHudPanel && (col <= 5 || col >= 34)) hudWingBgColor else bgColor0
                if (multiColorMode && (colorNibble and 0x08) != 0) {
                    val col3 = c64Palette[colorNibble and 0x07]
                    for (pair in 0 until 4) {
                        val bits = (charByte ushr (6 - pair * 2)) and 0x03
                        val pxColor = when (bits) {
                            1 -> bgColor1
                            2 -> bgColor2
                            3 -> col3
                            else -> cellBgColor0
                        }
                        val isFg = bits >= 2
                        val px0 = baseX + pair * 2
                        val px1 = px0 + 1
                        if (px0 in 0 until 320) {
                            charLinePixels[px0] = pxColor
                            charBgMask[px0] = isFg
                        }
                        if (px1 in 0 until 320) {
                            charLinePixels[px1] = pxColor
                            charBgMask[px1] = isFg
                        }
                    }
                } else {
                    val fgCol = c64Palette[colorNibble]
                    for (bit in 0 until 8) {
                        val isSet = ((charByte ushr (7 - bit)) and 0x01) != 0
                        val px = baseX + bit
                        if (px in 0 until 320) {
                            charLinePixels[px] = if (isSet) fgCol else cellBgColor0
                            charBgMask[px] = isSet
                        }
                    }
                }
            }
        }

        val resample38Cols = !is40Cols && !isHudPanel
        if (resample38Cols) {
            for (dstX in 0 until 320) {
                val srcX = 7 + (dstX * 304) / 320
                framePixels[rowOffset + dstX] = charLinePixels[srcX]
                bgMaskLine[dstX] = charBgMask[srcX]
            }
        } else {
            for (x in 0 until 320) {
                framePixels[rowOffset + x] = charLinePixels[x]
                bgMaskLine[x] = charBgMask[x]
            }
        }

        val spriteEnable = vicRegs[0x15]
        if (spriteEnable != 0) {
            val rasterY = displayY + 50
            val msbX = vicRegs[0x10]
            val bgPri = vicRegs[0x1B]
            val multiColSprites = vicRegs[0x1C]
            val xExp = vicRegs[0x1D]
            val multiColor0 = c64Palette[vicRegs[0x25] and 0x0F]
            val multiColor1 = c64Palette[vicRegs[0x26] and 0x0F]
            val spritePtrBase = (screenBase + 0x03F8) and 0xFFFF

            for (sp in 0 until 8) {
                spRenderNow[sp] = false
                val mask = 1 shl sp
                if ((spriteEnable and mask) == 0) continue

                val spY = vicRegs[sp * 2 + 1] and 0xFF
                if (!spriteActive[sp]) {
                    if ((rasterY and 0xFF) == spY) {
                        spriteActive[sp] = true
                        spriteMcRow[sp] = 0
                        spriteYExpandFlipFlop[sp] = false
                    } else {
                        continue
                    }
                }

                if (isHudPanel && spY < 210) continue

                val spRow = spriteMcRow[sp]
                if (spRow !in 0 until 21) continue
                val spXRaw = (vicRegs[sp * 2] and 0xFF) or (if ((msbX and mask) != 0) 0x100 else 0)
                var rawScreenX = spXRaw - 24
                if (isHudPanel && sp in 4..7 && rawScreenX >= 280) {
                    rawScreenX -= 10
                }
                val isXExpanded = (xExp and mask) != 0
                val rawWidth = if (isXExpanded) 48 else 24
                val isMultiColor = (multiColSprites and mask) != 0
                val behindFg = (bgPri and mask) != 0
                val spColor = c64Palette[vicRegs[0x27 + sp] and 0x0F]

                val ptrBlock = ram[(spritePtrBase + sp) and 0xFFFF].toInt() and 0xFF
                val dataAddr = (bankBase + ptrBlock * 64 + spRow * 3) and 0xFFFF
                val b0 = ram[dataAddr].toInt() and 0xFF
                val b1 = ram[(dataAddr + 1) and 0xFFFF].toInt() and 0xFF
                val b2 = ram[(dataAddr + 2) and 0xFFFF].toInt() and 0xFF
                val rowBits = (b0 shl 16) or (b1 shl 8) or b2

                spRenderNow[sp] = true
                spRowVals[sp] = spRow
                spRawStartX[sp] = rawScreenX
                spRawEndX[sp] = rawScreenX + rawWidth
                spIsXExpanded[sp] = isXExpanded
                spIsMultiColor[sp] = isMultiColor
                spBehindFg[sp] = behindFg
                spColorVals[sp] = spColor
                spRowBitsVals[sp] = rowBits
            }

            var s = 0
            while (s < 8) {
                if (!spRenderNow[s]) {
                    s++
                    continue
                }
                var groupEnd = s
                if (!is40Cols || isHudPanel) {
                    if ((s and 1) == 0 && s + 1 < 8 && spRenderNow[s + 1] &&
                        spRowVals[s] == spRowVals[s + 1] &&
                        spIsXExpanded[s] == spIsXExpanded[s + 1] &&
                        kotlin.math.abs(spRawEndX[s] - spRawStartX[s + 1]) <= 1
                    ) {
                        groupEnd = s + 1
                    }
                } else {
                    while (groupEnd + 1 < 8 && spRenderNow[groupEnd + 1] &&
                        spRowVals[groupEnd] == spRowVals[groupEnd + 1] &&
                        spIsXExpanded[groupEnd] == spIsXExpanded[groupEnd + 1] &&
                        kotlin.math.abs(spRawEndX[groupEnd] - spRawStartX[groupEnd + 1]) <= 1
                    ) {
                        groupEnd++
                    }
                }

                val gRawStart = spRawStartX[s]
                val gRawEnd = spRawEndX[groupEnd]
                val gRawCenter = (gRawStart + gRawEnd) * 0.5f
                val gMappedCenter = if (resample38Cols) {
                    (gRawCenter - 7f) * 320f / 304f
                } else {
                    gRawCenter
                }
                for (k in s..groupEnd) {
                    spGroupRawCenter[k] = gRawCenter
                    spGroupMappedCenter[k] = gMappedCenter
                }
                s = groupEnd + 1
            }

            val aspect = displayAspectRatio.coerceIn(1.2f, 2.6f)
            val pixelAspect = (aspect / 1.6f).coerceIn(1.0f, 1.45f)

            for (sp in 7 downTo 0) {
                if (!spRenderNow[sp]) continue
                val rowBits = spRowBitsVals[sp]
                if (rowBits == 0) continue

                val rawStart = spRawStartX[sp]
                val rawWidth = spRawEndX[sp] - rawStart
                val isXExpanded = spIsXExpanded[sp]
                val isMultiColor = spIsMultiColor[sp]
                val behindFg = spBehindFg[sp]
                val spColor = spColorVals[sp]
                val gRawCenter = spGroupRawCenter[sp]
                val gMappedCenter = spGroupMappedCenter[sp]

                val dstStartFloat = gMappedCenter + (rawStart - gRawCenter) / pixelAspect
                val dstEndFloat = gMappedCenter + (spRawEndX[sp] - gRawCenter) / pixelAspect
                val minPx = (dstStartFloat.toInt() - 1).coerceAtLeast(0)
                val maxPx = (dstEndFloat.toInt() + 1).coerceAtMost(319)

                for (px in minPx..maxPx) {
                    val sampleRawX = gRawCenter + (px + 0.5f - gMappedCenter) * pixelAspect
                    val relX = kotlin.math.floor((sampleRawX - rawStart).toDouble()).toInt()
                    if (relX !in 0 until rawWidth) continue

                    if (isMultiColor) {
                        val pairWidth = if (isXExpanded) 4 else 2
                        val pair = (relX / pairWidth).coerceIn(0, 11)
                        val bits = (rowBits ushr (22 - pair * 2)) and 0x03
                        if (bits == 0) continue
                        val pixelColor = when (bits) {
                            1 -> multiColor0
                            2 -> spColor
                            else -> multiColor1
                        }
                        if (!behindFg || !bgMaskLine[px]) {
                            framePixels[rowOffset + px] = pixelColor
                        }
                    } else {
                        val bitWidth = if (isXExpanded) 2 else 1
                        val bit = (relX / bitWidth).coerceIn(0, 23)
                        val isSet = ((rowBits ushr (23 - bit)) and 0x01) != 0
                        if (!isSet) continue
                        if (!behindFg || !bgMaskLine[px]) {
                            framePixels[rowOffset + px] = spColor
                        }
                    }
                }
            }
        }
    }
}
