package com.fuke.daily.floating

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fuke.daily.ui.theme.FukeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ═══════════════════════════════════════════════════
//  悬浮图标组件（ComposeView）
//  48dp圆形，三色追逐边框动画，中心✈图标
// ═══════════════════════════════════════════════════

private val ColorOrange = Color(0xFFE07A5F)
private val ColorBlue = Color(0xFF6BA3D6)
private val ColorGreen = Color(0xFF81C784)

@Composable
fun FloatingIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isFlashing: Boolean = false,
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val onDoubleTapState by rememberUpdatedState(onDoubleTap)
    val onLongPressState by rememberUpdatedState(onLongPress)

    val extended = FukeTheme.extended

    // 三色追逐动画
    val gradientColors = listOf(ColorOrange, ColorBlue, ColorGreen)
    var colorIndex by remember { mutableStateOf(0) }
    var displayColor by remember { mutableStateOf(gradientColors[0]) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(800L)
            colorIndex = (colorIndex + 1) % gradientColors.size
            displayColor = gradientColors[colorIndex]
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = displayColor,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "gradientColor",
    )

    // 闪烁动画
    var flashAlpha by remember { mutableStateOf(1f) }
    var flashScale by remember { mutableStateOf(1f) }
    LaunchedEffect(isFlashing) {
        if (isFlashing) {
            while (true) {
                flashAlpha = 0.2f
                flashScale = 1.2f
                delay(200L)
                flashAlpha = 1f
                flashScale = 1f
                delay(200L)
            }
        } else {
            flashAlpha = 1f
            flashScale = 1f
        }
    }

    // 拖动状态
    var totalDrag by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragAndTapGestures(
                    onDragStart = {
                        totalDrag = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag = Offset(
                            totalDrag.x + abs(dragAmount.x),
                            totalDrag.y + abs(dragAmount.y),
                        )
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        scope.launch {
                            delay(100)
                            totalDrag = Offset.Zero
                        }
                    },
                    onDoubleTap = {
                        if (abs(totalDrag.x) < 10 && abs(totalDrag.y) < 10) {
                            onDoubleTapState()
                        }
                    },
                    onLongPress = {
                        if (abs(totalDrag.x) < 10 && abs(totalDrag.y) < 10) {
                            onLongPressState()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 渐变边框
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 3.dp.toPx()
            val radius = (size.toPx() - strokeWidth) / 2

            val nextIdx = (colorIndex + 1) % gradientColors.size
            val colors = listOf(
                animatedColor,
                gradientColors[nextIdx],
                animatedColor,
            )

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = colors,
                    center = Offset(center.x, center.y),
                ),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }

        // 内部背景
        Box(
            modifier = Modifier
                .size((size - 6.dp) * flashScale)
                .clip(CircleShape)
                .background(
                    if (isFlashing) {
                        Color.Red.copy(alpha = 0.9f * flashAlpha)
                    } else {
                        extended.primary.copy(alpha = 0.9f * flashAlpha)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Floating Icon",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDragAndTapGestures(
    onDragStart: (Offset) -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit = {},
    onSingleTap: (Offset) -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
) {
    var lastTapTime = 0L
    val doubleTapTimeout = 300L
    val longPressTimeout = 500L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = System.currentTimeMillis()
        var totalDrag = Offset.Zero
        var isDrag = false
        var isLongPressTriggered = false

        val isPotentialDoubleTap = (downTime - lastTapTime) < doubleTapTimeout

        drag(down.id) { change ->
            val currentTime = System.currentTimeMillis()
            val delta = change.positionChange()
            totalDrag = Offset(totalDrag.x + abs(delta.x), totalDrag.y + abs(delta.y))

            if (!isLongPressTriggered && !isDrag &&
                currentTime - downTime >= longPressTimeout &&
                totalDrag.x < 10 && totalDrag.y < 10
            ) {
                isLongPressTriggered = true
                onLongPress(down.position)
            }

            if (totalDrag.x > 10 || totalDrag.y > 10) {
                if (!isDrag) {
                    isDrag = true
                    onDragStart(down.position)
                }
                onDrag(change, delta)
            }
        }

        val upTime = System.currentTimeMillis()

        if (isDrag) {
            onDragEnd()
        } else if (!isLongPressTriggered && totalDrag.x < 10 && totalDrag.y < 10) {
            if (isPotentialDoubleTap) {
                onDoubleTap(down.position)
                lastTapTime = 0L
            } else {
                lastTapTime = upTime
            }
        }
    }
}
