package com.fuke.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 筛选标签栏
 *
 * @param tabs 标签列表（默认: 全部/选择/随机/答题/人生主线）
 * @param selectedIndex 当前选中索引
 * @param onTabSelect 切换标签回调
 * @param mainlineTabIndex 人生主线tab的索引（该tab带金色小点指示器），null则无指示器
 */
@Composable
fun FilterTabs(
    selectedIndex: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<String> = listOf("全部", "选择", "随机", "答题", "人生主线"),
    mainlineTabIndex: Int? = tabs.indexOf("人生主线").takeIf { it >= 0 },
) {
    val extended = FukeTheme.extended
    val goldColor = Color(0xFFFFC107)

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex

            val bgColor = if (selected) extended.primary else extended.light
            val textColor = if (selected) Color.White else extended.muted

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
                    .clickable { onTabSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = tab,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                    )

                    // 主线tab金色小点指示器
                    if (index == mainlineTabIndex) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(goldColor, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}
