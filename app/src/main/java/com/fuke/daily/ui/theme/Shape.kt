package com.fuke.daily.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════
//  圆角规范 — 温暖手绘风格偏大圆角
// ═══════════════════════════════════════════════════

val FukeShapes = Shapes(
    // 小圆角 — 标签、徽章、小按钮
    small = RoundedCornerShape(8.dp),

    // 中圆角 — 卡片、输入框、对话框
    medium = RoundedCornerShape(16.dp),

    // 大圆角 — 大卡片、底部弹窗、全屏对话框
    large = RoundedCornerShape(24.dp),

    // 超大圆角 — 特殊场景（悬浮窗弹窗等）
    extraLarge = RoundedCornerShape(32.dp),
)
