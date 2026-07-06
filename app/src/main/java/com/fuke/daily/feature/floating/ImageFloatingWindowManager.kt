package com.fuke.daily.feature.floating

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * 图片悬浮窗管理器
 * - 可拖动移动（限制在屏幕边界内）
 * - 双指缩放（保持比例）
 * - 自动轮播
 * - 右上角关闭按钮
 */
class ImageFloatingWindowManager(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShowing = false

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun show(
        imageUris: List<String>,
        initialIndex: Int = 0,
        carouselInterval: Long = 3000L,
        onClose: () -> Unit,
    ) {
        if (isShowing) return
        if (!android.provider.Settings.canDrawOverlays(context)) return

        try {
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels

            // 悬浮窗大小：屏幕宽度的 80%，高度的 35%
            val windowWidth = (screenWidth * 0.8f).toInt()
            val windowHeight = (screenHeight * 0.35f).toInt()

            params = WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (screenWidth - windowWidth) / 2
                y = (screenHeight - windowHeight) / 2
            }

            composeView = ComposeView(context).apply {
                // 设置 LifecycleOwner 和 SavedStateRegistryOwner
                if (context is androidx.lifecycle.LifecycleOwner) {
                    setViewTreeLifecycleOwner(context as androidx.lifecycle.LifecycleOwner)
                }
                if (context is androidx.savedstate.SavedStateRegistryOwner) {
                    setViewTreeSavedStateRegistryOwner(context as androidx.savedstate.SavedStateRegistryOwner)
                }

                setContent {
                    ImageFloatingWindowContent(
                        imageUris = imageUris,
                        initialIndex = initialIndex,
                        carouselInterval = carouselInterval,
                        onClose = {
                            hide()
                            onClose()
                        },
                    )
                }
            }

            // 设置触摸监听（拖动）
            composeView?.setOnTouchListener(ImageFloatingTouchListener())

            windowManager.addView(composeView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        try {
            composeView?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isShowing = false
        composeView = null
        params = null
    }

    fun isShowing(): Boolean = isShowing

    private inner class ImageFloatingTouchListener : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    params?.let { p ->
                        p.x = initialX + deltaX
                        p.y = initialY + deltaY

                        // 限制在屏幕边界内
                        val screenWidth = context.resources.displayMetrics.widthPixels
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        val windowWidth = p.width
                        val windowHeight = p.height

                        p.x = max(0, min(p.x, screenWidth - windowWidth))
                        p.y = max(0, min(p.y, screenHeight - windowHeight))

                        windowManager.updateViewLayout(composeView, p)
                    }
                    return true
                }
            }
            return false
        }
    }
}

/**
 * 图片悬浮窗内容组件
 */
@Composable
private fun ImageFloatingWindowContent(
    imageUris: List<String>,
    initialIndex: Int = 0,
    carouselInterval: Long = 3000L,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // 当前显示的图片索引
    var currentIndex by remember { mutableStateOf(initialIndex) }
    // 缩放比例
    var scale by remember { mutableFloatStateOf(1f) }
    // 缩放偏移
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 自动轮播
    LaunchedEffect(currentIndex) {
        if (imageUris.size > 1) {
            delay(carouselInterval)
            currentIndex = (currentIndex + 1) % imageUris.size
        }
    }

    // 确保索引在有效范围内
    val safeIndex = currentIndex.coerceIn(0, imageUris.size - 1)
    val currentUri = imageUris.getOrNull(safeIndex) ?: return
    val isInternalPath = currentUri.startsWith(context.filesDir.absolutePath)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.8f)),
    ) {
        // 图片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // 缩放限制在 0.5f 到 3f 之间
                        scale = max(0.5f, min(scale * zoom, 3f))
                        // 更新偏移量
                        offset = Offset(
                            offset.x + pan.x,
                            offset.y + pan.y
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = if (isInternalPath) java.io.File(currentUri) else currentUri,
                contentDescription = "轮播图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // 右上角关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White,
            )
        }
    }
}
