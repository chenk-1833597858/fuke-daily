package com.fuke.daily.feature.floating

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuke.daily.util.AppLogger
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 全屏悬浮窗图片轮播组件
 * - 毛玻璃效果背景
 * - 左上角返回按钮
 * - 右上角轮播开关
 * - 单张图片居中显示，支持左右滑动切换
 * - 滑动时有缩放和透明度动画
 */
@Composable
fun FloatingImageCarousel(
    imageUris: List<String>,
    initialIndex: Int = 0,
    carouselInterval: Long = 3000L,
    carouselEnabled: Boolean = true,
    onBack: () -> Unit,
    onCarouselEnabledChange: (Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // 安全检查：如果图片列表为空，直接返回
    if (imageUris.isEmpty()) {
        AppLogger.w("FloatingImageCarousel: imageUris is empty, returning")
        onBack()
        return
    }

    // 确保 initialIndex 在有效范围内
    val safeInitialIndex = initialIndex.coerceIn(0, imageUris.size - 1)
    AppLogger.d("FloatingImageCarousel: imageUris.size=${imageUris.size}, initialIndex=$initialIndex, safeInitialIndex=$safeInitialIndex")

    // 当前显示的图片索引
    var currentIndex by remember { mutableStateOf(safeInitialIndex) }
    // 是否自动轮播
    var isAutoCarousel by remember { mutableStateOf(carouselEnabled) }
    // 滑动状态
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // 自动轮播
    LaunchedEffect(isAutoCarousel, currentIndex) {
        if (isAutoCarousel && imageUris.size > 1 && !isDragging) {
            delay(carouselInterval)
            currentIndex = (currentIndex + 1) % imageUris.size
        }
    }

    // 计算动画目标值
    val targetScale = if (abs(dragOffset) > 50f) 0.85f else 1f
    val targetAlpha = if (abs(dragOffset) > 50f) 0.7f else 1f
    
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(200),
        label = "scale",
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(200),
        label = "alpha",
    )

    // 毛玻璃效果背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
    ) {
        // 顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回按钮
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }

            // 轮播开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "自动轮播",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                Switch(
                    checked = isAutoCarousel,
                    onCheckedChange = {
                        isAutoCarousel = it
                        onCarouselEnabledChange(it)
                    },
                )
            }
        }

        // 图片轮播区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * 0.7f)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            // 根据滑动距离判断是否切换
                            if (dragOffset < -200f && imageUris.size > 1) {
                                // 向左滑动，显示下一张
                                currentIndex = (currentIndex + 1) % imageUris.size
                            } else if (dragOffset > 200f && imageUris.size > 1) {
                                // 向右滑动，显示上一张
                                currentIndex = if (currentIndex == 0) imageUris.size - 1 else currentIndex - 1
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // 当前图片
            val currentUri = imageUris.getOrNull(currentIndex) ?: imageUris.firstOrNull()
            AppLogger.d("FloatingImageCarousel: currentIndex=$currentIndex, currentUri=$currentUri")
            if (currentUri != null) {
                CarouselImage(
                    imageUri = currentUri,
                    modifier = Modifier
                        .width(screenWidth * 0.85f)
                        .height(screenHeight * 0.6f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            // 根据滑动偏移量移动
                            translationX = dragOffset * 0.3f
                        },
                )
            }
        }

        // 底部指示器
        if (imageUris.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                imageUris.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == currentIndex) Color.White
                                else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselImage(
    imageUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isInternalPath = imageUri.startsWith(context.filesDir.absolutePath)

    AppLogger.d("CarouselImage: imageUri=$imageUri, isInternalPath=$isInternalPath")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (isInternalPath) java.io.File(imageUri) else imageUri,
            contentDescription = "轮播图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
