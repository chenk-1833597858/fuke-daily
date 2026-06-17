package com.fuke.daily.ui.mainline

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.MainlineViewModel

// ═══════════════════════════════════════════════════
//  主线编辑页 — 深色沉浸 + 卡片式分组
// ═══════════════════════════════════════════════════

@Composable
fun MainlineEditScreen(
    listId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainlineViewModel = hiltViewModel(),
) {
    val ms = FukeTheme.mainlineSelect
    val uiState by viewModel.uiState.collectAsState()

    // 初始化
    LaunchedEffect(listId) {
        viewModel.loadMainline(listId)
    }

    val mainList = uiState.mainList
    val branches = uiState.branches
    val config = uiState.config

    // 主线名称编辑状态
    var mainlineName by remember(mainList?.name) {
        mutableStateOf(mainList?.name ?: "")
    }

    // 折叠状态
    val expandedBranches = remember { mutableStateListOf<Long>() }

    // 初始展开所有支线
    LaunchedEffect(branches) {
        expandedBranches.clear()
        branches.forEach { expandedBranches.add(it.id) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ms.bg),
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ms.gold),
            ) {
                Text("←", color = ms.gold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "编辑人生主线",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ms.gold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${branches.size}个路标",
                fontSize = 12.sp,
                color = ms.textLight,
            )
        }

        // ── 内容区（可滚动）──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ── 目标名称卡片 ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ms.card,
                border = BorderStroke(1.dp, ms.gold),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "目标名称",
                        color = ms.gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 金色底线下划线输入框
                    BasicTextField(
                        value = mainlineName,
                        onValueChange = { newName ->
                            mainlineName = newName
                            viewModel.updateMainListName(newName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = ms.gold,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            },
                        textStyle = TextStyle(
                            color = ms.gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(ms.gold),
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 时段卡片 ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ms.card,
                border = BorderStroke(1.dp, ms.gold),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "时段",
                        color = ms.gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 选路时段行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "选路时段",
                            color = ms.textLight,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        HourPicker(
                            currentHour = config.morningHour,
                            onHourChange = { viewModel.setMorningHour(it) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 回顾时段行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "回顾时段",
                            color = ms.textLight,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        HourPicker(
                            currentHour = config.eveningHour,
                            onHourChange = { viewModel.setEveningHour(it) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 说明文字
                    Text(
                        text = "此时段后第一次打开App将弹出选路页",
                        color = ms.muted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "设为0表示不启用该时段",
                        color = ms.muted.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 路标卡片列表 ──
            branches.forEach { branch ->
                val isExpanded = expandedBranches.contains(branch.id)
                val branchItems = uiState.items[branch.id] ?: emptyList()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ms.card,
                    border = BorderStroke(1.dp, ms.gold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // ── 路标头部（名称 + 展开/折叠 + 删除）──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExpanded) expandedBranches.remove(branch.id)
                                    else expandedBranches.add(branch.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 折叠图标
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "折叠" else "展开",
                                tint = ms.gold,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // 路标名称（可点击编辑）
                            var isEditingBranch by remember { mutableStateOf(false) }
                            var branchName by remember(branch.name) { mutableStateOf(branch.name) }

                            if (isEditingBranch) {
                                BasicTextField(
                                    value = branchName,
                                    onValueChange = { newName ->
                                        branchName = newName
                                        viewModel.updateBranch(branch.copy(name = newName))
                                    },
                                    modifier = Modifier.weight(1f),
                                    textStyle = TextStyle(
                                        color = ms.gold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(ms.gold),
                                    singleLine = true,
                                )
                                // 失焦保存
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(1.dp)
                                        .clickable { isEditingBranch = false }
                                )
                            } else {
                                Text(
                                    text = branchName.ifBlank { "路标名称" },
                                    color = if (branchName.isBlank()) ms.muted else ms.gold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { isEditingBranch = true },
                                )
                            }

                            // 删除路标按钮
                            IconButton(
                                onClick = { viewModel.deleteBranch(branch) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Text(
                                    text = "🗑",
                                    fontSize = 16.sp,
                                    color = Color(0xFFD4534A),
                                )
                            }
                        }

                        // ── 展开的子项列表 ──
                        if (isExpanded) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                // 所有子项统一显示（不区分isCurrent）
                                branchItems.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 金色短线前缀
                                        Text(
                                            text = "─",
                                            color = ms.gold,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        // 子项名称（可点击编辑）
                                        var isEditingItem by remember { mutableStateOf(false) }
                                        var itemName by remember(item.name) { mutableStateOf(item.name) }

                                        if (isEditingItem) {
                                            BasicTextField(
                                                value = itemName,
                                                onValueChange = { newName ->
                                                    itemName = newName
                                                    viewModel.updateBranchItem(item.copy(name = newName))
                                                },
                                                modifier = Modifier.weight(1f),
                                                textStyle = TextStyle(
                                                    color = ms.textLight,
                                                    fontSize = 13.sp,
                                                ),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(ms.gold),
                                                singleLine = true,
                                            )
                                        } else {
                                            Text(
                                                text = itemName.ifBlank { "子项名称" },
                                                color = if (itemName.isBlank()) ms.muted else ms.textLight,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { isEditingItem = true },
                                            )
                                        }

                                        // 删除子项按钮
                                        IconButton(
                                            onClick = { viewModel.deleteBranchItem(item) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Text(
                                                text = "🗑",
                                                fontSize = 14.sp,
                                                color = Color(0xFFD4534A).copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }

                                // 添加子项按钮
                                Text(
                                    text = "+ 添加",
                                    color = ms.gold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { viewModel.addBranchItem(branch.id) }
                                        .padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 添加路标卡片（虚线边框占位）──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { viewModel.addBranch() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, ms.gold.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+ 添加路标",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ms.gold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
//  小时数字选择器 (0-23)
// ═══════════════════════════════════════════════════

@Composable
private fun HourPicker(
    currentHour: Int,
    onHourChange: (Int) -> Unit,
) {
    val ms = FukeTheme.mainlineSelect
    val gold = ms.gold

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 减少按钮
        Surface(
            onClick = { if (currentHour > 0) onHourChange(currentHour - 1) },
            shape = CircleShape,
            color = ms.card,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "−",
                    color = if (currentHour > 0) gold else ms.muted.copy(alpha = 0.3f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 当前小时显示
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ms.card,
        ) {
            Text(
                text = String.format("%02d:00", currentHour),
                color = if (currentHour > 0) gold else ms.muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 增加按钮
        Surface(
            onClick = { if (currentHour < 23) onHourChange(currentHour + 1) },
            shape = CircleShape,
            color = ms.card,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    color = if (currentHour < 23) gold else ms.muted.copy(alpha = 0.3f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
