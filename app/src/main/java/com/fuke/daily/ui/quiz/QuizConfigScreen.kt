package com.fuke.daily.ui.quiz

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fuke.daily.data.model.QuizCard
import com.fuke.daily.data.model.QuizGroup
import com.fuke.daily.ui.components.PageHeader
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.ConfigViewModel

// ═══════════════════════════════════════════════════
//  答题配置页
// ═══════════════════════════════════════════════════

@Composable
fun QuizConfigScreen(
    listId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val extended = FukeTheme.extended
    val uiState by viewModel.uiState.collectAsState()

    // 初始化
    viewModel.setListId(listId)

    val groups = uiState.quizGroups
    val totalCards = uiState.quizCards.values.sumOf { it.size }
    val subtitle = "${groups.size}组·${totalCards}张卡片"

    // 展开状态
    var expandedGroups by rememberSaveable {
        mutableStateOf(setOf<Long>())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extended.bg),
    ) {
        // ── 顶栏 ──
        PageHeader(
            title = "答题配置",
            onBack = onBack,
            subtitle = subtitle,
        )

        // ── 组列表 ──
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = groups,
                key = { it.id },
            ) { group ->
                val isExpanded = group.id in expandedGroups
                val cards = uiState.quizCards[group.id] ?: emptyList()

                QuizGroupCard(
                    group = group,
                    cards = cards,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedGroups = if (isExpanded) {
                            expandedGroups - group.id
                        } else {
                            expandedGroups + group.id
                        }
                    },
                    onGroupNameChange = { newName ->
                        viewModel.updateQuizGroup(group.copy(name = newName))
                    },
                    onDeleteGroup = {
                        viewModel.deleteQuizGroup(group)
                    },
                    onAddCard = {
                        viewModel.addQuizCard(group.id)
                    },
                    onUpdateCard = { card ->
                        viewModel.updateQuizCard(card)
                    },
                    onDeleteCard = { card ->
                        viewModel.deleteQuizCard(card)
                    },
                )
            }

            // 底部"+ 添加组"虚线按钮
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { viewModel.addQuizGroup() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+ 添加组",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = extended.muted.copy(alpha = 0.6f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  答题组卡片（可折叠）
// ═══════════════════════════════════════════════════

@Composable
private fun QuizGroupCard(
    group: QuizGroup,
    cards: List<QuizCard>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onAddCard: () -> Unit,
    onUpdateCard: (QuizCard) -> Unit,
    onDeleteCard: (QuizCard) -> Unit,
) {
    val extended = FukeTheme.extended

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = extended.card,
        border = BorderStroke(1.dp, extended.border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── 组标题行 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 折叠箭头
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp),
                    tint = extended.muted,
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 组名（可编辑）
                var isEditingName by rememberSaveable { mutableStateOf(false) }
                var nameDraft by rememberSaveable { mutableStateOf(group.name) }

                if (isEditingName) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "组名",
                                fontSize = 14.sp,
                                color = extended.muted.copy(alpha = 0.4f),
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = extended.inputBg,
                            unfocusedContainerColor = extended.inputBg,
                            cursorColor = extended.muted,
                            focusedBorderColor = extended.border,
                            unfocusedBorderColor = extended.border,
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = extended.text,
                        ),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onGroupNameChange(nameDraft)
                                isEditingName = false
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = extended.success,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✓",
                                fontSize = 12.sp,
                                color = Color.White,
                            )
                        }
                    }
                } else {
                    Text(
                        text = group.name.ifBlank { "未命名组" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (group.name.isBlank()) extended.muted.copy(alpha = 0.5f) else extended.text,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                nameDraft = group.name
                                isEditingName = true
                            },
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 卡片数
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = extended.light,
                ) {
                    Text(
                        text = "${cards.size}张",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        color = extended.muted,
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 删除按钮
                IconButton(
                    onClick = onDeleteGroup,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除",
                        modifier = Modifier.size(14.dp),
                        tint = extended.muted.copy(alpha = 0.6f),
                    )
                }
            }

            // ── 展开内容 ──
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                cards.forEach { card ->
                    QuizCardEditor(
                        card = card,
                        onUpdate = onUpdateCard,
                        onDelete = onDeleteCard,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 添加卡片按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable(onClick = onAddCard),
                    shape = RoundedCornerShape(8.dp),
                    color = extended.light,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加卡片",
                            modifier = Modifier.size(16.dp),
                            tint = extended.muted,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "添加卡片",
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  答题卡片编辑器（问题+答案）
// ═══════════════════════════════════════════════════

@Composable
private fun QuizCardEditor(
    card: QuizCard,
    onUpdate: (QuizCard) -> Unit,
    onDelete: (QuizCard) -> Unit,
) {
    val extended = FukeTheme.extended

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = extended.contentBg,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 问题
            var frontDraft by rememberSaveable(card.id) { mutableStateOf(card.front) }

            Text(
                text = "问题",
                fontSize = 11.sp,
                color = extended.muted.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = frontDraft,
                onValueChange = { newFront ->
                    frontDraft = newFront
                    onUpdate(card.copy(front = newFront))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "输入问题",
                        fontSize = 12.sp,
                        color = extended.muted.copy(alpha = 0.4f),
                    )
                },
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = extended.inputBg,
                    unfocusedContainerColor = extended.inputBg,
                    cursorColor = extended.muted,
                    focusedBorderColor = extended.border,
                    unfocusedBorderColor = extended.border,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = extended.text,
                ),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 答案
            var backDraft by rememberSaveable(card.id) { mutableStateOf(card.back) }

            Text(
                text = "答案",
                fontSize = 11.sp,
                color = extended.muted.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = backDraft,
                onValueChange = { newBack ->
                    backDraft = newBack
                    onUpdate(card.copy(back = newBack))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "输入答案",
                        fontSize = 12.sp,
                        color = extended.muted.copy(alpha = 0.4f),
                    )
                },
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = extended.inputBg,
                    unfocusedContainerColor = extended.inputBg,
                    cursorColor = extended.muted,
                    focusedBorderColor = extended.border,
                    unfocusedBorderColor = extended.border,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = extended.text,
                ),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 删除卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    modifier = Modifier
                        .height(24.dp)
                        .clickable(onClick = { onDelete(card) }),
                    shape = RoundedCornerShape(12.dp),
                    color = extended.light,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "删除卡片",
                            modifier = Modifier.size(10.dp),
                            tint = extended.muted.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "删除",
                            fontSize = 10.sp,
                            color = extended.muted.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
