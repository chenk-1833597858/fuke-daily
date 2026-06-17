package com.fuke.daily.ui.theme

import androidx.compose.ui.graphics.Color
import com.fuke.daily.data.model.ListType

// ═══════════════════════════════════════════════════
//  温暖手绘主题
// ═══════════════════════════════════════════════════
object WarmColors {
    val bg = Color(0xFFFFF8F0)
    val card = Color(0xFFFFFDF9)
    val primary = Color(0xFFE07A5F)
    val accent = Color(0xFFFFE4C4)
    val text = Color(0xFF5D4E37)
    val muted = Color(0xFF8B7355)
    val light = Color(0xFFFFF0E0)
    val border = Color(0xFFE8D5C4)
    val success = Color(0xFF81C784)
    val contentBg = Color(0xFFFFF6ED)
    val inputBg = Color(0xFFFFFAF5)
    val slotColors = listOf(
        Color(0xFFE07A5F), Color(0xFF6BA3D6), Color(0xFF81C784),
        Color(0xFFFFB74D), Color(0xFFAB47BC)
    )
    val fixedSlotColors = listOf(
        Color(0xFFD4534A), Color(0xFF4A90D9), Color(0xFF5CB85C), Color(0xFFF0AD4E)
    )
    val overlay = Color(0x805D4E37)
}

// ═══════════════════════════════════════════════════
//  深紫暗夜主题
// ═══════════════════════════════════════════════════
object PurpleColors {
    val bg = Color(0xFF1A1025)
    val card = Color(0xFF2D1B4E)
    val primary = Color(0xFFA78BFA)
    val accent = Color(0xFF4C1D95)
    val text = Color(0xFFE9D5FF)
    val muted = Color(0xFFC4B5FD)
    val light = Color(0xFF3B2667)
    val border = Color(0xFF3B2667)
    val success = Color(0xFF86EFAC)
    val contentBg = Color(0xFF231540)
    val inputBg = Color(0xFF2A1848)
    val slotColors = listOf(
        Color(0xFFA78BFA), Color(0xFF67E8F9), Color(0xFF86EFAC),
        Color(0xFFFDE68A), Color(0xFFF9A8D4)
    )
    val fixedSlotColors = listOf(
        Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFF4ADE80), Color(0xFFFBBF24)
    )
    val overlay = Color(0xB31A1025)
}

// ═══════════════════════════════════════════════════
//  主线选择页专用深色（不受主题切换影响）
// ═══════════════════════════════════════════════════
object MainlineSelectColors {
    val bg = Color(0xFF0D0D1A)
    val card = Color(0xFF1A1A2E)
    val gold = Color(0xFFFFC107)
    val goldDim = Color(0x26FFC107)    // 15% alpha
    val textLight = Color(0xFFE8E8F0)
    val muted = Color(0xFF7A7A9A)
}

// ═══════════════════════════════════════════════════
//  类型色条 — 每种列表类型对应一个颜色
// ═══════════════════════════════════════════════════
val typeColors: Map<ListType, Color> = mapOf(
    ListType.SELECTION to Color(0xFFE07A5F),
    ListType.RANDOM    to Color(0xFF6BA3D6),
    ListType.QUIZ      to Color(0xFFAB47BC),
    ListType.MAINLINE  to Color(0xFFFFC107),
)
