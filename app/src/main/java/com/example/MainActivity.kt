package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.modding.ModStorageManager
import com.example.ui.AlteredBeastUiState
import com.example.ui.AlteredBeastViewModel
import com.example.ui.FloatingSvgControls
import com.example.ui.theme.AlteredBeastTheme
import com.example.ui.theme.ArcadeObsidian

class MainActivity : ComponentActivity() {

    private val viewModel: AlteredBeastViewModel by viewModels()

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStorageAndMods()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveDisplayAndRefreshRate()
        ensureModStoragePermissions()

        setContent {
            AlteredBeastTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                AlteredBeastScreen(
                    uiState = uiState,
                    onDpadChange = viewModel::updateDpad,
                    onFireChange = viewModel::setFirePressed,
                    onF1Change = viewModel::setF1Pressed,
                    onF7Change = viewModel::setF7Pressed,
                    onPauseChange = viewModel::setPausePressed,
                    onCheatChange = viewModel::setCheatPressed,
                    onRestoreClick = viewModel::pressRestoreKey,
                    onPortToggle = viewModel::toggleJoystickPort,
                    onSoundToggle = viewModel::toggleSound,
                    onControlsToggle = viewModel::toggleControlsVisibility
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegroundChanged(true)
        configureImmersiveDisplayAndRefreshRate()
        if (ModStorageManager.hasStorageAccessPermission(this)) {
            viewModel.refreshStorageAndMods()
        }
    }

    override fun onStop() {
        viewModel.onAppForegroundChanged(false)
        viewModel.flushSessionLogOnExit()
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.onAppForegroundChanged(false)
        viewModel.flushSessionLogOnExit()
        super.onDestroy()
    }

    private fun ensureModStoragePermissions() {
        if (ModStorageManager.hasStorageAccessPermission(this)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }.onFailure {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun configureImmersiveDisplayAndRefreshRate() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { display }.getOrNull() ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        }
        if (currentDisplay != null) {
            val modes = currentDisplay.supportedModes
            val highestMode = modes.maxByOrNull { it.refreshRate }
            if (highestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = highestMode.modeId
                params.preferredRefreshRate = highestMode.refreshRate
                window.attributes = params
            }
            viewModel.detectDeviceRefreshRateCap(this, currentDisplay)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                viewModel.updateDpad(true, state.dpadDown, state.dpadLeft, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                viewModel.updateDpad(state.dpadUp, true, state.dpadLeft, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                viewModel.updateDpad(state.dpadUp, state.dpadDown, true, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                viewModel.updateDpad(state.dpadUp, state.dpadDown, state.dpadLeft, true)
                true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                viewModel.setFirePressed(true)
                true
            }
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_BUTTON_START -> {
                viewModel.setF1Pressed(true)
                true
            }
            KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                viewModel.setF7Pressed(true)
                true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_BREAK -> {
                if ((event?.repeatCount ?: 0) == 0) {
                    viewModel.setPausePressed(true)
                }
                true
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ESCAPE -> {
                viewModel.pressRestoreKey()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                viewModel.updateDpad(false, state.dpadDown, state.dpadLeft, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                viewModel.updateDpad(state.dpadUp, false, state.dpadLeft, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                viewModel.updateDpad(state.dpadUp, state.dpadDown, false, state.dpadRight)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                viewModel.updateDpad(state.dpadUp, state.dpadDown, state.dpadLeft, false)
                true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                viewModel.setFirePressed(false)
                true
            }
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_BUTTON_START -> {
                viewModel.setF1Pressed(false)
                true
            }
            KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                viewModel.setF7Pressed(false)
                true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_BREAK -> {
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}

@Composable
fun AlteredBeastScreen(
    uiState: AlteredBeastUiState,
    onDpadChange: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onFireChange: (Boolean) -> Unit,
    onF1Change: (Boolean) -> Unit,
    onF7Change: (Boolean) -> Unit,
    onPauseChange: (Boolean) -> Unit,
    onCheatChange: (Boolean) -> Unit,
    onRestoreClick: () -> Unit,
    onPortToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onControlsToggle: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = ArcadeObsidian
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ArcadeObsidian)
        ) {
            val tick = uiState.frameTick
            val frameImg = uiState.frameBitmap
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("c64_screen_canvas")
            ) {
                if (frameImg != null && tick >= 0L) {
                    drawImage(
                        image = frameImg,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(320, 200),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.None
                    )
                }
            }

            FloatingSvgControls(
                uiState = uiState,
                onDpadChange = onDpadChange,
                onFireChange = onFireChange,
                onF1Change = onF1Change,
                onF7Change = onF7Change,
                onPauseChange = onPauseChange,
                onCheatChange = onCheatChange,
                onRestoreClick = onRestoreClick,
                onPortToggle = onPortToggle,
                onSoundToggle = onSoundToggle,
                onControlsToggle = onControlsToggle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
