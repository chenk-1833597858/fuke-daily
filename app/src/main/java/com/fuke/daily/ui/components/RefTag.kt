package com.fuke.daily.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 引用标签（固定64dp宽）
 *
 * @param storage 引用槽位号（0=无引用, 1-5=有引用）
 * @param onClick 点击回调
 * @param disabled 禁用状态（固定槽占用时）
 * @param disabledLabel 禁用时显示的文字（如"暂存1"）
 * @param disabledColor 禁用时的背景色（固定槽颜色）
 */
@Composable
fun RefTag(
    storage: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
    disabledLabel: String? = null,
    disabledColor: Color? = null,
) {
    val extended = FukeTheme.extended
    val hasRef = storage > 0

    val bgColor = when {
        disabled -> disabledColor ?: extended.contentBg
        hasRef -> extended.slotColors.getOrElse(storage - 1) { Color.Gray }
        else -> extended.light
    }

    val textColor = when {
        disabled -> Color.White
        hasRef -> Color.White
        else -> extended.muted
    }

    val label = when {
        disabled -> disabledLabel ?: "固定"
        hasRef -> "引用·$storage"
        else -> "引用"
    }

    Surface(
        modifier = modifier
            .size(width = 64.dp, height = 24.dp)
            .then(if (disabled) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = if (disabled) null else BorderStroke(1.dp, extended.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
    }
}
