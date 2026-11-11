package com.example.modding

import kotlin.math.abs
import kotlin.math.max

class ModCanvasRenderer(
    private val framePixels: IntArray,
    private val assetRouter: ModAssetRouter
) {

    fun drawPixel(x: Int, y: Int, color: Int) {
        if (x !in 0 until 320 || y !in 0 until 200) return
        val idx = y * 320 + x
        framePixels[idx] = blendArgb(framePixels[idx], normalizeColor(color))
    }

    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int, filled: Boolean) {
        if (width <= 0 || height <= 0) return
        val argb = normalizeColor(color)
        if (filled) {
            val x0 = x.coerceIn(0, 319)
            val x1 = (x + width - 1).coerceIn(0, 319)
            val y0 = y.coerceIn(0, 199)
            val y1 = (y + height - 1).coerceIn(0, 199)
            if (x > 319 || x + width <= 0 || y > 199 || y + height <= 0) return
            for (py in y0..y1) {
                val rowOff = py * 320
                for (px in x0..x1) {
                    framePixels[rowOff + px] = blendArgb(framePixels[rowOff + px], argb)
                }
            }
        } else {
            drawLine(x, y, x + width - 1, y, argb)
            drawLine(x, y + height - 1, x + width - 1, y + height - 1, argb)
            drawLine(x, y, x, y + height - 1, argb)
            drawLine(x + width - 1, y, x + width - 1, y + height - 1, argb)
        }
    }

    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val argb = normalizeColor(color)
        var cx = x1
        var cy = y1
        val dx = abs(x2 - x1)
        val dy = -abs(y2 - y1)
        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx + dy
        var steps = 0
        while (steps < 1024) {
            if (cx in 0 until 320 && cy in 0 until 200) {
                val idx = cy * 320 + cx
                framePixels[idx] = blendArgb(framePixels[idx], argb)
            }
            if (cx == x2 && cy == y2) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                cx += sx
            }
            if (e2 <= dx) {
                err += dx
                cy += sy
            }
            steps++
        }
    }

    fun drawText(x: Int, y: Int, text: String, color: Int, scale: Int = 1) {
        val s = max(1, scale)
        val argb = normalizeColor(color)
        var cursorX = x
        var cursorY = y
        for (ch in text) {
            if (ch == '\n') {
                cursorX = x
                cursorY += 8 * s
                continue
            }
            val glyph = glyphForChar(ch)
            for (row in 0 until 8) {
                val rowBits = glyph[row]
                if (rowBits == 0) continue
                for (col in 0 until 8) {
                    if (((rowBits ushr (7 - col)) and 0x01) != 0) {
                        for (sy in 0 until s) {
                            val py = cursorY + row * s + sy
                            if (py !in 0 until 200) continue
                            val rowOff = py * 320
                            for (sx in 0 until s) {
                                val px = cursorX + col * s + sx
                                if (px in 0 until 320) {
                                    framePixels[rowOff + px] = blendArgb(framePixels[rowOff + px], argb)
                                }
                            }
                        }
                    }
                }
            }
            cursorX += 8 * s
        }
    }

    fun blitPng(
        modPngPath: String,
        dstX: Int,
        dstY: Int,
        srcX: Int,
        srcY: Int,
        width: Int,
        height: Int,
        scale: Int = 1
    ) {
        val img = assetRouter.loadDecodedPng(modPngPath) ?: return
        val s = max(1, scale)
        val w = if (width <= 0) img.width - srcX else width
        val h = if (height <= 0) img.height - srcY else height
        if (w <= 0 || h <= 0) return

        for (ry in 0 until h) {
            val iy = srcY + ry
            if (iy !in 0 until img.height) continue
            for (rx in 0 until w) {
                val ix = srcX + rx
                if (ix !in 0 until img.width) continue
                val srcArgb = img.argbPixels[iy * img.width + ix]
                val alpha = (srcArgb ushr 24) and 0xFF
                if (alpha == 0) continue
                for (sy in 0 until s) {
                    val py = dstY + ry * s + sy
                    if (py !in 0 until 200) continue
                    val rowOff = py * 320
                    for (sx in 0 until s) {
                        val px = dstX + rx * s + sx
                        if (px in 0 until 320) {
                            framePixels[rowOff + px] = blendArgb(framePixels[rowOff + px], srcArgb)
                        }
                    }
                }
            }
        }
    }

    private fun normalizeColor(c: Int): Int {
        val alpha = (c ushr 24) and 0xFF
        return if (alpha == 0 && (c and 0xFFFFFF) != 0) {
            c or (0xFF shl 24)
        } else if (c == 0) {
            0xFF000000.toInt()
        } else {
            c
        }
    }

    private fun blendArgb(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa >= 255) return src or (0xFF shl 24)
        if (sa <= 0) return dst
        val invA = 255 - sa
        val sr = (src ushr 16) and 0xFF
        val sg = (src ushr 8) and 0xFF
        val sb = src and 0xFF
        val dr = (dst ushr 16) and 0xFF
        val dg = (dst ushr 8) and 0xFF
        val db = dst and 0xFF
        val r = (sr * sa + dr * invA) / 255
        val g = (sg * sa + dg * invA) / 255
        val b = (sb * sa + db * invA) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun glyphForChar(ch: Char): IntArray {
        return when (ch.uppercaseChar()) {
            '0' -> intArrayOf(0x3C, 0x66, 0x6E, 0x76, 0x66, 0x66, 0x3C, 0x00)
            '1' -> intArrayOf(0x18, 0x38, 0x18, 0x18, 0x18, 0x18, 0x7E, 0x00)
            '2' -> intArrayOf(0x3C, 0x66, 0x06, 0x1C, 0x30, 0x66, 0x7E, 0x00)
            '3' -> intArrayOf(0x3C, 0x66, 0x06, 0x1C, 0x06, 0x66, 0x3C, 0x00)
            '4' -> intArrayOf(0x0C, 0x1C, 0x3C, 0x6C, 0x7E, 0x0C, 0x0C, 0x00)
            '5' -> intArrayOf(0x7E, 0x60, 0x7C, 0x06, 0x06, 0x66, 0x3C, 0x00)
            '6' -> intArrayOf(0x1C, 0x30, 0x60, 0x7C, 0x66, 0x66, 0x3C, 0x00)
            '7' -> intArrayOf(0x7E, 0x66, 0x06, 0x0C, 0x18, 0x18, 0x18, 0x00)
            '8' -> intArrayOf(0x3C, 0x66, 0x66, 0x3C, 0x66, 0x66, 0x3C, 0x00)
            '9' -> intArrayOf(0x3C, 0x66, 0x66, 0x3E, 0x06, 0x0C, 0x38, 0x00)
            'A' -> intArrayOf(0x18, 0x3C, 0x66, 0x66, 0x7E, 0x66, 0x66, 0x00)
            'B' -> intArrayOf(0x7C, 0x66, 0x66, 0x7C, 0x66, 0x66, 0x7C, 0x00)
            'C' -> intArrayOf(0x3C, 0x66, 0x60, 0x60, 0x60, 0x66, 0x3C, 0x00)
            'D' -> intArrayOf(0x78, 0x6C, 0x66, 0x66, 0x66, 0x6C, 0x78, 0x00)
            'E' -> intArrayOf(0x7E, 0x60, 0x60, 0x7C, 0x60, 0x60, 0x7E, 0x00)
            'F' -> intArrayOf(0x7E, 0x60, 0x60, 0x7C, 0x60, 0x60, 0x60, 0x00)
            'G' -> intArrayOf(0x3C, 0x66, 0x60, 0x6E, 0x66, 0x66, 0x3E, 0x00)
            'H' -> intArrayOf(0x66, 0x66, 0x66, 0x7E, 0x66, 0x66, 0x66, 0x00)
            'I' -> intArrayOf(0x3C, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3C, 0x00)
            'J' -> intArrayOf(0x1E, 0x0C, 0x0C, 0x0C, 0x6C, 0x6C, 0x38, 0x00)
            'K' -> intArrayOf(0x66, 0x6C, 0x78, 0x70, 0x78, 0x6C, 0x66, 0x00)
            'L' -> intArrayOf(0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x7E, 0x00)
            'M' -> intArrayOf(0x63, 0x77, 0x7F, 0x6B, 0x63, 0x63, 0x63, 0x00)
            'N' -> intArrayOf(0x66, 0x76, 0x7E, 0x7E, 0x6E, 0x66, 0x66, 0x00)
            'O' -> intArrayOf(0x3C, 0x66, 0x66, 0x66, 0x66, 0x66, 0x3C, 0x00)
            'P' -> intArrayOf(0x7C, 0x66, 0x66, 0x7C, 0x60, 0x60, 0x60, 0x00)
            'Q' -> intArrayOf(0x3C, 0x66, 0x66, 0x66, 0x6A, 0x6C, 0x36, 0x00)
            'R' -> intArrayOf(0x7C, 0x66, 0x66, 0x7C, 0x6C, 0x66, 0x66, 0x00)
            'S' -> intArrayOf(0x3C, 0x66, 0x60, 0x3C, 0x06, 0x66, 0x3C, 0x00)
            'T' -> intArrayOf(0x7E, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x00)
            'U' -> intArrayOf(0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x3C, 0x00)
            'V' -> intArrayOf(0x66, 0x66, 0x66, 0x66, 0x66, 0x3C, 0x18, 0x00)
            'W' -> intArrayOf(0x63, 0x63, 0x63, 0x6B, 0x7F, 0x77, 0x63, 0x00)
            'X' -> intArrayOf(0x66, 0x66, 0x3C, 0x18, 0x3C, 0x66, 0x66, 0x00)
            'Y' -> intArrayOf(0x66, 0x66, 0x66, 0x3C, 0x18, 0x18, 0x18, 0x00)
            'Z' -> intArrayOf(0x7E, 0x06, 0x0C, 0x18, 0x30, 0x60, 0x7E, 0x00)
            ':' -> intArrayOf(0x00, 0x18, 0x18, 0x00, 0x18, 0x18, 0x00, 0x00)
            '-' -> intArrayOf(0x00, 0x00, 0x00, 0x7E, 0x00, 0x00, 0x00, 0x00)
            '+' -> intArrayOf(0x00, 0x18, 0x18, 0x7E, 0x18, 0x18, 0x00, 0x00)
            '.' -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00)
            ',' -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x30)
            '!' -> intArrayOf(0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x18, 0x00)
            '?' -> intArrayOf(0x3C, 0x66, 0x06, 0x0C, 0x18, 0x00, 0x18, 0x00)
            '/' -> intArrayOf(0x06, 0x0C, 0x18, 0x30, 0x60, 0x40, 0x00, 0x00)
            '(' -> intArrayOf(0x0C, 0x18, 0x30, 0x30, 0x30, 0x18, 0x0C, 0x00)
            ')' -> intArrayOf(0x30, 0x18, 0x0C, 0x0C, 0x0C, 0x18, 0x30, 0x00)
            '%' -> intArrayOf(0x62, 0x64, 0x08, 0x10, 0x26, 0x46, 0x00, 0x00)
            '_' -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7E, 0x00)
            else -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        }
    }
}
