package com.fuke.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 颜色选择弹窗 — 选择文本颜色和引用颜色
 */
@Composable
fun ColorPickerDialog(
    lineIndex: Int,
    currentTextColor: String,
    currentRefColor: String,
    onDismiss: () -> Unit,
    onConfirm: (textColor: String, refColor: String) -> Unit,
) {
    var selectedTextColor by remember { mutableStateOf(currentTextColor) }
    var selectedRefColor by remember { mutableStateOf(currentRefColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第${lineIndex + 1}行颜色设置") },
        text = {
            Column {
                // 文本颜色选择
                Text("文本颜色", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPalette(
                    selectedColor = selectedTextColor,
                    onColorSelected = { selectedTextColor = it },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 引用颜色选择
                Text("引用颜色", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPalette(
                    selectedColor = selectedRefColor,
                    onColorSelected = { selectedRefColor = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTextColor, selectedRefColor) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 颜色调色板 — 预设颜色网格（多行显示）
 */
@Composable
private fun ColorPalette(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
) {
    val colors = listOf(
        "#000000", "#FF0000", "#00FF00", "#0000FF",
        "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500",
        "#800080", "#FFC0CB", "#A52A2A", "#808080",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 每行6个颜色
        colors.chunked(6).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                rowColors.forEach { colorString ->
                    val color = try {
                        Color(android.graphics.Color.parseColor(colorString))
                    } catch (e: Exception) {
                        Color.Gray
                    }
                    
                    val isSelected = selectedColor == colorString
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(colorString) }
                            .then(
                                if (isSelected) {
                                    Modifier.background(Color.White.copy(alpha = 0.3f))
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
