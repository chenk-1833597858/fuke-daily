package com.fuke.daily.ui.mainline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.fuke.daily.viewmodel.MainlineDailyViewModel
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════
//  人生主线每日触发页 — 主动进入（底部导航"主线"tab）
//  固定深色背景，不受温暖/暗夜主题切换影响
// ═══════════════════════════════════════════════════

@Composable
fun MainlineDailyScreen(
    onBack: () -> Unit,
    onNavigateToMainlineDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainlineDailyViewModel = hiltViewModel(),
) {
    val ms = FukeTheme.mainlineSelect
    val uiState by viewModel.uiState.collectAsState()

    // ── 状态 ──
    var expanded by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf<MainlineBranch?>(null) }
    var selectedCurrentItem by remember { mutableStateOf<MainlineItem?>(null) }
    var selectedIdealBranch by remember { mutableStateOf<MainlineBranch?>(null) }

    // ── 呼吸光晕动画（breathGlow: 3s 循环脉冲）──
    val infiniteTransition = rememberInfiniteTransition(label = "breathGlow")
    val breathGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = { input ->
                // ease-in-out sine
                0.5f - 0.5f * kotlin.math.cos(input * kotlin.math.PI.toFloat())
            }),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathGlow",
    )

    // ── 大卡片缩放动画（scale 1 → 0.85）──
    val cardScale by animateFloatAsState(
        targetValue = if (expanded) 0.85f else 1f,
        animationSpec = tween(800),
        label = "cardScale",
    )

    // ── 大卡片margin/padding动画 ──
    val cardVerticalPadding by animateFloatAsState(
        targetValue = if (expanded) 14f else 36f,
        animationSpec = tween(800),
        label = "cardVPadding",
    )
    val cardHorizontalMargin by animateFloatAsState(
        targetValue = if (expanded) 8f else 0f,
        animationSpec = tween(800),
        label = "cardHMargin",
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

    // ── 辅助：找到现状关联的路标 ──
    fun findBranchForItem(item: MainlineItem): MainlineBranch? {
        return uiState.branches.find { it.id == item.branchId }
    }

    // 现状关联路标
    val currentBranch = selectedBranch

    // ── 确认回调 ──
    fun handleConfirm() {
        val currentItem = selectedCurrentItem ?: return
        val cBranch = currentBranch ?: return
        val mainList = uiState.mainList ?: return
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        viewModel.selectLink(
            currentItem = currentItem,
            currentBranch = cBranch,
            idealBranch = selectedIdealBranch,
            mainList = mainList,
            date = today,
            timestamp = System.currentTimeMillis(),
        )
        onNavigateToMainlineDetail(mainList.id)
    }

    fun handleSkip() {
        onBack()
    }

    // 主线名称
    val mainlineName = uiState.mainList?.name ?: "人生主线"

    // ── 空态 ──
    if (uiState.mainList == null && !uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ms.bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "✦",
                    color = ms.gold,
                    fontSize = 48.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无人生主线",
                    color = ms.textLight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请先在首页创建一个人生主线项目",
                    color = ms.muted,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(20.dp),
                    color = ms.gold,
                ) {
                    Text(
                        text = "返回",
                        color = ms.bg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
        return
    }

    // ── 构建路径条文字 ──
    fun buildPathText(): String {
        val cItem = selectedCurrentItem ?: return ""
        val cBranch = currentBranch ?: return ""
        val idealBranch = selectedIdealBranch

        return if (idealBranch != null && idealBranch.id != cBranch.id) {
            "${cItem.name} → ${cBranch.name} → ${idealBranch.name} → $mainlineName"
        } else {
            "${cItem.name} → ${cBranch.name} → $mainlineName"
        }
    }

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

            // ── 左上角跳过按钮（展开后）──
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

            Spacer(modifier = Modifier.height(if (expanded) 8.dp else 60.dp))

            // ── 人生主线大卡片（居中 / 缩小置顶）──
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    },
            ) {
                // ── 外层光晕（从边框向外散发，8层渐进扩散）──
                val glowAlpha = breathGlow
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            // 光晕从边框向外自然散发：用多层填充+alpha渐变模拟模糊扩散
                            val cornerRadius = 24.dp.toPx()
                            val maxExpand = 40f
                            val steps = 12
                            for (i in steps downTo 1) {
                                val expand = maxExpand * i / steps
                                // alpha从内(高)到外(低)平滑递减，模拟模糊扩散
                                val innerAlpha = glowAlpha * 0.25f
                                val alpha = innerAlpha * (1f - (i.toFloat() / steps))
                                drawRoundRect(
                                    color = ms.gold.copy(alpha = alpha),
                                    topLeft = Offset(-expand, -expand),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width + expand * 2,
                                        size.height + expand * 2,
                                    ),
                                    cornerRadius = CornerRadius(cornerRadius + expand),
                                )
                            }
                        }
                )

                // ── 卡片本体 ──
                Surface(
                    modifier = Modifier
                        .padding(
                            horizontal = cardHorizontalMargin.dp,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
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
                            .width(if (expanded) 320.dp else 280.dp)
                            .height(if (expanded) 80.dp else 160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 金色光晕背景层
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(ms.gold.copy(alpha = breathGlow * 0.12f)),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(
                                horizontal = cardVerticalPadding.dp,
                                vertical = (cardVerticalPadding * 0.4f).dp,
                            ),
                        ) {
                            if (!expanded) {
                                Text(
                                    text = "✦",
                                    color = ms.gold,
                                    fontSize = 28.sp,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = mainlineName,
                                color = ms.gold,
                                fontSize = if (expanded) 18.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!expanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (uiState.isEveningSession) "回顾今日所行" else "点击查看你的路",
                                    color = ms.muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 路标网格 + 展开内容 ──
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(),
                    ) {
                        // ── 路标2列网格（只选路标，不展开内容）──
                        val rows = branches.chunked(2)
                        rows.forEachIndexed { rowIndex, rowBranches ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowBranches.forEachIndexed { colIndex, branch ->
                                    val branchIndex = rowIndex * 2 + colIndex
                                    val isBranchSelected = selectedBranch?.id == branch.id
                                    val isOnTrack = isBranchSelected && selectedBranch?.id == selectedIdealBranch?.id

                                    Box(modifier = Modifier.weight(1f)) {
                                        StaggeredFadeInItem(index = branchIndex) {
                                            DailyBranchCard(
                                                branch = branch,
                                                isBranchSelected = isBranchSelected,
                                                isOnTrack = isOnTrack,
                                                onClick = {
                                                    if (isBranchSelected) {
                                                        // 取消选路标，清空后续选择
                                                        selectedBranch = null
                                                        selectedCurrentItem = null
                                                        selectedIdealBranch = null
                                                    } else {
                                                        selectedBranch = branch
                                                        selectedCurrentItem = null
                                                        selectedIdealBranch = null
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                if (rowBranches.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            if (rowIndex < rows.size - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // ── 现状标签卡片（选完路标后出现）──
                        if (selectedBranch != null) {
                            val branch = selectedBranch
                            if (branch != null) {
                                Spacer(modifier = Modifier.height(32.dp))
                                val isOnTrack = selectedBranch?.id == selectedIdealBranch?.id
                                val statusColor = if (isOnTrack) ms.gold else Color(0xFF64B5F6)
                                // 标签卡片：显示路标名，不可点击
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = ms.card,
                                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (uiState.isEveningSession) "今日所在" else "现状",
                                                color = statusColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "·",
                                                color = ms.muted,
                                                fontSize = 14.sp,
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = branch.name.ifBlank { "路标" },
                                                color = if (isOnTrack) ms.gold else ms.textLight,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 内容项选择列表
                                    val branchItems = uiState.items[branch.id] ?: emptyList()

                                    branchItems.forEach { item ->
                                        val isSelected = selectedCurrentItem?.id == item.id
                                        val itemColor = when {
                                            isSelected && isOnTrack -> ms.gold
                                            isSelected -> Color(0xFF64B5F6)
                                            else -> ms.muted
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isSelected) {
                                                        selectedCurrentItem = null
                                                        selectedIdealBranch = null
                                                    } else {
                                                        selectedCurrentItem = item
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(itemColor),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "已选中",
                                                        tint = ms.bg,
                                                        modifier = Modifier.size(9.dp),
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Transparent)
                                                        .padding(2.dp)
                                                        .clip(CircleShape)
                                                        .background(ms.muted.copy(alpha = 0.4f)),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item.name.ifBlank { "内容" },
                                                color = itemColor,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }

                        // ── 应走之路2列网格（选完现状后出现）──
                        if (selectedCurrentItem != null) {
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = if (uiState.isEveningSession) "应走之路" else "理想路标",
                                color = ms.textLight,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "选择你想走的路，与现状路标不同时可见差距",
                                color = ms.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )

                            val idealRows = branches.chunked(2)
                            idealRows.forEachIndexed { rowIndex, rowBranches ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    rowBranches.forEach { branch ->
                                        val isIdealSelected = selectedIdealBranch?.id == branch.id
                                        val isCurrentBranch = selectedBranch?.id == branch.id

                                        Box(modifier = Modifier.weight(1f)) {
                                            IdealBranchCard(
                                                branch = branch,
                                                isIdealSelected = isIdealSelected,
                                                isCurrentBranch = isCurrentBranch,
                                                onClick = {
                                                    if (isIdealSelected) {
                                                        selectedIdealBranch = null
                                                    } else {
                                                        selectedIdealBranch = branch
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    if (rowBranches.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                if (rowIndex < idealRows.size - 1) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // ── 底部路径条 ──
            if (selectedCurrentItem != null && currentBranch != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ms.card,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 路径文字
                            Text(
                                text = buildPathText(),
                                color = ms.gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            // 确认按钮（金色背景黑色文字）
                            Surface(
                                onClick = { handleConfirm() },
                                shape = RoundedCornerShape(20.dp),
                                color = ms.gold,
                            ) {
                                Text(
                                    text = if (uiState.isEveningSession) "完成回顾" else "确认选择",
                                    color = ms.bg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                            }
                        }
                        // 底部行字
                        Text(
                            text = "行之，成之，此去最近",
                            color = ms.gold.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            textAlign = TextAlign.Center,
                        )
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
//  路标卡片（只选路标，蓝色选中）
// ═══════════════════════════════════════════════════

@Composable
private fun DailyBranchCard(
    branch: MainlineBranch,
    isBranchSelected: Boolean,
    isOnTrack: Boolean,
    onClick: () -> Unit,
) {
    val ms = FukeTheme.mainlineSelect
    val blue = Color(0xFF64B5F6)
    val selectColor = if (isOnTrack && isBranchSelected) ms.gold else blue

    val borderColor = when {
        isBranchSelected -> selectColor
        else -> ms.card.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = ms.card,
        border = BorderStroke(
            width = if (isBranchSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBranchSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(selectColor),
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
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ms.card.copy(alpha = 0.5f))
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(ms.muted.copy(alpha = 0.4f)),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = branch.name.ifBlank { "路标" },
                color = if (isBranchSelected) selectColor else ms.textLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  理想路标卡片（金色选中，2列网格用）
// ═══════════════════════════════════════════════════

@Composable
private fun IdealBranchCard(
    branch: MainlineBranch,
    isIdealSelected: Boolean,
    isCurrentBranch: Boolean,
    onClick: () -> Unit,
) {
    val ms = FukeTheme.mainlineSelect
    val blue = Color(0xFF64B5F6)

    val isOnTrack = isCurrentBranch && isIdealSelected

    val borderColor = when {
        isOnTrack -> ms.gold
        isIdealSelected -> ms.gold
        isCurrentBranch -> blue.copy(alpha = 0.5f)
        else -> ms.card.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = ms.card,
        border = BorderStroke(
            width = if (isIdealSelected || isCurrentBranch) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isIdealSelected) {
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
            } else if (isCurrentBranch) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(blue.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(blue),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ms.card.copy(alpha = 0.5f))
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(ms.muted.copy(alpha = 0.4f)),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = branch.name.ifBlank { "路标" },
                color = when {
                    isOnTrack -> ms.gold
                    isIdealSelected -> ms.gold
                    isCurrentBranch -> blue
                    else -> ms.textLight
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isCurrentBranch && !isIdealSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "现状",
                    color = blue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(blue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}
// ═══════════════════════════════════════════════════

// ═══════════════════════════════════════════════════
//  Staggered Fade-In 动画项（cardFloatIn 效果）
//  opacity 0 + translateY 20 → opacity 1 + translateY 0
//  逐个延迟 0.08s
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
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(400),
        label = "staggerOffsetY",
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) {
        content()
    }
}

// ── 辅助：整除向上取整 ──
private fun Int.ceilDiv(other: Int): Int = (this + other - 1) / other
