package com.fuke.daily.data.model

import androidx.compose.ui.graphics.Color

/**
 * 带颜色的文本段
 * 用于悬浮弹窗显示不同颜色的文本
 */
data class ColoredTextSegment(
    val text: String,
    val color: Color = Color.Unspecified,  // Unspecified = 使用默认颜色
)

/**
 * 带颜色的内容行
 * 每行包含多个文本段，每个段有自己的颜色
 */
data class ColoredContentLine(
    val segments: List<ColoredTextSegment>,
)

/**
 * 带颜色的完整内容
 * 包含多行，每行有多个带颜色的文本段
 */
typealias ColoredContent = List<ColoredContentLine>
