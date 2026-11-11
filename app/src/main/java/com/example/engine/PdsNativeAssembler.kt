package com.example.engine

import android.content.res.AssetManager
import java.io.ByteArrayOutputStream

class AssembledProgram(
    val memory: ByteArray,
    val symbols: Map<String, Int>
)

interface BinaryAssetProvider {
    fun readBytes(assetPath: String): ByteArray
}

class AndroidAssetProvider(private val assetManager: AssetManager) : BinaryAssetProvider {
    override fun readBytes(assetPath: String): ByteArray {
        val normalized = assetPath.trimStart('/')
        return assetManager.open(normalized).use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var read: Int
            while (input.read(chunk).also { read = it } != -1) {
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }
}

class PdsNativeAssembler(private val assetProvider: BinaryAssetProvider) {

    private data class MacroDefinition(
        val params: List<String>,
        val body: List<Pair<String, String>>
    )

    private data class DoLoopState(
        val startIndex: Int
    )

    private val addressingModes = mapOf(
        "ADC" to mapOf("IMM" to 0x69, "ZP" to 0x65, "ZPX" to 0x75, "ABS" to 0x6D, "ABSX" to 0x7D, "ABSY" to 0x79, "INDX" to 0x61, "INDY" to 0x71),
        "AND" to mapOf("IMM" to 0x29, "ZP" to 0x25, "ZPX" to 0x35, "ABS" to 0x2D, "ABSX" to 0x3D, "ABSY" to 0x39, "INDX" to 0x21, "INDY" to 0x31),
        "ASL" to mapOf("IMP" to 0x0A, "ACC" to 0x0A, "ZP" to 0x06, "ZPX" to 0x16, "ABS" to 0x0E, "ABSX" to 0x1E),
        "BCC" to mapOf("REL" to 0x90),
        "BCS" to mapOf("REL" to 0xB0),
        "BEQ" to mapOf("REL" to 0xF0),
        "BIT" to mapOf("ZP" to 0x24, "ABS" to 0x2C),
        "BMI" to mapOf("REL" to 0x30),
        "BNE" to mapOf("REL" to 0xD0),
        "BPL" to mapOf("REL" to 0x10),
        "BRK" to mapOf("IMP" to 0x00),
        "BVC" to mapOf("REL" to 0x50),
        "BVS" to mapOf("REL" to 0x70),
        "CLC" to mapOf("IMP" to 0x18),
        "CLD" to mapOf("IMP" to 0xD8),
        "CLI" to mapOf("IMP" to 0x58),
        "CLV" to mapOf("IMP" to 0xB8),
        "CMP" to mapOf("IMM" to 0xC9, "ZP" to 0xC5, "ZPX" to 0xD5, "ABS" to 0xCD, "ABSX" to 0xDD, "ABSY" to 0xD9, "INDX" to 0xC1, "INDY" to 0xD1),
        "CPX" to mapOf("IMM" to 0xE0, "ZP" to 0xE4, "ABS" to 0xEC),
        "CPY" to mapOf("IMM" to 0xC0, "ZP" to 0xC4, "ABS" to 0xCC),
        "DEC" to mapOf("ZP" to 0xC6, "ZPX" to 0xD6, "ABS" to 0xCE, "ABSX" to 0xDE),
        "DEX" to mapOf("IMP" to 0xCA),
        "DEY" to mapOf("IMP" to 0x88),
        "EOR" to mapOf("IMM" to 0x49, "ZP" to 0x45, "ZPX" to 0x55, "ABS" to 0x4D, "ABSX" to 0x5D, "ABSY" to 0x59, "INDX" to 0x41, "INDY" to 0x51),
        "INC" to mapOf("ZP" to 0xE6, "ZPX" to 0xF6, "ABS" to 0xEE, "ABSX" to 0xFE),
        "INX" to mapOf("IMP" to 0xE8),
        "INY" to mapOf("IMP" to 0xC8),
        "JMP" to mapOf("ABS" to 0x4C, "IND" to 0x6C),
        "JSR" to mapOf("ABS" to 0x20),
        "LDA" to mapOf("IMM" to 0xA9, "ZP" to 0xA5, "ZPX" to 0xB5, "ABS" to 0xAD, "ABSX" to 0xBD, "ABSY" to 0xB9, "INDX" to 0xA1, "INDY" to 0xB1),
        "LDX" to mapOf("IMM" to 0xA2, "ZP" to 0xA6, "ZPY" to 0xB6, "ABS" to 0xAE, "ABSY" to 0xBE),
        "LDY" to mapOf("IMM" to 0xA0, "ZP" to 0xA4, "ZPX" to 0xB4, "ABS" to 0xAC, "ABSX" to 0xBC),
        "LSR" to mapOf("IMP" to 0x4A, "ACC" to 0x4A, "ZP" to 0x46, "ZPX" to 0x56, "ABS" to 0x4E, "ABSX" to 0x5E),
        "NOP" to mapOf("IMP" to 0xEA),
        "ORA" to mapOf("IMM" to 0x09, "ZP" to 0x05, "ZPX" to 0x15, "ABS" to 0x0D, "ABSX" to 0x1D, "ABSY" to 0x19, "INDX" to 0x01, "INDY" to 0x11),
        "PHA" to mapOf("IMP" to 0x48),
        "PHP" to mapOf("IMP" to 0x08),
        "PLA" to mapOf("IMP" to 0x68),
        "PLP" to mapOf("IMP" to 0x28),
        "ROL" to mapOf("IMP" to 0x2A, "ACC" to 0x2A, "ZP" to 0x26, "ZPX" to 0x36, "ABS" to 0x2E, "ABSX" to 0x3E),
        "ROR" to mapOf("IMP" to 0x6A, "ACC" to 0x6A, "ZP" to 0x66, "ZPX" to 0x76, "ABS" to 0x6E, "ABSX" to 0x7E),
        "RTI" to mapOf("IMP" to 0x40),
        "RTS" to mapOf("IMP" to 0x60),
        "SBC" to mapOf("IMM" to 0xE9, "ZP" to 0xE5, "ZPX" to 0xF5, "ABS" to 0xED, "ABSX" to 0xFD, "ABSY" to 0xF9, "INDX" to 0xE1, "INDY" to 0xF1),
        "SEC" to mapOf("IMP" to 0x38),
        "SED" to mapOf("IMP" to 0xF8),
        "SEI" to mapOf("IMP" to 0x78),
        "STA" to mapOf("ZP" to 0x85, "ZPX" to 0x95, "ABS" to 0x8D, "ABSX" to 0x9D, "ABSY" to 0x99, "INDX" to 0x81, "INDY" to 0x91),
        "STX" to mapOf("ZP" to 0x86, "ZPY" to 0x96, "ABS" to 0x8E),
        "STY" to mapOf("ZP" to 0x84, "ZPX" to 0x94, "ABS" to 0x8C),
        "TAX" to mapOf("IMP" to 0xAA),
        "TAY" to mapOf("IMP" to 0xA8),
        "TSX" to mapOf("IMP" to 0xBA),
        "TXA" to mapOf("IMP" to 0x8A),
        "TXS" to mapOf("IMP" to 0x9A),
        "TYA" to mapOf("IMP" to 0x98)
    )

    private val directives = setOf(
        "ORG", "EQU", "=", "DB", "DW", "DS", "DEFM", "HEX",
        "INCLUDE", "INCBIN", "IF", "ELSE", "ENDIF", "MACRO", "ENDM",
        "DO", "UNTIL", "REPEAT", "END", "SEND"
    )

    private val incbinCache = HashMap<String, ByteArray>()

    fun readPdsSourceLines(assetPath: String): List<String> {
        val raw = assetProvider.readBytes(assetPath)
        val hasPdsHeader = raw.size >= 6 &&
            raw[0] == 0x01.toByte() &&
            raw[1] == 0x00.toByte() &&
            raw[2] == 0x05.toByte() &&
            raw[3] == 0x02.toByte() &&
            raw[4] == 0x00.toByte() &&
            raw[5] == 0x00.toByte()
        val startOffset = if (hasPdsHeader) 0x200 else 0
        var endOffset = raw.size
        if (hasPdsHeader) {
            for (i in startOffset until raw.size - 1) {
                if (raw[i] == 0x00.toByte() && raw[i + 1] == 0x93.toByte()) {
                    endOffset = i
                    break
                }
            }
        }
        val sb = StringBuilder(endOffset - startOffset)
        var idx = startOffset
        while (idx < endOffset) {
            val b = raw[idx].toInt() and 0xFF
            if (b == 0x0D) {
                sb.append('\n')
                if (idx + 1 < endOffset && (raw[idx + 1].toInt() and 0xFF) == 0x0A) {
                    idx++
                }
            } else if (b == 0x0A) {
                sb.append('\n')
            } else if (b != 0x00) {
                sb.append(b.toChar())
            }
            idx++
        }
        return sb.toString().split('\n')
    }

    fun assembleIntro(): AssembledProgram {
        val files = (0..7).map { "INTRO/X$it.DIS" }
        val program = assembleFiles("INTRO", files, emptyMap())
        val music = getBinary("INTRO/MUSIC.DAT")
        System.arraycopy(music, 0, program.memory, 0xE000, music.size.coerceAtMost(0x10000 - 0xE000))
        val panel = getBinary("INTRO/GRAPHICS/PANNEL.CHR")
        System.arraycopy(panel, 0, program.memory, 0xB800, panel.size.coerceAtMost(2048))
        val sprites = getBinary("INTRO/SPRITES/SPRITES.SPR")
        System.arraycopy(sprites, 0, program.memory, 0x4240, sprites.size.coerceAtMost(0x5C0))
        val alteredPic = getBinary("INTRO/GRAPHICS/ALTERED.PIC")
        System.arraycopy(alteredPic, 0, program.memory, 0x1000, alteredPic.size.coerceAtMost(0x1900))
        val stonePic = getBinary("INTRO/GRAPHICS/STONE.PIC")
        System.arraycopy(stonePic, 0, program.memory, 0x2900, stonePic.size.coerceAtMost(0x1900))
        return program
    }

    fun assembleGameLevel(level: Int): AssembledProgram {
        val enemyIdx = if (level == 4) 1 else level
        val files = listOf(
            "GAME/X0.DIS",
            "GAME/X1.DIS",
            "GAME/X2.DIS",
            "GAME/X3.DIS",
            "GAME/X4.DIS",
            "GAME/X5.DIS",
            "GAME/LEVEL$level.PDS",
            "GAME/ENEMY$enemyIdx.PDS"
        )
        return assembleFiles("GAME", files, mapOf("GLEVEL" to level))
    }

    fun clearBinaryCache() {
        incbinCache.clear()
    }

    fun assembleSnippet(
        orgAddress: Int,
        pdsSource: String,
        baseSymbols: Map<String, Int>,
        targetRam: ByteArray
    ): Int {
        val snippetLines = ArrayList<Pair<String, String>>()
        snippetLines.add("MOD_PATCH" to " ORG &${Integer.toHexString(orgAddress and 0xFFFF).uppercase()}")
        for (line in pdsSource.split('\n')) {
            snippetLines.add("MOD_PATCH" to line.trimEnd('\r'))
        }
        snippetLines.add("MOD_PATCH" to "MOD_PATCH_END_LABEL")

        val upperPredefined = baseSymbols.entries.associate { (k, v) -> k.uppercase() to v }
        val symbols = HashMap<String, Int>(upperPredefined.size + 64)
        symbols.putAll(upperPredefined)
        val mutableVars = HashSet<String>()
        val macros = HashMap<String, MacroDefinition>()

        for (pass in 1..3) {
            val scratch = if (pass == 3) targetRam else targetRam.copyOf()
            runSinglePass(
                baseDir = "GAME",
                lines = snippetLines,
                pass = pass,
                predefinedSymbols = upperPredefined,
                symbols = symbols,
                mutableVars = mutableVars,
                macros = macros,
                memory = scratch
            )
        }
        val endPc = symbols["MOD_PATCH_END_LABEL"] ?: orgAddress
        return (endPc - orgAddress).coerceAtLeast(0)
    }

    private fun getBinary(path: String): ByteArray {
        val normalized = path.replace('\\', '/').trimStart('/')
        return incbinCache.getOrPut(normalized) {
            assetProvider.readBytes(normalized)
        }
    }

    fun assembleFiles(
        baseDir: String,
        entryFiles: List<String>,
        predefinedSymbols: Map<String, Int>
    ): AssembledProgram {
        val expandedLines = ArrayList<Pair<String, String>>(8192)
        for (file in entryFiles) {
            loadAndExpandIncludes(baseDir, file, expandedLines)
        }

        val upperPredefined = predefinedSymbols.entries.associate { (k, v) -> k.uppercase() to v }
        val symbols = HashMap<String, Int>(2048)
        symbols.putAll(upperPredefined)
        val mutableVars = HashSet<String>()
        val macros = HashMap<String, MacroDefinition>()
        var memory = ByteArray(65536)

        for (pass in 1..3) {
            val varsToClear = ArrayList<String>()
            for (k in symbols.keys) {
                if (k.contains("@") || mutableVars.contains(k)) {
                    varsToClear.add(k)
                }
            }
            for (k in varsToClear) {
                symbols.remove(k)
            }
            symbols.putAll(upperPredefined)
            if (pass == 3) {
                memory = ByteArray(65536)
            }
            runSinglePass(
                baseDir = baseDir,
                lines = expandedLines,
                pass = pass,
                predefinedSymbols = upperPredefined,
                symbols = symbols,
                mutableVars = mutableVars,
                macros = macros,
                memory = memory
            )
        }

        return AssembledProgram(memory = memory, symbols = symbols)
    }

    private fun loadAndExpandIncludes(
        baseDir: String,
        assetPath: String,
        out: MutableList<Pair<String, String>>
    ) {
        val rawLines = readPdsSourceLines(assetPath)
        for (rawLine in rawLines) {
            val clean = stripLineComment(rawLine).trim()
            val upper = clean.uppercase()
            if (upper.startsWith("INCLUDE ") || upper.startsWith("INCLUDE\t")) {
                val targetRaw = clean.substring(7).trim().trim('"', '\'').replace('\\', '/')
                val includePath = "$baseDir/$targetRaw"
                loadAndExpandIncludes(baseDir, includePath, out)
            } else {
                out.add(assetPath to rawLine)
            }
        }
    }

    private fun runSinglePass(
        baseDir: String,
        lines: List<Pair<String, String>>,
        pass: Int,
        predefinedSymbols: Map<String, Int>,
        symbols: MutableMap<String, Int>,
        mutableVars: MutableSet<String>,
        macros: MutableMap<String, MacroDefinition>,
        memory: ByteArray
    ) {
        var pc = 0
        val localCounts = HashMap<String, Int>()
        val ifStack = ArrayList<Boolean>()
        val doStack = ArrayList<DoLoopState>()
        var index = 0

        while (index < lines.size) {
            val (fileId, rawLine) = lines[index]
            val parsed = parseLine(rawLine)
            if (parsed == null) {
                index++
                continue
            }
            val (labelRaw, opRaw, operandRaw) = parsed
            val label = labelRaw?.uppercase()
            val opUpper = opRaw?.uppercase()

            if (opUpper == "MACRO" && label != null) {
                val params = if (operandRaw.isNullOrBlank()) {
                    emptyList()
                } else {
                    operandRaw.split(',').map { it.trim() }
                }
                val body = ArrayList<Pair<String, String>>()
                index++
                while (index < lines.size) {
                    val (bFile, bRaw) = lines[index]
                    val bParsed = parseLine(bRaw)
                    if (bParsed != null && bParsed.second?.uppercase() == "ENDM") {
                        break
                    }
                    body.add(bFile to bRaw)
                    index++
                }
                macros[label] = MacroDefinition(params, body)
                index++
                continue
            }

            if (opUpper == "IF") {
                val parentActive = ifStack.all { it }
                if (parentActive && label != null) {
                    symbols[label] = pc
                }
                val condVal = if (parentActive) {
                    evalExpr(operandRaw ?: "0", pc, symbols, fileId, localCounts, pass, true)
                } else {
                    0
                }
                ifStack.add(parentActive && condVal != 0)
                index++
                continue
            } else if (opUpper == "ELSE") {
                if (ifStack.isNotEmpty()) {
                    val parentActive = if (ifStack.size > 1) {
                        ifStack.subList(0, ifStack.size - 1).all { it }
                    } else {
                        true
                    }
                    if (parentActive && label != null) {
                        symbols[label] = pc
                    }
                    val last = ifStack.removeAt(ifStack.size - 1)
                    ifStack.add(parentActive && !last)
                }
                index++
                continue
            } else if (opUpper == "ENDIF") {
                if (ifStack.isNotEmpty()) {
                    ifStack.removeAt(ifStack.size - 1)
                }
                if (ifStack.all { it } && label != null) {
                    symbols[label] = pc
                }
                index++
                continue
            }

            if (!ifStack.all { it }) {
                index++
                continue
            }

            if (opUpper == "DO") {
                if (label != null) {
                    symbols[label] = pc
                }
                doStack.add(DoLoopState(index))
                index++
                continue
            } else if (opUpper == "UNTIL") {
                val cond = evalExpr(operandRaw ?: "1", pc, symbols, fileId, localCounts, pass, true)
                if (cond == 0 && doStack.isNotEmpty()) {
                    index = doStack.last().startIndex + 1
                    continue
                } else {
                    if (doStack.isNotEmpty()) {
                        doStack.removeAt(doStack.size - 1)
                    }
                    index++
                    continue
                }
            }

            if (opUpper == "REPEAT") {
                if (label != null) {
                    symbols[label] = pc
                }
                val count = evalExpr(operandRaw ?: "1", pc, symbols, fileId, localCounts, pass, false)
                index++
                if (index < lines.size) {
                    val (repFile, repRaw) = lines[index]
                    for (r in 0 until count) {
                        pc = processStatement(
                            baseDir = baseDir,
                            fileId = repFile,
                            rawLine = repRaw,
                            pc = pc,
                            pass = pass,
                            predefinedSymbols = predefinedSymbols,
                            symbols = symbols,
                            mutableVars = mutableVars,
                            macros = macros,
                            localCounts = localCounts,
                            memory = memory
                        )
                    }
                }
                index++
                continue
            }

            pc = processStatement(
                baseDir = baseDir,
                fileId = fileId,
                rawLine = rawLine,
                pc = pc,
                pass = pass,
                predefinedSymbols = predefinedSymbols,
                symbols = symbols,
                mutableVars = mutableVars,
                macros = macros,
                localCounts = localCounts,
                memory = memory
            )
            index++
        }
    }

    private fun processStatement(
        baseDir: String,
        fileId: String,
        rawLine: String,
        pc: Int,
        pass: Int,
        predefinedSymbols: Map<String, Int>,
        symbols: MutableMap<String, Int>,
        mutableVars: MutableSet<String>,
        macros: Map<String, MacroDefinition>,
        localCounts: MutableMap<String, Int>,
        memory: ByteArray
    ): Int {
        var currentPc = pc
        val parsed = parseLine(rawLine) ?: return currentPc
        val (labelRaw, opRaw, operandRaw) = parsed
        val label = labelRaw?.uppercase()
        val opUpper = opRaw?.uppercase()

        if (label != null && opUpper != "EQU" && opUpper != "=") {
            if (label.startsWith("!")) {
                val key = "$fileId:$label"
                val count = (localCounts[key] ?: 0) + 1
                localCounts[key] = count
                symbols["$key@$count"] = currentPc
            } else {
                symbols[label] = currentPc
            }
        }

        if (opUpper == null) {
            return currentPc
        }

        if (opUpper == "ORG") {
            currentPc = evalExpr(operandRaw ?: "0", currentPc, symbols, fileId, localCounts, pass, false) and 0xFFFF
            return currentPc
        }

        if (opUpper == "EQU") {
            if (label != null && !predefinedSymbols.containsKey(label)) {
                val v = evalExpr(operandRaw ?: "0", currentPc, symbols, fileId, localCounts, pass, false)
                symbols[label] = v
            }
            return currentPc
        }

        if (opUpper == "=") {
            if (label != null) {
                val v = evalExpr(operandRaw ?: "0", currentPc, symbols, fileId, localCounts, pass, false)
                symbols[label] = v
                mutableVars.add(label)
            }
            return currentPc
        }

        if (opUpper == "END" || opUpper == "SEND") {
            return currentPc
        }

        if (opUpper == "DS") {
            val dsArgs = splitArgs(operandRaw ?: "0")
            val size = if (dsArgs.isNotEmpty()) {
                evalExpr(dsArgs[0], currentPc, symbols, fileId, localCounts, pass, false)
            } else {
                0
            }
            val hasFill = dsArgs.size >= 2
            val fillByte = if (hasFill) {
                evalExpr(dsArgs[1], currentPc, symbols, fileId, localCounts, pass, false) and 0xFF
            } else {
                0
            }
            if (pass == 3 && hasFill && size > 0) {
                for (offset in 0 until size) {
                    memory[(currentPc + offset) and 0xFFFF] = fillByte.toByte()
                }
            }
            currentPc = (currentPc + size) and 0xFFFF
            return currentPc
        }

        if (opUpper == "INCBIN") {
            val rel = (operandRaw ?: "").trim().trim('"', '\'').replace('\\', '/')
            val data = getBinary("$baseDir/$rel")
            if (pass == 3) {
                val len = data.size.coerceAtMost(0x10000 - currentPc)
                if (len > 0) {
                    System.arraycopy(data, 0, memory, currentPc, len)
                }
            }
            currentPc = (currentPc + data.size) and 0xFFFF
            return currentPc
        }

        if (opUpper == "DB" || opUpper == "DEFM" || (opUpper == "DT" && !macros.containsKey("DT"))) {
            val parts = splitArgs(operandRaw ?: "")
            for (part in parts) {
                if ((part.startsWith("\"") && part.endsWith("\"")) ||
                    (part.startsWith("'") && part.endsWith("'"))
                ) {
                    val strContent = part.substring(1, part.length - 1)
                    for (ch in strContent) {
                        if (pass == 3) {
                            memory[currentPc and 0xFFFF] = (ch.code and 0xFF).toByte()
                        }
                        currentPc = (currentPc + 1) and 0xFFFF
                    }
                } else {
                    val v = evalExpr(part, currentPc, symbols, fileId, localCounts, pass, false)
                    if (pass == 3) {
                        memory[currentPc and 0xFFFF] = (v and 0xFF).toByte()
                    }
                    currentPc = (currentPc + 1) and 0xFFFF
                }
            }
            return currentPc
        }

        if (opUpper == "HEX") {
            val cleanHex = (operandRaw ?: "").replace(" ", "").replace(",", "")
            var i = 0
            while (i + 1 < cleanHex.length) {
                val byteVal = cleanHex.substring(i, i + 2).toInt(16)
                if (pass == 3) {
                    memory[currentPc and 0xFFFF] = (byteVal and 0xFF).toByte()
                }
                currentPc = (currentPc + 1) and 0xFFFF
                i += 2
            }
            return currentPc
        }

        if (opUpper == "DW") {
            val parts = splitArgs(operandRaw ?: "")
            for (part in parts) {
                val v = evalExpr(part, currentPc, symbols, fileId, localCounts, pass, false)
                if (pass == 3) {
                    memory[currentPc and 0xFFFF] = (v and 0xFF).toByte()
                    memory[(currentPc + 1) and 0xFFFF] = ((v ushr 8) and 0xFF).toByte()
                }
                currentPc = (currentPc + 2) and 0xFFFF
            }
            return currentPc
        }

        val macro = macros[opUpper]
        if (macro != null) {
            val args = if (operandRaw.isNullOrBlank()) emptyList() else splitArgs(operandRaw)
            val expanded = ArrayList<Pair<String, String>>(macro.body.size)
            for ((mFile, mLine) in macro.body) {
                var replaced = mLine
                for (aIdx in args.indices) {
                    replaced = replaced.replace("@${aIdx + 1}", args[aIdx])
                    replaced = replaced.replace("\\${aIdx + 1}", args[aIdx])
                }
                for (pIdx in macro.params.indices) {
                    val pName = macro.params[pIdx]
                    val argVal = if (pIdx < args.size) args[pIdx] else ""
                    if (pName.isNotEmpty()) {
                        replaced = replaceIdentifier(replaced, pName, argVal)
                    }
                }
                expanded.add(mFile to replaced)
            }
            return runMacroBody(
                baseDir = baseDir,
                lines = expanded,
                pc = currentPc,
                pass = pass,
                predefinedSymbols = predefinedSymbols,
                symbols = symbols,
                mutableVars = mutableVars,
                macros = macros,
                localCounts = localCounts,
                memory = memory
            )
        }

        val modes = addressingModes[opUpper] ?: return currentPc
        val opStr = (operandRaw ?: "").trim()

        if (opStr.isEmpty() || (modes.size == 1 && modes.containsKey("IMP"))) {
            val opcode = modes["IMP"] ?: modes["ACC"] ?: 0xEA
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
            }
            return (currentPc + 1) and 0xFFFF
        }

        if (opStr.equals("A", ignoreCase = true) && modes.containsKey("ACC")) {
            val opcode = modes.getValue("ACC")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
            }
            return (currentPc + 1) and 0xFFFF
        }

        if (modes.containsKey("REL")) {
            val target = evalExpr(opStr, currentPc, symbols, fileId, localCounts, pass, false)
            var offset = target - (currentPc + 2)
            if (pass < 3) {
                offset = 0
            }
            val opcode = modes.getValue("REL")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (offset and 0xFF).toByte()
            }
            return (currentPc + 2) and 0xFFFF
        }

        if (opStr.startsWith("#")) {
            val v = evalExpr(opStr.substring(1).trim(), currentPc, symbols, fileId, localCounts, pass, false)
            val opcode = modes.getValue("IMM")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (v and 0xFF).toByte()
            }
            return (currentPc + 2) and 0xFFFF
        }

        val upperOpStr = opStr.uppercase().replace(" ", "")
        if (upperOpStr.startsWith("(") && upperOpStr.endsWith(",X)")) {
            val inner = opStr.trim().let { it.substring(1, it.lastIndexOf(',')).trim() }
            val v = evalExpr(inner, currentPc, symbols, fileId, localCounts, pass, false)
            val opcode = modes.getValue("INDX")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (v and 0xFF).toByte()
            }
            return (currentPc + 2) and 0xFFFF
        }

        if (upperOpStr.startsWith("(") && upperOpStr.endsWith("),Y")) {
            val inner = opStr.trim().let {
                val closeParen = it.lastIndexOf(')')
                it.substring(1, closeParen).trim()
            }
            val v = evalExpr(inner, currentPc, symbols, fileId, localCounts, pass, false)
            val opcode = modes.getValue("INDY")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (v and 0xFF).toByte()
            }
            return (currentPc + 2) and 0xFFFF
        }

        if (upperOpStr.startsWith("(") && upperOpStr.endsWith(")") && modes.containsKey("IND")) {
            val inner = opStr.trim().substring(1, opStr.trim().length - 1)
            val v = evalExpr(inner, currentPc, symbols, fileId, localCounts, pass, false)
            val opcode = modes.getValue("IND")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (v and 0xFF).toByte()
                memory[(currentPc + 2) and 0xFFFF] = ((v ushr 8) and 0xFF).toByte()
            }
            return (currentPc + 3) and 0xFFFF
        }

        var indexReg: String? = null
        var baseExpr = opStr
        val commaIndex = findTopLevelComma(opStr)
        if (commaIndex != -1) {
            val suffix = opStr.substring(commaIndex + 1).trim().uppercase()
            if (suffix == "X" || suffix == "Y") {
                indexReg = suffix
                baseExpr = opStr.substring(0, commaIndex).trim()
            }
        }

        val knownInPass1 = canEvaluateInPass1(baseExpr, symbols, fileId)
        val value = evalExpr(baseExpr, currentPc, symbols, fileId, localCounts, pass, false)
        val canUseZeroPage = (value in 0..0xFF) && (pass > 1 || knownInPass1)

        if (indexReg == "X") {
            if ((canUseZeroPage || !modes.containsKey("ABSX")) && modes.containsKey("ZPX")) {
                val opcode = modes.getValue("ZPX")
                if (pass == 3) {
                    memory[currentPc and 0xFFFF] = opcode.toByte()
                    memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
                }
                return (currentPc + 2) and 0xFFFF
            }
            val opcode = modes.getValue("ABSX")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
                memory[(currentPc + 2) and 0xFFFF] = ((value ushr 8) and 0xFF).toByte()
            }
            return (currentPc + 3) and 0xFFFF
        }

        if (indexReg == "Y") {
            if ((canUseZeroPage || !modes.containsKey("ABSY")) && modes.containsKey("ZPY")) {
                val opcode = modes.getValue("ZPY")
                if (pass == 3) {
                    memory[currentPc and 0xFFFF] = opcode.toByte()
                    memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
                }
                return (currentPc + 2) and 0xFFFF
            }
            val opcode = modes.getValue("ABSY")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
                memory[(currentPc + 2) and 0xFFFF] = ((value ushr 8) and 0xFF).toByte()
            }
            return (currentPc + 3) and 0xFFFF
        }

        if (canUseZeroPage && modes.containsKey("ZP")) {
            val opcode = modes.getValue("ZP")
            if (pass == 3) {
                memory[currentPc and 0xFFFF] = opcode.toByte()
                memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
            }
            return (currentPc + 2) and 0xFFFF
        }

        val opcode = modes["ABS"] ?: modes.getValue("ZP")
        val size = if (modes.containsKey("ABS")) 3 else 2
        if (pass == 3) {
            memory[currentPc and 0xFFFF] = opcode.toByte()
            memory[(currentPc + 1) and 0xFFFF] = (value and 0xFF).toByte()
            if (size == 3) {
                memory[(currentPc + 2) and 0xFFFF] = ((value ushr 8) and 0xFF).toByte()
            }
        }
        return (currentPc + size) and 0xFFFF
    }

    private fun runMacroBody(
        baseDir: String,
        lines: List<Pair<String, String>>,
        pc: Int,
        pass: Int,
        predefinedSymbols: Map<String, Int>,
        symbols: MutableMap<String, Int>,
        mutableVars: MutableSet<String>,
        macros: Map<String, MacroDefinition>,
        localCounts: MutableMap<String, Int>,
        memory: ByteArray
    ): Int {
        var currentPc = pc
        val doStack = ArrayList<DoLoopState>()
        var idx = 0
        while (idx < lines.size) {
            val (fileId, rawLine) = lines[idx]
            val parsed = parseLine(rawLine)
            if (parsed == null) {
                idx++
                continue
            }
            val opUpper = parsed.second?.uppercase()
            if (opUpper == "DO") {
                if (parsed.first != null) {
                    symbols[parsed.first!!.uppercase()] = currentPc
                }
                doStack.add(DoLoopState(idx))
                idx++
                continue
            } else if (opUpper == "UNTIL") {
                val cond = evalExpr(parsed.third ?: "1", currentPc, symbols, fileId, localCounts, pass, true)
                if (cond == 0 && doStack.isNotEmpty()) {
                    idx = doStack.last().startIndex + 1
                    continue
                } else {
                    if (doStack.isNotEmpty()) {
                        doStack.removeAt(doStack.size - 1)
                    }
                    idx++
                    continue
                }
            }
            currentPc = processStatement(
                baseDir = baseDir,
                fileId = fileId,
                rawLine = rawLine,
                pc = currentPc,
                pass = pass,
                predefinedSymbols = predefinedSymbols,
                symbols = symbols,
                mutableVars = mutableVars,
                macros = macros,
                localCounts = localCounts,
                memory = memory
            )
            idx++
        }
        return currentPc
    }

    private fun stripLineComment(line: String): String {
        if (line.trimStart().startsWith("*")) {
            return ""
        }
        var inSingle = false
        var inDouble = false
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
            } else if (ch == ';' && !inSingle && !inDouble) {
                return line.substring(0, i)
            }
        }
        return line
    }

    private fun parseLine(rawLine: String): Triple<String?, String?, String?>? {
        val line = stripLineComment(rawLine).trimEnd()
        if (line.isBlank()) return null

        val trimmed = line.trim()
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx > 0) {
            val lhs = trimmed.substring(0, eqIdx).trim()
            val rhs = trimmed.substring(eqIdx + 1).trim()
            if (lhs.isNotEmpty() &&
                lhs.all { it.isLetterOrDigit() || it == '_' || it == '?' } &&
                !addressingModes.containsKey(lhs.uppercase()) &&
                !directives.contains(lhs.uppercase())
            ) {
                return Triple(lhs, "=", rhs)
            }
        }

        var label: String? = null
        var rest: String
        if (!line[0].isWhitespace()) {
            var splitIdx = -1
            for (i in line.indices) {
                if (line[i].isWhitespace()) {
                    splitIdx = i
                    break
                }
            }
            if (splitIdx == -1) {
                label = line.trimEnd(':')
                rest = ""
            } else {
                label = line.substring(0, splitIdx).trimEnd(':')
                rest = line.substring(splitIdx).trim()
            }
            val labelUpper = label.uppercase()
            val isImpliedWithRest = rest.isNotEmpty() && addressingModes[labelUpper]?.keys == setOf("IMP")
            if ((addressingModes.containsKey(labelUpper) && !isImpliedWithRest) || directives.contains(labelUpper)) {
                rest = line.trim()
                label = null
            }
        } else {
            rest = line.trim()
        }

        if (rest.isEmpty()) {
            return Triple(label, null, null)
        }

        if (rest.startsWith("=")) {
            return Triple(label, "=", rest.substring(1).trim())
        }

        var opSplit = -1
        for (i in rest.indices) {
            if (rest[i].isWhitespace()) {
                opSplit = i
                break
            }
        }
        val op: String
        val operand: String?
        if (opSplit == -1) {
            op = rest
            operand = null
        } else {
            op = rest.substring(0, opSplit)
            operand = rest.substring(opSplit).trim()
        }

        if (label == null && operand != null) {
            val upperOperand = operand.uppercase()
            if (upperOperand == "EQU" || upperOperand.startsWith("EQU ") || upperOperand.startsWith("EQU\t")) {
                return Triple(op.trimEnd(':'), "EQU", operand.substring(3).trim())
            }
            if (upperOperand == "MACRO" || upperOperand.startsWith("MACRO ") || upperOperand.startsWith("MACRO\t")) {
                return Triple(op.trimEnd(':'), "MACRO", operand.substring(5).trim())
            }
        }

        return Triple(label, op, operand)
    }

    private fun splitArgs(operand: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        for (ch in operand) {
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                current.append(ch)
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                current.append(ch)
            } else if (ch == ',' && !inSingle && !inDouble) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            result.add(tail)
        }
        return result
    }

    private fun findTopLevelComma(opStr: String): Int {
        var depth = 0
        var inSingle = false
        var inDouble = false
        for (i in opStr.indices) {
            val ch = opStr[i]
            if (ch == '\'' && !inDouble) inSingle = !inSingle
            else if (ch == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (ch == '(') depth++
                else if (ch == ')') depth--
                else if (ch == ',' && depth == 0) return i
            }
        }
        return -1
    }

    private fun replaceIdentifier(line: String, param: String, replacement: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch.isLetter() || ch == '_') {
                val start = i
                i++
                while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
                    i++
                }
                val token = line.substring(start, i)
                if (token.equals(param, ignoreCase = true)) {
                    sb.append(replacement)
                } else {
                    sb.append(token)
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun canEvaluateInPass1(
        expr: String,
        symbols: Map<String, Int>,
        fileId: String
    ): Boolean {
        val trimmed = expr.trim()
        if (trimmed.startsWith(">") || trimmed.startsWith("<")) {
            return true
        }
        var i = 0
        while (i < trimmed.length) {
            val ch = trimmed[i]
            if (ch == '!') {
                return false
            }
            if (ch == '&' || ch == '$' || ch == '%' || ch == '"' || ch == '\'') {
                i++
                while (i < trimmed.length && (trimmed[i].isLetterOrDigit() || trimmed[i] == '_' || trimmed[i] == '"' || trimmed[i] == '\'')) {
                    i++
                }
                continue
            }
            if (ch.isLetter() || ch == '_') {
                val start = i
                i++
                while (i < trimmed.length && (trimmed[i].isLetterOrDigit() || trimmed[i] == '_')) {
                    i++
                }
                val name = trimmed.substring(start, i).uppercase()
                if (!symbols.containsKey(name)) {
                    return false
                }
            } else {
                i++
            }
        }
        return true
    }

    private fun evalExpr(
        rawExpr: String,
        pc: Int,
        symbols: Map<String, Int>,
        fileId: String,
        localCounts: Map<String, Int>,
        pass: Int,
        isIfCondition: Boolean
    ): Int {
        var expr = rawExpr.trim()
        if (expr.isEmpty()) return 0

        var byteSelector = 0
        if (expr.startsWith(">")) {
            byteSelector = 1
            expr = expr.substring(1).trim()
        } else if (expr.startsWith("<")) {
            byteSelector = 2
            expr = expr.substring(1).trim()
        }

        val value = parseExpressionValue(expr, pc, symbols, fileId, localCounts, isIfCondition)
        return when (byteSelector) {
            1 -> value and 0xFF
            2 -> (value ushr 8) and 0xFF
            else -> value
        }
    }

    private sealed class ExprToken {
        data class Num(val v: Int) : ExprToken()
        data class Op(val op: String) : ExprToken()
        object LParen : ExprToken()
        object RParen : ExprToken()
    }

    private fun parseExpressionValue(
        expr: String,
        pc: Int,
        symbols: Map<String, Int>,
        fileId: String,
        localCounts: Map<String, Int>,
        isIfCondition: Boolean
    ): Int {
        val tokens = tokenizeExpression(expr, pc, symbols, fileId, localCounts, isIfCondition)
        if (tokens.isEmpty()) return 0
        return ExprEvaluator(tokens).parseComparison()
    }

    private class ExprEvaluator(private val tokens: List<ExprToken>) {
        private var pos = 0

        fun parseComparison(): Int {
            var left = parseBitwiseOr()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is ExprToken.Op && (tok.op == "=" || tok.op == "==" || tok.op == "!=" || tok.op == "<" || tok.op == ">" || tok.op == "<=" || tok.op == ">=")) {
                    pos++
                    val right = parseBitwiseOr()
                    left = when (tok.op) {
                        "=", "==" -> if (left == right) 1 else 0
                        "!=" -> if (left != right) 1 else 0
                        "<" -> if (left < right) 1 else 0
                        ">" -> if (left > right) 1 else 0
                        "<=" -> if (left <= right) 1 else 0
                        ">=" -> if (left >= right) 1 else 0
                        else -> left
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parseBitwiseOr(): Int {
            var left = parseAddSub()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is ExprToken.Op && (tok.op == "|" || tok.op == "^" || tok.op == "&")) {
                    pos++
                    val right = parseAddSub()
                    left = when (tok.op) {
                        "|" -> left or right
                        "^" -> left xor right
                        "&" -> left and right
                        else -> left
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parseAddSub(): Int {
            var left = parseMulDiv()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is ExprToken.Op && (tok.op == "+" || tok.op == "-")) {
                    pos++
                    val right = parseMulDiv()
                    left = if (tok.op == "+") left + right else left - right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseMulDiv(): Int {
            var left = parseUnary()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is ExprToken.Op && (tok.op == "*" || tok.op == "/")) {
                    pos++
                    val right = parseUnary()
                    left = if (tok.op == "*") {
                        left * right
                    } else {
                        if (right != 0) left / right else 0
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parseUnary(): Int {
            if (pos >= tokens.size) return 0
            val tok = tokens[pos]
            if (tok is ExprToken.Op) {
                if (tok.op == "+") {
                    pos++
                    return parseUnary()
                }
                if (tok.op == "-") {
                    pos++
                    return -parseUnary()
                }
                if (tok.op == "~") {
                    pos++
                    return parseUnary().inv()
                }
                if (tok.op == "LOW_BYTE") {
                    pos++
                    return parseUnary() and 0xFF
                }
                if (tok.op == "HIGH_BYTE") {
                    pos++
                    return (parseUnary() ushr 8) and 0xFF
                }
            }
            if (tok is ExprToken.LParen) {
                pos++
                val inside = parseComparison()
                if (pos < tokens.size && tokens[pos] is ExprToken.RParen) {
                    pos++
                }
                return inside
            }
            if (tok is ExprToken.Num) {
                pos++
                return tok.v
            }
            pos++
            return 0
        }
    }

    private fun tokenizeExpression(
        expr: String,
        pc: Int,
        symbols: Map<String, Int>,
        fileId: String,
        localCounts: Map<String, Int>,
        isIfCondition: Boolean
    ): List<ExprToken> {
        val tokens = ArrayList<ExprToken>()
        var i = 0
        while (i < expr.length) {
            val ch = expr[i]
            if (ch.isWhitespace()) {
                i++
                continue
            }
            if (ch == '(') {
                tokens.add(ExprToken.LParen)
                i++
                continue
            }
            if (ch == ')') {
                tokens.add(ExprToken.RParen)
                i++
                continue
            }
            if (ch == '\'' || ch == '"') {
                val quote = ch
                val charVal = if (i + 1 < expr.length) expr[i + 1].code else 0
                tokens.add(ExprToken.Num(charVal))
                i += 2
                if (i < expr.length && expr[i] == quote) {
                    i++
                }
                continue
            }
            if (ch == '&' || ch == '$') {
                val prevIsValue = tokens.lastOrNull().let { it is ExprToken.Num || it is ExprToken.RParen }
                val nextIsHex = i + 1 < expr.length && expr[i + 1].let { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                if (ch == '&' && (prevIsValue || !nextIsHex)) {
                    tokens.add(ExprToken.Op("&"))
                    i++
                    continue
                }
                i++
                val start = i
                while (i < expr.length && expr[i].let { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    i++
                }
                val hexStr = expr.substring(start, i)
                val v = if (hexStr.isNotEmpty()) hexStr.toInt(16) else 0
                tokens.add(ExprToken.Num(v))
                continue
            }
            if (ch == '%') {
                i++
                val start = i
                while (i < expr.length && (expr[i] == '0' || expr[i] == '1')) {
                    i++
                }
                val binStr = expr.substring(start, i)
                val v = if (binStr.isNotEmpty()) binStr.toInt(2) else 0
                tokens.add(ExprToken.Num(v))
                continue
            }
            if (ch == '!') {
                if (i + 1 < expr.length && (expr[i + 1].isLetterOrDigit() || expr[i + 1] == '_')) {
                    val prevIsValue = tokens.lastOrNull().let { it is ExprToken.Num || it is ExprToken.RParen }
                    if (isIfCondition && prevIsValue) {
                        tokens.add(ExprToken.Op("|"))
                        i++
                        continue
                    }
                    i++
                    val start = i
                    while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '_')) {
                        i++
                    }
                    val localName = "!" + expr.substring(start, i).uppercase()
                    val key = "$fileId:$localName"
                    val curCount = localCounts[key] ?: 0
                    val nextTarget = symbols["$key@${curCount + 1}"]
                    val prevTarget = symbols["$key@$curCount"]
                    val resolved = when {
                        nextTarget != null && prevTarget != null -> {
                            val dNext = nextTarget - pc
                            val dPrev = pc - prevTarget
                            if (dPrev in 0..dNext) prevTarget else nextTarget
                        }
                        nextTarget != null -> nextTarget
                        prevTarget != null -> prevTarget
                        else -> pc
                    }
                    tokens.add(ExprToken.Num(resolved))
                    continue
                } else if (i + 1 < expr.length && expr[i + 1] == '=') {
                    tokens.add(ExprToken.Op("!="))
                    i += 2
                    continue
                } else {
                    tokens.add(ExprToken.Op("|"))
                    i++
                    continue
                }
            }
            if (ch == '*') {
                val prevIsValue = tokens.lastOrNull().let { it is ExprToken.Num || it is ExprToken.RParen }
                if (!prevIsValue) {
                    tokens.add(ExprToken.Num(pc))
                } else {
                    tokens.add(ExprToken.Op("*"))
                }
                i++
                continue
            }
            if (ch == '+' || ch == '-' || ch == '/' || ch == '|' || ch == '^' || ch == '~') {
                tokens.add(ExprToken.Op(ch.toString()))
                i++
                continue
            }
            if (ch == '=') {
                if (i + 1 < expr.length && expr[i + 1] == '=') {
                    tokens.add(ExprToken.Op("=="))
                    i += 2
                } else {
                    tokens.add(ExprToken.Op("="))
                    i++
                }
                continue
            }
            if (ch == '<' || ch == '>') {
                val prevIsValue = tokens.lastOrNull().let { it is ExprToken.Num || it is ExprToken.RParen }
                if (!prevIsValue && !isIfCondition) {
                    tokens.add(ExprToken.Op(if (ch == '>') "LOW_BYTE" else "HIGH_BYTE"))
                    i++
                    continue
                }
                if (i + 1 < expr.length && expr[i + 1] == '=') {
                    tokens.add(ExprToken.Op("$ch="))
                    i += 2
                } else {
                    tokens.add(ExprToken.Op(ch.toString()))
                    i++
                }
                continue
            }
            if (ch.isDigit()) {
                val start = i
                while (i < expr.length && expr[i].isDigit()) {
                    i++
                }
                val numVal = expr.substring(start, i).toIntOrNull() ?: 0
                tokens.add(ExprToken.Num(numVal))
                continue
            }
            if (ch.isLetter() || ch == '_' || ch == '?') {
                val start = i
                i++
                while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '_' || expr[i] == '?')) {
                    i++
                }
                val ident = expr.substring(start, i).uppercase()
                val symVal = symbols[ident] ?: 0
                tokens.add(ExprToken.Num(symVal))
                continue
            }
            i++
        }
        return tokens
    }
}
