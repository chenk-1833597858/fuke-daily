package com.fuke.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 底部导航栏
 *
 * @param selectedIndex 当前选中的tab索引（0=项目, 1=定时, 2=记忆）
 * @param onTabSelect 切换tab回调
 */
@Composable
fun BottomNav(
    selectedIndex: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = extended.card,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomNavItem(
                icon = Icons.Filled.List,
                label = "项目",
                selected = selectedIndex == 0,
                onSelect = { onTabSelect(0) },
            )
            BottomNavItem(
                icon = Icons.Filled.Schedule,
                label = "定时",
                selected = selectedIndex == 1,
                onSelect = { onTabSelect(1) },
            )
            BottomNavItem(
                icon = Icons.Filled.PlayArrow,
                label = "日志",
                selected = selectedIndex == 2,
                onSelect = { onTabSelect(2) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val extended = FukeTheme.extended
    val tintColor = if (selected) extended.primary else extended.muted
    val bgColor = if (selected) extended.accent else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tintColor,
        )
    }
}
