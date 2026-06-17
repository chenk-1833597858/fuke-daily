package com.fuke.daily.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 引用暂存选择弹窗 — 网页原型风格：竖排横条
 *
 * @param currentSlot 当前选中的暂存（0=无）
 * @param onSlotSelect 选择暂存回调（0=无, 1-5=暂存）
 * @param onDismiss 关闭弹窗回调
 */
@Composable
fun SlotDialog(
    currentSlot: Int,
    onSlotSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    // 全屏遮罩
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // 居中卡片
        Surface(
            modifier = Modifier
                .width(280.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(20.dp),
            color = extended.card,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // 标题
                Text(
                    text = "引用暂存",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.text,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 说明文字
                Text(
                    text = "选择暂存（点击按钮时引用选项名）",
                    fontSize = 11.sp,
                    color = extended.muted,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // "无" 选项
                SlotBarItem(
                    label = "无",
                    isSelected = currentSlot == 0,
                    selectedColor = extended.primary,
                    onClick = { onSlotSelect(0) },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 暂存1-2 一行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SlotBarItem(
                        label = "暂存1",
                        isSelected = currentSlot == 1,
                        selectedColor = extended.slotColors.getOrElse(0) { Color.Gray },
                        onClick = { onSlotSelect(1) },
                        modifier = Modifier.weight(1f),
                    )
                    SlotBarItem(
                        label = "暂存2",
                        isSelected = currentSlot == 2,
                        selectedColor = extended.slotColors.getOrElse(1) { Color.Gray },
                        onClick = { onSlotSelect(2) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 暂存3-4 一行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SlotBarItem(
                        label = "暂存3",
                        isSelected = currentSlot == 3,
                        selectedColor = extended.slotColors.getOrElse(2) { Color.Gray },
                        onClick = { onSlotSelect(3) },
                        modifier = Modifier.weight(1f),
                    )
                    SlotBarItem(
                        label = "暂存4",
                        isSelected = currentSlot == 4,
                        selectedColor = extended.slotColors.getOrElse(3) { Color.Gray },
                        onClick = { onSlotSelect(4) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 暂存5 单独一行
                SlotBarItem(
                    label = "暂存5",
                    isSelected = currentSlot == 5,
                    selectedColor = extended.slotColors.getOrElse(4) { Color.Gray },
                    onClick = { onSlotSelect(5) },
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 取消按钮
                SlotBarItem(
                    label = "取消",
                    isSelected = false,
                    selectedColor = extended.primary,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * 横条选项 — 网页原型风格：圆角矩形、选中填色、未选中浅底+边框
 */
@Composable
private fun SlotBarItem(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) selectedColor else extended.light,
        border = if (isSelected) null else BorderStroke(1.dp, if (label == "取消") extended.border else extended.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else extended.muted,
            )
        }
    }
}
