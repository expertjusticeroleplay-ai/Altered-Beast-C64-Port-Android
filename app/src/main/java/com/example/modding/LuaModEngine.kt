package com.example.modding

import com.example.engine.C64ExecutionCore
import com.example.engine.ModExecutionListener
import org.luaj.vm2.LuaFunction
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

    private val engineLock = Any()
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

    @Volatile
    override var hasBeforeFrameHooks: Boolean = false
        private set

    @Volatile
    override var hasAfterFrameHooks: Boolean = false
        private set

    @Volatile
    override var hasScanlineHooks: Boolean = false
        private set

    @Volatile
    override var hasDrawHooks: Boolean = false
        private set

    @Volatile
    override var hasPcHooks: Boolean = false
        private set

    val loadedModNames = ArrayList<String>()

    @Volatile
    private var lastStorageSignature: String? = null

    init {
        val preload = LuaValue.NIL
        if (preload.isnil()) {
            LuaValue.valueOf(0)
            LuaValue.valueOf(true)
            LuaValue.valueOf("")
        }
        core.modListener = this
    }

    fun initializeFromStorage(
        customRootDir: File? = null,
        resetLogSession: Boolean = false
    ): StorageInitializationReport = synchronized(engineLock) {
        val report = ModStorageManager.initializeAndVerifyStorage(customRootDir, resetLogSession)
        assetRouter.modsDir = report.modsDir
        loadLuaFiles(report.discoveredLuaFiles, report.modsDir)
        lastStorageSignature = computeStorageSignature(report.modsDir, report.guideFile, report.discoveredLuaFiles)
        report
    }

    fun refreshFromStorageIfChanged(customRootDir: File? = null): Boolean = synchronized(engineLock) {
        val rootDir = customRootDir ?: ModStorageManager.resolveDefaultRootDirectory()
        val gzBeastDir = File(rootDir, ModStorageManager.GZBEAST_FOLDER_NAME)
        val modsDir = File(gzBeastDir, ModStorageManager.MODS_FOLDER_NAME)
        val guideFile = File(modsDir, ModStorageManager.GUIDE_FILE_NAME)

        if (lastStorageSignature != null &&
            gzBeastDir.exists() &&
            modsDir.exists() &&
            guideFile.exists() &&
            guideFile.length() == GuideDocumentation.EXPECTED_BYTE_SIZE
        ) {
            val currentLuaFiles = ModStorageManager.discoverLuaScripts(modsDir)
            val currentSig = computeStorageSignature(modsDir, guideFile, currentLuaFiles)
            if (currentSig == lastStorageSignature) {
                return false
            }
        }

        val report = initializeFromStorage(customRootDir, resetLogSession = false)
        return report.discoveredLuaFiles.isNotEmpty()
    }

    private fun computeStorageSignature(modsDir: File, guideFile: File, luaFiles: List<File>): String {
        val sb = StringBuilder()
        sb.append(modsDir.absolutePath).append('|')
        sb.append(if (guideFile.exists()) guideFile.length() else -1L).append('|')
        for (f in luaFiles) {
            sb.append(f.absolutePath).append(':').append(f.length()).append(':').append(f.lastModified()).append(';')
        }
        return sb.toString()
    }

    fun loadLuaFiles(files: List<File>, baseModsDir: File = assetRouter.modsDir) = synchronized(engineLock) {
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

    fun loadModScript(modName: String, luaSourceCode: String): Boolean = synchronized(engineLock) {
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

    fun clearLoadedMods() = synchronized(engineLock) {
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

    private fun registerPcHook(address: Int, fn: LuaFunction) = synchronized(engineLock) {
        val addr = address and 0xFFFF
        val list = pcHookTable.getOrPut(addr) { ArrayList(2) }
        list.add(fn)
        pcHookMask[addr] = true
        hasPcHooks = true
    }

    private fun clearPcHook(address: Int) = synchronized(engineLock) {
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

    override fun onEngineInitialized() = synchronized(engineLock) {
        if (initHooks.isEmpty()) return
        for (i in 0 until initHooks.size) {
            val hook = initHooks[i]
            try {
                hook.function.call()
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_init()", t)
            }
        }
    }

    override fun onBeforeFrame(frameCount: Long, inGame: Boolean, currentLevel: Int) = synchronized(engineLock) {
        if (beforeFrameHooks.isEmpty()) return
        val fVal = LuaValue.valueOf(frameCount.toInt())
        val gVal = LuaValue.valueOf(inGame)
        val lVal = LuaValue.valueOf(currentLevel)
        for (i in 0 until beforeFrameHooks.size) {
            val hook = beforeFrameHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_before_frame()", t)
            }
        }
    }

    override fun onAfterFrame(frameCount: Long, inGame: Boolean, currentLevel: Int) = synchronized(engineLock) {
        if (afterFrameHooks.isEmpty()) return
        val fVal = LuaValue.valueOf(frameCount.toInt())
        val gVal = LuaValue.valueOf(inGame)
        val lVal = LuaValue.valueOf(currentLevel)
        for (i in 0 until afterFrameHooks.size) {
            val hook = afterFrameHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_frame()", t)
            }
        }
    }

    override fun onScanline(rasterY: Int, inGame: Boolean, currentLevel: Int) = synchronized(engineLock) {
        if (scanlineHooks.isEmpty()) return
        val rVal = LuaValue.valueOf(rasterY)
        val gVal = LuaValue.valueOf(inGame)
        val lVal = LuaValue.valueOf(currentLevel)
        for (i in 0 until scanlineHooks.size) {
            val hook = scanlineHooks[i]
            try {
                hook.function.call(rVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_scanline()", t)
            }
        }
    }

    override fun onDraw(frameCount: Long, inGame: Boolean, currentLevel: Int) = synchronized(engineLock) {
        if (drawHooks.isEmpty()) return
        val fVal = LuaValue.valueOf(frameCount.toInt())
        val gVal = LuaValue.valueOf(inGame)
        val lVal = LuaValue.valueOf(currentLevel)
        for (i in 0 until drawHooks.size) {
            val hook = drawHooks[i]
            try {
                hook.function.call(fVal, gVal, lVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_draw()", t)
            }
        }
    }

    override fun onLevelLoad(levelId: Int, fileId: Int) = synchronized(engineLock) {
        if (levelLoadHooks.isEmpty()) return
        val lVal = LuaValue.valueOf(levelId)
        val fVal = LuaValue.valueOf(fileId)
        for (i in 0 until levelLoadHooks.size) {
            val hook = levelLoadHooks[i]
            try {
                hook.function.call(lVal, fVal)
            } catch (t: Throwable) {
                SessionLogManager.logError("MOD:${hook.modName}", "Error in on_level_load()", t)
            }
        }
    }

    override fun onPcHook(pc: Int): Boolean = synchronized(engineLock) {
        val addr = pc and 0xFFFF
        val callbacks = pcHookTable[addr]
        if (callbacks == null || callbacks.isEmpty()) return false
        val pcVal = LuaValue.valueOf(addr)
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

    fun onApplicationExit() = synchronized(engineLock) {
        if (exitHooks.isNotEmpty()) {
            for (i in 0 until exitHooks.size) {
                val hook = exitHooks[i]
                try {
                    hook.function.call()
                } catch (t: Throwable) {
                    SessionLogManager.logError("MOD:${hook.modName}", "Error in on_exit()", t)
                }
            }
        }
        SessionLogManager.flushToDisk(assetRouter.modsDir)
    }
}
