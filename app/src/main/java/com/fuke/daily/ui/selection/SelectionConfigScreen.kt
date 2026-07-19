package com.fuke.daily.ui.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.ui.components.PageHeader
import com.fuke.daily.ui.components.SlotDialog
import com.fuke.daily.ui.components.SubListCard
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.viewmodel.ConfigViewModel

// ═══════════════════════════════════════════════════
//  选择列表配置页
// ═══════════════════════════════════════════════════

@Composable
fun SelectionConfigScreen(
    listId: Long,
    onBack: () -> Unit,
    onNavigateToImageList: (Long, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val extended = FukeTheme.extended
    val uiState by viewModel.uiState.collectAsState()

    // 初始化
    viewModel.setListId(listId)

    val subLists = uiState.subLists
    val subtitle = "${subLists.size}个子列表"

    // 引用槽弹窗状态
    var showSlotDialog by rememberSaveable { mutableStateOf(false) }
    var slotDialogSubListId by rememberSaveable { mutableStateOf(0L) }
    var slotDialogLineIndex by rememberSaveable { mutableIntStateOf(-1) }
    var slotDialogCurrentSlot by rememberSaveable { mutableIntStateOf(0) }

    // 选项按钮引用槽弹窗状态
    var showOptionSlotDialog by rememberSaveable { mutableStateOf(false) }
    var optionSlotDialogButton by rememberSaveable { mutableStateOf<OptionButton?>(null) }

    // 图片选择器
    var imageSelectSubListId by rememberSaveable { mutableStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 将图片复制到内部存储，避免 Content URI 过期
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val fileName = "Fuker_${java.text.SimpleDateFormat("yyyyMMddHHmmssSSS", java.util.Locale.US).format(System.currentTimeMillis())}_${(1000..9999).random()}.jpg"
                val destFile = java.io.File(context.filesDir, "images/$fileName")
                destFile.parentFile?.mkdirs()
                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.updateSubListImage(imageSelectSubListId, destFile.absolutePath)
            } catch (e: Exception) {
                // 复制失败，回退到直接使用 URI
                viewModel.updateSubListImage(imageSelectSubListId, selectedUri.toString())
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(extended.bg),
        ) {
            // ── 顶栏 ──
            PageHeader(
                title = "选择列表",
                onBack = onBack,
                subtitle = subtitle,
            )

            // ── 子列表卡片列表 ──
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = subLists,
                    key = { _, sub -> sub.id },
                ) { index, subList ->
                    val config = uiState.contentConfigs[subList.id]
                    val buttons = uiState.optionButtons[subList.id] ?: emptyList()

                    SubListCard(
                        subList = subList,
                        index = index,
                        contentConfig = config,
                        optionButtons = buttons,
                        showFixedSlot = false,
                        onNameChange = { name ->
                            viewModel.updateSubList(subList.copy(name = name))
                        },
                        onDelete = {
                            viewModel.deleteSubList(subList)
                        },
                        onContentConfigChange = { newConfig ->
                            viewModel.saveContentConfig(newConfig)
                        },
                        onRefTagClick = { lineIndex ->
                            val currentStorage = when (lineIndex) {
                                0 -> config?.button1Storage ?: 0
                                1 -> config?.button2Storage ?: 0
                                2 -> config?.button3Storage ?: 0
                                else -> 0
                            }
                            slotDialogSubListId = subList.id
                            slotDialogLineIndex = lineIndex
                            slotDialogCurrentSlot = currentStorage
                            showSlotDialog = true
                        },
                        onAddOptionButton = {
                            viewModel.addOptionButton(subList.id)
                        },
                        onUpdateOptionButton = { button ->
                            viewModel.updateOptionButton(button)
                        },
                        onDeleteOptionButton = { button ->
                            viewModel.deleteOptionButton(button)
                        },
                        onOptionRefTagClick = { button ->
                            optionSlotDialogButton = button
                            showOptionSlotDialog = true
                        },
                        onImageClick = {
                            onNavigateToImageList(subList.id, subList.name, subList.imageUris)
                        },
                    )
                }

                // 底部"+ 添加子列表"虚线按钮
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { viewModel.addSubList() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+ 添加子列表",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = extended.muted.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // 引用槽弹窗（内容行）
        if (showSlotDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(extended.overlay),
                contentAlignment = Alignment.Center,
            ) {
                SlotDialog(
                    currentSlot = slotDialogCurrentSlot,
                    onSlotSelect = { slot ->
                        viewModel.assignSlot(slotDialogSubListId, slotDialogLineIndex, slot)
                        showSlotDialog = false
                    },
                    onDismiss = { showSlotDialog = false },
                )
            }
        }

        // 引用槽弹窗（选项按钮）
        if (showOptionSlotDialog && optionSlotDialogButton != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(extended.overlay),
                contentAlignment = Alignment.Center,
            ) {
                SlotDialog(
                    currentSlot = optionSlotDialogButton!!.storageSlot,
                    onSlotSelect = { slot ->
                        viewModel.assignOptionSlot(optionSlotDialogButton!!, slot)
                        showOptionSlotDialog = false
                    },
                    onDismiss = { showOptionSlotDialog = false },
                )
            }
        }
    }
}
