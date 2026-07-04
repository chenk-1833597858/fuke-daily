package com.fuke.daily.ui.imagelist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fuke.daily.ui.theme.FukeTheme
import com.fuke.daily.util.AppLogger
import com.fuke.daily.viewmodel.ConfigViewModel
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import org.json.JSONArray
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════
//  图片列表页
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageListScreen(
    subListId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val extended = FukeTheme.extended
    val context = LocalContext.current
    
    // 从 ViewModel 获取子列表信息
    val uiState by viewModel.uiState.collectAsState()
    val subList = uiState.subLists.find { it.id == subListId }
    val subListName = subList?.name ?: ""
    
    // 加载数据
    LaunchedEffect(subListId) {
        viewModel.loadSubListById(subListId)
    }
    
    // 解析图片列表 - 使用 uiState.subLists 作为 key，确保数据更新时重新计算
    val imageUris = remember(uiState.subLists, subListId) {
        val currentSubList = uiState.subLists.find { it.id == subListId }
        AppLogger.d("ImageListScreen: subListId=$subListId, currentSubList=${currentSubList?.name}, imageUris=${currentSubList?.imageUris}")
        try {
            val array = JSONArray(currentSubList?.imageUris ?: "[]")
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            AppLogger.d("ImageListScreen: parsed ${list.size} images")
            list
        } catch (e: Exception) {
            AppLogger.e("ImageListScreen: parse imageUris failed", e)
            emptyList<String>()
        }
    }
    
    // 拖动状态
    var draggedIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    
    // 裁剪状态
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var cropRatioX by remember { mutableStateOf(1) }
    var cropRatioY by remember { mutableStateOf(1) }
    var cropIsFree by remember { mutableStateOf(false) }
    
    // CanHub 裁剪结果接收器
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            AppLogger.d("Crop result: uri=$uri")
            uri?.let { cropUri ->
                try {
                    // 复制裁剪后的图片到内部存储
                    context.contentResolver.openInputStream(cropUri)?.use { input ->
                        val destFile = java.io.File(context.filesDir, "images/${System.currentTimeMillis()}.jpg")
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        AppLogger.d("CropImage: saved to ${destFile.absolutePath}")
                        viewModel.updateSubListImage(subListId, destFile.absolutePath)
                    }
                } catch (e: Exception) {
                    AppLogger.e("CropImage: save failed", e)
                }
            }
        } else {
            AppLogger.w("CropImage: crop failed or cancelled")
        }
        pendingCropUri = null
    }
    
    // 图片选择器（选完后显示比例选择对话框）
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        AppLogger.d("Picker result: uri=$uri")
        uri?.let {
            pendingCropUri = it
            showAspectRatioDialog = true
        } ?: AppLogger.w("Picker returned null uri")
    }
    
    // 比例选择对话框
    if (showAspectRatioDialog && pendingCropUri != null) {
        AspectRatioDialog(
            onDismiss = { 
                showAspectRatioDialog = false
                pendingCropUri = null
            },
            onConfirm = { ratioX, ratioY, isFree ->
                showAspectRatioDialog = false
                cropRatioX = ratioX
                cropRatioY = ratioY
                cropIsFree = isFree
                
                // 启动 CanHub 裁剪
                val options = CropImageOptions().apply {
                    if (isFree) {
                        // 自由模式：不限制比例
                        fixAspectRatio = false
                    } else {
                        // 固定比例
                        aspectRatioX = ratioX
                        aspectRatioY = ratioY
                        fixAspectRatio = true
                    }
                    // 裁剪框样式
                    showCropOverlay = true
                    showProgressBar = true
                    // 输出质量
                    outputCompressQuality = 90
                }
                
                cropLauncher.launch(
                    CropImageContractOptions(pendingCropUri!!, options)
                )
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "$subListName - 图片管理", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        bottomBar = {
            // 底部添加按钮
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(12.dp),
                color = extended.card,
                border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "+", fontSize = 24.sp, color = extended.muted.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "添加图片", fontSize = 16.sp, color = extended.muted.copy(alpha = 0.7f))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // 轮播速度设置
            val carouselInterval = subList?.carouselInterval ?: 0L
            val globalInterval by viewModel.globalCarouselInterval.collectAsState()
            val effectiveInterval = if (carouselInterval > 0) carouselInterval else globalInterval
            var showCarouselDialog by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { showCarouselDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "轮播速度",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = extended.text,
                    )
                    Text(
                        text = if (carouselInterval > 0) "${effectiveInterval}毫秒（局部）" else "${effectiveInterval}毫秒（全局）",
                        fontSize = 13.sp,
                        color = extended.muted,
                    )
                }
                Text(
                    text = "设置",
                    fontSize = 14.sp,
                    color = extended.accent,
                )
            }
            
            if (showCarouselDialog) {
                var inputValue by remember { mutableStateOf(if (carouselInterval > 0) carouselInterval.toString() else "") }
                
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
                                text = "输入范围：500 ~ 10000毫秒，为空时使用全局设置",
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.Gray,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val interval = inputValue.toLongOrNull() ?: 0L
                                val clamped = if (interval > 0) interval.coerceIn(500L, 10000L) else 0L
                                viewModel.updateSubListCarouselInterval(subListId, clamped)
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
            
            // 图片列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = imageUris,
                    key = { index, _ -> index },
                ) { index, imageUri ->
                    ImageListItem(
                        imageUri = imageUri,
                        index = index,
                        totalCount = imageUris.size,
                        isDragging = draggedIndex == index,
                        onDelete = {
                            viewModel.deleteSubListImage(subListId, imageUri)
                        },
                        onDragStart = {
                            draggedIndex = index
                        },
                        onDragEnd = {
                            if (draggedIndex != -1 && targetIndex != -1 && draggedIndex != targetIndex) {
                                viewModel.reorderSubListImages(subListId, draggedIndex, targetIndex)
                            }
                            draggedIndex = -1
                            targetIndex = -1
                        },
                        onDragMove = { offset ->
                            // 计算目标位置
                            val itemHeight = 100f // 估算每项高度
                            val newTarget = (offset.y / itemHeight).roundToInt()
                            targetIndex = newTarget.coerceIn(0, imageUris.size - 1)
                        },
                    )
                }
                
                // 底部留白，避免被底部栏遮挡
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  图片列表项
// ═══════════════════════════════════════════════════

@Composable
private fun ImageListItem(
    imageUri: String,
    index: Int,
    totalCount: Int,
    isDragging: Boolean,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragMove: (Offset) -> Unit,
) {
    val extended = FukeTheme.extended
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = if (isDragging) 0.5f else 1f
            },
        shape = RoundedCornerShape(12.dp),
        color = extended.card,
        border = BorderStroke(1.dp, extended.border.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 拖动把手（左侧）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragMove(change.position)
                            }
                        )
                    }
            ) {
                // 拖动把手图标（三条横线）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(extended.muted.copy(alpha = 0.3f))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 图片缩略图
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(8.dp),
                color = extended.contentBg,
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "图片 ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 图片信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "图片 ${index + 1}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = extended.text,
                )
                Text(
                    text = imageUri.substringAfterLast("/", "..."),
                    fontSize = 12.sp,
                    color = extended.muted.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            
            // 删除按钮
            Surface(
                modifier = Modifier.size(32.dp).clickable(onClick = onDelete),
                shape = RoundedCornerShape(16.dp),
                color = extended.light,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "×", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = extended.muted)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  裁剪比例选择对话框
// ═══════════════════════════════════════════════════

@Composable
private fun AspectRatioDialog(
    onDismiss: () -> Unit,
    onConfirm: (ratioX: Int, ratioY: Int, isFree: Boolean) -> Unit,
) {
    val extended = FukeTheme.extended
    
    // 比例选项
    val ratios = listOf(
        Triple("自由裁剪", 0, 0),
        Triple("1:1", 1, 1),
        Triple("4:3", 4, 3),
        Triple("16:9", 16, 9),
        Triple("9:16", 9, 16),
    )
    
    var selectedIndex by remember { mutableStateOf(0) }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = extended.card,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "选择裁剪比例",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extended.text,
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 比例选项
                ratios.forEachIndexed { index, (label, x, y) ->
                    val isSelected = selectedIndex == index
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) extended.primary.copy(alpha = 0.15f) else extended.contentBg.copy(alpha = 0.3f),
                        border = if (isSelected) BorderStroke(2.dp, extended.primary) else BorderStroke(1.dp, extended.border.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (isSelected) extended.primary else extended.text,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                    
                    if (index < ratios.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 取消按钮
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(10.dp),
                        color = extended.contentBg.copy(alpha = 0.3f),
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "取消",
                                fontSize = 15.sp,
                                color = extended.muted,
                            )
                        }
                    }
                    
                    // 确认按钮
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val (_, x, y) = ratios[selectedIndex]
                                onConfirm(x, y, selectedIndex == 0)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = extended.primary,
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "确认",
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
