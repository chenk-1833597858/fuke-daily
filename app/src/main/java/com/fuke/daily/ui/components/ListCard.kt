package com.fuke.daily.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.data.model.ListType
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.ui.theme.typeColors

/**
 * 主界面列表卡片 — 苗条版，按网页原型
 * 色条：4dp宽×32dp高，不撑满高度，往右靠
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListCard(
    name: String,
    type: ListType,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    subListCount: Int = 0,
) {
    val extended = FukeTheme.extended
    val stripeColor = typeColors[type] ?: Color.Gray
    val isMainline = type == ListType.MAINLINE
    val goldColor = Color(0xFFFFC107)

    val typeLabel = when (type) {
        ListType.SELECTION -> "选择"
        ListType.RANDOM -> "随机"
        ListType.QUIZ -> "答题"
        ListType.MAINLINE -> "人生主线"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isMainline) {
                    Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = goldColor.copy(alpha = 0.3f),
                            spotColor = goldColor.copy(alpha = 0.3f),
                        )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = extended.card,
        border = if (isMainline) {
            BorderStroke(1.5.dp, goldColor)
        } else {
            BorderStroke(1.dp, if (isEnabled) extended.border else extended.border.copy(alpha = 0.3f))
        },
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧色条 — 短条，不撑满高度
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(stripeColor, RoundedCornerShape(2.dp)),
            )

            // 内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = extended.text,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 类型标签（颜色框包裹）+ 子项数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = stripeColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = typeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = stripeColor,
                        )
                    }

                    if (subListCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${subListCount}个子项",
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                    }
                }
            }

            // 右侧开关
            ToggleSwitch(
                checked = isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
