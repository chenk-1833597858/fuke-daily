package com.fuke.daily.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.data.model.ContentConfig
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.data.model.SubList
import com.fuke.daily.ui.theme.FukeTheme

private val cnNumbers = listOf("一", "二", "三", "四", "五")

/**
 * 子列表卡片 — 选择列表和随机列表共用
 * Fix 2: 紧凑内边距
 * Fix 5: 固定槽移到标题行，删除"未分配"提示
 * Fix 7: 图片占位区域
 */
@Composable
fun SubListCard(
    subList: SubList,
    index: Int,
    contentConfig: ContentConfig?,
    optionButtons: List<OptionButton>,
    showFixedSlot: Boolean,
    modifier: Modifier = Modifier,
    fixedSlotCount: Int = 0,
    onNameChange: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onContentConfigChange: (ContentConfig) -> Unit = {},
    onRefTagClick: (lineIndex: Int) -> Unit = {},
    onAddOptionButton: () -> Unit = {},
    onUpdateOptionButton: (OptionButton) -> Unit = {},
    onDeleteOptionButton: (OptionButton) -> Unit = {},
    onOptionRefTagClick: (OptionButton) -> Unit = {},
    onFixedSlotChange: (Int) -> Unit = {},
    onImageClick: () -> Unit = {},
) {
    val extended = FukeTheme.extended

    val indexColor = if (subList.fixedSlot > 0 && showFixedSlot) {
        extended.fixedSlotColors.getOrElse(subList.fixedSlot - 1) { Color.Gray }
    } else {
        extended.slotColors.getOrElse(index % 5) { Color.Gray }
    }

    var isEditingName by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf(subList.name) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = extended.card,
        border = BorderStroke(1.dp, extended.border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── 第1行：序号名称 + 排序按钮 + 删除 + 图片（右边） ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 序号 + 名称
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = indexColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isEditingName) {
                    val nameShape = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(nameShape)
                            .background(extended.inputBg)
                            .drawBehind {
                                drawRoundRect(
                                    color = extended.primary,
                                    style = Stroke(width = 1.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (nameDraft.isEmpty()) {
                            Text(text = "子列表名称", fontSize = 14.sp, color = extended.muted.copy(alpha = 0.4f))
                        }
                        BasicTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = extended.text),
                            cursorBrush = SolidColor(extended.muted),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        modifier = Modifier.size(28.dp).clickable { onNameChange(nameDraft); isEditingName = false },
                        shape = RoundedCornerShape(6.dp),
                        color = extended.success,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(text = "✓", fontSize = 13.sp, color = Color.White) }
                    }
                } else {
                    Text(
                        text = subList.name.ifBlank { "未命名" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (subList.name.isBlank()) extended.muted.copy(alpha = 0.5f) else extended.text,
                        modifier = Modifier.weight(1f).clickable { nameDraft = subList.name; isEditingName = true },
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 右侧图片缩略图（48dp）
                val firstImageUri = remember(subList.imageUris) {
                    try {
                        val uris = org.json.JSONArray(subList.imageUris)
                        if (uris.length() > 0) uris.getString(0) else null
                    } catch (_: Exception) { null }
                }
                
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onImageClick() },
                    shape = RoundedCornerShape(8.dp),
                    color = extended.contentBg,
                    border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
                ) {
                    if (firstImageUri != null) {
                        coil.compose.AsyncImage(
                            model = firstImageUri,
                            contentDescription = "子列表图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "+", fontSize = 20.sp, color = extended.muted.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 删除按钮
                Surface(
                    modifier = Modifier.size(24.dp).clickable(onClick = onDelete),
                    shape = RoundedCornerShape(12.dp),
                    color = extended.light,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "×", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = extended.muted)
                    }
                }
            }

            // ── 第2行：固定槽（靠左） ──
            if (showFixedSlot && fixedSlotCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 固定槽靠左
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (slot in 1..fixedSlotCount) {
                            val isSelected = subList.fixedSlot == slot
                            val slotColor = extended.fixedSlotColors.getOrElse(slot - 1) { Color.Gray }
                            Surface(
                                modifier = Modifier.height(24.dp).clickable { onFixedSlotChange(if (isSelected) 0 else slot) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) slotColor else extended.light,
                                border = if (!isSelected) BorderStroke(1.dp, extended.border) else null,
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "【${cnNumbers.getOrElse(slot - 1) { "$slot" }}】", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else extended.muted)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 内容编辑器 ──
            if (contentConfig != null) {
                // 颜色选择弹窗状态
                var showColorPicker by rememberSaveable { mutableStateOf(false) }
                var colorPickerLineIndex by rememberSaveable { mutableStateOf(0) }
                
                ContentEditor(
                    input1Text = contentConfig.input1Text,
                    input2Text = contentConfig.input2Text,
                    input3Text = contentConfig.input3Text,
                    storage1 = contentConfig.button1Storage,
                    storage2 = contentConfig.button2Storage,
                    storage3 = contentConfig.button3Storage,
                    input1TextColor = contentConfig.input1TextColor,
                    input1RefColor = contentConfig.input1RefColor,
                    input2TextColor = contentConfig.input2TextColor,
                    input2RefColor = contentConfig.input2RefColor,
                    input3TextColor = contentConfig.input3TextColor,
                    input3RefColor = contentConfig.input3RefColor,
                    onInput1Change = { onContentConfigChange(contentConfig.copy(input1Text = it)) },
                    onInput2Change = { onContentConfigChange(contentConfig.copy(input2Text = it)) },
                    onInput3Change = { onContentConfigChange(contentConfig.copy(input3Text = it)) },
                    onRefTagClick = onRefTagClick,
                    onColorClick = { lineIndex ->
                        colorPickerLineIndex = lineIndex
                        showColorPicker = true
                    },
                    fixedSlotActive = subList.fixedSlot > 0,
                    fixedSlotName = if (subList.fixedSlot > 0) "【${cnNumbers.getOrElse(subList.fixedSlot - 1) { "${subList.fixedSlot}" }}】" else "固定槽",
                    fixedSlotColor = if (subList.fixedSlot > 0) extended.fixedSlotColors.getOrElse(subList.fixedSlot - 1) { androidx.compose.ui.graphics.Color.Gray } else null,
                )
                
                // 颜色选择弹窗
                if (showColorPicker) {
                    val currentTextColor = when (colorPickerLineIndex) {
                        0 -> contentConfig.input1TextColor
                        1 -> contentConfig.input2TextColor
                        2 -> contentConfig.input3TextColor
                        else -> ""
                    }
                    val currentRefColor = when (colorPickerLineIndex) {
                        0 -> contentConfig.input1RefColor
                        1 -> contentConfig.input2RefColor
                        2 -> contentConfig.input3RefColor
                        else -> ""
                    }
                    
                    ColorPickerDialog(
                        lineIndex = colorPickerLineIndex,
                        currentTextColor = currentTextColor,
                        currentRefColor = currentRefColor,
                        onDismiss = { showColorPicker = false },
                        onConfirm = { textColor, refColor ->
                            val updatedConfig = when (colorPickerLineIndex) {
                                0 -> contentConfig.copy(input1TextColor = textColor, input1RefColor = refColor)
                                1 -> contentConfig.copy(input2TextColor = textColor, input2RefColor = refColor)
                                2 -> contentConfig.copy(input3TextColor = textColor, input3RefColor = refColor)
                                else -> contentConfig
                            }
                            onContentConfigChange(updatedConfig)
                            showColorPicker = false
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OptionButtonConfig(
                buttons = optionButtons,
                onAddButton = onAddOptionButton,
                onUpdateButton = onUpdateOptionButton,
                onDeleteButton = onDeleteOptionButton,
                onRefTagClick = onOptionRefTagClick,
            )
        }
    }
}
