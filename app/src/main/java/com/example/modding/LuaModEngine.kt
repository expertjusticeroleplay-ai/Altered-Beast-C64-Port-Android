package com.example.modding

import com.example.engine.C64ExecutionCore
import com.example.engine.ModExecutionListener
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

data class NamedLuaHook(
    val modName: String,
    val function: LuaFunction
)

class LuaModEngine(
    private val core: C64ExecutionCore,
    val assetRouter: ModAssetRouter
) : ModExecutionListener {

    private val canvasRenderer = ModCanvasRenderer(core.videoRenderer.framePixels, assetRouter)
    private val initHooks = ArrayList<NamedLuaHook>()
    private val beforeFrameHooks = ArrayList<NamedLuaHook>()
    private val afterFrameHooks = ArrayList<NamedLuaHook>()
    private val levelLoadHooks = ArrayList<NamedLuaHook>()
    private val scanlineHooks = ArrayList<NamedLuaHook>()
    private val drawHooks = ArrayList<NamedLuaHook>()
    private val exitHooks = ArrayList<NamedLuaHook>()

    private val pcHookMask = BooleanArray(65536)
    private val pcHookTable = HashMap<Int, MutableList<LuaFunction>>()

    override var hasBeforeFrameHooks: Boolean = false
        private set
    override var hasAfterFrameHooks: Boolean = false
        private set
    override var hasScanlineHooks: Boolean = false
        private set
    override var hasDrawHooks: Boolean = false
        private set
    override var hasPcHooks: Boolean = false
        private set

    val loadedModNames = ArrayList<String>()

    init {
        core.modListener = this
    }

    fun initializeFromStorage(customRootDir: File? = null): StorageInitializationReport {
        val report = ModStorageManager.initializeAndVerifyStorage(customRootDir)
        assetRouter.modsDir = report.modsDir
        loadLuaFiles(report.discoveredLuaFiles, report.modsDir)
        return report
    }

    fun loadLuaFiles(files: List<File>, baseModsDir: File = assetRouter.modsDir) {
        clearLoadedMods()
        assetRouter.modsDir = baseModsDir
        for (file in files) {
            val relName = runCatching {
                file.relativeTo(baseModsDir).invariantSeparatorsPath
            }.getOrElse { file.name }
            val scriptText = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { err ->
                SessionLogManager.logError("MODS", "Failed to read mod script $relName", err)
                continue
            }
            loadModScript(relName, scriptText)
        }
        refreshHookFlags()
    }

    fun loadModScript(modName: String, luaSourceCode: String): Boolean {
        return try {
            val globals = JsePlatform.standardGlobals()
            val apiBuilder = LuaHostApiBuilder(
                core = core,
                assetRouter = assetRouter,
                canvasRenderer = canvasRenderer,
                registerPcCallback = ::registerPcHook,
                clearPcCallbacks = ::clearPcHook
            )
            val gzTable = apiBuilder.buildGzTable(modName)
            globals.set("gz", gzTable)
            globals.set("print", gzTable.get("log"))

            val chunk = globals.load(luaSourceCode, modName)
            chunk.call()

            extractHookIfPresent(globals, modName, "on_init", initHooks)
            extractHookIfPresent(globals, modName, "on_before_frame", beforeFrameHooks)
            extractHookIfPresent(globals, modName, "on_frame", afterFrameHooks)
            extractHookIfPresent(globals, modName, "on_level_load", levelLoadHooks)
            extractHookIfPresent(globals, modName, "on_scanline", scanlineHooks)
            extractHookIfPresent(globals, modName, "on_draw", drawHooks)
            extractHookIfPresent(globals, modName, "on_exit", exitHooks)

            loadedModNames.add(modName)
            refreshHookFlags()
            SessionLogManager.logInfo("MODS", "Loaded Lua mod: $modName")
            true
        } catch (t: Throwable) {
            SessionLogManager.logError("MODS", "Error compiling or initializing Lua mod: $modName", t)
            false
        }
    }

    fun clearLoadedMods() {
        initHooks.clear()
        beforeFrameHooks.clear()
        afterFrameHooks.clear()
        levelLoadHooks.clear()
        scanlineHooks.clear()
        drawHooks.clear()
        exitHooks.clear()
        pcHookTable.clear()
        pcHookMask.fill(false)
        loadedModNames.clear()
        refreshHookFlags()
    }

    private fun extractHookIfPresent(
        globals: LuaValue,
        modName: String,
        fnName: String,
        targetList: MutableList<NamedLuaHook>
    ) {
        val v = globals.get(fnName)
        if (v.isfunction()) {
            targetList.add(NamedLuaHook(modName, v.checkfunction()))
        }
    }

    private fun registerPcHook(address: Int, fn: LuaFunction) {
        val addr = address and 0xFFFF
        val list = pcHookTable.getOrPut(addr) { ArrayList(2) }
        list.add(fn)
        pcHookMask[addr] = true
        hasPcHooks = true
    }

    private fun clearPcHook(address: Int) {
        val addr = address and 0xFFFF
        pcHookTable.remove(addr)
        pcHookMask[addr] = false
        hasPcHooks = pcHookTable.isNotEmpty()
    }

    private fun refreshHookFlags() {
        hasBeforeFrameHooks = beforeFrameHooks.isNotEmpty()
        hasAfterFrameHooks = afterFrameHooks.isNotEmpty()
        hasScanlineHooks = scanlineHooks.isNotEmpty()
        hasDrawHooks = drawHooks.isNotEmpty()
        hasPcHooks = pcHookTable.isNotEmpty()
    }

    override fun isPcHooked(pc: Int): Boolean {
        return pcHookMask[pc and 0xFFFF]
    }

    override fun onEngineInitialized() {
        for (i in 0 until initHooks.size) {
            val hook = initHooks[i]
            try {
                hook.function.call()
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_init()", t)
            }
        }
    }

    override fun onBeforeFrame(frameCount: Long, inGame: Boolean, currentLevel: Int) {
        val fVal = LuaInteger.valueOf(frameCount.toInt())
        val gVal = LuaBoolean.valueOf(inGame)
        val lVal = LuaInteger.valueOf(currentLevel)
        for (i in 0 until beforeFrameHooks.size) {
            val hook = beforeFrameHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_before_frame()", t)
            }
        }
    }

    override fun onAfterFrame(frameCount: Long, inGame: Boolean, currentLevel: Int) {
        val fVal = LuaInteger.valueOf(frameCount.toInt())
        val gVal = LuaBoolean.valueOf(inGame)
        val lVal = LuaInteger.valueOf(currentLevel)
        for (i in 0 until afterFrameHooks.size) {
            val hook = afterFrameHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_frame()", t)
            }
        }
    }

    override fun onScanline(rasterY: Int, inGame: Boolean, currentLevel: Int) {
        val rVal = LuaInteger.valueOf(rasterY)
        val gVal = LuaBoolean.valueOf(inGame)
        val lVal = LuaInteger.valueOf(currentLevel)
        for (i in 0 until scanlineHooks.size) {
            val hook = scanlineHooks[i]
            try {
                hook.function.call(rVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_scanline()", t)
            }
        }
    }

    override fun onDraw(frameCount: Long, inGame: Boolean, currentLevel: Int) {
        val fVal = LuaInteger.valueOf(frameCount.toInt())
        val gVal = LuaBoolean.valueOf(inGame)
        val lVal = LuaInteger.valueOf(currentLevel)
        for (i in 0 until drawHooks.size) {
            val hook = drawHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_draw()", t)
            }
        }
    }

    override fun onLevelLoad(levelId: Int, fileId: Int) {
        val lVal = LuaInteger.valueOf(levelId)
        val fVal = LuaInteger.valueOf(fileId)
        for (i in 0 until levelLoadHooks.size) {
            val hook = levelLoadHooks[i]
            try {
                hook.function.call(lVal, fVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_level_load()", t)
            }
        }
    }

    override fun onPcHook(pc: Int): Boolean {
        val addr = pc and 0xFFFF
        val callbacks = pcHookTable[addr] ?: return false
        val pcVal = LuaInteger.valueOf(addr)
        var skipOriginal = false
        for (i in 0 until callbacks.size) {
            val fn = callbacks[i]
            try {
                val res = fn.call(pcVal)
                if ((res.isboolean() && res.toboolean()) || (core.regPC and 0xFFFF) != addr) {
                    skipOriginal = true
                }
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD_PC", "Error in PC hook at 0x${Integer.toHexString(addr)}", t)
            }
        }
        return skipOriginal
    }

    fun onApplicationExit() {
        for (i in 0 until exitHooks.size) {
            val hook = exitHooks[i]
            try {
                hook.function.call()
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_exit()", t)
            }
        }
        SessionLogManager.flushToDisk(assetRouter.modsDir)
    }
}
