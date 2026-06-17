package com.fuke.daily.ui.mainline

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.MainlineBranch
import com.fuke.daily.data.model.MainlineItem
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.MainlineViewModel
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════
//  主线选择页 — 被动弹出，固定深色背景
// ═══════════════════════════════════════════════════

@Composable
fun MainlineSelectScreen(
    listId: Long,
    onBack: () -> Unit,
    onNavigateToDetail: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainlineViewModel = hiltViewModel(),
    onSelected: ((List<Pair<Long, String>>) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val ms = FukeTheme.mainlineSelect
    val uiState by viewModel.uiState.collectAsState()

    // 初始化加载
    LaunchedEffect(listId) {
        viewModel.loadMainline(listId)
    }

    // ── 状态 ──
    var expanded by remember { mutableStateOf(false) }       // 大卡片是否已点击（展开路标网格）
    var selectedPath = remember { mutableStateListOf<Pair<Long, String>>() }

    // ── 呼吸光晕动画（breathGlow: 3s 循环脉冲）──
    val infiniteTransition = rememberInfiniteTransition(label = "breathGlow")
    val breathGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathGlow",
    )

    // ── 大卡片缩放动画 ──
    val cardScale by animateFloatAsState(
        targetValue = if (expanded) 0.6f else 1f,
        animationSpec = tween(500),
        label = "cardScale",
    )

    // ── 路标网格 staggered fade-in ──
    var branchesVisible by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(300)
            branchesVisible = true
        } else {
            branchesVisible = false
        }
    }

    // ── 确认回调 ──
    fun handleConfirm() {
        if (selectedPath.isNotEmpty()) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            viewModel.selectLink(selectedPath.toList(), today, System.currentTimeMillis())
            onSelected?.invoke(selectedPath.toList())
        }
        onBack()
    }

    fun handleSkip() {
        onSkip?.invoke()
        onBack()
    }

    // 主线名称
    val mainlineName = uiState.mainList?.name ?: "人生主线"

    // ── 渲染 ──
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ms.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── 左上角跳过按钮 ──
            if (expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 8.dp),
                ) {
                    TextButton(onClick = { handleSkip() }) {
                        Text(
                            text = "跳过",
                            color = ms.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (expanded) 8.dp else 80.dp))

            // ── 人生主线大卡片（居中 / 缩小置顶）──
            Surface(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .clickable {
                        if (!expanded) expanded = true
                    },
                shape = RoundedCornerShape(24.dp),
                color = ms.card,
                border = BorderStroke(
                    width = 2.dp,
                    color = ms.gold.copy(alpha = breathGlow),
                ),
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 金色光晕背景层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(ms.gold.copy(alpha = breathGlow * 0.08f)),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "✦",
                            color = ms.gold,
                            fontSize = 28.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = mainlineName,
                            color = ms.gold,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击查看你的路",
                                color = ms.muted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 路标2列网格 ──
            if (expanded && branchesVisible) {
                val branches = uiState.branches

                if (branches.isEmpty()) {
                    Text(
                        text = "暂无路标，请先编辑人生主线",
                        color = ms.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 路标卡片
                        itemsIndexed(
                            items = branches,
                            key = { _, branch -> branch.id },
                        ) { index, branch ->
                            val branchItems = uiState.items[branch.id] ?: emptyList()
                            val isBranchSelected = selectedPath.any { it.first == branch.id }

                            StaggeredFadeInItem(
                                index = index,
                            ) {
                                BranchCard(
                                    branch = branch,
                                    items = branchItems,
                                    isSelected = isBranchSelected,
                                    selectedItems = selectedPath,
                                    onSelectBranch = {
                                        // 选择路标：如果已选该路标则取消，否则加入路径
                                        if (isBranchSelected) {
                                            selectedPath.removeAll { it.first == branch.id }
                                        } else {
                                            // 先清除其他路标
                                            selectedPath.clear()
                                            selectedPath.add(branch.id to branch.name)
                                        }
                                    },
                                    onSelectItem = { item ->
                                        // 选择子项：加入路径
                                        selectedPath.clear()
                                        selectedPath.add(branch.id to branch.name)
                                        // 如果不在路径中则加入
                                        if (!selectedPath.any { it.first == item.id }) {
                                            selectedPath.add(item.id to item.name)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // ── 底部当前链路路径 ──
            if (selectedPath.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ms.card,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedPath.joinToString(" > ") { it.second },
                            color = ms.gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // 确认按钮（金色）
                        Surface(
                            onClick = { handleConfirm() },
                            shape = RoundedCornerShape(20.dp),
                            color = ms.gold,
                        ) {
                            Text(
                                text = "确认",
                                color = ms.bg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── 左上角跳过按钮（未展开时）──
        if (!expanded) {
            TextButton(
                onClick = { handleSkip() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 48.dp),
            ) {
                Text(
                    text = "跳过",
                    color = ms.muted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  路标卡片
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BranchCard(
    branch: MainlineBranch,
    items: List<MainlineItem>,
    isSelected: Boolean,
    selectedItems: List<Pair<Long, String>>,
    onSelectBranch: () -> Unit,
    onSelectItem: (MainlineItem) -> Unit,
) {
    val ms = FukeTheme.mainlineSelect

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ms.card,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ms.gold else ms.card,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // 路标名称（长按选中）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onSelectBranch,
                        onLongClick = onSelectBranch,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 选中标记
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ms.gold),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = ms.bg,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = branch.name.ifBlank { "路标" },
                    color = if (isSelected) ms.gold else ms.textLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 子项列表
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                items.forEach { item ->
                    val isItemSelected = selectedItems.any { it.first == item.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectItem(item) },
                                onLongClick = { onSelectItem(item) },
                            )
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isItemSelected) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(ms.gold),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = ms.bg,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Spacer(modifier = Modifier.width(18.dp))
                        }
                        Text(
                            text = item.name.ifBlank { "子项" },
                            color = if (isItemSelected) ms.gold else ms.muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  Staggered Fade-In 动画项
// ═══════════════════════════════════════════════════

@Composable
private fun StaggeredFadeInItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(index) {
        delay(index * 80L)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "staggerAlpha",
    )

    Box(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    ) {
        content()
    }
}
