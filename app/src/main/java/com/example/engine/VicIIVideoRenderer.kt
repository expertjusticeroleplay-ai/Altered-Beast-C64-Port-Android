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

    private val bgMaskLine = BooleanArray(320)
    private val spriteActive = BooleanArray(8)
    private val spriteMcRow = IntArray(8)
    private val spriteYExpandFlipFlop = BooleanArray(8)

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
        val bgColor0 = c64Palette[vicRegs[0x21] and 0x0F]
        val bgColor1 = c64Palette[vicRegs[0x22] and 0x0F]
        val bgColor2 = c64Palette[vicRegs[0x23] and 0x0F]

        val screenEnabled = (ctrl1 and 0x10) != 0
        if (!screenEnabled) {
            for (x in 0 until 320) {
                framePixels[rowOffset + x] = borderColor
            }
            return
        }

        val yScroll = ctrl1 and 0x07
        val xScroll = ctrl2 and 0x07
        val is40Cols = (ctrl2 and 0x08) != 0
        val is25Rows = (ctrl1 and 0x08) != 0
        val bitmapMode = (ctrl1 and 0x20) != 0
        val multiColorMode = (ctrl2 and 0x10) != 0

        if (!is25Rows && (displayY < 4 || displayY >= 196)) {
            for (x in 0 until 320) {
                framePixels[rowOffset + x] = borderColor
            }
            return
        }

        val bankBase = (3 - (cia2PortA and 0x03)) shl 14
        val screenBase = bankBase + (((memSetup ushr 4) and 0x0F) shl 10)
        val charBase = bankBase + (((memSetup ushr 1) and 0x07) shl 11)
        val bitmapBase = bankBase + (((memSetup ushr 3) and 0x01) shl 13)

        val adjustedY = (displayY - (yScroll - 3)).coerceIn(0, 199)
        val charRow = adjustedY ushr 3
        val lineInChar = adjustedY and 0x07
        val rowScreenIndex = charRow * 40

        for (x in 0 until 320) {
            framePixels[rowOffset + x] = bgColor0
            bgMaskLine[x] = false
        }

        for (col in 0 until 40) {
            val screenAddr = (screenBase + rowScreenIndex + col) and 0xFFFF
            val screenCode = ram[screenAddr].toInt() and 0xFF
            val colorNibble = colorRam[(rowScreenIndex + col) and 0x03FF].toInt() and 0x0F
            val baseX = col * 8 + xScroll

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
                            framePixels[rowOffset + px0] = pxColor
                            bgMaskLine[px0] = isFg
                        }
                        if (px1 in 0 until 320) {
                            framePixels[rowOffset + px1] = pxColor
                            bgMaskLine[px1] = isFg
                        }
                    }
                } else {
                    val fgCol = c64Palette[(screenCode ushr 4) and 0x0F]
                    val bgCol = c64Palette[screenCode and 0x0F]
                    for (bit in 0 until 8) {
                        val isSet = ((bmpByte ushr (7 - bit)) and 0x01) != 0
                        val px = baseX + bit
                        if (px in 0 until 320) {
                            framePixels[rowOffset + px] = if (isSet) fgCol else bgCol
                            bgMaskLine[px] = isSet
                        }
                    }
                }
            } else {
                val charAddr = (charBase + screenCode * 8 + lineInChar) and 0xFFFF
                val charByte = ram[charAddr].toInt() and 0xFF
                if (multiColorMode && (colorNibble and 0x08) != 0) {
                    val col3 = c64Palette[colorNibble and 0x07]
                    for (pair in 0 until 4) {
                        val bits = (charByte ushr (6 - pair * 2)) and 0x03
                        val pxColor = when (bits) {
                            1 -> bgColor1
                            2 -> bgColor2
                            3 -> col3
                            else -> bgColor0
                        }
                        val isFg = bits >= 2
                        val px0 = baseX + pair * 2
                        val px1 = px0 + 1
                        if (px0 in 0 until 320) {
                            framePixels[rowOffset + px0] = pxColor
                            bgMaskLine[px0] = isFg
                        }
                        if (px1 in 0 until 320) {
                            framePixels[rowOffset + px1] = pxColor
                            bgMaskLine[px1] = isFg
                        }
                    }
                } else {
                    val fgCol = c64Palette[colorNibble]
                    for (bit in 0 until 8) {
                        val isSet = ((charByte ushr (7 - bit)) and 0x01) != 0
                        val px = baseX + bit
                        if (px in 0 until 320) {
                            framePixels[rowOffset + px] = if (isSet) fgCol else bgColor0
                            bgMaskLine[px] = isSet
                        }
                    }
                }
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

            for (sp in 7 downTo 0) {
                val mask = 1 shl sp
                if ((spriteEnable and mask) == 0) continue

                if (!spriteActive[sp]) {
                    val spY = vicRegs[sp * 2 + 1] and 0xFF
                    if ((rasterY and 0xFF) == spY) {
                        spriteActive[sp] = true
                        spriteMcRow[sp] = 0
                        spriteYExpandFlipFlop[sp] = false
                    } else {
                        continue
                    }
                }

                val spRow = spriteMcRow[sp]
                if (spRow !in 0 until 21) continue
                val spXRaw = (vicRegs[sp * 2] and 0xFF) or (if ((msbX and mask) != 0) 0x100 else 0)
                val screenX = spXRaw - 24
                val isXExpanded = (xExp and mask) != 0
                val isMultiColor = (multiColSprites and mask) != 0
                val behindFg = (bgPri and mask) != 0
                val spColor = c64Palette[vicRegs[0x27 + sp] and 0x0F]

                val ptrBlock = ram[(spritePtrBase + sp) and 0xFFFF].toInt() and 0xFF
                val dataAddr = (bankBase + ptrBlock * 64 + spRow * 3) and 0xFFFF
                val b0 = ram[dataAddr].toInt() and 0xFF
                val b1 = ram[(dataAddr + 1) and 0xFFFF].toInt() and 0xFF
                val b2 = ram[(dataAddr + 2) and 0xFFFF].toInt() and 0xFF
                val rowBits = (b0 shl 16) or (b1 shl 8) or b2

                if (isMultiColor) {
                    val pairWidth = if (isXExpanded) 4 else 2
                    for (pair in 0 until 12) {
                        val bits = (rowBits ushr (22 - pair * 2)) and 0x03
                        if (bits == 0) continue
                        val pixelColor = when (bits) {
                            1 -> multiColor0
                            2 -> spColor
                            else -> multiColor1
                        }
                        val startX = screenX + pair * pairWidth
                        for (dx in 0 until pairWidth) {
                            val px = startX + dx
                            if (px in 0 until 320) {
                                if (!behindFg || !bgMaskLine[px]) {
                                    framePixels[rowOffset + px] = pixelColor
                                }
                            }
                        }
                    }
                } else {
                    val bitWidth = if (isXExpanded) 2 else 1
                    for (bit in 0 until 24) {
                        val isSet = ((rowBits ushr (23 - bit)) and 0x01) != 0
                        if (!isSet) continue
                        val startX = screenX + bit * bitWidth
                        for (dx in 0 until bitWidth) {
                            val px = startX + dx
                            if (px in 0 until 320) {
                                if (!behindFg || !bgMaskLine[px]) {
                                    framePixels[rowOffset + px] = spColor
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!is40Cols) {
            for (x in 0 until 7) {
                framePixels[rowOffset + x] = borderColor
            }
            for (x in 311 until 320) {
                framePixels[rowOffset + x] = borderColor
            }
        }
    }
}
