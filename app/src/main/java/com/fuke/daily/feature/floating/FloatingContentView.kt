package com.fuke.daily.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.fuke.daily.data.model.ListType
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.data.model.QuizCard
import com.fuke.daily.ui.theme.FukeTheme

// ═══════════════════════════════════════════════════
//  悬浮弹窗组件 — 底部滑入弹窗风格（原项目风格）
//  - 全屏弹窗，从底部 slideInVertically 滑入
//  - 半透明黑色遮罩，点击遮罩→关闭
//  - 弹窗展开时隐藏悬浮图标，关闭时恢复
//  - 按钮布局：1-4单列/5-8双列/9-12三列
//  - 内容框解析[xxx]按钮标记，支持点击高亮
//  - 顶部圆角24dp的Surface，有阴影
// ═══════════════════════════════════════════════════

@Composable
fun FloatingPopup(
    modifier: Modifier = Modifier,
    content: AnnotatedString = AnnotatedString(""),
    buttons: List<OptionButton> = emptyList(),
    activeButtons: Int = 0,
    isVisible: Boolean = true,
    imageUri: String? = null,
    imageEnabled: Boolean = true,
    imageUris: List<String> = emptyList(),
    imageIndex: Int = 0,
    listType: ListType = ListType.SELECTION,
    quizCards: List<QuizCard> = emptyList(),
    onQuizNext: () -> Unit = {},
    onContentButtonClick: (Int) -> Unit = {},
    onButtonClick: (OptionButton) -> Unit = {},
    onDismiss: () -> Unit = {},
    onImageCarouselVisibilityChange: (Boolean) -> Unit = {},
) {
    val isQuizMode = listType == ListType.QUIZ && quizCards.isNotEmpty()
    
    // 获取屏幕尺寸
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val popupWidth = screenWidth.coerceAtMost(400.dp)
    val halfScreenHeight = screenHeight / 2
    
    // 图片展示区域（弹窗上方，独立显示）
    val validImageUri = remember(imageUri, imageEnabled) {
        if (imageEnabled) imageUri?.takeIf { it.isNotBlank() } else null
    }

    // 是否显示图片轮播
    var showImageCarousel by remember { mutableStateOf(false) }
    var carouselIndex by remember { mutableStateOf(0) }
    
    // 监听图片轮播状态变化
    LaunchedEffect(showImageCarousel) {
        onImageCarouselVisibilityChange(showImageCarousel)
    }

    Box(
        modifier = if (showImageCarousel) {
            modifier  // 不设置 fillMaxSize，让 Box 只包裹内容
        } else {
            modifier.fillMaxSize()  // 正常模式下覆盖整个屏幕
        }
    ) {
        // 遮罩层（只在正常模式下显示）
        if (isVisible && !showImageCarousel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onDismiss()
                    },
            )
        }

        // 图片轮播（全屏显示）
        if (showImageCarousel) {
            ImageCarouselOverlay(
                imageUris = imageUris,
                initialIndex = carouselIndex,
                onClose = {
                    showImageCarousel = false
                },
            )
        }

        // 底部弹窗内容（只在正常模式下显示）
        AnimatedVisibility(
            visible = isVisible && !showImageCarousel,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(250),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200),
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
        ) {
            if (isQuizMode) {
                QuizBottomSheet(
                    cards = quizCards,
                    onNextQuiz = onQuizNext,
                )
            } else {
                Column(
                    modifier = Modifier
                        .width(popupWidth)
                        .heightIn(min = halfScreenHeight, max = screenHeight * 0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 图片显示在弹窗内部顶部
                    if (validImageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FloatingImage(
                                imageUri = validImageUri,
                                imageUris = imageUris,
                                imageIndex = imageIndex,
                                onImageClick = { clickedIndex ->
                                    carouselIndex = clickedIndex
                                    showImageCarousel = true
                                },
                            )
                        }
                    }
                    
                    BottomSheetContent(
                        content = content,
                        buttons = buttons,
                        activeButtons = activeButtons,
                        onContentButtonClick = onContentButtonClick,
                        onButtonClick = onButtonClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetContent(
    content: AnnotatedString,
    buttons: List<OptionButton>,
    activeButtons: Int,
    onContentButtonClick: (Int) -> Unit,
    onButtonClick: (OptionButton) -> Unit,
) {
    val extended = FukeTheme.extended
    val popupWidth = 380.dp
    
    // 获取屏幕高度的一半
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val halfScreenHeight = screenHeight / 2

    Surface(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(min = halfScreenHeight, max = screenHeight * 0.9f)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                clip = false,
            ),
        color = extended.card,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .width(popupWidth)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 拖动指示条
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extended.muted.copy(alpha = 0.3f)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 内容框
            if (content.isNotEmpty()) {
                ContentBox(
                    content = content,
                    activeButtons = activeButtons,
                    onButtonClick = onContentButtonClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 选项按钮
            if (buttons.isNotEmpty()) {
                OptionButtonGrid(
                    buttons = buttons,
                    onButtonClick = onButtonClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  悬浮窗图片组件
// ═══════════════════════════════════════════════════

@Composable
private fun FloatingImage(
    imageUri: String,
    imageUris: List<String> = emptyList(),
    imageIndex: Int = 0,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
) {
    // 获取屏幕尺寸
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    
    // 图片最大宽度为屏幕宽度的 80%
    val imageMaxWidth = screenWidthDp * 0.8f
    // 图片最大高度为屏幕高度的 35%
    val imageMaxHeight = screenHeightDp * 0.35f
    
    // 安全检查：确保 URI 非空且非空白
    if (imageUri.isBlank()) {
        android.util.Log.w("FloatingImage", "图片 URI 为空，跳过渲染")
        return
    }
    
    android.util.Log.d("FloatingImage", "开始加载图片: $imageUri")
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isInternalPath = imageUri.startsWith(context.filesDir.absolutePath)
    
    // 使用 Box 包裹，让遮罩跟随图片大小
    Box(
        modifier = modifier
            .width(imageMaxWidth)
            .height(imageMaxHeight) // 固定高度，避免抖动
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            )
            .clickable {
                // 点击图片进入全屏轮播
                if (onImageClick != null) {
                    onImageClick(imageIndex)
                } else if (imageUris.isNotEmpty()) {
                    android.util.Log.d("FloatingImage", "点击图片，发送广播显示全屏轮播，imageUris.size=${imageUris.size}, imageIndex=$imageIndex")
                    // 发送广播给 FloatingWindowService 显示全屏轮播
                    val intent = android.content.Intent(context, com.fuke.daily.feature.floating.FloatingWindowService::class.java).apply {
                        action = "com.fuke.daily.OPEN_IMAGE_CAROUSEL"
                        putExtra("imageUris", imageUris.toTypedArray())
                        putExtra("imageIndex", imageIndex)
                    }
                    context.startService(intent)
                } else {
                    android.util.Log.w("FloatingImage", "点击图片时 imageUris 为空，不执行操作")
                }
            }
    ) {
        // 使用 Crossfade 实现图片切换动画
        Crossfade(
            targetState = imageUri,
            animationSpec = tween(durationMillis = 500),
            label = "imageCrossfade",
        ) { currentUri ->
            var isLoaded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = if (isInternalPath) java.io.File(currentUri) else currentUri,
                    contentDescription = "子列表图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = imageMaxHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    // 使用 Fit 让竖向/横向图片都能适应显示
                    contentScale = ContentScale.Fit,
                    // 错误处理：加载失败时静默处理
                    onError = {
                        android.util.Log.e("FloatingImage", "图片加载失败: ${it.result.throwable}")
                        isLoaded = true
                    },
                    onSuccess = {
                        android.util.Log.d("FloatingImage", "图片加载成功: $currentUri, 尺寸: ${it.result.drawable?.intrinsicWidth}x${it.result.drawable?.intrinsicHeight}")
                        isLoaded = true
                    }
                )
                
                // 加载占位
                if (!isLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    )
                }
            }
        }
        
        // 轮播指示器
        if (imageUris.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                imageUris.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == imageIndex) androidx.compose.ui.graphics.Color.White
                                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  图片轮播悬浮窗（可拖动的小悬浮窗）
// ═══════════════════════════════════════════════════

@Composable
private fun ImageCarouselOverlay(
    imageUris: List<String>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var currentIndex by remember { mutableStateOf(initialIndex) }
    
    // 自动轮播
    LaunchedEffect(currentIndex) {
        if (imageUris.size > 1) {
            delay(3000)
            currentIndex = (currentIndex + 1) % imageUris.size
        }
    }
    
    // 确保索引在有效范围内
    val safeIndex = currentIndex.coerceIn(0, imageUris.size - 1)
    val currentUri = imageUris.getOrNull(safeIndex) ?: return
    val isInternalPath = currentUri.startsWith(context.filesDir.absolutePath)
    
    // 屏幕尺寸（dp）
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    
    // 悬浮窗大小（dp）
    val windowWidthDp = screenWidthDp * 0.8f
    val windowHeightDp = screenHeightDp * 0.35f
    
    Box(
        modifier = Modifier
            .width(windowWidthDp)
            .height(windowHeightDp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.9f)),
    ) {
        // 图片
        AsyncImage(
            model = if (isInternalPath) java.io.File(currentUri) else currentUri,
            contentDescription = "轮播图片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        
        // 右上角关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)  // 减小内边距
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
                modifier = Modifier.size(16.dp),
            )
        }
        
        // 底部指示器
        if (imageUris.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                imageUris.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == safeIndex) Color.White
                                else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

                    // ═══════════════════════════════════════════════════
                    //  内容框组件 — 解析[xxx]按钮标记
                    // ═══════════════════════════════════════════════════

@Composable
private fun ContentBox(
    content: AnnotatedString,
    activeButtons: Int,
    onButtonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Box(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(extended.contentBg.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = content,
            fontSize = 18.sp,
            color = extended.text,
        )
    }
}

// ═══════════════════════════════════════════════════
//  内容解析
// ═══════════════════════════════════════════════════

private sealed class ContentPart {
    data class Button(val text: String, val buttonIndex: Int) : ContentPart()
    data class Text(val text: String) : ContentPart()
}

private fun parseContentWithButtons(content: String): List<List<ContentPart>> {
    val lines = content.split("\n")
    val result = mutableListOf<List<ContentPart>>()
    var buttonIndex = 0

    lines.forEach { line ->
        if (line.isBlank()) return@forEach

        val parts = mutableListOf<ContentPart>()
        val regex = Regex("""\[([^\]]+)\]""")
        var lastIndex = 0

        regex.findAll(line).forEach { matchResult ->
            if (matchResult.range.first > lastIndex) {
                val textBefore = line.substring(lastIndex, matchResult.range.first)
                if (textBefore.isNotBlank()) {
                    parts.add(ContentPart.Text(textBefore))
                }
            }

            val buttonText = matchResult.groupValues[1]
            parts.add(ContentPart.Button(buttonText, buttonIndex))
            buttonIndex++

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < line.length) {
            val remainingText = line.substring(lastIndex)
            if (remainingText.isNotBlank()) {
                parts.add(ContentPart.Text(remainingText))
            }
        }

        if (parts.isNotEmpty()) {
            result.add(parts)
        }
    }

    return result
}

// ═══════════════════════════════════════════════════
//  选项按钮网格
//  1-4单列/5-8双列/9-12三列
// ═══════════════════════════════════════════════════

@Composable
private fun OptionButtonGrid(
    buttons: List<OptionButton>,
    onButtonClick: (OptionButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonCount = buttons.size

    when {
        buttonCount <= 4 -> SingleColumnLayout(buttons, onButtonClick, modifier)
        buttonCount <= 8 -> DoubleColumnLayout(buttons, onButtonClick, modifier)
        else -> TripleColumnLayout(buttons, onButtonClick, modifier)
    }
}

@Composable
private fun SingleColumnLayout(
    buttons: List<OptionButton>,
    onButtonClick: (OptionButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(4) { index ->
            if (index < buttons.size) {
                OptionButtonView(
                    button = buttons[index],
                    onClick = { onButtonClick(buttons[index]) },
                    modifier = Modifier.fillMaxWidth(),
                    primaryColor = extended.primary,
                    textColor = Color.White,
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(44.dp))
            }
        }
    }
}

@Composable
private fun DoubleColumnLayout(
    buttons: List<OptionButton>,
    onButtonClick: (OptionButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(4) { rowIndex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(2) { colIndex ->
                    val buttonIndex = rowIndex * 2 + colIndex
                    if (buttonIndex < buttons.size) {
                        OptionButtonView(
                            button = buttons[buttonIndex],
                            onClick = { onButtonClick(buttons[buttonIndex]) },
                            modifier = Modifier.weight(1f),
                            primaryColor = extended.primary,
                            textColor = Color.White,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TripleColumnLayout(
    buttons: List<OptionButton>,
    onButtonClick: (OptionButton) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(4) { rowIndex ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) { colIndex ->
                    val buttonIndex = rowIndex * 3 + colIndex
                    if (buttonIndex < buttons.size) {
                        OptionButtonView(
                            button = buttons[buttonIndex],
                            onClick = { onButtonClick(buttons[buttonIndex]) },
                            modifier = Modifier.weight(1f),
                            primaryColor = extended.primary,
                            textColor = Color.White,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionButtonView(
    button: OptionButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color,
    textColor: Color,
) {
    val buttonShape = RoundedCornerShape(10.dp)

    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = textColor,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(
            text = button.name.ifEmpty { "按钮${button.id}" },
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════════════════════════════════
//  答题底部弹窗
// ═══════════════════════════════════════════════════

@Composable
private fun QuizBottomSheet(
    cards: List<QuizCard>,
    onNextQuiz: () -> Unit,
) {
    val extended = FukeTheme.extended
    val popupWidth = 380.dp
    
    // 获取屏幕高度的一半
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val halfScreenHeight = screenHeight / 2

    // ── 卡片池状态 ──
    var currentPool by remember(cards) { mutableStateOf(cards.shuffled()) }
    var nextRoundPool by remember(cards) { mutableStateOf(mutableListOf<QuizCard>()) }
    var round by remember(cards) { mutableIntStateOf(1) }
    var isFlipped by remember(cards) { mutableStateOf(false) }

    val totalCards = cards.size
    val isAllDone = currentPool.isEmpty()
    val currentCard = currentPool.firstOrNull()

    Surface(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(min = halfScreenHeight, max = screenHeight * 0.9f)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                clip = false,
            ),
        color = extended.card,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .width(popupWidth)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 拖动指示条
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extended.muted.copy(alpha = 0.3f)),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isAllDone) {
                // ── 完成态 ──
                Text(
                    text = "🎉",
                    fontSize = 36.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "所有卡片已记住！",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = extended.text,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (nextRoundPool.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.clickable {
                            currentPool = nextRoundPool.shuffled()
                            nextRoundPool = mutableListOf()
                            round++
                            isFlipped = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = extended.primary,
                    ) {
                        Text(
                            text = "开始下一轮 (${nextRoundPool.size}张)",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 下一个答题项目按钮
                Surface(
                    modifier = Modifier.clickable(onClick = onNextQuiz),
                    shape = RoundedCornerShape(16.dp),
                    color = extended.muted.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "下一个项目 →",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = extended.muted,
                    )
                }
            } else {
                // ── 翻转卡片 ──
                val rotationY by animateFloatAsState(
                    targetValue = if (isFlipped) 180f else 0f,
                    animationSpec = tween(durationMillis = 400),
                    label = "cardFlip",
                )
                val showFront = rotationY < 90f

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            this.rotationY = rotationY
                            this.cameraDistance = 12f * density
                        }
                        .clickable { isFlipped = !isFlipped },
                    shape = RoundedCornerShape(16.dp),
                    color = extended.contentBg.copy(alpha = 0.7f),
                    shadowElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showFront) {
                            Text(
                                text = currentCard!!.front,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = extended.text,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                            )
                        } else {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    this.scaleX = -1f
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = currentCard!!.back,
                                    fontSize = 14.sp,
                                    color = extended.text,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }

                // 翻转提示
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!isFlipped) "点击卡片查看答案" else "点击卡片翻回正面",
                    fontSize = 11.sp,
                    color = extended.muted.copy(alpha = 0.5f),
                )

                // ── 自评按钮（正面占位，翻面后可点击）──
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 忘记 → 放回当前池末尾
                    QuizSelfEvalButton(
                        label = "忘记",
                        color = Color(0xFFEF4444),
                        enabled = isFlipped,
                        onClick = {
                            val card = currentPool.first()
                            currentPool = currentPool.drop(1) + card
                            isFlipped = false
                        },
                    )

                    // 模糊 → 放入下一轮
                    QuizSelfEvalButton(
                        label = "模糊",
                        color = Color(0xFFF59E0B),
                        enabled = isFlipped,
                        onClick = {
                            val card = currentPool.first()
                            nextRoundPool.add(card)
                            currentPool = currentPool.drop(1)
                            isFlipped = false
                        },
                    )

                    // 记住 → 移除
                    QuizSelfEvalButton(
                        label = "记住",
                        color = Color(0xFF22C55E),
                        enabled = isFlipped,
                        onClick = {
                            currentPool = currentPool.drop(1)
                            isFlipped = false
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── 底部状态 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "第${round}轮 · 剩余${currentPool.size}张 / 共${totalCards}张",
                        fontSize = 12.sp,
                        color = extended.muted.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  答题自评按钮
// ═══════════════════════════════════════════════════

@Composable
private fun QuizSelfEvalButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier
            .width(88.dp)
            .height(40.dp)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) color.copy(alpha = 0.15f) else Color.Transparent,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            if (enabled) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  虚线边框辅助
// ═══════════════════════════════════════════════════

private fun Modifier.drawDashedBorder(
    color: Color,
    strokeWidth: Dp,
    dashLength: Dp,
    gapLength: Dp,
    cornerRadius: Dp,
): Modifier = this.then(DashedBorderModifier(color, strokeWidth, dashLength, gapLength, cornerRadius))

private class DashedBorderModifier(
    private val color: Color,
    private val strokeWidth: Dp,
    private val dashLength: Dp,
    private val gapLength: Dp,
    private val cornerRadius: Dp,
) : DrawModifier {
    override fun ContentDrawScope.draw() {
        drawContent()
        val strokeWidthPx = strokeWidth.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                    phase = 0f,
                ),
            ),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidthPx / 2, strokeWidthPx / 2),
            size = androidx.compose.ui.geometry.Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
            cornerRadius = CornerRadius(cornerRadiusPx),
        )
    }
}
