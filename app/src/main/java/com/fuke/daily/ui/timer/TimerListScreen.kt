package com.fuke.daily.ui.timer

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.model.TimerItem
import com.fuke.daily.data.model.TimerType
import com.fuke.daily.data.model.RepeatMode
import com.fuke.daily.data.model.ReminderSubType
import com.fuke.daily.data.model.dayOfWeekLabels
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.TimerViewModel

// ═══════════════════════════════════════════════════
//  定时任务列表页
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerListScreen(
    onAddTimer: () -> Unit,
    onEditTimer: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val extended = FukeTheme.extended
    val uiState by viewModel.uiState.collectAsState()

    var deleteTarget by remember { mutableStateOf<TimerItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时任务", color = extended.text) },
                actions = {
                    IconButton(onClick = onAddTimer) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            tint = extended.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = extended.bg),
            )
        },
        containerColor = extended.bg,
    ) { paddingValues ->
        if (uiState.timers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = extended.muted.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无定时任务", fontSize = 14.sp, color = extended.muted.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.clickable(onClick = onAddTimer),
                        shape = RoundedCornerShape(16.dp),
                        color = extended.light,
                    ) {
                        Text(
                            text = "  + 添加定时任务  ",
                            fontSize = 13.sp,
                            color = extended.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(
                    items = uiState.timers,
                    key = { it.id },
                ) { timer ->
                    TimerCard(
                        timer = timer,
                        mainLists = uiState.mainLists,
                        onToggle = { viewModel.toggleTimer(timer.id, context) },
                        onEdit = { onEditTimer(timer.id) },
                        onDelete = { deleteTarget = timer },
                    )
                }
            }
        }
    }

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除", color = extended.text) },
            text = { Text("确定要删除「${target.name}」吗？", color = extended.muted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTimer(target.id, context)
                    deleteTarget = null
                }) {
                    Text("删除", color = Color(0xFFD4534A))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = extended.muted)
                }
            },
            containerColor = extended.card,
        )
    }
}

@Composable
private fun TimerCard(
    timer: TimerItem,
    mainLists: List<MainList>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val extended = FukeTheme.extended

    val typeColor = when (timer.type) {
        TimerType.ALARM -> extended.primary
        TimerType.REMINDER -> Color(0xFF6BA3D6)
    }

    val typeIcon = when (timer.type) {
        TimerType.ALARM -> Icons.Default.Alarm
        TimerType.REMINDER -> Icons.Default.AccessTime
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        color = extended.card,
        border = BorderStroke(1.dp, extended.border),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = timer.type.displayName,
                    tint = typeColor,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = timer.name.ifBlank { "未命名" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Switch(
                    checked = timer.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedTrackColor = typeColor),
                    modifier = Modifier.height(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 详情
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (timer.type) {
                    TimerType.ALARM -> {
                        Text(
                            text = "${timer.hour.toString().padStart(2, '0')}:${timer.minute.toString().padStart(2, '0')}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = extended.text,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (timer.repeatMode) {
                                RepeatMode.NONE -> "不重复"
                                RepeatMode.DAILY -> "每天"
                                RepeatMode.WEEKLY -> "每周"
                                RepeatMode.CUSTOM -> {
                                    if (timer.selectedDays.isNotBlank()) {
                                        timer.selectedDays.split(",").mapNotNull {
                                            dayOfWeekLabels[it.trim().toIntOrNull() ?: 0]
                                        }.joinToString(" ")
                                    } else "自定义"
                                }
                            },
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                    }
                    TimerType.REMINDER -> {
                        Text(
                            text = "${timer.startHour.toString().padStart(2, '0')}:${timer.startMinute.toString().padStart(2, '0')} - ${timer.endHour.toString().padStart(2, '0')}:${timer.endMinute.toString().padStart(2, '0')}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = extended.text,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (timer.reminderSubType) {
                                ReminderSubType.LOOP -> "每隔${timer.intervalMinutes}分钟"
                                ReminderSubType.COUNT -> "${timer.count}次"
                            },
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 提醒方式图标
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (timer.alarmEnabled) {
                        Text("🔔", fontSize = 12.sp)
                    }
                    if (timer.vibrationEnabled) {
                        Text("📳", fontSize = 12.sp)
                    }
                    if (timer.floatingWindowEnabled) {
                        Text("🪟", fontSize = 12.sp)
                    }
                }
            }

            // 关联项目
            if (timer.linkedProjectId > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                val linkedName = mainLists.find { it.id == timer.linkedProjectId }?.name
                    ?: "项目#${timer.linkedProjectId}"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = extended.light,
                ) {
                    Text(
                        text = "🔗 $linkedName",
                        fontSize = 11.sp,
                        color = extended.muted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // 删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = extended.muted.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
