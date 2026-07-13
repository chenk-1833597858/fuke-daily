package com.fuke.daily.feature.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.*
import com.fuke.daily.ui.components.CompactInputField
import com.fuke.daily.ui.components.PageHeader
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.feature.timer.TimerViewModel

// ═══════════════════════════════════════════════════
//  定时任务配置页 — 严格按网页原型 task-app-warm.html
// ═══════════════════════════════════════════════════

@Composable
fun TimerConfigScreen(
    timerId: Long = 0,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val extended = FukeTheme.extended
    val uiState by viewModel.uiState.collectAsState()
    val existingTimer = uiState.timers.find { it.id == timerId }

    // ── 表单状态 ──
    var name by rememberSaveable { mutableStateOf("") }
    var timerType by rememberSaveable { mutableStateOf(TimerType.ALARM) }

    // 闹钟模式
    var alarmHour by rememberSaveable { mutableIntStateOf(8) }
    var alarmMinute by rememberSaveable { mutableIntStateOf(0) }
    var repeatMode by rememberSaveable { mutableStateOf(RepeatMode.NONE) }
    var selectedDays by rememberSaveable { mutableStateOf(setOf<Int>()) }

    // 提醒模式
    var startHour by rememberSaveable { mutableIntStateOf(9) }
    var startMinute by rememberSaveable { mutableIntStateOf(0) }
    var endHour by rememberSaveable { mutableIntStateOf(17) }
    var endMinute by rememberSaveable { mutableIntStateOf(0) }
    var reminderSubType by rememberSaveable { mutableStateOf(ReminderSubType.LOOP) }
    var intervalMinutes by rememberSaveable { mutableIntStateOf(30) }
    var reminderCount by rememberSaveable { mutableIntStateOf(3) }

    // 随机间隔模式
    var randomBaseInterval by rememberSaveable { mutableIntStateOf(1) }
    var randomMinMultiplier by rememberSaveable { mutableIntStateOf(1) }
    var randomMaxMultiplier by rememberSaveable { mutableIntStateOf(10) }
    var isAllDay by rememberSaveable { mutableStateOf(true) }

    // 提醒方式
    var alarmEnabled by rememberSaveable { mutableStateOf(true) }
    var vibrationEnabled by rememberSaveable { mutableStateOf(true) }
    var floatingWindowEnabled by rememberSaveable { mutableStateOf(true) }
    var alarmDuration by rememberSaveable { mutableIntStateOf(20) } // 响铃时长（秒）

    // 关联项目
    var linkedProjectId by rememberSaveable { mutableIntStateOf(0) }
    var showProjectPicker by rememberSaveable { mutableStateOf(false) }

    // ── 加载现有数据 ──
    LaunchedEffect(existingTimer) {
        existingTimer?.let { t ->
            name = t.name
            timerType = t.type
            alarmHour = t.hour
            alarmMinute = t.minute
            repeatMode = t.repeatMode
            selectedDays = if (t.selectedDays.isNotBlank()) {
                t.selectedDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            } else emptySet()
            startHour = t.startHour
            startMinute = t.startMinute
            endHour = t.endHour
            endMinute = t.endMinute
            reminderSubType = t.reminderSubType
            intervalMinutes = t.intervalMinutes
            reminderCount = t.count
            // 加载随机间隔模式字段
            randomBaseInterval = t.randomBaseInterval
            randomMinMultiplier = t.randomMinMultiplier
            randomMaxMultiplier = t.randomMaxMultiplier
            isAllDay = t.isAllDay
            alarmEnabled = t.alarmEnabled
            vibrationEnabled = t.vibrationEnabled
            floatingWindowEnabled = t.floatingWindowEnabled
            alarmDuration = t.alarmDuration
            linkedProjectId = t.linkedProjectId.toInt()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extended.bg),
    ) {
        // ── 页面头部 ──
        PageHeader(
            title = if (timerId == 0L) "新建定时任务" else "编辑定时任务",
            onBack = onBack,
        )

        // ── 可滚动内容区 ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ══════════════════════════════════════
            //  任务名称
            // ══════════════════════════════════════
            Section(title = "任务名称") {
                CompactInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "输入任务名称",
                    fontSize = 13,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 8.dp,
                    ),
                    cornerRadius = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ══════════════════════════════════════
            //  任务类型 — 竖排 SelectCard
            // ══════════════════════════════════════
            Section(title = "任务类型") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectCard(
                        icon = "⏰",
                        title = "闹钟",
                        desc = "在固定时间提醒，支持重复",
                        selected = timerType == TimerType.ALARM,
                        onClick = { timerType = TimerType.ALARM },
                    )
                    SelectCard(
                        icon = "🔔",
                        title = "提醒",
                        desc = "在时间范围内按间隔提醒",
                        selected = timerType == TimerType.REMINDER,
                        onClick = { timerType = TimerType.REMINDER },
                    )
                }
            }

            // ══════════════════════════════════════
            //  闹钟模式
            // ══════════════════════════════════════
            if (timerType == TimerType.ALARM) {
                // 闹钟时间
                Section(title = "闹钟时间") {
                    TimeRow(
                        label = "",
                        hour = alarmHour,
                        minute = alarmMinute,
                        onTimeChange = { h, m ->
                            alarmHour = h
                            alarmMinute = m
                        },
                    )
                }

                // 重复
                Section(title = "重复") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RepeatMode.entries.forEach { mode ->
                            SelectCard(
                                title = mode.displayName,
                                selected = repeatMode == mode,
                                onClick = { repeatMode = mode },
                            ) {
                                if (mode == RepeatMode.CUSTOM) {
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        dayOfWeekLabels.entries.forEach { (dayNum, dayLabel) ->
                                            val isSelected = dayNum in selectedDays
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    selectedDays = if (isSelected) {
                                                        selectedDays - dayNum
                                                    } else {
                                                        selectedDays + dayNum
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) extended.primary else extended.light,
                                                border = if (!isSelected) BorderStroke(1.dp, extended.border) else null,
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        dayLabel,
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) Color.White else extended.muted,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════
            //  提醒模式
            // ══════════════════════════════════════
            if (timerType == TimerType.REMINDER) {
                // 时间范围
                Section(title = "时间范围") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 全时段开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAllDay = !isAllDay }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("全时段", fontSize = 13.sp, color = extended.text)
                                Text("全天24小时随机提醒", fontSize = 11.sp, color = extended.muted)
                            }
                            // 开关
                            Surface(
                                modifier = Modifier.size(44.dp, 24.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAllDay) extended.primary else extended.border,
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .align(if (isAllDay) Alignment.CenterEnd else Alignment.CenterStart),
                                    )
                                }
                            }
                        }
                        // 非全时段时显示开始/结束时间
                        if (!isAllDay) {
                            TimeRow(
                                label = "开始",
                                hour = startHour,
                                minute = startMinute,
                                onTimeChange = { h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                            )
                            TimeRow(
                                label = "结束",
                                hour = endHour,
                                minute = endMinute,
                                onTimeChange = { h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                            )
                        }
                    }
                }

                // 间隔模式
                Section(title = "间隔模式") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 循环模式
                        SelectCard(
                            icon = "🔄",
                            title = "循环模式",
                            selected = reminderSubType == ReminderSubType.LOOP,
                            onClick = { reminderSubType = ReminderSubType.LOOP },
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("每", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = intervalMinutes.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 1
                                            intervalMinutes = value.coerceIn(1, 1440)
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("分钟提醒一次", fontSize = 12.sp, color = extended.text)
                                }
                                Text(
                                    "无限循环",
                                    fontSize = 11.sp,
                                    color = extended.muted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }

                        // 次数模式
                        SelectCard(
                            icon = "🔢",
                            title = "次数模式",
                            selected = reminderSubType == ReminderSubType.COUNT,
                            onClick = { reminderSubType = ReminderSubType.COUNT },
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("每", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = intervalMinutes.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 1
                                            intervalMinutes = value.coerceIn(1, 1440)
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("分钟提醒一次", fontSize = 12.sp, color = extended.text)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("共", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = reminderCount.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 1
                                            reminderCount = value.coerceIn(1, 999)
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("次后结束", fontSize = 12.sp, color = extended.text)
                                }
                            }
                        }

                        // 随机间隔模式
                        SelectCard(
                            icon = "🎲",
                            title = "随机间隔模式",
                            desc = "基础间隔 × 随机倍数，用于睡眠辅助",
                            selected = reminderSubType == ReminderSubType.RANDOM,
                            onClick = { reminderSubType = ReminderSubType.RANDOM },
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // 基础间隔
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("基础间隔", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = randomBaseInterval.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 1
                                            randomBaseInterval = value.coerceIn(1, 720)
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("分钟", fontSize = 12.sp, color = extended.text)
                                }
                                // 倍数范围
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("倍数", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = randomMinMultiplier.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 1
                                            randomMinMultiplier = value.coerceIn(1, 999)
                                            // 确保最小倍数不超过最大倍数
                                            if (randomMinMultiplier > randomMaxMultiplier) {
                                                randomMaxMultiplier = randomMinMultiplier
                                            }
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("~", fontSize = 12.sp, color = extended.text)
                                    CompactInputField(
                                        value = randomMaxMultiplier.toString(),
                                        onValueChange = { 
                                            val value = it.toIntOrNull() ?: 10
                                            randomMaxMultiplier = value.coerceAtLeast(randomMinMultiplier)
                                        },
                                        fontSize = 12,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 4.dp,
                                        ),
                                        cornerRadius = 6,
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                // 显示最大间隔
                                val maxIntervalMinutes = randomBaseInterval * randomMaxMultiplier
                                val maxIntervalText = if (maxIntervalMinutes >= 60) {
                                    "${maxIntervalMinutes / 60}小时${maxIntervalMinutes % 60}分钟"
                                } else {
                                    "${maxIntervalMinutes}分钟"
                                }
                                Text(
                                    "最大间隔: $maxIntervalText",
                                    fontSize = 11.sp,
                                    color = if (maxIntervalMinutes > 720) androidx.compose.ui.graphics.Color.Red else extended.muted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════
            //  提醒方式 — CheckRow (分割线用Column+Box实现)
            // ══════════════════════════════════════
            Section(title = "提醒方式") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CheckRow(
                        title = "闹铃",
                        desc = "播放系统闹铃声音",
                        checked = alarmEnabled,
                        onCheckedChange = { alarmEnabled = it },
                    )
                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(extended.border)
                    )
                    CheckRow(
                        title = "震动",
                        desc = "设备震动提醒",
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it },
                    )
                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(extended.border)
                    )
                    CheckRow(
                        title = "悬浮窗",
                        desc = "显示悬浮窗特效",
                        checked = floatingWindowEnabled,
                        onCheckedChange = { floatingWindowEnabled = it },
                    )
                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(extended.border)
                    )
                    // 响铃时长
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "响铃时长",
                                fontSize = 14.sp,
                                color = extended.text,
                            )
                            Text(
                                "闹钟响铃后自动关闭的时间",
                                fontSize = 11.sp,
                                color = extended.muted,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            CompactInputField(
                                value = alarmDuration.toString(),
                                onValueChange = { 
                                    val value = it.toIntOrNull() ?: 20
                                    alarmDuration = value.coerceIn(5, 300)
                                },
                                fontSize = 12,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 4.dp,
                                ),
                                cornerRadius = 6,
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text("秒", fontSize = 12.sp, color = extended.text)
                        }
                    }
                }
            }

            // ══════════════════════════════════════
            //  关联项目 — 点击展开选择器
            // ══════════════════════════════════════
            Section(title = "关联项目") {
                // 选择器触发行
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProjectPicker = !showProjectPicker },
                    shape = RoundedCornerShape(8.dp),
                    color = extended.inputBg,
                    border = BorderStroke(1.dp, extended.border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (linkedProjectId > 0) {
                                val list = uiState.mainLists.find { it.id == linkedProjectId.toLong() }
                                val listName = list?.name ?: "未知"
                                val typeLabel = list?.type?.let { getListTypeLabel(it) } ?: ""
                                if (typeLabel.isNotEmpty()) "已关联: $typeLabel . $listName" else "已关联: $listName"
                            } else {
                                "未关联"
                            },
                            fontSize = 13.sp,
                            color = if (linkedProjectId > 0) extended.primary else extended.muted,
                        )
                        Text("›", fontSize = 12.sp, color = extended.muted)
                    }
                }

                // 展开的选择器
                if (showProjectPicker) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 不关联
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    linkedProjectId = 0
                                    showProjectPicker = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (linkedProjectId == 0) extended.primary else extended.light,
                        ) {
                            Text(
                                "不关联",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                color = if (linkedProjectId == 0) Color.White else extended.muted,
                            )
                        }

                        // 项目列表
                        uiState.mainLists.forEach { list ->
                            val isSelected = linkedProjectId.toLong() == list.id
                            val typeLabel = getListTypeLabel(list.type)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        linkedProjectId = list.id.toInt()
                                        showProjectPicker = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) extended.primary else extended.light,
                            ) {
                                Text(
                                    "$typeLabel . ${list.name}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else extended.muted,
                                )
                            }
                        }
                    }
                }

                // 底部提示
                Text(
                    "定时任务触发时，双击悬浮图标将直接进入关联项目",
                    fontSize = 11.sp,
                    color = extended.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ══════════════════════════════════════
            //  提示卡片
            // ══════════════════════════════════════
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(10.dp),
                color = extended.card,
                border = BorderStroke(1.dp, extended.border),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("💡", fontSize = 14.sp)
                    Text(
                        text = when {
                            timerType == TimerType.ALARM && repeatMode == RepeatMode.NONE ->
                                "闹钟将在 ${alarmHour.toString().padStart(2, '0')}:${alarmMinute.toString().padStart(2, '0')} 提醒一次"
                            timerType == TimerType.ALARM && repeatMode == RepeatMode.DAILY ->
                                "闹钟将在每天 ${alarmHour.toString().padStart(2, '0')}:${alarmMinute.toString().padStart(2, '0')} 提醒"
                            timerType == TimerType.ALARM ->
                                "闹钟将在每周 ${alarmHour.toString().padStart(2, '0')}:${alarmMinute.toString().padStart(2, '0')} 提醒"
                            timerType == TimerType.REMINDER && reminderSubType == ReminderSubType.LOOP ->
                                "循环提醒将每隔 $intervalMinutes 分钟提醒一次，直到手动关闭"
                            timerType == TimerType.REMINDER && reminderSubType == ReminderSubType.RANDOM -> {
                                val maxInterval = randomBaseInterval * randomMaxMultiplier
                                val maxText = if (maxInterval >= 60) "${maxInterval / 60}小时${maxInterval % 60}分钟" else "${maxInterval}分钟"
                                "随机间隔提醒：基础间隔${randomBaseInterval}分钟，最大间隔$maxText"
                            }
                            else ->
                                "次数提醒将提醒 $reminderCount 次后自动停止"
                        },
                        fontSize = 12.sp,
                        color = extended.muted,
                    )
                }
            }
        }

        // ══════════════════════════════════════
        //  保存按钮
        // ══════════════════════════════════════
        val canSave = name.isNotBlank()
        Button(
            onClick = {
                val timer = TimerItem(
                    id = if (timerId > 0) timerId else 0,
                    name = name,
                    type = timerType,
                    hour = alarmHour,
                    minute = alarmMinute,
                    repeatMode = repeatMode,
                    selectedDays = selectedDays.sorted().joinToString(","),
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    reminderSubType = reminderSubType,
                    intervalMinutes = intervalMinutes,
                    count = reminderCount,
                    randomBaseInterval = randomBaseInterval,
                    randomMinMultiplier = randomMinMultiplier,
                    randomMaxMultiplier = randomMaxMultiplier,
                    isAllDay = isAllDay,
                    alarmEnabled = alarmEnabled,
                    vibrationEnabled = vibrationEnabled,
                    floatingWindowEnabled = floatingWindowEnabled,
                    alarmDuration = alarmDuration,
                    isEnabled = true,
                    linkedProjectId = linkedProjectId.toLong(),
                    message = "",
                )
                if (timerId > 0) {
                    viewModel.updateTimer(timer, context)
                } else {
                    viewModel.insertTimer(timer, context)
                }
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canSave) extended.primary else extended.border,
                contentColor = Color.White,
                disabledContainerColor = extended.border,
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                "保存",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ═══════════════════════════════════════════════════
//  辅助组件 — 严格按网页原型
// ═══════════════════════════════════════════════════

/**
 * Section 组件 — 标题(12sp, muted色) + 卡片容器(card背景, 12dp圆角, border边框, 12dp内边距)
 */
@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    val extended = FukeTheme.extended
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = extended.muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = extended.card,
            border = BorderStroke(1.dp, extended.border),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

/**
 * SelectCard — 选择卡片（竖排，带图标+描述+✓）
 * 选中时: primary色边框 + contentBg背景 + ✓
 * 未选中: border边框 + card背景
 */
@Composable
private fun SelectCard(
    icon: String? = null,
    title: String,
    desc: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    children: @Composable () -> Unit = {},
) {
    val extended = FukeTheme.extended
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) extended.contentBg else extended.card,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) extended.primary else extended.border,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Text(icon, fontSize = 16.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = extended.text,
                    )
                    if (desc != null) {
                        Text(
                            desc,
                            fontSize = 11.sp,
                            color = extended.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (selected) {
                    Text("✓", fontSize = 14.sp, color = extended.primary)
                }
            }
            // 展开子内容
            if (selected) {
                children()
            }
        }
    }
}

/**
 * TimeRow — 时间显示行（点击弹出滚轮时间选择器）
 * label(50dp宽) + 时:分显示(inputBg背景+border边框包裹)，点击弹出TimePickerDialog
 */
@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
) {
    val extended = FukeTheme.extended
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // label
        if (label.isNotEmpty()) {
            Box(modifier = Modifier.width(50.dp)) {
                Text(label, fontSize = 12.sp, color = extended.muted)
            }
        }
        // 时:分显示区域（可点击）
        Surface(
            modifier = Modifier
                .clickable { showPicker = true },
            shape = RoundedCornerShape(8.dp),
            color = extended.inputBg,
            border = BorderStroke(1.dp, extended.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = hour.toString().padStart(2, '0'),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = extended.text,
                )
                Text(
                    ":",
                    fontSize = 16.sp,
                    color = extended.muted,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = minute.toString().padStart(2, '0'),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = extended.text,
                )
            }
        }
    }

    // 时间选择弹窗（底部弹窗升起动画）
    TimePickerDialog(
        showPicker = showPicker,
        initialHour = hour,
        initialMinute = minute,
        onConfirm = { h, m ->
            onTimeChange(h, m)
            showPicker = false
        },
        onDismiss = {
            showPicker = false
        },
    )
}

/**
 * TimePickerDialog — 全局弹窗式时间选择器
 * 用Android原生TimePicker，自带滚轮，不会出现选中值与实际不一致
 * 顶部：取消(muted色) + 标题 + 确定(primary色)
 */
@Composable
private fun TimePickerDialog(
    showPicker: Boolean,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val extended = FukeTheme.extended
    var selectedHour by rememberSaveable { mutableIntStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialMinute) }

    // 当弹窗显示时，重置选中值为当前传入的值
    LaunchedEffect(showPicker) {
        if (showPicker) {
            selectedHour = initialHour
            selectedMinute = initialMinute
        }
    }

    if (showPicker) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            // 弹窗主体
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = extended.card,
                border = BorderStroke(1.dp, extended.border),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 顶部操作栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "取消",
                            fontSize = 14.sp,
                            color = extended.muted,
                            modifier = Modifier.clickable { onDismiss() },
                        )
                        Text(
                            "选择时间",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = extended.text,
                        )
                        Text(
                            "确定",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = extended.primary,
                            modifier = Modifier.clickable { onConfirm(selectedHour, selectedMinute) },
                        )
                    }

                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(extended.border)
                    )

                    // 原生TimePicker
                    AndroidView(
                        factory = { context ->
                            android.widget.TimePicker(context).apply {
                                setIs24HourView(true)
                                hour = initialHour
                                minute = initialMinute
                                setOnTimeChangedListener { _, h, m ->
                                    selectedHour = h
                                    selectedMinute = m
                                }
                            }
                        },
                        update = { picker ->
                            // 只在弹窗刚打开时同步，避免滚动时冲突
                            if (showPicker) {
                                if (picker.hour != initialHour) picker.hour = initialHour
                                if (picker.minute != initialMinute) picker.minute = initialMinute
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * WheelPicker — 滚轮选择器
 * 用LazyColumn + contentPadding实现，无占位item，索引一一对应
 * 显示5行，中间高亮，上下渐变遮罩，自动snap
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = FukeTheme.extended
    val itemHeightDp = 36.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { itemHeightDp.toPx() }
    val visibleItemCount = 5
    val halfVisible = visibleItemCount / 2  // 2

    // 初始位置：让selectedIndex出现在中间
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex,
        initialFirstVisibleItemScrollOffset = 0,
    )

    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 监听滚动，计算中间项索引
    LaunchedEffect(listState) {
        snapshotFlow {
            // firstVisibleItemIndex就是实际item索引（没有占位项了）
            // 中间项 = firstVisibleItemIndex + halfVisible + (是否过半)
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val pastHalf = if (offset > itemHeightPx / 2) 1 else 0
            (firstIndex + halfVisible + pastHalf).coerceIn(0, items.lastIndex)
        }.collect { actualIndex ->
            if (actualIndex != selectedIndex) {
                onSelectedChange(actualIndex)
            }
        }
    }

    // contentPadding让首尾项能滚到中间位置
    val paddingPx = with(density) { (itemHeightDp * halfVisible).toPx().toInt() }

    Box(modifier = modifier.height(itemHeightDp * visibleItemCount)) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = with(density) { paddingPx.toDp() }, bottom = with(density) { paddingPx.toDp() }),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 实际选项，索引0=items[0]，一一对应
            items(items.size) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isSelected) 20.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) extended.primary else extended.muted,
                    )
                }
            }
        }

        // 上方渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeightDp * 2)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(extended.card, extended.card.copy(alpha = 0f)),
                    )
                )
        )
        // 下方渐变遮罩
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeightDp * 2)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(extended.card.copy(alpha = 0f), extended.card),
                    )
                )
        )

        // 中间高亮指示
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(extended.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
        )
    }
}

/**
 * CheckRow — 自定义勾选框行
 * 18dp方形圆角4dp勾选框（选中primary色+白✓，未选border+透明）
 * 右边标题+描述
 * 分割线由外部Column+Box实现，不再使用showBottomBorder参数
 */
@Composable
private fun CheckRow(
    title: String,
    desc: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extended = FukeTheme.extended
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 方形勾选框
        Surface(
            modifier = Modifier.size(18.dp),
            shape = RoundedCornerShape(4.dp),
            color = if (checked) extended.primary else Color.Transparent,
            border = BorderStroke(1.5.dp, if (checked) extended.primary else extended.border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Text("✓", fontSize = 11.sp, color = Color.White, lineHeight = 11.sp)
                }
            }
        }
        // 标题+描述
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = extended.text)
            if (desc != null) {
                Text(desc, fontSize = 11.sp, color = extended.muted, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  辅助函数：获取列表类型中文标签
// ═══════════════════════════════════════════════════

private fun getListTypeLabel(type: com.fuke.daily.data.model.ListType): String = when (type) {
    com.fuke.daily.data.model.ListType.SELECTION -> "选择"
    com.fuke.daily.data.model.ListType.RANDOM -> "随机"
    com.fuke.daily.data.model.ListType.QUIZ -> "答题"
    com.fuke.daily.data.model.ListType.MAINLINE -> "人生主线"
}
