package com.fuke.daily.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 选项按钮配置组件
 * 跳转和槽位在名称输入框下方一行
 * 用BasicTextField实现极小内边距
 */
@Composable
fun OptionButtonConfig(
    buttons: List<OptionButton>,
    modifier: Modifier = Modifier,
    onAddButton: () -> Unit = {},
    onUpdateButton: (OptionButton) -> Unit = {},
    onDeleteButton: (OptionButton) -> Unit = {},
    onRefTagClick: (OptionButton) -> Unit = {},
) {
    val extended = FukeTheme.extended

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "选项按钮", fontSize = 12.sp, color = extended.muted, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            if (buttons.size < 12) {
                Surface(
                    modifier = Modifier
                        .height(24.dp)
                        .clickable(onClick = onAddButton),
                    shape = RoundedCornerShape(12.dp),
                    color = extended.light,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "添加", modifier = Modifier.size(12.dp), tint = extended.muted)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "添加", fontSize = 11.sp, color = extended.muted)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        buttons.forEachIndexed { index, button ->
            OptionButtonRow(
                button = button,
                index = index,
                onUpdate = onUpdateButton,
                onDelete = onDeleteButton,
            )
            if (index < buttons.size - 1) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (buttons.isEmpty()) {
            Text(text = "暂无选项按钮", fontSize = 12.sp, color = extended.muted.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OptionButtonRow(
    button: OptionButton,
    index: Int,
    onUpdate: (OptionButton) -> Unit,
    onDelete: (OptionButton) -> Unit,
) {
    val extended = FukeTheme.extended
    var nameDraft by rememberSaveable(button.id) { mutableStateOf(button.name) }

    Column {
        // ── 第一行：序号 + 名称输入框 + 空白 + 删除 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}.",
                fontSize = 12.sp,
                color = extended.primary,
                modifier = Modifier.width(20.dp),
            )

            // 输入框占过半多一点
            CompactInput(
                text = nameDraft,
                onTextChange = { newName ->
                    nameDraft = newName
                    onUpdate(button.copy(name = newName))
                },
                placeholder = "输入选择项",
                modifier = Modifier.fillMaxWidth(0.55f),
            )

            // 中间弹性空白
            Spacer(modifier = Modifier.weight(1f))

            // 删除按钮 — 醒目
            Surface(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDelete(button) },
                shape = RoundedCornerShape(12.dp),
                color = extended.light,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = extended.muted)
                }
            }
        }

        // ── 第二行：跳转 + 槽位 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "跳转", fontSize = 11.sp, color = extended.muted)

            Spacer(modifier = Modifier.width(4.dp))

            CompactInput(
                text = if (button.jumpTo > 0) button.jumpTo.toString() else "",
                onTextChange = { newText ->
                    val num = newText.toIntOrNull() ?: 0
                    onUpdate(button.copy(jumpTo = num))
                },
                placeholder = "0",
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
                keyboardType = KeyboardType.Number,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = "暂存", fontSize = 11.sp, color = extended.muted)

            Spacer(modifier = Modifier.width(4.dp))

            SlotSelector(
                currentSlot = button.storageSlot,
                onSlotSelect = { slot -> onUpdate(button.copy(storageSlot = slot)) },
            )
        }
    }
}

/**
 * 紧凑输入框 — BasicTextField + 边框，内边距4dp
 */
@Composable
private fun CompactInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val extended = FukeTheme.extended
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(26.dp)
            .clip(shape)
            .background(extended.inputBg)
            .drawBehind {
                drawRoundRect(
                    color = extended.border,
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 11.sp,
                color = extended.muted.copy(alpha = 0.5f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                color = extended.text,
                textAlign = textAlign,
            ),
            cursorBrush = SolidColor(extended.muted),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

/**
 * 槽位下拉选择器
 */
@Composable
private fun SlotSelector(
    currentSlot: Int,
    onSlotSelect: (Int) -> Unit,
) {
    val extended = FukeTheme.extended
    var expanded by remember { mutableStateOf(false) }
    val label = if (currentSlot == 0) "无" else "暂存$currentSlot"

    Box {
        Surface(
            modifier = Modifier
                .height(24.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(4.dp),
            color = extended.inputBg,
            border = BorderStroke(1.dp, extended.border),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, fontSize = 11.sp, color = extended.text)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "无", fontSize = 12.sp) },
                onClick = { onSlotSelect(0); expanded = false },
            )
            for (slot in 1..5) {
                DropdownMenuItem(
                    text = { Text(text = "暂存$slot", fontSize = 12.sp) },
                    onClick = { onSlotSelect(slot); expanded = false },
                )
            }
        }
    }
}
