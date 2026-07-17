package com.fuke.daily.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

// ═══════════════════════════════════════════════════
//  API 设置界面
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApiSettingsScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val apiConfigs by viewModel.apiConfigs.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<ApiConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "配置你的 AI API 密钥，支持所有 OpenAI 兼容接口",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 已有配置列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apiConfigs) { config ->
                    ApiConfigCard(
                        config = config,
                        onActivate = { viewModel.activateApiConfig(config.id) },
                        onEdit = { editingConfig = config },
                        onDelete = { viewModel.deleteApiConfig(config) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 添加按钮
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+ 添加 API 配置")
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog || editingConfig != null) {
        ApiConfigEditDialog(
            initialConfig = editingConfig,
            onDismiss = {
                showAddDialog = false
                editingConfig = null
            },
            onSave = { config ->
                viewModel.saveApiConfig(config)
                showAddDialog = false
                editingConfig = null
            },
        )
    }
}

// ═══════════════════════════════════════════════════
//  API 配置卡片
// ═══════════════════════════════════════════════════

@Composable
fun ApiConfigCard(
    config: ApiConfig,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(config.name, style = MaterialTheme.typography.titleSmall)
                    if (config.isActive) {
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("✓ 已激活", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${config.model} · ${config.baseUrl.substringAfter("://").substringBefore("/")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!config.isActive) {
                FilledTonalButton(onClick = onActivate) {
                    Text("激活")
                }
            }
            IconButton(onClick = onEdit) {
                Text("✏️", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  API 配置编辑对话框
// ═══════════════════════════════════════════════════

@Composable
fun ApiConfigEditDialog(
    initialConfig: ApiConfig? = null,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: "https://api.deepseek.com/v1") }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: "deepseek-chat") }
    var maxTokens by remember { mutableStateOf(initialConfig?.maxTokens ?: 4096) }
    var temperature by remember { mutableStateOf(initialConfig?.temperature ?: 0.7f) }
    var systemPrompt by remember { mutableStateOf(initialConfig?.systemPrompt ?: "") }
    var setAsActive by remember { mutableStateOf(initialConfig?.isActive ?: true) }

    // 预设快捷选择
    var showPresets by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) "添加 API 配置" else "编辑配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 快捷预设
                if (!showPresets) {
                    TextButton(onClick = { showPresets = true }) {
                        Text("📋 快捷预设")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            baseUrl = "https://api.deepseek.com/v1"
                            model = "deepseek-chat"
                            name = "DeepSeek"
                            showPresets = false
                        }) { Text("DeepSeek") }
                        TextButton(onClick = {
                            baseUrl = "https://api.openai.com/v1"
                            model = "gpt-4o-mini"
                            name = "OpenAI"
                            showPresets = false
                        }) { Text("OpenAI") }
                        TextButton(onClick = {
                            baseUrl = "https://openrouter.ai/api/v1"
                            model = "deepseek/deepseek-chat"
                            name = "OpenRouter"
                            showPresets = false
                        }) { Text("OpenRouter") }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("温度: ${String.format("%.1f", temperature)}")
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = if (maxTokens == 0) "" else maxTokens.toString(),
                    onValueChange = { maxTokens = it.toIntOrNull() ?: 0 },
                    label = { Text("最大Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为激活")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = setAsActive, onCheckedChange = { setAsActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ApiConfig(
                            id = initialConfig?.id ?: 0,
                            name = name.ifBlank { "未命名" },
                            baseUrl = baseUrl.ifBlank { "https://api.deepseek.com/v1" },
                            apiKey = apiKey,
                            model = model.ifBlank { "deepseek-chat" },
                            isActive = setAsActive,
                            maxTokens = maxTokens.coerceAtLeast(1),
                            temperature = temperature,
                            systemPrompt = systemPrompt,
                            createdAt = initialConfig?.createdAt ?: System.currentTimeMillis(),
                        )
                    )
                },
                enabled = apiKey.isNotBlank() && baseUrl.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
