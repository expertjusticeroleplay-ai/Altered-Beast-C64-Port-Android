package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ArcadeCrimson
import com.example.ui.theme.ArcadeCyan
import com.example.ui.theme.ArcadeEmerald
import com.example.ui.theme.ArcadeGold
import com.example.ui.theme.ArcadeObsidian
import com.example.ui.theme.ArcadeTextPrimary
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun FloatingSvgControls(
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
    onControlsToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.controlsVisible) {
                    FloatingSvgPillButton(
                        label = stringResource(R.string.control_f1_one_player),
                        isActive = uiState.f1Pressed,
                        accentColor = ArcadeGold,
                        iconType = SvgActionIcon.ONE_PLAYER,
                        testTag = "f1_button",
                        onPressStateChange = onF1Change
                    )
                    FloatingSvgPillButton(
                        label = stringResource(R.string.control_f7_two_players),
                        isActive = uiState.f7Pressed,
                        accentColor = ArcadeCyan,
                        iconType = SvgActionIcon.TWO_PLAYERS,
                        testTag = "f7_button",
                        onPressStateChange = onF7Change
                    )
                    FloatingSvgPillButton(
                        label = if (uiState.pausePressed) "PAUSED" else stringResource(R.string.control_pause),
                        isActive = uiState.pausePressed,
                        accentColor = ArcadeGold,
                        iconType = SvgActionIcon.PAUSE,
                        testTag = "pause_button",
                        onPressStateChange = { pressed ->
                            if (pressed) onPauseChange(true)
                        }
                    )
                    FloatingSvgPillButton(
                        label = stringResource(R.string.control_restore),
                        isActive = false,
                        accentColor = ArcadeCrimson,
                        iconType = SvgActionIcon.RESTORE,
                        testTag = "restore_button",
                        onPressStateChange = { pressed ->
                            if (pressed) onRestoreClick()
                        }
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.controlsVisible) {
                        FloatingSvgPillButton(
                            label = if (uiState.cheatPressed) "CHEAT ON" else stringResource(R.string.control_cheat),
                            isActive = uiState.cheatPressed,
                            accentColor = ArcadeEmerald,
                            iconType = SvgActionIcon.CHEAT,
                            testTag = "cheat_button",
                            onPressStateChange = { pressed ->
                                if (pressed) onCheatChange(true)
                            }
                        )
                        FloatingSvgPillButton(
                            label = if (uiState.joystickPort == 2) {
                                stringResource(R.string.control_port_2)
                            } else {
                                stringResource(R.string.control_port_1)
                            },
                            isActive = uiState.joystickPort == 2,
                            accentColor = ArcadeCyan,
                            iconType = SvgActionIcon.JOYSTICK_PORT,
                            testTag = "port_button",
                            onPressStateChange = { pressed ->
                                if (pressed) onPortToggle()
                            }
                        )
                        FloatingSvgPillButton(
                            label = stringResource(R.string.control_audio),
                            isActive = uiState.soundEnabled,
                            accentColor = ArcadeGold,
                            iconType = SvgActionIcon.SOUND,
                            testTag = "sound_button",
                            onPressStateChange = { pressed ->
                                if (pressed) onSoundToggle()
                            }
                        )
                    }

                    FloatingSvgPillButton(
                        label = "HUD",
                        isActive = uiState.controlsVisible,
                        accentColor = ArcadeTextPrimary,
                        iconType = SvgActionIcon.VISIBILITY,
                        testTag = "controls_toggle_button",
                        onPressStateChange = { pressed ->
                            if (pressed) onControlsToggle()
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .testTag("fps_cap_badge")
                        .background(
                            color = ArcadeObsidian.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = ArcadeGold.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${uiState.activeRenderFps}/${uiState.deviceFpsCap} FPS",
                        color = ArcadeGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (uiState.controlsVisible) {
            FloatingSvgEightWayDpad(
                up = uiState.dpadUp,
                down = uiState.dpadDown,
                left = uiState.dpadLeft,
                right = uiState.dpadRight,
                onDirectionChange = onDpadChange,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 20.dp)
                    .size(164.dp)
                    .testTag("dpad_control")
            )

            FloatingSvgFireButton(
                isPressed = uiState.firePressed,
                onPressChange = onFireChange,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 28.dp)
                    .size(124.dp)
                    .testTag("fire_button")
            )
        }
    }
}

private enum class SvgActionIcon {
    ONE_PLAYER,
    TWO_PLAYERS,
    PAUSE,
    RESTORE,
    CHEAT,
    JOYSTICK_PORT,
    SOUND,
    VISIBILITY
}

@Composable
private fun FloatingSvgPillButton(
    label: String,
    isActive: Boolean,
    accentColor: Color,
    iconType: SvgActionIcon,
    testTag: String,
    onPressStateChange: (Boolean) -> Unit
) {
    val bgAlpha = if (isActive) 0.48f else 0.28f
    val borderAlpha = if (isActive) 0.95f else 0.50f

    Row(
        modifier = Modifier
            .testTag(testTag)
            .height(48.dp)
            .background(
                color = ArcadeObsidian.copy(alpha = bgAlpha),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(testTag) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressStateChange(true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                    } while (event.changes.any { it.pressed })
                    onPressStateChange(false)
                }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (iconType) {
                SvgActionIcon.ONE_PLAYER -> {
                    drawCircle(
                        color = accentColor,
                        radius = w * 0.22f,
                        center = Offset(w * 0.5f, h * 0.32f),
                        style = stroke
                    )
                    val arcPath = Path().apply {
                        moveTo(w * 0.2f, h * 0.85f)
                        quadraticTo(w * 0.5f, h * 0.52f, w * 0.8f, h * 0.85f)
                    }
                    drawPath(arcPath, color = accentColor, style = stroke)
                }
                SvgActionIcon.TWO_PLAYERS -> {
                    drawCircle(
                        color = accentColor,
                        radius = w * 0.17f,
                        center = Offset(w * 0.35f, h * 0.32f),
                        style = stroke
                    )
                    drawCircle(
                        color = accentColor,
                        radius = w * 0.17f,
                        center = Offset(w * 0.68f, h * 0.32f),
                        style = stroke
                    )
                    val p1 = Path().apply {
                        moveTo(w * 0.12f, h * 0.85f)
                        quadraticTo(w * 0.35f, h * 0.55f, w * 0.58f, h * 0.85f)
                    }
                    val p2 = Path().apply {
                        moveTo(w * 0.45f, h * 0.85f)
                        quadraticTo(w * 0.68f, h * 0.55f, w * 0.90f, h * 0.85f)
                    }
                    drawPath(p1, color = accentColor, style = stroke)
                    drawPath(p2, color = accentColor, style = stroke)
                }
                SvgActionIcon.PAUSE -> {
                    drawLine(
                        color = accentColor,
                        start = Offset(w * 0.35f, h * 0.22f),
                        end = Offset(w * 0.35f, h * 0.78f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(w * 0.65f, h * 0.22f),
                        end = Offset(w * 0.65f, h * 0.78f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                SvgActionIcon.RESTORE -> {
                    val path = Path().apply {
                        moveTo(w * 0.25f, h * 0.35f)
                        lineTo(w * 0.25f, h * 0.15f)
                        lineTo(w * 0.45f, h * 0.15f)
                    }
                    drawPath(path, color = accentColor, style = stroke)
                    drawArc(
                        color = accentColor,
                        startAngle = -140f,
                        sweepAngle = 280f,
                        useCenter = false,
                        style = stroke
                    )
                }
                SvgActionIcon.CHEAT -> {
                    val bolt = Path().apply {
                        moveTo(w * 0.58f, h * 0.12f)
                        lineTo(w * 0.26f, h * 0.52f)
                        lineTo(w * 0.50f, h * 0.52f)
                        lineTo(w * 0.40f, h * 0.88f)
                        lineTo(w * 0.74f, h * 0.46f)
                        lineTo(w * 0.50f, h * 0.46f)
                        close()
                    }
                    drawPath(bolt, color = accentColor, style = stroke)
                }
                SvgActionIcon.JOYSTICK_PORT -> {
                    drawLine(
                        color = accentColor,
                        start = Offset(w * 0.5f, h * 0.25f),
                        end = Offset(w * 0.5f, h * 0.72f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = accentColor,
                        radius = w * 0.16f,
                        center = Offset(w * 0.5f, h * 0.22f)
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(w * 0.22f, h * 0.78f),
                        end = Offset(w * 0.78f, h * 0.78f),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                SvgActionIcon.SOUND -> {
                    val spk = Path().apply {
                        moveTo(w * 0.18f, h * 0.40f)
                        lineTo(w * 0.38f, h * 0.40f)
                        lineTo(w * 0.62f, h * 0.20f)
                        lineTo(w * 0.62f, h * 0.80f)
                        lineTo(w * 0.38f, h * 0.60f)
                        lineTo(w * 0.18f, h * 0.60f)
                        close()
                    }
                    drawPath(spk, color = accentColor, style = stroke)
                    if (isActive) {
                        drawArc(
                            color = accentColor,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = stroke
                        )
                    } else {
                        drawLine(
                            color = ArcadeCrimson,
                            start = Offset(w * 0.72f, h * 0.30f),
                            end = Offset(w * 0.92f, h * 0.70f),
                            strokeWidth = 1.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                SvgActionIcon.VISIBILITY -> {
                    val eye = Path().apply {
                        moveTo(w * 0.10f, h * 0.50f)
                        quadraticTo(w * 0.50f, h * 0.15f, w * 0.90f, h * 0.50f)
                        quadraticTo(w * 0.50f, h * 0.85f, w * 0.10f, h * 0.50f)
                    }
                    drawPath(eye, color = accentColor, style = stroke)
                    drawCircle(
                        color = accentColor,
                        radius = w * 0.14f,
                        center = Offset(w * 0.50f, h * 0.50f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (isActive) accentColor else ArcadeTextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FloatingSvgEightWayDpad(
    up: Boolean,
    down: Boolean,
    left: Boolean,
    right: Boolean,
    onDirectionChange: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val downEvent = awaitFirstDown(requireUnconsumed = false)
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val deadZone = size.width * 0.12f

                    fun updateFromOffset(pos: Offset) {
                        val dx = pos.x - centerX
                        val dy = pos.y - centerY
                        val dist = hypot(dx, dy)
                        if (dist < deadZone) {
                            onDirectionChange(false, false, false, false)
                            return
                        }
                        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val isRight = deg > -67.5f && deg < 67.5f
                        val isDown = deg > 22.5f && deg < 157.5f
                        val isLeft = deg > 112.5f || deg < -112.5f
                        val isUp = deg < -35f && deg > -145f
                        onDirectionChange(isUp, isDown, isLeft, isRight)
                    }

                    updateFromOffset(downEvent.position)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val activeChange = event.changes.firstOrNull { it.pressed }
                        if (activeChange != null) {
                            updateFromOffset(activeChange.position)
                        }
                    } while (event.changes.any { it.pressed })
                    onDirectionChange(false, false, false, false)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val outerRadius = w * 0.48f

            drawCircle(
                color = ArcadeObsidian.copy(alpha = 0.34f),
                radius = outerRadius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = ArcadeGold.copy(alpha = 0.42f),
                radius = outerRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )

            val armHalfWidth = w * 0.14f
            val armOuter = w * 0.42f
            val crossPath = Path().apply {
                moveTo(cx - armHalfWidth, cy - armOuter)
                lineTo(cx + armHalfWidth, cy - armOuter)
                lineTo(cx + armHalfWidth, cy - armHalfWidth)
                lineTo(cx + armOuter, cy - armHalfWidth)
                lineTo(cx + armOuter, cy + armHalfWidth)
                lineTo(cx + armHalfWidth, cy + armHalfWidth)
                lineTo(cx + armHalfWidth, cy + armOuter)
                lineTo(cx - armHalfWidth, cy + armOuter)
                lineTo(cx - armHalfWidth, cy + armHalfWidth)
                lineTo(cx - armOuter, cy + armHalfWidth)
                lineTo(cx - armOuter, cy - armHalfWidth)
                lineTo(cx - armHalfWidth, cy - armHalfWidth)
                close()
            }
            drawPath(
                path = crossPath,
                color = ArcadeObsidian.copy(alpha = 0.48f)
            )
            drawPath(
                path = crossPath,
                color = ArcadeGold.copy(alpha = 0.55f),
                style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round)
            )

            val upArrow = Path().apply {
                moveTo(cx, cy - w * 0.36f)
                lineTo(cx - w * 0.08f, cy - w * 0.22f)
                lineTo(cx + w * 0.08f, cy - w * 0.22f)
                close()
            }
            drawPath(
                path = upArrow,
                color = if (up) ArcadeGold else ArcadeTextPrimary.copy(alpha = 0.65f)
            )

            val downArrow = Path().apply {
                moveTo(cx, cy + w * 0.36f)
                lineTo(cx - w * 0.08f, cy + w * 0.22f)
                lineTo(cx + w * 0.08f, cy + w * 0.22f)
                close()
            }
            drawPath(
                path = downArrow,
                color = if (down) ArcadeGold else ArcadeTextPrimary.copy(alpha = 0.65f)
            )

            val leftArrow = Path().apply {
                moveTo(cx - w * 0.36f, cy)
                lineTo(cx - w * 0.22f, cy - h * 0.08f)
                lineTo(cx - w * 0.22f, cy + h * 0.08f)
                close()
            }
            drawPath(
                path = leftArrow,
                color = if (left) ArcadeGold else ArcadeTextPrimary.copy(alpha = 0.65f)
            )

            val rightArrow = Path().apply {
                moveTo(cx + w * 0.36f, cy)
                lineTo(cx + w * 0.22f, cy - h * 0.08f)
                lineTo(cx + w * 0.22f, cy + h * 0.08f)
                close()
            }
            drawPath(
                path = rightArrow,
                color = if (right) ArcadeGold else ArcadeTextPrimary.copy(alpha = 0.65f)
            )

            drawCircle(
                color = ArcadeGold.copy(alpha = 0.25f),
                radius = w * 0.08f,
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
private fun FloatingSvgFireButton(
    isPressed: Boolean,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressChange(true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                    } while (event.changes.any { it.pressed })
                    onPressChange(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = w * 0.46f

            drawCircle(
                color = if (isPressed) {
                    ArcadeCrimson.copy(alpha = 0.58f)
                } else {
                    ArcadeObsidian.copy(alpha = 0.38f)
                },
                radius = radius,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = if (isPressed) ArcadeGold else ArcadeCrimson.copy(alpha = 0.85f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = ArcadeGold.copy(alpha = if (isPressed) 0.70f else 0.35f),
                radius = radius * 0.78f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            val flameOuter = Path().apply {
                moveTo(cx, cy - h * 0.26f)
                quadraticTo(cx + w * 0.22f, cy - h * 0.06f, cx + w * 0.16f, cy + h * 0.14f)
                quadraticTo(cx + w * 0.10f, cy + h * 0.26f, cx, cy + h * 0.26f)
                quadraticTo(cx - w * 0.10f, cy + h * 0.26f, cx - w * 0.16f, cy + h * 0.14f)
                quadraticTo(cx - w * 0.22f, cy - h * 0.06f, cx, cy - h * 0.26f)
                close()
            }
            drawPath(
                path = flameOuter,
                color = if (isPressed) ArcadeGold.copy(alpha = 0.90f) else ArcadeCrimson.copy(alpha = 0.82f)
            )
            drawPath(
                path = flameOuter,
                color = ArcadeGold,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val clawSlashes = Path().apply {
                moveTo(cx - w * 0.13f, cy - h * 0.14f)
                quadraticTo(cx - w * 0.05f, cy, cx - w * 0.11f, cy + h * 0.15f)
                moveTo(cx, cy - h * 0.18f)
                quadraticTo(cx + w * 0.07f, cy, cx, cy + h * 0.18f)
                moveTo(cx + w * 0.13f, cy - h * 0.14f)
                quadraticTo(cx + w * 0.19f, cy, cx + w * 0.11f, cy + h * 0.15f)
            }
            drawPath(
                path = clawSlashes,
                color = if (isPressed) ArcadeObsidian else ArcadeGold,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
