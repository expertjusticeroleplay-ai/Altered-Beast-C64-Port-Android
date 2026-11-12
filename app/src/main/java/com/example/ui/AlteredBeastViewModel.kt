package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.AndroidAssetProvider
import com.example.engine.C64ExecutionCore
import com.example.engine.PdsNativeAssembler
import com.example.engine.SidAudioSynthesizer
import com.example.engine.VicIIVideoRenderer
import com.example.modding.LuaModEngine
import com.example.modding.ModAssetRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class AlteredBeastUiState(
    val frameBitmap: ImageBitmap? = null,
    val frameTick: Long = 0L,
    val isEngineReady: Boolean = false,
    val isInGameMode: Boolean = false,
    val currentLevel: Int = 1,
    val deviceFpsCap: Int = 60,
    val activeRenderFps: Int = 60,
    val joystickPort: Int = 2,
    val soundEnabled: Boolean = true,
    val controlsVisible: Boolean = true,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,
    val firePressed: Boolean = false,
    val f1Pressed: Boolean = false,
    val f7Pressed: Boolean = false,
    val pausePressed: Boolean = false,
    val cheatPressed: Boolean = false
)

class AlteredBeastViewModel(application: Application) : AndroidViewModel(application) {

    private val outputBitmaps = arrayOf(
        Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
    )
    private var activeBitmapIndex = 0

    private val _uiState = MutableStateFlow(
        AlteredBeastUiState(
            frameBitmap = outputBitmaps[0].asImageBitmap()
        )
    )
    val uiState: StateFlow<AlteredBeastUiState> = _uiState.asStateFlow()

    private val baseAssetProvider = AndroidAssetProvider(application.assets)
    val modAssetRouter = ModAssetRouter(baseAssetProvider)
    private val assembler = PdsNativeAssembler(modAssetRouter)
    private val videoRenderer = VicIIVideoRenderer()
    private val sidSynthesizer = SidAudioSynthesizer()
    val executionCore = C64ExecutionCore(
        assembler = assembler,
        assetProvider = modAssetRouter,
        videoRenderer = videoRenderer,
        sidSynthesizer = sidSynthesizer
    )
    val luaModEngine = LuaModEngine(executionCore, modAssetRouter)

    private var engineJob: Job? = null

    @Volatile
    private var targetDeviceRefreshRate: Float = 60f

    init {
        startNativeEngineLoop()
    }

    fun detectDeviceRefreshRateCap(context: Context, activityDisplay: Display? = null) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = activityDisplay ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            var maxRate = display.refreshRate
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > maxRate) {
                        maxRate = mode.refreshRate
                    }
                }
            }
            val clampedRate = maxRate.coerceIn(30f, 240f)
            targetDeviceRefreshRate = clampedRate
            val capInt = clampedRate.roundToInt()
            _uiState.update {
                it.copy(
                    deviceFpsCap = capInt,
                    activeRenderFps = capInt
                )
            }
        }
    }

    fun updateDisplayAspectRatio(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            val ratio = (width / height).coerceIn(1.2f, 2.6f)
            if (kotlin.math.abs(videoRenderer.displayAspectRatio - ratio) > 0.005f) {
                videoRenderer.displayAspectRatio = ratio
            }
        }
    }

    private fun publishFrameBitmap(): ImageBitmap {
        val nextIndex = activeBitmapIndex xor 1
        val targetBmp = outputBitmaps[nextIndex]
        targetBmp.setPixels(videoRenderer.framePixels, 0, 320, 0, 0, 320, 200)
        targetBmp.prepareToDraw()
        activeBitmapIndex = nextIndex
        return targetBmp.asImageBitmap()
    }

    private fun startNativeEngineLoop() {
        if (engineJob != null) return
        val appContext = getApplication<Application>()
        engineJob = viewModelScope.launch(Dispatchers.Default) {
            luaModEngine.initializeFromStorage()
            executionCore.initializeEngine()
            sidSynthesizer.start(appContext)
            executionCore.runSinglePalFrame()
            val initialComposeBitmap = publishFrameBitmap()
            _uiState.update {
                it.copy(
                    frameBitmap = initialComposeBitmap,
                    frameTick = 1L,
                    isEngineReady = true,
                    isInGameMode = executionCore.inGameMode,
                    currentLevel = executionCore.currentLoadedLevel
                )
            }

            val palStepNs = 20_000_000L
            var lastTimeNs = System.nanoTime()
            var palAccumulatorNs = 0L
            var tickCounter = 1L
            var fpsFrameCounter = 0
            var fpsWindowStartNs = lastTimeNs

            while (isActive) {
                val fpsCap = targetDeviceRefreshRate.coerceIn(30f, 240f)
                val renderStepNs = (1_000_000_000f / fpsCap).toLong().coerceAtLeast(4_166_666L)
                val nowNs = System.nanoTime()
                val elapsedNs = (nowNs - lastTimeNs).coerceIn(0L, 60_000_000L)
                lastTimeNs = nowNs
                palAccumulatorNs += elapsedNs

                var steppedPal = false
                var stepsThisCycle = 0
                while (palAccumulatorNs >= palStepNs && stepsThisCycle < 2) {
                    executionCore.runSinglePalFrame()
                    palAccumulatorNs -= palStepNs
                    steppedPal = true
                    stepsThisCycle++
                }
                if (palAccumulatorNs >= palStepNs * 2) {
                    palAccumulatorNs = palStepNs - 1L
                }

                var updatedComposeBmp: ImageBitmap? = null
                if (steppedPal) {
                    updatedComposeBmp = publishFrameBitmap()
                    tickCounter++
                }

                fpsFrameCounter++
                val windowElapsedNs = nowNs - fpsWindowStartNs
                var measuredFps = _uiState.value.activeRenderFps
                if (windowElapsedNs >= 500_000_000L) {
                    measuredFps = ((fpsFrameCounter * 1_000_000L) / (windowElapsedNs / 1_000L)).toInt()
                        .coerceIn(1, fpsCap.roundToInt())
                    fpsFrameCounter = 0
                    fpsWindowStartNs = nowNs
                }

                if (steppedPal || windowElapsedNs >= 500_000_000L) {
                    _uiState.update { state ->
                        state.copy(
                            frameBitmap = updatedComposeBmp ?: state.frameBitmap,
                            frameTick = tickCounter,
                            isInGameMode = executionCore.inGameMode,
                            currentLevel = executionCore.currentLoadedLevel,
                            pausePressed = executionCore.keyPausePressed,
                            cheatPressed = executionCore.cheatEnabled,
                            deviceFpsCap = fpsCap.roundToInt(),
                            activeRenderFps = measuredFps
                        )
                    }
                }

                val afterWorkNs = System.nanoTime()
                val workDurationNs = afterWorkNs - nowNs
                val timeToNextPalNs = (palStepNs - palAccumulatorNs).coerceAtLeast(1_000_000L)
                val targetStepNs = minOf(renderStepNs, timeToNextPalNs)
                val sleepNs = targetStepNs - workDurationNs
                if (sleepNs > 1_000_000L) {
                    delay(sleepNs / 1_000_000L)
                } else {
                    delay(1L)
                }
            }
        }
    }

    fun updateDpad(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        executionCore.joyUp = up
        executionCore.joyDown = down
        executionCore.joyLeft = left
        executionCore.joyRight = right
        _uiState.update {
            it.copy(
                dpadUp = up,
                dpadDown = down,
                dpadLeft = left,
                dpadRight = right
            )
        }
    }

    fun setFirePressed(pressed: Boolean) {
        executionCore.joyFire = pressed
        _uiState.update { it.copy(firePressed = pressed) }
    }

    fun setF1Pressed(pressed: Boolean) {
        executionCore.keyF1Pressed = pressed
        _uiState.update { it.copy(f1Pressed = pressed) }
    }

    fun setF7Pressed(pressed: Boolean) {
        executionCore.keyF7Pressed = pressed
        _uiState.update { it.copy(f7Pressed = pressed) }
    }

    fun setPausePressed(pressed: Boolean) {
        if (pressed) {
            val active = executionCore.togglePauseMode()
            _uiState.update { it.copy(pausePressed = active) }
        }
    }

    fun setCheatPressed(pressed: Boolean) {
        if (pressed) {
            val active = executionCore.toggleCheatMode()
            _uiState.update { it.copy(cheatPressed = active) }
        }
    }

    fun pressRestoreKey() {
        executionCore.triggerRestoreKey()
        _uiState.update {
            it.copy(
                pausePressed = false,
                cheatPressed = false
            )
        }
    }

    fun skipIntroToGame() {
        if (!executionCore.inGameMode) {
            executionCore.bootGameProgram()
        }
    }

    fun toggleJoystickPort() {
        val nextPort = if (executionCore.joystickPort == 2) 1 else 2
        executionCore.joystickPort = nextPort
        _uiState.update { it.copy(joystickPort = nextPort) }
    }

    fun toggleSound() {
        val nextSound = !sidSynthesizer.soundEnabled
        sidSynthesizer.soundEnabled = nextSound
        _uiState.update { it.copy(soundEnabled = nextSound) }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        sidSynthesizer.setAppForeground(inForeground)
    }

    fun refreshStorageAndMods() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedNewMods = luaModEngine.refreshFromStorageIfChanged()
            if (loadedNewMods && _uiState.value.isEngineReady) {
                luaModEngine.onEngineInitialized()
            }
        }
    }

    fun flushSessionLogOnExit() {
        luaModEngine.onApplicationExit()
    }

    override fun onCleared() {
        flushSessionLogOnExit()
        super.onCleared()
        engineJob?.cancel()
        sidSynthesizer.stop()
    }
}
