package com.fuke.daily.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

/**
 * 紧凑输入框组件 — 光标准确、内边距可控
 *
 * 用 TextFieldDefaults.DecorationBox 包裹 BasicTextField：
 * - BasicTextField 负责文字布局和光标（位置准确）
 * - DecorationBox 负责边框、背景、padding
 *
 * 用法：
 *   CompactInputField(
 *       value = text,
 *       onValueChange = { ... },
 *       placeholder = "输入内容...",
 *   )
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    fontSize: Int = 12,
    textAlign: TextAlign = TextAlign.Start,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    cornerRadius: Int = 6,
    minHeight: Int = 28,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val extended = FukeTheme.extended
    val shape = RoundedCornerShape(cornerRadius.dp)
    val interactionSource = remember { MutableInteractionSource() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = minHeight.dp),
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(
            fontSize = fontSize.sp,
            color = extended.text,
            textAlign = textAlign,
        ),
        cursorBrush = SolidColor(extended.primary),
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        readOnly = readOnly,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    if (placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = fontSize.sp,
                            color = extended.muted.copy(alpha = 0.5f),
                            textAlign = textAlign,
                        )
                    }
                },
                shape = shape,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = extended.inputBg,
                    focusedBorderColor = extended.border,
                    unfocusedBorderColor = extended.border,
                    disabledBorderColor = extended.border,
                    cursorColor = extended.primary,
                    focusedLabelColor = extended.muted,
                    unfocusedLabelColor = extended.muted,
                ),
                contentPadding = contentPadding,
            )
        },
    )
}
