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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    onNavigateToPermission: () -> Unit,
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
                onClick = onNavigateToPermission,
            )
            
            // ── 轮播速度 ──
            val carouselInterval by viewModel.carouselInterval.collectAsState(initial = 0L)
            val displayInterval = if (carouselInterval > 0) carouselInterval else 3000L
            var showCarouselDialog by remember { mutableStateOf(false) }
            
            SettingItem(
                icon = Icons.Filled.Timer,
                title = "轮播速度",
                subtitle = "${displayInterval}毫秒",
                onClick = { showCarouselDialog = true },
            )
            
            if (showCarouselDialog) {
                var inputValue by remember { mutableStateOf(if (carouselInterval > 0) carouselInterval.toString() else "3000") }
                
                AlertDialog(
                    onDismissRequest = { showCarouselDialog = false },
                    title = { Text("设置轮播速度") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = inputValue,
                                onValueChange = { inputValue = it.filter { c -> c.isDigit() } },
                                label = { Text("轮播间隔（毫秒）") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                singleLine = true,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "输入范围：500 ~ 10000毫秒",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.Gray,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val interval = inputValue.toLongOrNull() ?: 3000L
                                val clamped = interval.coerceIn(500L, 10000L)
                                viewModel.setCarouselInterval(clamped)
                                showCarouselDialog = false
                            },
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCarouselDialog = false }) {
                            Text("取消")
                        }
                    },
                )
            }
            
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
            var showBackupDialog by remember { mutableStateOf(false) }
            
            // 文件选择器（导入）
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    try {
                        val dbFile = File(context.getDatabasePath("fuke-daily-db").absolutePath)
                        context.contentResolver.openInputStream(it)?.use { input ->
                            dbFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        // 重启应用以加载新数据库
                        val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(restartIntent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } catch (e: Exception) {
                        // 导入失败
                    }
                }
            }
            
            SettingItem(
                icon = Icons.Filled.Backup,
                title = "数据备份与恢复",
                subtitle = "导出/导入 .db 文件",
                onClick = {
                    showBackupDialog = true
                },
            )
            
            // 备份/恢复对话框
            if (showBackupDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text("数据备份与恢复") },
                    text = { Text("选择操作：导出当前数据或导入备份文件") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                // 导出：复制数据库文件到 Downloads
                                try {
                                    val dbFile = File(context.getDatabasePath("fuke-daily-db").absolutePath)
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    val backupFile = File(downloadsDir, "fuke_daily_backup_$dateStr.db")
                                    dbFile.copyTo(backupFile, overwrite = true)
                                } catch (_: Exception) {}
                            }
                        ) {
                            Text("导出")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                // 导入：启动文件选择器
                                importLauncher.launch("*/*")
                            }
                        ) {
                            Text("导入")
                        }
                    },
                )
            }
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
