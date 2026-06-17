package com.fuke.daily.ui.random

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.RichText
import com.fuke.daily.ui.components.CompactInputField
import com.fuke.daily.ui.components.PageHeader
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.ConfigViewModel

// 中文序号
private val cnNumbers = listOf("一", "二", "三", "四")
private fun eventLabel(index: Int): String = "【${cnNumbers.getOrElse(index) { "${index + 1}" }}】"

// ═══════════════════════════════════════════════════
//  事件编辑页（随机列表第1页）
// ═══════════════════════════════════════════════════

@Composable
fun RichTextPage(
    listId: Long,
    onBack: () -> Unit,
    onNavigateToConfig: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val extended = FukeTheme.extended
    val uiState by viewModel.uiState.collectAsState()

    viewModel.setListId(listId)

    val richTexts = uiState.richTexts
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val validIndex = if (richTexts.isEmpty()) 0 else selectedTabIndex.coerceIn(0, richTexts.size - 1)
    if (validIndex != selectedTabIndex) selectedTabIndex = validIndex

    val currentRichText = richTexts.getOrNull(validIndex)
    val subtitle = "${richTexts.size}/4 个"

    // 删除确认弹窗
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRichText by rememberSaveable { mutableStateOf<RichText?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extended.bg),
    ) {
        PageHeader(
            title = "事件",
            onBack = onBack,
            subtitle = subtitle,
            actions = {
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(onClick = onNavigateToConfig),
                    shape = RoundedCornerShape(16.dp),
                    color = extended.primary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "数据配置", fontSize = 12.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "→", fontSize = 12.sp, color = Color.White)
                    }
                }
            },
        )

        // ── 标签切换区 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            richTexts.forEachIndexed { index, _ ->
                val isSelected = index == validIndex

                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { selectedTabIndex = index },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) extended.primary else extended.light,
                    border = if (!isSelected) BorderStroke(1.dp, extended.border) else null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = eventLabel(index),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else extended.muted,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (richTexts.size > 1) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "删除",
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable {
                                        pendingDeleteRichText = richTexts[index]
                                        showDeleteConfirm = true
                                    },
                                tint = if (isSelected) Color.White.copy(alpha = 0.8f) else extended.muted.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            if (richTexts.size < 4) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            viewModel.addRichText()
                            selectedTabIndex = richTexts.size
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = extended.light,
                    border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加",
                            modifier = Modifier.size(14.dp),
                            tint = extended.muted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 编辑区 ──
        if (currentRichText != null) {
            var contentDraft by rememberSaveable(currentRichText.id) { mutableStateOf(currentRichText.content) }
            val lineCount = contentDraft.lines().size

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                // 内容标题行
                Text(text = "内容（换行分隔）", fontSize = 12.sp, color = extended.muted)
                Spacer(modifier = Modifier.height(4.dp))

                // 内容输入框 — CompactInputField + 多行 + 撑满剩余空间
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    CompactInputField(
                        value = contentDraft,
                        onValueChange = { newContent ->
                            contentDraft = newContent
                            viewModel.updateRichText(currentRichText.copy(content = newContent))
                        },
                        placeholder = "每行一条内容\n换行分隔",
                        singleLine = false,
                        maxLines = 50,
                        fontSize = 14,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        cornerRadius = 8,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "$lineCount 行", fontSize = 11.sp, color = extended.muted.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "暂无事件", fontSize = 14.sp, color = extended.muted.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.clickable { viewModel.addRichText() },
                        shape = RoundedCornerShape(16.dp),
                        color = extended.light,
                    ) {
                        Text(
                            text = "  + 添加事件  ",
                            fontSize = 13.sp,
                            color = extended.muted,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm && pendingDeleteRichText != null) {
        val deleteIndex = richTexts.indexOf(pendingDeleteRichText)
        val deleteLabel = if (deleteIndex >= 0) eventLabel(deleteIndex) else "事件"

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                pendingDeleteRichText = null
            },
            title = { Text("确认删除", color = extended.text) },
            text = { Text("确定要删除$deleteLabel 吗？", color = extended.muted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRichText(pendingDeleteRichText!!)
                    if (validIndex >= richTexts.size - 1) {
                        selectedTabIndex = (richTexts.size - 2).coerceAtLeast(0)
                    }
                    showDeleteConfirm = false
                    pendingDeleteRichText = null
                }) {
                    Text("删除", color = Color(0xFFD4534A))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    pendingDeleteRichText = null
                }) {
                    Text("取消", color = extended.muted)
                }
            },
            containerColor = extended.card,
        )
    }
}
