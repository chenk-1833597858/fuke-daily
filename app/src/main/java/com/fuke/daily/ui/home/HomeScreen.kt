package com.fuke.daily.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.ListType
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.model.AppUpdateInfo
import com.fuke.daily.ui.components.*
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppUpdater
import com.fuke.daily.viewmodel.MainViewModel

// ═══════════════════════════════════════════════════
//  主界面
// ═══════════════════════════════════════════════════

@Composable
fun HomeScreen(
    onNavigateToConfig: (String) -> Unit,
    onNavigateToTimer: () -> Unit,
    onNavigateToMainline: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMainlineDetail: (Long) -> Unit,
    onNavigateToRichText: (Long) -> Unit,
    onNavigateToQuizConfig: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredLists = viewModel.getFilteredLists()
    val extended = FukeTheme.extended
    val context = LocalContext.current

    // 更新检查状态
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var checkMessage by remember { mutableStateOf("") }

    fun doCheckUpdate() {
        if (isChecking) return
        isChecking = true
        checkMessage = ""
        kotlinx.coroutines.MainScope().launch {
            val info = AppUpdater.checkUpdate(context)
            isChecking = false
            if (info != null) {
                updateInfo = info
            } else {
                checkMessage = "已是最新版本"
            }
        }
    }

    fun doDownloadAndInstall() {
        if (isDownloading) return
        val info = updateInfo ?: return
        isDownloading = true
        downloadProgress = "下载中…"
        kotlinx.coroutines.MainScope().launch {
            val apkFile = AppUpdater.downloadApk(context, info)
            isDownloading = false
            if (apkFile != null) {
                downloadProgress = "下载完成，正在安装…"
                AppUpdater.installApk(context, apkFile)
            } else {
                downloadProgress = "下载失败"
            }
        }
    }

    // 筛选标签的index映射: 0=全部, 1=选择, 2=随机, 3=答题, 4=主线
    val filterToIndex = mapOf("ALL" to 0, "SELECTION" to 1, "RANDOM" to 2, "QUIZ" to 3, "MAINLINE" to 4)
    val indexToFilter = filterToIndex.entries.associate { it.value to it.key }
    val currentFilterIndex = filterToIndex[uiState.currentFilter] ?: 0

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FukeTheme.extended.bg),
        ) {
            // ── 顶栏 ──
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "浮刻日常",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = FukeTheme.extended.text,
                )

                // 设置按钮
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onNavigateToSettings() },
                    shape = RoundedCornerShape(16.dp),
                    color = extended.light,
                    border = BorderStroke(1.dp, extended.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "⚙️",
                            fontSize = 12.sp,
                            color = extended.muted,
                        )
                    }
                }
            }

            // ── 筛选标签 ──
            FilterTabs(
                selectedIndex = currentFilterIndex,
                onTabSelect = { index ->
                    viewModel.setFilter(indexToFilter[index] ?: "ALL")
                },
            )

            // ── 列表 ──
            if (filteredLists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无项目，点击 + 创建",
                        color = FukeTheme.extended.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filteredLists, key = { it.id }) { item ->
                        ListCard(
                            name = item.name,
                            type = item.type,
                            isEnabled = item.isEnabled,
                            onClick = {
                                handleItemClick(
                                    item = item,
                                    onNavigateToMainlineDetail = onNavigateToMainlineDetail,
                                    onNavigateToRichText = onNavigateToRichText,
                                    onNavigateToQuizConfig = onNavigateToQuizConfig,
                                    onNavigateToConfig = onNavigateToConfig,
                                )
                            },
                            onToggle = { _ -> viewModel.toggleList(item.id) },
                            onLongClick = {
                                viewModel.showItemActionDialog(item)
                            },
                        )
                    }

                    // 底部留白给FAB
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // ── 底部导航 ──
            BottomNav(
                selectedIndex = uiState.currentBottomTab,
                onTabSelect = { index ->
                    when (index) {
                        0 -> viewModel.setBottomTab(0) // 项目页，保持在首页
                        1 -> onNavigateToTimer() // 定时页，不修改tab状态
                        2 -> onNavigateToLogs() // 记忆页，不修改tab状态
                    }
                },
            )
        }

        // ── FAB ──
        FAB(
            onClick = { viewModel.showCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 80.dp),
        )
    }

    // ── 创建对话框 ──
    if (uiState.showCreateDialog) {
        CreateDialog(
            hasMainline = uiState.hasMainline,
            onDismiss = { viewModel.dismissCreateDialog() },
            onConfirm = { name, type ->
                viewModel.createList(name, type)
            },
        )
    }

    // ── 长按操作弹窗 ──
    val actionItem = uiState.actionListItem
    if (uiState.showItemActionDialog && actionItem != null) {
        ItemActionDialog(
            item = actionItem,
            onPin = {
                viewModel.pinList(actionItem.id)
                viewModel.dismissItemActionDialog()
            },
            onDelete = {
                viewModel.deleteList(actionItem.id)
                viewModel.dismissItemActionDialog()
            },
            onDismiss = { viewModel.dismissItemActionDialog() },
        )
    }

    // ── 更新弹窗 ──
    if (updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onConfirm = { doDownloadAndInstall() },
            onDismiss = { updateInfo = null },
        )
    }

    // ── 已是最新提示 ──
    if (checkMessage.isNotEmpty()) {
        LaunchedEffect(checkMessage) {
            kotlinx.coroutines.delay(2000)
            checkMessage = ""
        }
        SnackbarHost(
            hostState = remember { SnackbarHostState() },
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = extended.card,
                border = BorderStroke(1.dp, extended.border),
            ) {
                Text(
                    text = checkMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = extended.text,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  长按操作弹窗
// ═══════════════════════════════════════════════════

@Composable
private fun ItemActionDialog(
    item: MainList,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extended = FukeTheme.extended
    val pinLabel = if (item.pinned) "取消置顶" else "置顶"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.name,
                fontWeight = FontWeight.SemiBold,
                color = extended.text,
            )
        },
        text = {
            Column {
                // 置顶按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPin),
                    shape = RoundedCornerShape(8.dp),
                    color = extended.light,
                ) {
                    Text(
                        text = if (item.pinned) "📌 $pinLabel" else "📌 $pinLabel",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        fontSize = 15.sp,
                        color = extended.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 删除按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete),
                    shape = RoundedCornerShape(8.dp),
                    color = extended.light,
                ) {
                    Text(
                        text = "🗑️ 删除",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        fontSize = 15.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFE53935),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = extended.muted)
            }
        },
        containerColor = extended.card,
        shape = RoundedCornerShape(16.dp),
    )
}

// ═══════════════════════════════════════════════════
//  点击列表项的路由逻辑
// ═══════════════════════════════════════════════════

private fun handleItemClick(
    item: MainList,
    onNavigateToMainlineDetail: (Long) -> Unit,
    onNavigateToRichText: (Long) -> Unit,
    onNavigateToQuizConfig: (Long) -> Unit,
    onNavigateToConfig: (String) -> Unit,
) {
    // 主线开关关了，点击无反应
    if (item.type == ListType.MAINLINE && !item.isEnabled) return

    when (item.type) {
        ListType.MAINLINE -> onNavigateToMainlineDetail(item.id)
        ListType.RANDOM -> onNavigateToRichText(item.id)
        ListType.QUIZ -> onNavigateToQuizConfig(item.id)
        ListType.SELECTION -> onNavigateToConfig("selection/${item.id}")
    }
}

// ═══════════════════════════════════════════════════
//  更新弹窗
// ═══════════════════════════════════════════════════

@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    isDownloading: Boolean,
    downloadProgress: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extended = FukeTheme.extended

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Text(
                text = "发现新版本 v${info.versionName}",
                fontWeight = FontWeight.SemiBold,
                color = extended.text,
            )
        },
        text = {
            Column {
                Text(
                    text = info.updateLog.replace("\\n", "\n"),
                    color = extended.muted,
                    fontSize = 14.sp,
                )
                if (downloadProgress.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadProgress,
                        color = extended.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDownloading,
            ) {
                Text(
                    if (isDownloading) "下载中…" else "立即更新",
                    color = if (isDownloading) extended.muted else extended.primary,
                )
            }
        },
        dismissButton = {
            if (!info.forceUpdate && !isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("稍后再说", color = extended.muted)
                }
            }
        },
        containerColor = extended.card,
        shape = RoundedCornerShape(16.dp),
    )
}
