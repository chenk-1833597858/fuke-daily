package com.fuke.daily.ui.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.util.AppLogger
import kotlin.math.max
import kotlin.math.min

// ═══════════════════════════════════════════════════
//  自定义裁剪界面
//  - 裁剪框固定在中央，白色边框
//  - 遮罩只在裁剪框外围显示（半透明黑色）
//  - 裁剪框内部透明，可以看到图片
//  - 双指缩放/拖动：操作图片
//  - 初始时图片自动填充裁剪框
// ═══════════════════════════════════════════════════

@Composable
fun CropImageDialog(
    imageUri: Uri,
    aspectRatioX: Int,
    aspectRatioY: Int,
    isFreeRatio: Boolean,
    onDismiss: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val extended = FukeTheme.extended
    val density = LocalDensity.current

    // 加载图片
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var bitmapWidth by remember { mutableFloatStateOf(0f) }
    var bitmapHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    imageBitmap = bitmap.asImageBitmap()
                    bitmapWidth = bitmap.width.toFloat()
                    bitmapHeight = bitmap.height.toFloat()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("CropImageDialog: failed to load image", e)
        }
    }

    // 图片变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight - 120.dp

            // 计算裁剪框大小
            val cropWidthDp: androidx.compose.ui.unit.Dp
            val cropHeightDp: androidx.compose.ui.unit.Dp

            if (isFreeRatio) {
                cropWidthDp = screenWidth * 0.9f
                cropHeightDp = screenHeight * 0.7f
            } else {
                val ratio = aspectRatioX.toFloat() / aspectRatioY.toFloat()
                val maxCropWidth = screenWidth * 0.9f
                val maxCropHeight = screenHeight * 0.8f
                val widthBasedHeight = maxCropWidth / ratio
                if (widthBasedHeight <= maxCropHeight) {
                    cropWidthDp = maxCropWidth
                    cropHeightDp = widthBasedHeight
                } else {
                    cropHeightDp = maxCropHeight
                    cropWidthDp = maxCropHeight * ratio
                }
            }

            val cropW = with(density) { cropWidthDp.toPx() }
            val cropH = with(density) { cropHeightDp.toPx() }

            // 图片显示区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight),
                contentAlignment = Alignment.Center,
            ) {
                if (imageBitmap != null && bitmapWidth > 0 && bitmapHeight > 0) {
                    // 初始化缩放（只执行一次）
                    if (!isInitialized) {
                        val scaleX = cropW / bitmapWidth
                        val scaleY = cropH / bitmapHeight
                        scale = max(scaleX, scaleY)
                        offsetX = 0f
                        offsetY = 0f
                        isInitialized = true
                    }

                    // 图片显示尺寸
                    val displayedWidth = bitmapWidth * scale
                    val displayedHeight = bitmapHeight * scale

                    // 手势区域（全屏）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = max(0.3f, min(scale * zoom, 5f))
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // 用 Canvas 绘制图片
                        Canvas(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 计算图片在 Canvas 中的位置
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val imgLeft = (canvasWidth - displayedWidth) / 2 + offsetX
                            val imgTop = (canvasHeight - displayedHeight) / 2 + offsetY

                            // 绘制图片
                            drawImage(
                                image = imageBitmap!!,
                                topLeft = Offset(imgLeft, imgTop),
                            )
                        }
                    }

                    // 裁剪框遮罩层
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 裁剪框位置
                        val cropBoxLeft = (with(density) { screenWidth.toPx() } - cropW) / 2
                        val cropBoxTop = (with(density) { screenHeight.toPx() } - cropH) / 2

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 绘制半透明遮罩（裁剪框外围）
                            val maskColor = Color.Black.copy(alpha = 0.6f)

                            // 上遮罩
                            drawRect(
                                color = maskColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, cropBoxTop),
                            )
                            // 下遮罩
                            drawRect(
                                color = maskColor,
                                topLeft = Offset(0f, cropBoxTop + cropH),
                                size = Size(size.width, size.height - cropBoxTop - cropH),
                            )
                            // 左遮罩
                            drawRect(
                                color = maskColor,
                                topLeft = Offset(0f, cropBoxTop),
                                size = Size(cropBoxLeft, cropH),
                            )
                            // 右遮罩
                            drawRect(
                                color = maskColor,
                                topLeft = Offset(cropBoxLeft + cropW, cropBoxTop),
                                size = Size(size.width - cropBoxLeft - cropW, cropH),
                            )

                            // 绘制裁剪框边框
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cropBoxLeft, cropBoxTop),
                                size = Size(cropW, cropH),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                            )
                        }
                    }
                } else {
                    // 加载中
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "加载中...",
                            fontSize = 16.sp,
                            color = extended.muted,
                        )
                    }
                }
            }

            // 底部按钮栏
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isFreeRatio) "自由裁剪" else "${aspectRatioX}:${aspectRatioY}",
                    fontSize = 14.sp,
                    color = extended.muted,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = extended.contentBg.copy(alpha = 0.3f),
                        onClick = onDismiss,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "取消",
                                fontSize = 15.sp,
                                color = extended.muted,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = extended.contentBg.copy(alpha = 0.3f),
                        onClick = {
                            isInitialized = false
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "重置",
                                fontSize = 15.sp,
                                color = extended.muted,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = extended.primary,
                        onClick = {
                            // TODO: 实现裁剪逻辑
                            onDismiss()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "确认",
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
