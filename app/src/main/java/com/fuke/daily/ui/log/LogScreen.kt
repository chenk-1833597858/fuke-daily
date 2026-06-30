package com.fuke.daily.ui.log

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import com.fuke.daily.util.AppLogger
import com.fuke.daily.ui.theme.FukeTheme
import kotlinx.coroutines.delay

/**
 * 日志页面 — 显示应用内存日志
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
) {
    val extended = FukeTheme.extended
    var logs by remember { mutableStateOf<List<AppLogger.LogEntry>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            logs = AppLogger.getLogs()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志", color = extended.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = extended.text,
                        )
                    }
                },
                actions = {
                    val clipboardManager = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") { "${it.time} ${it.level}/${it.tag}: ${it.message}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                            copied = true
                        }
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = if (copied) "已复制" else "复制全部",
                            tint = if (copied) androidx.compose.ui.graphics.Color(0xFF66BB6A) else extended.muted,
                        )
                    }
                    
                    IconButton(onClick = { AppLogger.clearLogs() }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "清空",
                            tint = extended.muted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = extended.card,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(extended.bg)
                .padding(padding),
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无日志",
                        color = extended.muted,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true,
                ) {
                    items(logs, key = { it.hashCode() }) { entry ->
                        LogItem(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(entry: AppLogger.LogEntry) {
    val extended = FukeTheme.extended
    val levelColor = when (entry.level) {
        "E" -> androidx.compose.ui.graphics.Color(0xFFE53935)
        "W" -> androidx.compose.ui.graphics.Color(0xFFFFA726)
        "I" -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
        "D" -> androidx.compose.ui.graphics.Color(0xFF42A5F5)
        else -> extended.muted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(extended.card, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "[${entry.level}]",
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = entry.time,
                color = extended.muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = entry.tag,
            color = extended.muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = entry.message,
            color = extended.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        )
    }
}
