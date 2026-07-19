package com.fuke.daily.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.fuke.daily.data.model.ListType
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppUpdater
import com.fuke.daily.util.AppLogger
import com.fuke.daily.util.DatabaseImportMerger
import com.fuke.daily.util.FukeBackupExporter
import com.fuke.daily.util.FukeBackupImporter
import com.fuke.daily.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * 导入状态
 */
sealed class ImportState {
    data object Idle : ImportState()           // 空闲
    data object Validating : ImportState()      // 校验中
    data class PartialMatch(
        val similarity: Float,
        val matchedTables: List<String>,
        val missingTables: List<String>,
        val uri: Uri
    ) : ImportState()                          // 部分匹配，需要用户确认
    data object Merging : ImportState()         // 合并中
    data class Success(val counts: Map<String, Int>) : ImportState()  // 成功
    data class Error(val message: String) : ImportState()             // 失败
    data object Mismatch : ImportState()        // 文件不匹配
    data class MainlineConflict(val uri: Uri) : ImportState()  // 导入文件含人生主线，当前也有
}

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

    // 导入状态
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }

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
                        val info = AppUpdater.checkUpdate(context)
                        isChecking = false
                        if (info != null) {
                            // 手动检查不受3天限制，清除跳过记录让首页能弹窗
                            context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            checkMessage = "发现新版本 v${info.versionName}，请返回首页更新"
                        } else {
                            checkMessage = "已是最新版本"
                        }
                    }
                },
            )
            
            // ── 数据备份与恢复 ──
            var showBackupDialog by remember { mutableStateOf(false) }
            var backupMessage by remember { mutableStateOf("") }
            var showExportMainlineDialog by remember { mutableStateOf(false) }
            var exportIncludeMainline by remember { mutableStateOf(true) }
            
            // 导出：SAF让用户选择保存位置
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                uri?.let {
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // 关闭Room数据库连接，触发WAL自动checkpoint
                            viewModel.closeDatabaseForCheckpoint()
                            Thread.sleep(300)
                            
                            // 使用 FukeBackupExporter 打包 .fuke 文件
                            val fukeFile = FukeBackupExporter.exportFullBackup(context, !exportIncludeMainline)
                            
                            // 导出临时文件到SAF uri
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                fukeFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            fukeFile.delete()
                            AppLogger.i("导出：成功")
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                backupMessage = "导出成功"
                            }
                        } catch (e: Exception) {
                            AppLogger.e("导出失败: ${e.message}")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                backupMessage = "导出失败: ${e.message}"
                            }
                        }
                    }
                }
            }
            
            // 导入：SAF让用户选择文件
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    // 判断文件类型：.fuke 还是 .db
                    val fileName = it.lastPathSegment ?: ""
                    val isFukeFile = fileName.endsWith(".fuke", ignoreCase = true)
                    
                    if (isFukeFile) {
                        // .fuke 格式：使用 FukeBackupImporter
                        importState = ImportState.Validating
                        try {
                            AppLogger.d("导入：开始校验 .fuke 文件，uri=$it")
                            // 先将 .fuke 文件复制到临时目录
                            val tempFukeFile = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}.fuke")
                            context.contentResolver.openInputStream(it)?.use { input ->
                                tempFukeFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            } ?: throw IllegalStateException("无法读取导入文件")
                            
                            val validationResult = FukeBackupImporter.validateBackup(context, tempFukeFile)
                            tempFukeFile.delete()
                            
                            AppLogger.d("导入：校验结果 similarity=${validationResult.similarity}, matchedTables=${validationResult.matchedTables}, missingTables=${validationResult.missingTables}, fieldMatchRatio=${validationResult.fieldMatchRatio}, hasMainline=${validationResult.hasMainline}")
                            when {
                                validationResult.similarity < 0.5f -> {
                                    importState = ImportState.Mismatch
                                }
                                validationResult.similarity <= 0.8f -> {
                                    importState = ImportState.PartialMatch(
                                        similarity = validationResult.similarity,
                                        matchedTables = validationResult.matchedTables,
                                        missingTables = validationResult.missingTables,
                                        uri = it
                                    )
                                }
                                else -> {
                                    val currentHasMainline = uiState.lists.any { it.type == ListType.MAINLINE }
                                    if (validationResult.hasMainline && currentHasMainline) {
                                        importState = ImportState.MainlineConflict(uri = it)
                                    } else {
                                        performFukeMerge(context, it) { newState ->
                                            importState = newState
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("导入：校验异常 ${e.message}\n${e.stackTraceToString()}")
                            importState = ImportState.Error("校验失败: ${e.message}")
                        }
                    } else {
                        // .db 格式：保持原逻辑
                        importState = ImportState.Validating
                        try {
                            AppLogger.d("导入：开始校验，uri=$it")
                            val result = DatabaseImportMerger.validateSchema(context, it)
                            AppLogger.d("导入：校验结果 similarity=${result.similarity}, matchedTables=${result.matchedTables}, missingTables=${result.missingTables}, fieldMatchRatio=${result.fieldMatchRatio}, hasMainline=${result.hasMainline}")
                            when {
                                result.similarity < 0.5f -> {
                                    importState = ImportState.Mismatch
                                }
                                result.similarity <= 0.8f -> {
                                    importState = ImportState.PartialMatch(
                                        similarity = result.similarity,
                                        matchedTables = result.matchedTables,
                                        missingTables = result.missingTables,
                                        uri = it
                                    )
                                }
                                else -> {
                                    val currentHasMainline = uiState.lists.any { it.type == ListType.MAINLINE }
                                    if (result.hasMainline && currentHasMainline) {
                                        importState = ImportState.MainlineConflict(uri = it)
                                    } else {
                                        performMerge(context, it) { newState ->
                                            importState = newState
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("导入：校验异常 ${e.message}\n${e.stackTraceToString()}")
                            importState = ImportState.Error("校验失败: ${e.message}")
                        }
                    }
                }
            }
            
            // 导入状态显示文本
            val importSubtitle = when (val state = importState) {
                is ImportState.Idle -> ""
                is ImportState.Validating -> "校验中…"
                is ImportState.PartialMatch -> "等待确认导入"
                is ImportState.Merging -> "合并中…"
                is ImportState.Success -> {
                    val total = state.counts.values.sum()
                    "导入成功，共导入 $total 条数据"
                }
                is ImportState.Error -> state.message
                is ImportState.Mismatch -> "文件不匹配"
                is ImportState.MainlineConflict -> "人生主线冲突"
            }

            // 备份恢复的副标题：优先显示导入状态，其次显示导出状态
            val backupSubtitle = when {
                importSubtitle.isNotEmpty() -> importSubtitle
                backupMessage.isNotEmpty() -> backupMessage
                else -> "导出/导入 .fuke 文件"
            }

            SettingItem(
                icon = Icons.Filled.Backup,
                title = "数据备份与恢复",
                subtitle = backupSubtitle,
                onClick = {
                    showBackupDialog = true
                },
            )
            
            // 备份/恢复对话框
            if (showBackupDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text("数据备份与恢复") },
                    text = { Text("导出：将当前数据保存为.fuke文件（含图片）\n导入：从.fuke或.db文件合并数据（追加方式，不会覆盖现有数据）") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                // 检查是否有人生主线
                                val hasMainline = uiState.lists.any { it.type == ListType.MAINLINE }
                                if (hasMainline) {
                                    showExportMainlineDialog = true
                                } else {
                                    exportIncludeMainline = true
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("fuke_daily_backup_$dateStr.fuke")
                                }
                            }
                        ) {
                            Text("导出")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showBackupDialog = false
                                // 重置导入状态
                                importState = ImportState.Idle
                                importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
                            }
                        ) {
                            Text("导入")
                        }
                    },
                )
            }
            
            // ── 导出人生主线选择弹窗 ──
            if (showExportMainlineDialog) {
                AlertDialog(
                    onDismissRequest = { showExportMainlineDialog = false },
                    title = { Text("人生主线") },
                    text = { Text("检测到人生主线数据，是否包含在导出文件中？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExportMainlineDialog = false
                                exportIncludeMainline = true
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportLauncher.launch("fuke_daily_backup_$dateStr.fuke")
                            }
                        ) {
                            Text("包含")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showExportMainlineDialog = false
                                exportIncludeMainline = false
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportLauncher.launch("fuke_daily_backup_$dateStr.fuke")
                            }
                        ) {
                            Text("不包含")
                        }
                    },
                )
            }

            // ── 导入状态对话框 ──
            when (val state = importState) {
                is ImportState.Validating -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("导入数据") },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("正在校验数据库格式…")
                            }
                        },
                        confirmButton = {},
                    )
                }

                is ImportState.Mismatch -> {
                    AlertDialog(
                        onDismissRequest = { importState = ImportState.Idle },
                        title = { Text("导入失败") },
                        text = { Text("文件不匹配，无法导入。\n该数据库结构与当前应用不兼容。") },
                        confirmButton = {
                            TextButton(onClick = { importState = ImportState.Idle }) {
                                Text("确定")
                            }
                        },
                    )
                }

                is ImportState.PartialMatch -> {
                    val simPercent = (state.similarity * 100).toInt()
                    val missingInfo = if (state.missingTables.isNotEmpty()) {
                        "\n缺失的表：${state.missingTables.joinToString(", ")}"
                    } else ""
                    AlertDialog(
                        onDismissRequest = { importState = ImportState.Idle },
                        title = { Text("文件部分匹配") },
                        text = { Text("数据库匹配度：$simPercent%\n可能丢失部分数据。$missingInfo\n\n是否继续导入？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val fileName = state.uri.lastPathSegment ?: ""
                                    val isFukeFile = fileName.endsWith(".fuke", ignoreCase = true)
                                    if (isFukeFile) {
                                        performFukeMerge(context, state.uri) { newState ->
                                            importState = newState
                                        }
                                    } else {
                                        performMerge(context, state.uri) { newState ->
                                            importState = newState
                                        }
                                    }
                                }
                            ) {
                                Text("继续导入")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { importState = ImportState.Idle }) {
                                Text("取消")
                            }
                        },
                    )
                }

                is ImportState.Merging -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("导入数据") },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("正在合并数据…")
                            }
                        },
                        confirmButton = {},
                    )
                }

                is ImportState.Success -> {
                    val total = state.counts.values.sum()
                    val detail = state.counts.entries
                        .filter { entry -> entry.value > 0 }
                        .joinToString("\n") { entry -> "${entry.key}: ${entry.value} 条" }
                    AlertDialog(
                        onDismissRequest = { restartApp(context) },
                        title = { Text("导入成功") },
                        text = {
                            Text("共导入 $total 条数据\n\n$detail\n\n应用将重启以生效。")
                        },
                        confirmButton = {
                            TextButton(onClick = { restartApp(context) }) {
                                Text("重启应用")
                            }
                        },
                    )
                }

                is ImportState.Error -> {
                    AlertDialog(
                        onDismissRequest = { importState = ImportState.Idle },
                        title = { Text("导入失败") },
                        text = { Text(state.message) },
                        confirmButton = {
                            TextButton(onClick = { importState = ImportState.Idle }) {
                                Text("确定")
                            }
                        },
                    )
                }

                is ImportState.MainlineConflict -> {
                    AlertDialog(
                        onDismissRequest = { importState = ImportState.Idle },
                        title = { Text("人生主线冲突") },
                        text = { Text("导入文件包含人生主线数据，当前应用也已有人生主线。\n\n是否用导入的人生主线覆盖当前的？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    // 判断文件类型，使用对应的合并方法
                                    val fileName = state.uri.lastPathSegment ?: ""
                                    val isFukeFile = fileName.endsWith(".fuke", ignoreCase = true)
                                    if (isFukeFile) {
                                        performFukeMerge(context, state.uri, overwriteMainline = true) { newState ->
                                            importState = newState
                                        }
                                    } else {
                                        performMerge(context, state.uri, overwriteMainline = true) { newState ->
                                            importState = newState
                                        }
                                    }
                                }
                            ) {
                                Text("覆盖")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    val fileName = state.uri.lastPathSegment ?: ""
                                    val isFukeFile = fileName.endsWith(".fuke", ignoreCase = true)
                                    if (isFukeFile) {
                                        performFukeMerge(context, state.uri, overwriteMainline = false) { newState ->
                                            importState = newState
                                        }
                                    } else {
                                        performMerge(context, state.uri, overwriteMainline = false) { newState ->
                                            importState = newState
                                        }
                                    }
                                }
                            ) {
                                Text("跳过主线")
                            }
                        },
                    )
                }

                else -> { /* Idle, no dialog */ }
            }
        }
    }
}

/**
 * 执行合并操作（同步，因为用户偏好本地数据库查询不用协程）
 */
private fun performMerge(
    context: Context,
    uri: Uri,
    overwriteMainline: Boolean = true,
    onStateUpdate: (ImportState) -> Unit,
) {
    onStateUpdate(ImportState.Merging)
    try {
        AppLogger.d("导入：开始合并数据, overwriteMainline=$overwriteMainline")
        val result = DatabaseImportMerger.mergeData(context, uri, overwriteMainline)
        AppLogger.d("导入：合并结果 success=${result.success}, counts=${result.importedCounts}, error=${result.error}")
        if (result.success) {
            onStateUpdate(ImportState.Success(result.importedCounts))
        } else {
            onStateUpdate(ImportState.Error(result.error ?: "未知错误"))
        }
    } catch (e: Exception) {
        AppLogger.e("导入：合并异常 ${e.message}\n${e.stackTraceToString()}")
        onStateUpdate(ImportState.Error("合并失败: ${e.message}"))
    }
}

/**
 * 执行 .fuke 格式的合并操作
 */
private fun performFukeMerge(
    context: Context,
    uri: Uri,
    overwriteMainline: Boolean = true,
    onStateUpdate: (ImportState) -> Unit,
) {
    onStateUpdate(ImportState.Merging)
    try {
        AppLogger.d("导入：开始 .fuke 合并数据, overwriteMainline=$overwriteMainline")
        // 将 .fuke 文件复制到临时目录
        val tempFukeFile = File(context.cacheDir, "import_merge_temp_${System.currentTimeMillis()}.fuke")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFukeFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("无法读取导入文件")

        // 解压
        val extracted = FukeBackupImporter.extractBackup(context, tempFukeFile)
        tempFukeFile.delete()

        // 导入
        val result = FukeBackupImporter.importBackup(context, extracted, overwriteMainline)
        AppLogger.d("导入：.fuke 合并结果 success=${result.success}, counts=${result.importedCounts}, error=${result.error}")
        if (result.success) {
            onStateUpdate(ImportState.Success(result.importedCounts))
        } else {
            onStateUpdate(ImportState.Error(result.error ?: "未知错误"))
        }
    } catch (e: Exception) {
        AppLogger.e("导入：.fuke 合并异常 ${e.message}\n${e.stackTraceToString()}")
        onStateUpdate(ImportState.Error("合并失败: ${e.message}"))
    }
}

/**
 * 重启App
 */
private fun restartApp(context: Context) {
    val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    context.startActivity(restartIntent)
    android.os.Process.killProcess(android.os.Process.myPid())
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
