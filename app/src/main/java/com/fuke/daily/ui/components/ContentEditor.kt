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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 内容编辑器 — 3行输入框 + RefTag引用标签 + 颜色选择
 * 每行前面有颜色按钮，点击弹出颜色选择弹窗
 */
@Composable
fun ContentEditor(
    input1Text: String,
    input2Text: String,
    input3Text: String,
    storage1: Int,
    storage2: Int,
    storage3: Int,
    // 颜色配置
    input1TextColor: String = "",
    input1RefColor: String = "",
    input2TextColor: String = "",
    input2RefColor: String = "",
    input3TextColor: String = "",
    input3RefColor: String = "",
    onInput1Change: (String) -> Unit,
    onInput2Change: (String) -> Unit,
    onInput3Change: (String) -> Unit,
    onRefTagClick: (lineIndex: Int) -> Unit,
    onColorClick: (lineIndex: Int) -> Unit,  // 点击颜色按钮
    modifier: Modifier = Modifier,
    fixedSlotActive: Boolean = false,
    fixedSlotName: String = "固定槽",
    fixedSlotColor: Color? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ContentLine(
            lineIndex = 0,
            text = input1Text,
            onTextChange = onInput1Change,
            storage = storage1,
            textColor = input1TextColor,
            refColor = input1RefColor,
            onRefTagClick = { onRefTagClick(0) },
            onColorClick = { onColorClick(0) },
            placeholder = "输入内容...",
        )
        Spacer(modifier = Modifier.height(6.dp))
        ContentLine(
            lineIndex = 1,
            text = input2Text,
            onTextChange = onInput2Change,
            storage = if (fixedSlotActive) 0 else storage2,
            textColor = input2TextColor,
            refColor = input2RefColor,
            onRefTagClick = { onRefTagClick(1) },
            onColorClick = { onColorClick(1) },
            placeholder = "输入内容...",
            refDisabled = fixedSlotActive,
            refDisabledLabel = if (fixedSlotActive) fixedSlotName else null,
            refDisabledColor = fixedSlotColor,
        )
        Spacer(modifier = Modifier.height(6.dp))
        ContentLine(
            lineIndex = 2,
            text = input3Text,
            onTextChange = onInput3Change,
            storage = storage3,
            textColor = input3TextColor,
            refColor = input3RefColor,
            onRefTagClick = { onRefTagClick(2) },
            onColorClick = { onColorClick(2) },
            placeholder = "输入内容...",
        )
    }
}

@Composable
private fun ContentLine(
    lineIndex: Int,
    text: String,
    onTextChange: (String) -> Unit,
    storage: Int,
    textColor: String,
    refColor: String,
    onRefTagClick: () -> Unit,
    onColorClick: () -> Unit,
    placeholder: String,
    refDisabled: Boolean = false,
    refDisabledLabel: String? = null,
    refDisabledColor: Color? = null,
) {
    val extended = FukeTheme.extended
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 颜色选择按钮（小圆点）
        ColorPickerButton(
            textColor = textColor,
            refColor = refColor,
            onClick = onColorClick,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 输入框
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(shape)
                .background(extended.inputBg)
                .drawBehind {
                    drawRoundRect(
                        color = extended.border,
                        style = Stroke(width = 1.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 12.sp,
                    color = extended.muted.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = extended.text,
                ),
                cursorBrush = SolidColor(extended.muted),
            )
        }

        Spacer(modifier = Modifier.width(26.dp))

        RefTag(
            storage = storage,
            onClick = onRefTagClick,
            disabled = refDisabled,
            disabledLabel = refDisabledLabel,
            disabledColor = refDisabledColor,
        )
    }
}

/**
 * 颜色选择按钮 — 显示两个颜色圆点（文本颜色 + 引用颜色）
 */
@Composable
private fun ColorPickerButton(
    textColor: String,
    refColor: String,
    onClick: () -> Unit,
) {
    val defaultTextColor = Color.Gray
    val defaultRefColor = Color.Gray
    
    val parsedTextColor = parseColor(textColor) ?: defaultTextColor
    val parsedRefColor = parseColor(refColor) ?: defaultRefColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp),
    ) {
        // 文本颜色圆点
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parsedTextColor),
        )
        // 引用颜色圆点
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parsedRefColor),
        )
    }
}

/**
 * 解析颜色字符串，返回 Color
 */
private fun parseColor(colorString: String): Color? {
    return try {
        if (colorString.isBlank()) return null
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        null
    }
}
