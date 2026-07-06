package com.fuke.daily.ui.imagecarousel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════
//  图片轮播 Activity — 全屏查看，左右预览效果
//  从悬浮窗点击图片后进入
// ═══════════════════════════════════════════════════

@AndroidEntryPoint
class ImageCarouselActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IMAGE_URIS = "image_uris"
        const val EXTRA_INITIAL_INDEX = "initial_index"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTO_PLAY_INTERVAL = "auto_play_interval"

        fun start(context: android.content.Context, imageUris: List<String>, initialIndex: Int = 0, title: String = "", autoPlayInterval: Long = 3000L) {
            // 先隐藏悬浮窗
            val hideIntent = android.content.Intent(context, com.fuke.daily.feature.floating.FloatingWindowService::class.java).apply {
                action = com.fuke.daily.feature.floating.FloatingWindowService.ACTION_HIDE_ALL
            }
            context.startService(hideIntent)
            
            val intent = android.content.Intent(context, ImageCarouselActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_IMAGE_URIS, ArrayList(imageUris))
                putExtra(EXTRA_INITIAL_INDEX, initialIndex)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTO_PLAY_INTERVAL, autoPlayInterval)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val imageUris = intent.getStringArrayListExtra(EXTRA_IMAGE_URIS) ?: emptyList()
        val initialIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val autoPlayInterval = intent.getLongExtra(EXTRA_AUTO_PLAY_INTERVAL, 3000L)

        AppLogger.i("ImageCarouselActivity: 打开图片轮播，图片数量=${imageUris.size}, 初始索引=$initialIndex")

        setContent {
            ImageCarouselScreen(
                imageUris = imageUris,
                initialIndex = initialIndex,
                title = title,
                autoPlayInterval = autoPlayInterval,
                onClose = { finish() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarouselScreen(
    imageUris: List<String>,
    initialIndex: Int = 0,
    title: String = "",
    autoPlayInterval: Long = 3000L,
    enableAutoPlay: Boolean = true,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        initialPageOffsetFraction = 0f,
        pageCount = { imageUris.size }
    )

    // 自动轮播
    if (enableAutoPlay && imageUris.size > 1) {
        LaunchedEffect(pagerState.currentPage) {
            delay(autoPlayInterval)
            val nextPage = (pagerState.currentPage + 1) % imageUris.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 图片轮播
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp), // 左右留出空间显示预览
        ) { page ->
            CarouselImageItem(
                imageUri = imageUris[page],
                isCurrentPage = page == pagerState.currentPage,
            )
        }

        // 顶部关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        // 底部信息区域
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 指示器
            if (imageUris.size > 1) {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(imageUris.size) { index ->
                        val isSelected = index == pagerState.currentPage
                        val dotSize by animateFloatAsState(
                            targetValue = if (isSelected) 10f else 6f,
                            label = "dotSize",
                        )
                        val dotAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.5f,
                            label = "dotAlpha",
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(dotSize.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = dotAlpha))
                        )
                    }
                }
            }

            // 标题
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 页码指示
            Text(
                text = "${pagerState.currentPage + 1} / ${imageUris.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CarouselImageItem(
    imageUri: String,
    isCurrentPage: Boolean,
) {
    val context = LocalContext.current
    val isInternalPath = imageUri.startsWith(context.filesDir.absolutePath)

    // 当前页面缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isCurrentPage) 1f else 0.85f,
        label = "imageScale",
    )

    // 当前页面透明度动画
    val alpha by animateFloatAsState(
        targetValue = if (isCurrentPage) 1f else 0.5f,
        label = "imageAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (isInternalPath) java.io.File(imageUri) else imageUri,
            contentDescription = "轮播图片",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
            onError = {
                AppLogger.e("CarouselImage: 图片加载失败", it.result.throwable)
            },
        )
    }
}
