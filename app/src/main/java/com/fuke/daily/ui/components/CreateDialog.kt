package com.fuke.daily.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.fuke.daily.data.model.ListType
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 创建项目对话框
 * 支持类型选择（选择/随机/答题/主线）
 * 已修复：软键盘无法弹出
 */
@Composable
fun CreateDialog(
    hasMainline: Boolean = false,
    onConfirm: (String, ListType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ListType.SELECTION) }

    Dialog(onDismissRequest = onDismiss) {
        // 修复软键盘无法弹出
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        Surface(
            modifier = modifier.width(320.dp),
            shape = RoundedCornerShape(16.dp),
            color = extended.card,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = "新建项目",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.text,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "输入名称",
                            fontSize = 14.sp,
                            color = extended.muted.copy(alpha = 0.5f),
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = extended.inputBg,
                        unfocusedContainerColor = extended.inputBg,
                        cursorColor = extended.muted,
                        focusedBorderColor = extended.border,
                        unfocusedBorderColor = extended.border,
                    ),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = extended.text),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 类型选择
                Text(
                    text = "项目类型",
                    fontSize = 12.sp,
                    color = extended.muted,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                val types = buildList {
                    add(ListType.SELECTION to "选择" to "手动选取子列表")
                    add(ListType.RANDOM to "随机" to "随机展示内容")
                    add(ListType.QUIZ to "答题" to "问答卡片组")
                    if (!hasMainline) {
                        add(ListType.MAINLINE to "人生主线" to "每日目标链路")
                    }
                }

                types.forEach { (typeInfo, desc) ->
                    val (type, label) = typeInfo
                    val selected = selectedType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .background(
                                color = if (selected) extended.primary.copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) extended.primary else extended.text,
                            modifier = Modifier.width(40.dp),
                        )
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (selected) {
                            Text("✓", fontSize = 14.sp, color = extended.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", color = extended.muted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name.trim(), selectedType)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extended.primary,
                            contentColor = Color.White,
                        ),
                        enabled = name.isNotBlank(),
                    ) {
                        Text(text = "创建", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
