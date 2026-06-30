package com.fuke.daily.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val extended = FukeTheme.extended
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 更新检查状态
    var isChecking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = extended.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = extended.text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = extended.card,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(extended.bg)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 主题切换 ──
            SettingItem(
                icon = Icons.Filled.DarkMode,
                title = "主题",
                subtitle = if (uiState.currentTheme == ThemeMode.WARM) "温暖" else "暗夜",
                onClick = { viewModel.switchTheme() },
            )
            
            // ── 权限获取 ──
            SettingItem(
                icon = Icons.Filled.Security,
                title = "权限获取",
                subtitle = "管理应用所需权限",
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
            )
            
            // ── 日志 ──
            SettingItem(
                icon = Icons.Filled.Description,
                title = "日志",
                subtitle = "查看应用运行日志",
                onClick = onNavigateToLogs,
            )
            
            // ── 检查更新 ──
            SettingItem(
                icon = Icons.Filled.Update,
                title = "检查更新",
                subtitle = if (isChecking) "检查中…" else if (checkMessage.isNotEmpty()) checkMessage else "检查最新版本",
                onClick = {
                    if (isChecking) return@SettingItem
                    isChecking = true
                    checkMessage = ""
                    scope.launch {
                        // 模拟检查更新（实际应调用 AppUpdater.checkUpdate）
                        kotlinx.coroutines.delay(1000)
                        isChecking = false
                        checkMessage = "已是最新版本"
                    }
                },
            )
            
            // ── 数据备份与恢复 ──
            SettingItem(
                icon = Icons.Filled.Backup,
                title = "数据备份与恢复",
                subtitle = "导出/导入 .db 文件",
                onClick = {
                    // TODO: 实现数据备份恢复
                },
            )
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val extended = FukeTheme.extended
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = extended.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = extended.primary,
                modifier = Modifier.size(24.dp),
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = extended.text,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = extended.muted,
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "进入",
                tint = extended.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
