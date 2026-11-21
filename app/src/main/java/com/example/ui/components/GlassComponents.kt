package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.security.KeypadHelper
import com.example.ui.theme.LocalVaultColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun SmartAiShieldLogo(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val shieldPath = Path().apply {
            moveTo(cx, h * 0.06f)
            lineTo(w * 0.88f, h * 0.21f)
            cubicTo(
                w * 0.88f, h * 0.56f,
                w * 0.72f, h * 0.80f,
                cx, h * 0.94f
            )
            cubicTo(
                w * 0.28f, h * 0.80f,
                w * 0.12f, h * 0.56f,
                w * 0.12f, h * 0.21f
            )
            close()
        }

        val innerShieldPath = Path().apply {
            moveTo(cx, h * 0.13f)
            lineTo(w * 0.80f, h * 0.25f)
            cubicTo(
                w * 0.80f, h * 0.54f,
                w * 0.67f, h * 0.74f,
                cx, h * 0.86f
            )
            cubicTo(
                w * 0.33f, h * 0.74f,
                w * 0.20f, h * 0.54f,
                w * 0.20f, h * 0.25f
            )
            close()
        }

        val cyan = Color(0xFF00E5FF)
        val indigo = Color(0xFF6366F1)
        val navyCore = Color(0xFF0B1020)

        // Outer shield border gradient
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(navyCore, Color(0xFF131C38)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(cyan, indigo),
                start = Offset(w * 0.1f, h * 0.1f),
                end = Offset(w * 0.9f, h * 0.9f)
            ),
            style = Stroke(width = (w * 0.055f).coerceAtLeast(2.5f), join = StrokeJoin.Round)
        )

        // Glassmorphic highlight on upper left half
        drawPath(
            path = innerShieldPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f),
                    indigo.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                start = Offset(w * 0.2f, h * 0.15f),
                end = Offset(w * 0.75f, h * 0.75f)
            )
        )

        // Padlock shackle
        val shackleWidth = w * 0.24f
        val shackleHeight = h * 0.20f
        drawArc(
            brush = Brush.horizontalGradient(listOf(cyan, indigo)),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - shackleWidth / 2f, h * 0.31f),
            size = Size(shackleWidth, shackleHeight),
            style = Stroke(width = (w * 0.055f).coerceAtLeast(2f), cap = StrokeCap.Round)
        )

        // Padlock body
        val bodyW = w * 0.36f
        val bodyH = h * 0.26f
        val bodyTop = h * 0.41f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(cyan, indigo),
                start = Offset(cx - bodyW / 2f, bodyTop),
                end = Offset(cx + bodyW / 2f, bodyTop + bodyH)
            ),
            topLeft = Offset(cx - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        // Keyhole + AI core dot
        val keyholeCenter = Offset(cx, bodyTop + bodyH * 0.44f)
        drawCircle(
            color = navyCore,
            radius = w * 0.042f,
            center = keyholeCenter
        )
        drawLine(
            color = navyCore,
            start = keyholeCenter,
            end = Offset(cx, bodyTop + bodyH * 0.78f),
            strokeWidth = (w * 0.036f).coerceAtLeast(1.8f),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = cyan,
            radius = w * 0.018f,
            center = keyholeCenter
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlightBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val vaultColors = LocalVaultColors.current
    val borderColor = if (highlightBorder) {
        vaultColors.accentGlow.copy(alpha = 0.55f)
    } else {
        vaultColors.glassBorder
    }

    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = vaultColors.glassSurface,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun PinEntryPad(
    pinLength: Int,
    enteredPin: String,
    isError: Boolean,
    showBiometricButton: Boolean,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier,
    randomizeKeypad: Boolean = false,
    shuffleNonce: Int = 0
) {
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }
    val rows = remember(randomizeKeypad, shuffleNonce) {
        KeypadHelper.generatePinKeypadRows(randomized = randomizeKeypad)
    }

    LaunchedEffect(isError) {
        if (isError) {
            for (delta in listOf(-18f, 18f, -12f, 12f, -6f, 0f)) {
                shakeOffset.animateTo(delta, animationSpec = spring(stiffness = Spring.StiffnessHigh))
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // PIN Indicator Dots
        Row(
            modifier = Modifier
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pinLength) { idx ->
                val filled = idx < enteredPin.length
                val dotColor by animateColorAsState(
                    targetValue = when {
                        isError -> MaterialTheme.colorScheme.error
                        filled -> MaterialTheme.colorScheme.primary
                        else -> LocalVaultColors.current.glassBorder
                    },
                    label = "pinDot"
                )
                Box(
                    modifier = Modifier
                        .size(if (filled) 16.dp else 14.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled || isError) dotColor else Color.Transparent
                        )
                        .then(
                            if (!filled && !isError) {
                                Modifier.background(dotColor.copy(alpha = 0.35f))
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }

        if (randomizeKeypad) {
            Text(
                text = "Anti-Shoulder-Surfing Shuffled Keypad",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        when (key) {
                            "BIO" -> {
                                if (showBiometricButton) {
                                    KeypadCircleButton(
                                        testTag = "pin_biometric_button",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onBiometricClick()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Unlock with Biometrics",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(68.dp))
                                }
                            }
                            "DEL" -> {
                                KeypadCircleButton(
                                    testTag = "pin_backspace_button",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onBackspace()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete digit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> {
                                KeypadCircleButton(
                                    testTag = "pin_key_$key",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDigitPress(key)
                                    }
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val vaultColors = LocalVaultColors.current
    Surface(
        modifier = Modifier
            .size(68.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = vaultColors.glassSurface,
        border = BorderStroke(1.dp, vaultColors.glassBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun PatternLockPad(
    isError: Boolean,
    onPatternCompleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val selectedNodes = remember { mutableStateListOf<Int>() }
    var currentTouch by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val inactiveNodeColor = LocalVaultColors.current.glassBorder
    val lineColor = if (isError) errorColor else primaryColor

    LaunchedEffect(isError) {
        if (isError) {
            kotlinx.coroutines.delay(450L)
            selectedNodes.clear()
            currentTouch = null
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(LocalVaultColors.current.glassSurface.copy(alpha = 0.6f))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selectedNodes.clear()
                        currentTouch = offset
                        val hit = hitTestPatternNode(offset, canvasSize)
                        if (hit != null) {
                            selectedNodes.add(hit)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentTouch = change.position
                        val hit = hitTestPatternNode(change.position, canvasSize)
                        if (hit != null && !selectedNodes.contains(hit)) {
                            selectedNodes.add(hit)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        currentTouch = null
                        if (selectedNodes.isNotEmpty()) {
                            val code = selectedNodes.joinToString(separator = "")
                            onPatternCompleted(code)
                            if (code.length < 4) {
                                selectedNodes.clear()
                            } else {
                                selectedNodes.clear()
                            }
                        }
                    },
                    onDragCancel = {
                        currentTouch = null
                        selectedNodes.clear()
                    }
                )
            }
            .testTag("pattern_lock_pad")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centers = computeNodeCenters(size.width, size.height)

            // Draw connecting lines
            if (selectedNodes.size >= 2) {
                for (i in 0 until selectedNodes.size - 1) {
                    val start = centers[selectedNodes[i]]
                    val end = centers[selectedNodes[i + 1]]
                    drawLine(
                        color = lineColor.copy(alpha = 0.85f),
                        start = start,
                        end = end,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }
            }
            val activeDrag = currentTouch
            if (selectedNodes.isNotEmpty() && activeDrag != null) {
                drawLine(
                    color = lineColor.copy(alpha = 0.45f),
                    start = centers[selectedNodes.last()],
                    end = activeDrag,
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }

            // Draw 3x3 nodes
            centers.forEachIndexed { index, center ->
                val isSelected = selectedNodes.contains(index)
                val outerColor = when {
                    isSelected && isError -> errorColor.copy(alpha = 0.28f)
                    isSelected -> primaryColor.copy(alpha = 0.25f)
                    else -> inactiveNodeColor.copy(alpha = 0.35f)
                }
                val dotColor = when {
                    isSelected && isError -> errorColor
                    isSelected -> primaryColor
                    else -> inactiveNodeColor
                }
                drawCircle(
                    color = outerColor,
                    radius = if (isSelected) 34f else 24f,
                    center = center
                )
                drawCircle(
                    color = dotColor,
                    radius = if (isSelected) 13f else 9f,
                    center = center
                )
            }
        }
    }
}

private fun computeNodeCenters(width: Float, height: Float): List<Offset> {
    val centers = mutableListOf<Offset>()
    val padX = width * 0.2f
    val padY = height * 0.2f
    val stepX = (width - 2 * padX) / 2f
    val stepY = (height - 2 * padY) / 2f
    for (row in 0..2) {
        for (col in 0..2) {
            centers += Offset(padX + col * stepX, padY + row * stepY)
        }
    }
    return centers
}

private fun hitTestPatternNode(point: Offset, size: IntSize): Int? {
    if (size.width <= 0 || size.height <= 0) return null
    val centers = computeNodeCenters(size.width.toFloat(), size.height.toFloat())
    val hitRadius = (size.width / 8.5f).coerceAtLeast(36f)
    centers.forEachIndexed { index, center ->
        if (hypot(point.x - center.x, point.y - center.y) <= hitRadius) {
            return index
        }
    }
    return null
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatTimestamp(timestampMs: Long): String {
    val fmt = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}

@Composable
fun SensorMonitoringBadge(
    isMonitoring: Boolean,
    isEnabled: Boolean,
    hasSensor: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val vaultColors = LocalVaultColors.current
    val pulseTransition = rememberInfiniteTransition(label = "sensorPulseBadge")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val tint = when {
        isMonitoring -> vaultColors.verified
        !hasSensor -> vaultColors.warning
        isEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        isMonitoring -> "Sensors Live • Face-Down/Shake"
        !hasSensor -> "Sensor Unavailable"
        isEnabled -> "Panic Gesture Standby"
        else -> "Panic Gesture Off"
    }

    val shape = RoundedCornerShape(50)
    val pillContent: @Composable () -> Unit = {
        Surface(
            shape = shape,
            color = tint.copy(alpha = if (isMonitoring) 0.16f else 0.11f),
            border = BorderStroke(
                width = 1.dp,
                color = tint.copy(alpha = if (isMonitoring) 0.48f else 0.22f)
            ),
            modifier = if (onClick == null) modifier else Modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(16.dp)
                ) {
                    if (isMonitoring) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(tint)
                        )
                    }
                    Icon(
                        imageVector = if (isMonitoring || isEnabled) Icons.Default.Sensors else Icons.Default.SensorsOff,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }

    if (onClick != null) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clip(shape)
                .clickable(onClick = onClick)
        ) {
            pillContent()
        }
    } else {
        pillContent()
    }
}

@Composable
fun SensorMonitoringStatusBanner(
    isMonitoring: Boolean,
    isEnabled: Boolean,
    hasSensor: Boolean,
    modifier: Modifier = Modifier
) {
    val vaultColors = LocalVaultColors.current
    val pulseTransition = rememberInfiniteTransition(label = "sensorStatusBanner")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloScale"
    )
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )

    val tint = when {
        isMonitoring -> vaultColors.verified
        !hasSensor -> vaultColors.warning
        isEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusTitle = when {
        isMonitoring -> "Monitoring Motion Sensors Now"
        !hasSensor -> "Accelerometer / Gravity Sensor Unavailable"
        isEnabled -> "Panic Gesture Armed (Standby)"
        else -> "Sensor Monitoring Paused"
    }
    val statusSubtitle = when {
        isMonitoring -> "SensorManager is actively listening for face-down flip or firm shake"
        !hasSensor -> "This device lacks the required accelerometer/gravity hardware"
        isEnabled -> "SensorManager listener activates automatically while vault is unlocked"
        else -> "Turn on Panic Gesture to enable real-time face-down & shake detection"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = if (isMonitoring) 0.13f else 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (isMonitoring) 0.45f else 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp)
            ) {
                if (isMonitoring) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .scale(haloScale)
                            .alpha(haloAlpha)
                            .clip(CircleShape)
                            .background(tint)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = if (isMonitoring || isEnabled) Icons.Default.Sensors else Icons.Default.SensorsOff,
                        contentDescription = statusTitle,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
