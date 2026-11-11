package com.example.modding

import com.example.engine.C64ExecutionCore
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction

class LuaHostApiBuilder(
    private val core: C64ExecutionCore,
    private val assetRouter: ModAssetRouter,
    private val canvasRenderer: ModCanvasRenderer,
    private val registerPcCallback: (Int, LuaFunction) -> Unit,
    private val clearPcCallbacks: (Int) -> Unit
) {

    init {
        val ensureLuaValueInit = LuaValue.NIL
        if (ensureLuaValueInit.isnil()) {
            LuaValue.valueOf(0)
        }
    }

    fun buildGzTable(modName: String): LuaTable {
        val gz = LuaTable()

        gz.set("version", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf("GZBeast-Lua-1.0")
        })

        gz.set("log", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val sb = StringBuilder()
                for (i in 1..args.narg()) {
                    if (i > 1) sb.append(' ')
                    sb.append(args.arg(i).tojstring())
                }
                SessionLogManager.logMod(modName, sb.toString())
                return LuaValue.NONE
            }
        })

        bindMemoryAndHardwareApi(gz)
        bindCpuAndSymbolApi(gz)
        gz.set("player", buildPlayerTable())
        gz.set("enemy", buildEnemyTable())
        gz.set("boss", buildBossTable())
        gz.set("level", buildLevelTable())
        gz.set("palette", buildPaletteTable())
        gz.set("sprite", buildSpriteTable())
        gz.set("canvas", buildCanvasTable())
        gz.set("asset", buildAssetTable())
        gz.set("asm", buildAsmTable())
        gz.set("input", buildInputTable())

        return gz
    }

    private fun bindMemoryAndHardwareApi(gz: LuaTable) {
        gz.set("peek", object : OneArgFunction() {
            override fun call(addrArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                return LuaValue.valueOf(core.ram[addr].toInt() and 0xFF)
            }
        })

        gz.set("poke", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, valArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                core.ram[addr] = (valArg.checkint() and 0xFF).toByte()
                return LuaValue.NONE
            }
        })

        gz.set("peek16", object : OneArgFunction() {
            override fun call(addrArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                val lo = core.ram[addr].toInt() and 0xFF
                val hi = core.ram[(addr + 1) and 0xFFFF].toInt() and 0xFF
                return LuaValue.valueOf((hi shl 8) or lo)
            }
        })

        gz.set("poke16", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, valArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                val word = valArg.checkint() and 0xFFFF
                core.ram[addr] = (word and 0xFF).toByte()
                core.ram[(addr + 1) and 0xFFFF] = ((word ushr 8) and 0xFF).toByte()
                return LuaValue.NONE
            }
        })

        gz.set("peek_bytes", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, lenArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                val len = lenArg.checkint().coerceIn(0, 65536)
                val out = LuaTable(len, 0)
                for (i in 0 until len) {
                    out.rawset(i + 1, LuaValue.valueOf(core.ram[(addr + i) and 0xFFFF].toInt() and 0xFF))
                }
                return out
            }
        })

        gz.set("poke_bytes", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, tblArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                val tbl = tblArg.checktable()
                val len = tbl.length()
                for (i in 1..len) {
                    val b = tbl.rawget(i).optint(0) and 0xFF
                    core.ram[(addr + i - 1) and 0xFFFF] = b.toByte()
                }
                return LuaValue.NONE
            }
        })

        gz.set("read_io", object : OneArgFunction() {
            override fun call(addrArg: LuaValue): LuaValue {
                return LuaValue.valueOf(core.readMem(addrArg.checkint()))
            }
        })

        gz.set("write_io", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, valArg: LuaValue): LuaValue {
                core.writeMem(addrArg.checkint(), valArg.checkint())
                return LuaValue.NONE
            }
        })

        gz.set("color_peek", object : OneArgFunction() {
            override fun call(offArg: LuaValue): LuaValue {
                val off = offArg.checkint() and 0x03FF
                return LuaValue.valueOf(core.colorRam[off].toInt() and 0x0F)
            }
        })

        gz.set("color_poke", object : TwoArgFunction() {
            override fun call(offArg: LuaValue, valArg: LuaValue): LuaValue {
                val off = offArg.checkint() and 0x03FF
                core.colorRam[off] = (valArg.checkint() and 0x0F).toByte()
                return LuaValue.NONE
            }
        })

        gz.set("vic_get", object : OneArgFunction() {
            override fun call(regArg: LuaValue): LuaValue {
                val reg = regArg.checkint() and 0x3F
                return LuaValue.valueOf(core.vicRegs[reg] and 0xFF)
            }
        })

        gz.set("vic_set", object : TwoArgFunction() {
            override fun call(regArg: LuaValue, valArg: LuaValue): LuaValue {
                val reg = regArg.checkint() and 0x3F
                core.vicRegs[reg] = valArg.checkint() and 0xFF
                return LuaValue.NONE
            }
        })

        gz.set("sid_get", object : OneArgFunction() {
            override fun call(regArg: LuaValue): LuaValue {
                return LuaValue.valueOf(core.sidSynthesizer.readRegister(regArg.checkint()))
            }
        })

        gz.set("sid_poke", object : TwoArgFunction() {
            override fun call(regArg: LuaValue, valArg: LuaValue): LuaValue {
                core.sidSynthesizer.writeRegister(regArg.checkint(), valArg.checkint())
                return LuaValue.NONE
            }
        })

        gz.set("sid_note", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val voice = args.arg(1).optint(0)
                val freq = args.arg(2).optdouble(440.0)
                val wave = args.arg(3).optint(0x21)
                val ad = args.arg(4).optint(0x19)
                val sr = args.arg(5).optint(0xF5)
                val pw = args.arg(6).optint(2048)
                core.sidSynthesizer.playSynthesizedNote(voice, freq, wave, ad, sr, pw)
                return LuaValue.NONE
            }
        })
    }

    private fun bindCpuAndSymbolApi(gz: LuaTable) {
        gz.set("cpu_get", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val t = LuaTable()
                t.set("a", LuaValue.valueOf(core.regA and 0xFF))
                t.set("x", LuaValue.valueOf(core.regX and 0xFF))
                t.set("y", LuaValue.valueOf(core.regY and 0xFF))
                t.set("sp", LuaValue.valueOf(core.regSP and 0xFF))
                t.set("pc", LuaValue.valueOf(core.regPC and 0xFFFF))
                t.set("raster", LuaValue.valueOf(core.currentRaster))
                t.set("n", LuaValue.valueOf(core.flagN))
                t.set("v", LuaValue.valueOf(core.flagV))
                t.set("d", LuaValue.valueOf(core.flagD))
                t.set("i", LuaValue.valueOf(core.flagI))
                t.set("z", LuaValue.valueOf(core.flagZ))
                t.set("c", LuaValue.valueOf(core.flagC))
                return t
            }
        })

        gz.set("cpu_set", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val t = arg.checktable()
                t.get("a").takeIf { !it.isnil() }?.let { core.regA = it.checkint() and 0xFF }
                t.get("x").takeIf { !it.isnil() }?.let { core.regX = it.checkint() and 0xFF }
                t.get("y").takeIf { !it.isnil() }?.let { core.regY = it.checkint() and 0xFF }
                t.get("sp").takeIf { !it.isnil() }?.let { core.regSP = it.checkint() and 0xFF }
                t.get("pc").takeIf { !it.isnil() }?.let { core.regPC = it.checkint() and 0xFFFF }
                t.get("n").takeIf { !it.isnil() }?.let { core.flagN = it.toboolean() }
                t.get("v").takeIf { !it.isnil() }?.let { core.flagV = it.toboolean() }
                t.get("d").takeIf { !it.isnil() }?.let { core.flagD = it.toboolean() }
                t.get("i").takeIf { !it.isnil() }?.let { core.flagI = it.toboolean() }
                t.get("z").takeIf { !it.isnil() }?.let { core.flagZ = it.toboolean() }
                t.get("c").takeIf { !it.isnil() }?.let { core.flagC = it.toboolean() }
                return LuaValue.NONE
            }
        })

        gz.set("sym", object : TwoArgFunction() {
            override fun call(nameArg: LuaValue, lvlArg: LuaValue): LuaValue {
                val name = nameArg.checkjstring()
                val lvl = if (lvlArg.isnil()) core.currentLoadedLevel else lvlArg.checkint()
                val addr = core.resolveSymbol(name, lvl) ?: return LuaValue.NIL
                return LuaValue.valueOf(addr)
            }
        })

        gz.set("sym_peek", object : TwoArgFunction() {
            override fun call(nameArg: LuaValue, offArg: LuaValue): LuaValue {
                val base = core.resolveSymbol(nameArg.checkjstring()) ?: return LuaValue.valueOf(0)
                val off = offArg.optint(0)
                return LuaValue.valueOf(core.ram[(base + off) and 0xFFFF].toInt() and 0xFF)
            }
        })

        gz.set("sym_poke", object : ThreeArgFunction() {
            override fun call(nameArg: LuaValue, arg2: LuaValue, arg3: LuaValue): LuaValue {
                val base = core.resolveSymbol(nameArg.checkjstring()) ?: return LuaValue.NONE
                val offset: Int
                val value: Int
                if (arg3.isnil()) {
                    offset = 0
                    value = arg2.checkint()
                } else {
                    offset = arg2.checkint()
                    value = arg3.checkint()
                }
                core.ram[(base + offset) and 0xFFFF] = (value and 0xFF).toByte()
                return LuaValue.NONE
            }
        })

        gz.set("on_pc", object : TwoArgFunction() {
            override fun call(targetArg: LuaValue, fnArg: LuaValue): LuaValue {
                val fn = fnArg.checkfunction()
                val addr = resolveAddressOrSymbol(targetArg) ?: return LuaValue.FALSE
                registerPcCallback(addr and 0xFFFF, fn)
                return LuaValue.TRUE
            }
        })

        gz.set("clear_pc", object : OneArgFunction() {
            override fun call(targetArg: LuaValue): LuaValue {
                val addr = resolveAddressOrSymbol(targetArg) ?: return LuaValue.FALSE
                clearPcCallbacks(addr and 0xFFFF)
                return LuaValue.TRUE
            }
        })
    }

    private fun resolveAddressOrSymbol(targetArg: LuaValue): Int? {
        return if (targetArg.isnumber()) {
            targetArg.checkint() and 0xFFFF
        } else {
            core.resolveSymbol(targetArg.checkjstring())
        }
    }

    private fun buildPlayerTable(): LuaTable {
        val player = LuaTable()
        player.set("get", object : OneArgFunction() {
            override fun call(idxArg: LuaValue): LuaValue {
                val p = (idxArg.optint(1) - 1).coerceIn(0, 1)
                val t = LuaTable()
                t.set("x", LuaValue.valueOf(readSym("X", p)))
                t.set("y", LuaValue.valueOf(readSym("Y", p)))
                t.set("dir", LuaValue.valueOf(readSym("DIR", p)))
                t.set("action", LuaValue.valueOf(readSym("ACTION", p)))
                t.set("frame", LuaValue.valueOf(readSym("FRAME", p)))
                t.set("up", LuaValue.valueOf(readSym("UP", p)))
                t.set("lives", LuaValue.valueOf(readSym("LIVES", p)))
                t.set("hits", LuaValue.valueOf(readSym("HITS", p)))
                t.set("balls", LuaValue.valueOf(readSym("BALLS", p)))
                t.set("altered", LuaValue.valueOf(readSym("ALTERED", p)))
                t.set("blanking", LuaValue.valueOf(readSym("BLANKING", p)))
                t.set("attached", LuaValue.valueOf(readSym("ATTACHED", p)))
                return t
            }
        })

        player.set("set", object : TwoArgFunction() {
            override fun call(idxArg: LuaValue, tblArg: LuaValue): LuaValue {
                val p = (idxArg.optint(1) - 1).coerceIn(0, 1)
                val t = tblArg.checktable()
                t.get("x").takeIf { !it.isnil() }?.let { writeSym("X", p, it.checkint()) }
                t.get("y").takeIf { !it.isnil() }?.let { writeSym("Y", p, it.checkint()) }
                t.get("dir").takeIf { !it.isnil() }?.let { writeSym("DIR", p, it.checkint()) }
                t.get("action").takeIf { !it.isnil() }?.let { writeSym("ACTION", p, it.checkint()) }
                t.get("frame").takeIf { !it.isnil() }?.let { writeSym("FRAME", p, it.checkint()) }
                t.get("up").takeIf { !it.isnil() }?.let { writeSym("UP", p, it.checkint()) }
                t.get("lives").takeIf { !it.isnil() }?.let { writeSym("LIVES", p, it.checkint()) }
                t.get("hits").takeIf { !it.isnil() }?.let { writeSym("HITS", p, it.checkint()) }
                t.get("balls").takeIf { !it.isnil() }?.let { writeSym("BALLS", p, it.checkint()) }
                t.get("altered").takeIf { !it.isnil() }?.let { writeSym("ALTERED", p, it.checkint()) }
                t.get("blanking").takeIf { !it.isnil() }?.let { writeSym("BLANKING", p, it.checkint()) }
                t.get("attached").takeIf { !it.isnil() }?.let { writeSym("ATTACHED", p, it.checkint()) }
                return LuaValue.NONE
            }
        })

        player.set("transform", object : TwoArgFunction() {
            override fun call(idxArg: LuaValue, enableArg: LuaValue): LuaValue {
                val p = (idxArg.optint(1) - 1).coerceIn(0, 1)
                val enable = if (enableArg.isnil()) true else enableArg.toboolean()
                if (enable) {
                    writeSym("BALLS", p, 3)
                    writeSym("ALTERED", p, 255)
                    writeSym("FACEFLASH", p, 50)
                } else {
                    writeSym("BALLS", p, 0)
                    writeSym("ALTERED", p, 0)
                    writeSym("FACEFLASH", p, 0)
                }
                return LuaValue.NONE
            }
        })
        return player
    }

    private fun buildEnemyTable(): LuaTable {
        val enemy = LuaTable()
        enemy.set("get", object : OneArgFunction() {
            override fun call(slotArg: LuaValue): LuaValue {
                val s = slotArg.checkint().coerceIn(2, 7)
                val t = LuaTable()
                t.set("slot", LuaValue.valueOf(s))
                t.set("id", LuaValue.valueOf(readSym("ENEMY", s)))
                t.set("x", LuaValue.valueOf(readSym("X", s)))
                t.set("y", LuaValue.valueOf(readSym("Y", s)))
                t.set("dir", LuaValue.valueOf(readSym("DIR", s)))
                t.set("action", LuaValue.valueOf(readSym("ACTION", s)))
                t.set("frame", LuaValue.valueOf(readSym("FRAME", s)))
                t.set("up", LuaValue.valueOf(readSym("UP", s)))
                t.set("hits", LuaValue.valueOf(readSym("EHITS", s)))
                return t
            }
        })

        enemy.set("set", object : TwoArgFunction() {
            override fun call(slotArg: LuaValue, tblArg: LuaValue): LuaValue {
                val s = slotArg.checkint().coerceIn(2, 7)
                val t = tblArg.checktable()
                t.get("id").takeIf { !it.isnil() }?.let { writeSym("ENEMY", s, it.checkint()) }
                t.get("x").takeIf { !it.isnil() }?.let { writeSym("X", s, it.checkint()) }
                t.get("y").takeIf { !it.isnil() }?.let { writeSym("Y", s, it.checkint()) }
                t.get("dir").takeIf { !it.isnil() }?.let { writeSym("DIR", s, it.checkint()) }
                t.get("action").takeIf { !it.isnil() }?.let { writeSym("ACTION", s, it.checkint()) }
                t.get("frame").takeIf { !it.isnil() }?.let { writeSym("FRAME", s, it.checkint()) }
                t.get("up").takeIf { !it.isnil() }?.let { writeSym("UP", s, it.checkint()) }
                t.get("hits").takeIf { !it.isnil() }?.let { writeSym("EHITS", s, it.checkint()) }
                return LuaValue.NONE
            }
        })

        enemy.set("spawn", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val s = args.arg(1).optint(2).coerceIn(2, 7)
                val id = args.arg(2).optint(4)
                val x = args.arg(3).optint(120)
                val y = args.arg(4).optint(138)
                val act = args.arg(5).optint(1)
                val hits = args.arg(6).optint(1)
                writeSym("ENEMY", s, id)
                writeSym("X", s, x)
                writeSym("Y", s, y)
                writeSym("ACTION", s, act)
                writeSym("FRAME", s, 0)
                writeSym("EHITS", s, hits)
                return LuaValue.NONE
            }
        })

        enemy.set("kill_all", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                for (s in 2..7) {
                    val en = readSym("ENEMY", s)
                    if (en > 3) {
                        writeSym("ENEMY", s, 0)
                        writeSym("ACTION", s, 0)
                    }
                }
                return LuaValue.NONE
            }
        })
        return enemy
    }

    private fun buildBossTable(): LuaTable {
        val boss = LuaTable()
        boss.set("spawn", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                writeSym("ENDSCROLL", 0, 1)
                return LuaValue.NONE
            }
        })

        boss.set("kill", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                writeSym("KILLED", 0, 255)
                return LuaValue.NONE
            }
        })
        return boss
    }

    private fun buildLevelTable(): LuaTable {
        val level = LuaTable()
        level.set("get", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val t = LuaTable()
                t.set("level", LuaValue.valueOf(core.currentLoadedLevel))
                t.set("in_game", LuaValue.valueOf(core.inGameMode))
                t.set("scroll", LuaValue.valueOf(readSym("SCROLL", 0)))
                t.set("end_scroll", LuaValue.valueOf(readSym("ENDSCROLL", 0)))
                t.set("boss_killed", LuaValue.valueOf(readSym("KILLED", 0)))
                return t
            }
        })

        level.set("load", object : OneArgFunction() {
            override fun call(lvlArg: LuaValue): LuaValue {
                core.loadLevelDirect(lvlArg.checkint().coerceIn(1, 5))
                return LuaValue.NONE
            }
        })

        level.set("set_char", object : TwoArgFunction() {
            override fun call(idxArg: LuaValue, tblArg: LuaValue): LuaValue {
                val idx = idxArg.checkint() and 0xFF
                val tbl = tblArg.checktable()
                val charsetBase = core.resolveSymbol("CHARSET") ?: 0xF800
                for (i in 0 until 8) {
                    val b = tbl.rawget(i + 1).optint(0) and 0xFF
                    core.ram[(charsetBase + idx * 8 + i) and 0xFFFF] = b.toByte()
                }
                return LuaValue.NONE
            }
        })

        level.set("get_char", object : OneArgFunction() {
            override fun call(idxArg: LuaValue): LuaValue {
                val idx = idxArg.checkint() and 0xFF
                val charsetBase = core.resolveSymbol("CHARSET") ?: 0xF800
                val out = LuaTable(8, 0)
                for (i in 0 until 8) {
                    out.rawset(i + 1, LuaValue.valueOf(core.ram[(charsetBase + idx * 8 + i) and 0xFFFF].toInt() and 0xFF))
                }
                return out
            }
        })

        level.set("set_block", object : ThreeArgFunction() {
            override fun call(idxArg: LuaValue, tblArg: LuaValue, colArg: LuaValue): LuaValue {
                val idx = idxArg.checkint() and 0xFF
                val tbl = tblArg.checktable()
                val blockBase = core.resolveSymbol("BLOCKMEM") ?: return LuaValue.NONE
                for (i in 0 until 16) {
                    val b = tbl.rawget(i + 1).optint(0) and 0xFF
                    core.ram[(blockBase + idx * 16 + i) and 0xFFFF] = b.toByte()
                }
                if (!colArg.isnil()) {
                    val colBase = core.resolveSymbol("COLTABLE")
                    if (colBase != null) {
                        core.ram[(colBase + idx) and 0xFFFF] = (colArg.checkint() and 0xFF).toByte()
                    }
                }
                return LuaValue.NONE
            }
        })

        level.set("set_map_byte", object : TwoArgFunction() {
            override fun call(offArg: LuaValue, valArg: LuaValue): LuaValue {
                val mapBase = core.resolveSymbol("MAP") ?: return LuaValue.NONE
                val off = offArg.checkint().coerceAtLeast(0)
                core.ram[(mapBase + off) and 0xFFFF] = (valArg.checkint() and 0xFF).toByte()
                return LuaValue.NONE
            }
        })
        return level
    }

    private fun buildPaletteTable(): LuaTable {
        val pal = LuaTable()
        pal.set("get", object : OneArgFunction() {
            override fun call(idxArg: LuaValue): LuaValue {
                return LuaValue.valueOf(core.videoRenderer.getPaletteColor(idxArg.checkint()))
            }
        })
        pal.set("set", object : TwoArgFunction() {
            override fun call(idxArg: LuaValue, rgbArg: LuaValue): LuaValue {
                core.videoRenderer.setPaletteColor(idxArg.checkint(), rgbArg.checkint())
                return LuaValue.NONE
            }
        })
        pal.set("reset", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                core.videoRenderer.resetPalette()
                return LuaValue.NONE
            }
        })
        return pal
    }

    private fun buildSpriteTable(): LuaTable {
        val sp = LuaTable()
        sp.set("read", object : TwoArgFunction() {
            override fun call(addrArg: LuaValue, countArg: LuaValue): LuaValue {
                val addr = addrArg.checkint() and 0xFFFF
                val count = countArg.optint(64).coerceIn(1, 4096)
                val out = LuaTable(count, 0)
                for (i in 0 until count) {
                    out.rawset(i + 1, LuaValue.valueOf(core.ram[(addr + i) and 0xFFFF].toInt() and 0xFF))
                }
                return out
            }
        })

        sp.set("write", object : ThreeArgFunction() {
            override fun call(addrArg: LuaValue, idxArg: LuaValue, tblArg: LuaValue): LuaValue {
                val base = addrArg.checkint() and 0xFFFF
                val idx = idxArg.checkint().coerceAtLeast(0)
                val tbl = tblArg.checktable()
                val dst = (base + idx * 64) and 0xFFFF
                val len = tbl.length().coerceAtMost(64)
                for (i in 1..len) {
                    core.ram[(dst + i - 1) and 0xFFFF] = (tbl.rawget(i).optint(0) and 0xFF).toByte()
                }
                return LuaValue.NONE
            }
        })

        sp.set("from_png", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val path = args.arg(1).checkjstring()
                val bg = args.arg(2).optint(0x000000)
                val mc0 = args.arg(3).optint(0xB86962)
                val spCol = args.arg(4).optint(0x9F9F9F)
                val mc1 = args.arg(5).optint(0x000000)
                val sprites = assetRouter.convertPngToC64Sprites(path, bg, mc0, spCol, mc1)
                val out = LuaTable(sprites.size, 0)
                for (sIdx in sprites.indices) {
                    val bytes = sprites[sIdx]
                    val spTable = LuaTable(64, 0)
                    for (bIdx in bytes.indices) {
                        spTable.rawset(bIdx + 1, LuaValue.valueOf(bytes[bIdx].toInt() and 0xFF))
                    }
                    out.rawset(sIdx + 1, spTable)
                }
                return out
            }
        })
        return sp
    }

    private fun buildCanvasTable(): LuaTable {
        val cv = LuaTable()
        cv.set("pixel", object : ThreeArgFunction() {
            override fun call(xArg: LuaValue, yArg: LuaValue, colArg: LuaValue): LuaValue {
                canvasRenderer.drawPixel(xArg.checkint(), yArg.checkint(), colArg.checkint())
                return LuaValue.NONE
            }
        })

        cv.set("rect", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val x = args.arg(1).checkint()
                val y = args.arg(2).checkint()
                val w = args.arg(3).checkint()
                val h = args.arg(4).checkint()
                val col = args.arg(5).checkint()
                val filled = if (args.narg() >= 6 && !args.arg(6).isnil()) args.arg(6).toboolean() else true
                canvasRenderer.drawRect(x, y, w, h, col, filled)
                return LuaValue.NONE
            }
        })

        cv.set("line", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                canvasRenderer.drawLine(
                    args.arg(1).checkint(),
                    args.arg(2).checkint(),
                    args.arg(3).checkint(),
                    args.arg(4).checkint(),
                    args.arg(5).checkint()
                )
                return LuaValue.NONE
            }
        })

        cv.set("text", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                canvasRenderer.drawText(
                    args.arg(1).checkint(),
                    args.arg(2).checkint(),
                    args.arg(3).checkjstring(),
                    args.arg(4).optint(0xFFFFFFFF.toInt()),
                    args.arg(5).optint(1)
                )
                return LuaValue.NONE
            }
        })

        cv.set("blit_png", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val path = args.arg(1).checkjstring()
                val dstX = args.arg(2).optint(0)
                val dstY = args.arg(3).optint(0)
                val srcX = args.arg(4).optint(0)
                val srcY = args.arg(5).optint(0)
                val w = args.arg(6).optint(0)
                val h = args.arg(7).optint(0)
                val scale = args.arg(8).optint(1)
                canvasRenderer.blitPng(path, dstX, dstY, srcX, srcY, w, h, scale)
                return LuaValue.NONE
            }
        })
        return cv
    }

    private fun buildAssetTable(): LuaTable {
        val asset = LuaTable()
        asset.set("override", object : TwoArgFunction() {
            override fun call(intArg: LuaValue, relArg: LuaValue): LuaValue {
                assetRouter.registerFileOverride(intArg.checkjstring(), relArg.checkjstring())
                return LuaValue.NONE
            }
        })

        asset.set("override_bytes", object : TwoArgFunction() {
            override fun call(intArg: LuaValue, tblArg: LuaValue): LuaValue {
                val tbl = tblArg.checktable()
                val len = tbl.length()
                val bytes = ByteArray(len)
                for (i in 1..len) {
                    bytes[i - 1] = (tbl.rawget(i).optint(0) and 0xFF).toByte()
                }
                assetRouter.registerByteOverride(intArg.checkjstring(), bytes)
                return LuaValue.NONE
            }
        })

        asset.set("clear_overrides", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                assetRouter.clearOverrides()
                return LuaValue.NONE
            }
        })

        asset.set("rebuild_level", object : OneArgFunction() {
            override fun call(lvlArg: LuaValue): LuaValue {
                core.rebuildLevelProgram(lvlArg.checkint())
                return LuaValue.NONE
            }
        })

        asset.set("exists", object : OneArgFunction() {
            override fun call(relArg: LuaValue): LuaValue {
                return LuaValue.valueOf(assetRouter.modFileExists(relArg.checkjstring()))
            }
        })

        asset.set("read_bytes", object : OneArgFunction() {
            override fun call(relArg: LuaValue): LuaValue {
                val bytes = assetRouter.readModFileBytes(relArg.checkjstring()) ?: return LuaValue.NIL
                val out = LuaTable(bytes.size, 0)
                for (i in bytes.indices) {
                    out.rawset(i + 1, LuaValue.valueOf(bytes[i].toInt() and 0xFF))
                }
                return out
            }
        })

        asset.set("write_bytes", object : TwoArgFunction() {
            override fun call(relArg: LuaValue, tblArg: LuaValue): LuaValue {
                val tbl = tblArg.checktable()
                val len = tbl.length()
                val bytes = ByteArray(len)
                for (i in 1..len) {
                    bytes[i - 1] = (tbl.rawget(i).optint(0) and 0xFF).toByte()
                }
                return LuaValue.valueOf(assetRouter.writeModFileBytes(relArg.checkjstring(), bytes))
            }
        })

        asset.set("read_text", object : OneArgFunction() {
            override fun call(relArg: LuaValue): LuaValue {
                val txt = assetRouter.readModFileText(relArg.checkjstring()) ?: return LuaValue.NIL
                return LuaValue.valueOf(txt)
            }
        })

        asset.set("write_text", object : TwoArgFunction() {
            override fun call(relArg: LuaValue, txtArg: LuaValue): LuaValue {
                return LuaValue.valueOf(assetRouter.writeModFileText(relArg.checkjstring(), txtArg.checkjstring()))
            }
        })

        asset.set("list", object : OneArgFunction() {
            override fun call(subArg: LuaValue): LuaValue {
                val sub = if (subArg.isnil()) "" else subArg.checkjstring()
                val files = assetRouter.listModFiles(sub)
                val out = LuaTable(files.size, 0)
                for (i in files.indices) {
                    out.rawset(i + 1, LuaValue.valueOf(files[i]))
                }
                return out
            }
        })
        return asset
    }

    private fun buildAsmTable(): LuaTable {
        val asm = LuaTable()
        asm.set("patch", object : TwoArgFunction() {
            override fun call(orgArg: LuaValue, srcArg: LuaValue): LuaValue {
                val org = resolveAddressOrSymbol(orgArg) ?: return LuaValue.valueOf(0)
                val written = core.assembler.assembleSnippet(
                    orgAddress = org,
                    pdsSource = srcArg.checkjstring(),
                    baseSymbols = core.currentSymbolMap(),
                    targetRam = core.ram
                )
                return LuaValue.valueOf(written)
            }
        })
        return asm
    }

    private fun buildInputTable(): LuaTable {
        val inp = LuaTable()
        inp.set("get", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val t = LuaTable()
                t.set("up", LuaValue.valueOf(core.joyUp))
                t.set("down", LuaValue.valueOf(core.joyDown))
                t.set("left", LuaValue.valueOf(core.joyLeft))
                t.set("right", LuaValue.valueOf(core.joyRight))
                t.set("fire", LuaValue.valueOf(core.joyFire))
                t.set("f1", LuaValue.valueOf(core.keyF1Pressed))
                t.set("f7", LuaValue.valueOf(core.keyF7Pressed))
                t.set("pause", LuaValue.valueOf(core.keyPausePressed))
                t.set("cheat", LuaValue.valueOf(core.cheatEnabled))
                t.set("port", LuaValue.valueOf(core.joystickPort))
                return t
            }
        })

        inp.set("set", object : OneArgFunction() {
            override fun call(tblArg: LuaValue): LuaValue {
                val t = tblArg.checktable()
                t.get("up").takeIf { !it.isnil() }?.let { core.joyUp = it.toboolean() }
                t.get("down").takeIf { !it.isnil() }?.let { core.joyDown = it.toboolean() }
                t.get("left").takeIf { !it.isnil() }?.let { core.joyLeft = it.toboolean() }
                t.get("right").takeIf { !it.isnil() }?.let { core.joyRight = it.toboolean() }
                t.get("fire").takeIf { !it.isnil() }?.let { core.joyFire = it.toboolean() }
                t.get("f1").takeIf { !it.isnil() }?.let { core.keyF1Pressed = it.toboolean() }
                t.get("f7").takeIf { !it.isnil() }?.let { core.keyF7Pressed = it.toboolean() }
                t.get("pause").takeIf { !it.isnil() }?.let { core.keyPausePressed = it.toboolean() }
                t.get("cheat").takeIf { !it.isnil() }?.let {
                    val want = it.toboolean()
                    if (core.cheatEnabled != want) {
                        core.toggleCheatMode()
                    }
                }
                return LuaValue.NONE
            }
        })
        return inp
    }

    private fun readSym(name: String, offset: Int): Int {
        val base = core.resolveSymbol(name) ?: return 0
        return core.ram[(base + offset) and 0xFFFF].toInt() and 0xFFFF and 0xFF
    }

    private fun writeSym(name: String, offset: Int, value: Int) {
        val base = core.resolveSymbol(name) ?: return
        core.ram[(base + offset) and 0xFFFF] = (value and 0xFF).toByte()
    }
}
