package com.fuke.daily.ui.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuke.daily.ui.theme.FukeTheme

// ═══════════════════════════════════════════════════
//  权限引导页
// ═══════════════════════════════════════════════════

@Composable
fun PermissionGuideScreen(
    onAllRequiredPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extended = FukeTheme.extended

    // ── 权限状态 ──
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotification by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasExactAlarm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else true
        )
    }

    // ── 从系统设置返回后刷新权限状态 ──
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotification = granted
    }

    val alarmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            hasExactAlarm = alarmManager.canScheduleExactAlarms()
        }
    }

    // ── 每次从系统设置返回时刷新权限状态 ──
    LifecycleResumeEffect(Unit) {
        // onResume: 刷新权限
        hasOverlay = Settings.canDrawOverlays(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotification = context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            hasExactAlarm = alarmManager.canScheduleExactAlarms()
        }
        onPauseOrDispose { /* nothing */ }
    }

    val allRequiredGranted = hasOverlay && hasNotification

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extended.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 标题区 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = "权限设置",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = extended.text,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "为了正常使用应用功能，请开启以下权限",
                fontSize = 14.sp,
                color = extended.muted,
            )
        }

        // ── 必须权限 ──
        Text(
            text = "必须权限",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = extended.muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 8.dp),
        )

        // 悬浮窗权限
        PermissionCard(
            title = "悬浮窗权限",
            description = "用于显示悬浮图标和弹窗",
            isRequired = true,
            isGranted = hasOverlay,
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                overlayLauncher.launch(intent)
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 通知权限
        PermissionCard(
            title = "通知权限",
            description = "用于显示提醒通知",
            isRequired = true,
            isGranted = hasNotification,
            onOpenSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── 可选权限 ──
        Text(
            text = "可选权限",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = extended.muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 8.dp),
        )

        // 精确闹钟权限
        PermissionCard(
            title = "精确闹钟权限",
            description = "用于定时任务的精确触发",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Android 12+" else null,
            isRequired = false,
            isGranted = hasExactAlarm,
            onOpenSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    )
                    alarmLauncher.launch(intent)
                }
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 电池优化白名单
        var hasBatteryWhitelist by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            hasBatteryWhitelist = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        PermissionCard(
            title = "电池优化白名单",
            description = "防止系统后台杀死应用，确保定时任务和悬浮窗正常运行",
            isRequired = false,
            isGranted = hasBatteryWhitelist,
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 自启动权限
        PermissionCard(
            title = "自启动权限",
            description = "允许应用开机自启动，确保定时任务和悬浮窗在重启后自动恢复",
            subtitle = "部分国产 ROM 需要手动设置",
            isRequired = false,
            isGranted = false, // 无法直接检测，引导用户手动设置
            onOpenSettings = {
                // 尝试打开自启动设置页面（不同厂商路径不同）
                val intents = listOf(
                    Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                    Intent("com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
                for (intent in intents) {
                    try {
                        context.startActivity(intent)
                        break
                    } catch (_: Exception) {
                        continue
                    }
                }
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── 底部按钮 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    // 刷新权限状态后再回调
                    hasOverlay = Settings.canDrawOverlays(context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        hasNotification = context.checkSelfPermission(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    onAllRequiredPermissionsGranted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (allRequiredGranted) extended.success else extended.accent,
                    contentColor = if (allRequiredGranted) androidx.compose.ui.graphics.Color.White else extended.text,
                ),
            ) {
                Text(
                    text = if (allRequiredGranted) "进入应用" else "我已开启权限",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  权限卡片
// ═══════════════════════════════════════════════════

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isRequired: Boolean,
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val extended = FukeTheme.extended

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(extended.card)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：标题 + 标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.text,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = extended.muted,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 必须/可选标签
                Text(
                    text = if (isRequired) "必须" else "可选",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isRequired) extended.accent else extended.success)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // 右侧：按钮或已开启
            if (isGranted) {
                Text(
                    text = "已开启✓",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.success,
                )
            } else {
                androidx.compose.material3.TextButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "去开启",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = extended.text,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = extended.muted,
        )
    }
}
