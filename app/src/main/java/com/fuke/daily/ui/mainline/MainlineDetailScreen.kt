package com.fuke.daily.ui.mainline

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.LinkHistoryEntry
import com.fuke.daily.viewmodel.MainlineViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════
//  主线详情页 — 深色沉浸 + 路径节点线
//  固定深色背景 #0D0D1A，不受主题切换影响
// ═══════════════════════════════════════════════════

@Composable
fun MainlineDetailScreen(
    listId: Long,
    onBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainlineViewModel = hiltViewModel(),
) {
    val ms = FukeTheme.mainlineSelect
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 初始化
    LaunchedEffect(listId) {
        viewModel.loadMainline(listId)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModel.loadTodayLinks(today)
    }
    
    // 每次进入页面都重新加载今日数据
    LaunchedEffect(Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModel.loadTodayLinks(today)
    }

    val todayLinks = uiState.todayLinks
    val recentHistory = uiState.linkHistory
    val mainlineName = uiState.mainList?.name ?: "人生主线"

    // ── 呼吸光晕动画（breathGlow: 3s 循环脉冲）──
    val infiniteTransition = rememberInfiniteTransition(label = "breathGlow")
    val breathGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = { input ->
                0.5f - 0.5f * kotlin.math.cos(input * kotlin.math.PI.toFloat())
            }),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathGlow",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ms.bg),
    ) {
        // ── 顶栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 48.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回按钮（金色边框）
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ms.gold),
            ) {
                Text("←", color = ms.gold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 标题
            Text(
                text = "人生主线详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ms.gold,
                modifier = Modifier.weight(1f),
            )
            // 编辑按钮
            IconButton(onClick = onNavigateToEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = ms.gold,
                )
            }
            // 删除按钮
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFE57373),
                )
            }
        }

        // 删除确认对话框
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = { Text("确定要删除这个人生主线项目吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteMainline()
                            onDelete()
                        }
                    ) {
                        Text("删除", color = Color(0xFFE57373))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // ── 内容区（可滚动）──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ── 目标卡片（带呼吸光晕）──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 外层光晕（从边框自然散发）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            val cornerRadius = 20.dp.toPx()
                            val maxExpand = 40f
                            val steps = 12
                            for (i in steps downTo 1) {
                                val expand = maxExpand * i / steps
                                val innerAlpha = breathGlow * 0.25f
                                val alpha = innerAlpha * (1f - (i.toFloat() / steps))
                                drawRoundRect(
                                    color = ms.gold.copy(alpha = alpha),
                                    topLeft = Offset(-expand, -expand),
                                    size = Size(size.width + expand * 2, size.height + expand * 2),
                                    cornerRadius = CornerRadius(cornerRadius + expand),
                                )
                            }
                        }
                )

                // 卡片本体
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ms.card,
                    border = BorderStroke(
                        width = 2.dp,
                        color = ms.gold.copy(alpha = breathGlow),
                    ),
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 金色光晕背景层
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(ms.gold.copy(alpha = breathGlow * 0.12f))
                                .align(Alignment.Center)
                                .matchParentSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "✦",
                                color = ms.gold,
                                fontSize = 24.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mainlineName,
                                color = ms.gold,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── 今日所行 ──
            Text(
                text = "今日所行",
                color = ms.textLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (todayLinks.isEmpty()) {
                // ── 空态 ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "✦",
                            color = ms.gold,
                            fontSize = 36.sp,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "等待选择...",
                            color = ms.muted,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "今天还没有选择链路",
                            color = ms.muted.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                // ── 路径节点线展示（只显示最新一条）──
                todayLinks.firstOrNull()?.let { link ->
                    PathNodeLine(path = link.path)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── 历程（每天只显示一条，最多7天）──
            if (recentHistory.isNotEmpty()) {
                Text(
                    text = "历程",
                    color = ms.textLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                recentHistory.forEachIndexed { index, entry ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HistoryRow(entry = entry)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
//  路径节点线 — 垂直圆点 + 竖线连接
// ═══════════════════════════════════════════════════

@Composable
private fun PathNodeLine(
    path: List<Pair<Long, String>>,
) {
    val gold = Color(0xFFFFC107)
    val blue = Color(0xFF64B5F6)

    // ── 节点颜色推断 ──
    // path[0] = 现状（蓝色，一致时金色）
    // path[1] = 现状关联路标（蓝色，一致时金色）
    // path[last-1] = 理想路标（金色）
    // path[last] = 目标（金色）
    // 现状路标=理想路标时，现状和现状路标都变金色
    val isOnTrack = when {
        path.size >= 4 -> path[1].first == path[path.lastIndex - 1].first
        path.size == 3 -> true // 只有一个路标，必然一致
        else -> false
    }
    fun nodeColor(index: Int): Color {
        return when {
            path.size <= 2 -> {
                // 现状 → 目标
                if (index == 0 && isOnTrack) gold else if (index == 0) blue else gold
            }
            path.size == 3 -> {
                // 现状(金) → 路标(金) → 目标(金) — 只有一个路标=走对了
                gold
            }
            else -> {
                // path.size >= 4
                when (index) {
                    0 -> if (isOnTrack) gold else blue  // 现状
                    1 -> if (isOnTrack) gold else blue  // 现状路标
                    else -> gold // 理想路标 + 目标
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
    ) {
        path.forEachIndexed { index, node ->
            val color = nodeColor(index)

            // 节点行：圆点 + 文字
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 圆点
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = node.second,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 连接竖线（非最后一个节点）
            if (index < path.lastIndex) {
                val lineColor = color // 跟上方节点颜色一致
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp) // 圆点中心对齐 (12/2 - 1)
                        .width(2.dp)
                        .height(24.dp)
                        .background(lineColor),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  历程行 — 日期 + 路径文字
// ═══════════════════════════════════════════════════

@Composable
private fun HistoryRow(
    entry: LinkHistoryEntry,
) {
    val ms = FukeTheme.mainlineSelect
    val gold = Color(0xFFFFC107)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ms.card,
        border = BorderStroke(1.dp, gold.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 日期
            Text(
                text = entry.date,
                color = ms.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(12.dp))
            // 路径文字（用 → 连接）
            Text(
                text = entry.path.joinToString(" → ") { it.second },
                color = ms.textLight,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
