package com.example.modding

import android.graphics.BitmapFactory
import com.example.engine.BinaryAssetProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import kotlin.math.max

data class DecodedModImage(
    val width: Int,
    val height: Int,
    val argbPixels: IntArray
)

class ModAssetRouter(
    private val baseProvider: BinaryAssetProvider,
    @Volatile var modsDir: File = File("/storage/emulated/0/GZBeast/mods")
) : BinaryAssetProvider {

    private val byteOverrides = ConcurrentHashMap<String, ByteArray>()
    private val fileOverrides = ConcurrentHashMap<String, String>()
    private val imageCache = ConcurrentHashMap<String, DecodedModImage>()

    private fun normalizeKey(path: String): String {
        return path.trim().replace('\\', '/').trimStart('/').uppercase()
    }

    override fun readBytes(assetPath: String): ByteArray {
        val key = normalizeKey(assetPath)

        val memBytes = byteOverrides[key]
        if (memBytes != null) {
            return memBytes.copyOf()
        }

        val redirectedRel = fileOverrides[key]
        if (redirectedRel != null) {
            val targetFile = resolveModFile(redirectedRel)
            if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                return targetFile.readBytes()
            }
        }

        val directDropIn = resolveCaseInsensitiveFile(modsDir, assetPath.trim().replace('\\', '/').trimStart('/'))
        if (directDropIn != null && directDropIn.exists() && directDropIn.isFile) {
            return directDropIn.readBytes()
        }

        return baseProvider.readBytes(assetPath)
    }

    fun registerFileOverride(internalPath: String, modRelativePath: String) {
        val key = normalizeKey(internalPath)
        fileOverrides[key] = modRelativePath.trim().replace('\\', '/').trimStart('/')
        SessionLogManager.logInfo("ASSET", "Registered file override: $key -> $modRelativePath")
    }

    fun registerByteOverride(internalPath: String, bytes: ByteArray) {
        val key = normalizeKey(internalPath)
        byteOverrides[key] = bytes.copyOf()
        SessionLogManager.logInfo("ASSET", "Registered in-memory byte override: $key (${bytes.size} bytes)")
    }

    fun clearOverrides() {
        byteOverrides.clear()
        fileOverrides.clear()
        imageCache.clear()
        SessionLogManager.logInfo("ASSET", "Cleared all asset overrides")
    }

    fun resolveModFile(modRelativePath: String): File? {
        val sanitized = modRelativePath.trim().replace('\\', '/').trimStart('/')
        if (sanitized.isEmpty() || sanitized.contains("..")) return null
        val exact = File(modsDir, sanitized)
        if (exact.exists()) return exact
        val caseInsensitive = resolveCaseInsensitiveFile(modsDir, sanitized)
        return caseInsensitive ?: exact
    }

    private fun resolveCaseInsensitiveFile(root: File, relPath: String): File? {
        if (!root.exists()) return null
        val segments = relPath.split('/').filter { it.isNotEmpty() }
        var current = root
        for (seg in segments) {
            val direct = File(current, seg)
            if (direct.exists()) {
                current = direct
                continue
            }
            val match = current.listFiles()?.firstOrNull { it.name.equals(seg, ignoreCase = true) }
                ?: return null
            current = match
        }
        return current
    }

    fun modFileExists(modRelativePath: String): Boolean {
        val f = resolveModFile(modRelativePath) ?: return false
        return f.exists() && f.isFile
    }

    fun readModFileBytes(modRelativePath: String): ByteArray? {
        val f = resolveModFile(modRelativePath) ?: return null
        if (!f.exists() || !f.isFile) return null
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun writeModFileBytes(modRelativePath: String, bytes: ByteArray): Boolean {
        val sanitized = modRelativePath.trim().replace('\\', '/').trimStart('/')
        if (sanitized.isEmpty() || sanitized.contains("..")) return false
        val target = File(modsDir, sanitized)
        return try {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            imageCache.remove(sanitized.uppercase())
            true
        } catch (t: Throwable) {
            SessionLogManager.logError("ASSET", "Failed to write mod file $sanitized", t)
            false
        }
    }

    fun readModFileText(modRelativePath: String): String? {
        val bytes = readModFileBytes(modRelativePath) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    fun writeModFileText(modRelativePath: String, content: String): Boolean {
        return writeModFileBytes(modRelativePath, content.toByteArray(Charsets.UTF_8))
    }

    fun listModFiles(modRelativeSubdir: String = ""): List<String> {
        val sanitized = modRelativeSubdir.trim().replace('\\', '/').trimStart('/')
        if (sanitized.contains("..")) return emptyList()
        val dir = if (sanitized.isEmpty()) modsDir else (resolveModFile(sanitized) ?: return emptyList())
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }

    fun loadDecodedPng(modRelativePath: String): DecodedModImage? {
        val cacheKey = modRelativePath.trim().replace('\\', '/').trimStart('/').uppercase()
        imageCache[cacheKey]?.let { return it }

        val rawBytes = readModFileBytes(modRelativePath) ?: return null
        val decoded = decodePngOrBitmapBytes(rawBytes) ?: return null
        imageCache[cacheKey] = decoded
        return decoded
    }

    private fun decodePngOrBitmapBytes(rawBytes: ByteArray): DecodedModImage? {
        val viaAndroid = runCatching {
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val w = bmp.width
                val h = bmp.height
                val px = IntArray(w * h)
                bmp.getPixels(px, 0, w, 0, 0, w, h)
                DecodedModImage(w, h, px)
            } else {
                null
            }
        }.getOrNull()
        if (viaAndroid != null) return viaAndroid

        return decodePurePngFallback(rawBytes)
    }

    private fun decodePurePngFallback(bytes: ByteArray): DecodedModImage? {
        if (bytes.size < 33) return null
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in 0 until 8) {
            if (bytes[i] != sig[i]) return null
        }
        var pos = 8
        var width = 0
        var height = 0
        var bitDepth = 8
        var colorType = 6
        val idatStream = java.io.ByteArrayOutputStream()

        while (pos + 8 <= bytes.size) {
            val len = readIntBE(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            pos += 8
            if (pos + len > bytes.size) break
            when (type) {
                "IHDR" -> {
                    width = readIntBE(bytes, pos)
                    height = readIntBE(bytes, pos + 4)
                    bitDepth = bytes[pos + 8].toInt() and 0xFF
                    colorType = bytes[pos + 9].toInt() and 0xFF
                }
                "IDAT" -> {
                    idatStream.write(bytes, pos, len)
                }
                "IEND" -> break
            }
            pos += len + 4
        }

        if (width <= 0 || height <= 0 || bitDepth != 8) return null
        val bpp = when (colorType) {
            6 -> 4
            2 -> 3
            else -> return null
        }
        val compressed = idatStream.toByteArray()
        val rawScanlines = runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        }.getOrNull() ?: return null

        val stride = width * bpp
        if (rawScanlines.size < height * (stride + 1)) return null
        val outPixels = IntArray(width * height)
        val prevLine = IntArray(stride)
        val curLine = IntArray(stride)

        var srcIdx = 0
        for (y in 0 until height) {
            val filter = rawScanlines[srcIdx++].toInt() and 0xFF
            for (i in 0 until stride) {
                val raw = rawScanlines[srcIdx++].toInt() and 0xFF
                val a = if (i >= bpp) curLine[i - bpp] else 0
                val b = prevLine[i]
                val c = if (i >= bpp) prevLine[i - bpp] else 0
                val recon = when (filter) {
                    1 -> (raw + a) and 0xFF
                    2 -> (raw + b) and 0xFF
                    3 -> (raw + ((a + b) ushr 1)) and 0xFF
                    4 -> {
                        val p = a + b - c
                        val pa = abs(p - a)
                        val pb = abs(p - b)
                        val pc = abs(p - c)
                        val pr = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                        (raw + pr) and 0xFF
                    }
                    else -> raw
                }
                curLine[i] = recon
            }
            for (x in 0 until width) {
                val base = x * bpp
                val r = curLine[base]
                val g = curLine[base + 1]
                val b = curLine[base + 2]
                val alpha = if (bpp == 4) curLine[base + 3] else 0xFF
                outPixels[y * width + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
            System.arraycopy(curLine, 0, prevLine, 0, stride)
        }
        return DecodedModImage(width, height, outPixels)
    }

    private fun readIntBE(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }

    fun convertPngToC64Sprites(
        modPngPath: String,
        bgRgb: Int,
        mc0Rgb: Int,
        spriteRgb: Int,
        mc1Rgb: Int
    ): List<ByteArray> {
        val img = loadDecodedPng(modPngPath) ?: return emptyList()
        val cols = max(1, img.width / 24)
        val rows = max(1, img.height / 21)
        val palette = intArrayOf(
            bgRgb and 0xFFFFFF,
            mc0Rgb and 0xFFFFFF,
            spriteRgb and 0xFFFFFF,
            mc1Rgb and 0xFFFFFF
        )
        val result = ArrayList<ByteArray>(cols * rows)

        for (ry in 0 until rows) {
            for (cx in 0 until cols) {
                val spriteBytes = ByteArray(64)
                val startX = cx * 24
                val startY = ry * 21
                for (row in 0 until 21) {
                    val py = startY + row
                    var rowWord = 0
                    for (pair in 0 until 12) {
                        val px = startX + pair * 2
                        val argb = if (px < img.width && py < img.height) {
                            img.argbPixels[py * img.width + px]
                        } else {
                            0
                        }
                        val alpha = (argb ushr 24) and 0xFF
                        val bits = if (alpha < 64) {
                            0
                        } else {
                            nearestColorIndex(argb and 0xFFFFFF, palette)
                        }
                        rowWord = (rowWord shl 2) or (bits and 0x03)
                    }
                    val byteOff = row * 3
                    spriteBytes[byteOff] = ((rowWord ushr 16) and 0xFF).toByte()
                    spriteBytes[byteOff + 1] = ((rowWord ushr 8) and 0xFF).toByte()
                    spriteBytes[byteOff + 2] = (rowWord and 0xFF).toByte()
                }
                result.add(spriteBytes)
            }
        }
        return result
    }

    private fun nearestColorIndex(rgb: Int, candidates: IntArray): Int {
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in candidates.indices) {
            val c = candidates[i]
            val dr = r - ((c ushr 16) and 0xFF)
            val dg = g - ((c ushr 8) and 0xFF)
            val db = b - (c and 0xFF)
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }
}
