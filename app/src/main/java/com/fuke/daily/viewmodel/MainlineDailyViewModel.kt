package com.fuke.daily.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuke.daily.data.datastore.AppPrefs
import com.fuke.daily.data.model.MainlineBranch
import com.fuke.daily.data.model.MainlineItem
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.data.repository.MainlineRepo
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  人生主线每日触发页 UI 状态
// ═══════════════════════════════════════════════════

data class MainlineDailyUiState(
    val mainList: MainList? = null,
    val branches: List<MainlineBranch> = emptyList(),
    val items: Map<Long, List<MainlineItem>> = emptyMap(),
    val isLoading: Boolean = true,
    val isEveningSession: Boolean = false,   // true=晚间回顾模式，false=早间选路模式
    val sessionLabel: String = "",             // 当前时段标签
    val hasTriggeredToday: Boolean = false,    // 今天是否已经手动触发过
    val shouldAutoTrigger: Boolean = false,   // 是否应该自动触发（进入应用时检查）
)

// ═══════════════════════════════════════════════════
//  人生主线每日触发页 ViewModel
// ═══════════════════════════════════════════════════

@HiltViewModel
class MainlineDailyViewModel @Inject constructor(
    private val mainListRepo: MainListRepo,
    private val mainlineRepo: MainlineRepo,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainlineDailyUiState())
    val uiState: StateFlow<MainlineDailyUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        loadMainlineData()
    }

    // ── 自动加载主线数据（查type=MAINLINE的第一条）──

    private fun loadMainlineData() {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            // 获取配置
            var eveningHour = 21
            var lastMorningDate = ""
            var lastEveningDate = ""
            var autoTriggerDate = ""
            
            appPrefs.mainlineConfig.collect { config ->
                eveningHour = config.eveningHour
                lastMorningDate = config.lastMorningDate
                lastEveningDate = config.lastEveningDate
                autoTriggerDate = config.autoTriggerDate
                
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isEvening = currentHour >= eveningHour
                val isMorning = currentHour < eveningHour
                
                // 获取今天的日期
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                
                // 判断今天是否已经手动触发过
                val hasTriggeredToday = when {
                    isEvening -> lastEveningDate == today
                    isMorning -> lastMorningDate == today
                    else -> false
                }
                
                // 判断是否应该自动触发（今天还没有自动触发过）
                val shouldAutoTrigger = autoTriggerDate != today && !hasTriggeredToday
                
                // 时段标签
                val sessionLabel = when {
                    isEvening -> "晚间回顾"
                    isMorning -> "早间选路"
                    else -> ""
                }
                
                AppLogger.d("MainlineDaily: 当前时段=$sessionLabel, 今天=$today, " +
                    "lastMorningDate=$lastMorningDate, lastEveningDate=$lastEveningDate, " +
                    "autoTriggerDate=$autoTriggerDate, hasTriggeredToday=$hasTriggeredToday, " +
                    "shouldAutoTrigger=$shouldAutoTrigger")
                
                _uiState.update { it.copy(
                    isEveningSession = isEvening,
                    sessionLabel = sessionLabel,
                    hasTriggeredToday = hasTriggeredToday,
                    shouldAutoTrigger = shouldAutoTrigger,
                ) }
            }
        }

        viewModelScope.launch {
            val mainlineList = mainListRepo.getMainlineList()
            if (mainlineList != null) {
                _uiState.update { it.copy(
                    mainList = mainlineList,
                    isLoading = false,
                ) }
                loadBranches(mainlineList.id)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ── 记录自动触发（进入应用时自动触发调用）──
    
    fun recordAutoTrigger() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            
            // 获取当前配置
            appPrefs.mainlineConfig.collect { config ->
                val newConfig = config.copy(autoTriggerDate = today)
                appPrefs.setMainlineConfig(newConfig)
                
                AppLogger.d("MainlineDaily: 记录自动触发，日期=$today")
                
                // 更新状态为已自动触发
                _uiState.update { it.copy(shouldAutoTrigger = false) }
            }
        }
    }

    // ── 记录选择完成（手动触发时调用）──
    
    fun recordSelectionComplete() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
            val isEvening = _uiState.value.isEveningSession
            
            // 获取当前配置
            appPrefs.mainlineConfig.collect { config ->
                val newConfig = if (isEvening) {
                    config.copy(lastEveningDate = today)
                } else {
                    config.copy(lastMorningDate = today)
                }
                appPrefs.setMainlineConfig(newConfig)
                
                AppLogger.d("MainlineDaily: 记录选择完成，时段=${if (isEvening) "晚间" else "早间"}，日期=$today")
                
                // 更新状态为已触发
                _uiState.update { it.copy(hasTriggeredToday = true) }
            }
        }
    }

    private fun loadBranches(listId: Long) {
        viewModelScope.launch {
            val branches = mainListRepo.getBranches(listId)
            _uiState.update { it.copy(branches = branches) }
            branches.forEach { branch ->
                loadBranchItems(branch.id)
            }
        }
    }

    private fun loadBranchItems(branchId: Long) {
        viewModelScope.launch {
            val items = mainListRepo.getBranchItems(branchId)
            _uiState.update { state ->
                state.copy(items = state.items + (branchId to items))
            }
        }
    }

    // ── 链路选择记录 ──
    // path JSON格式：[{id, name}] 与 MainlineViewModel 保持一致

    fun selectLink(
        currentItem: MainlineItem,
        currentBranch: MainlineBranch,
        idealBranch: MainlineBranch?,
        mainList: MainList,
        date: String,
        timestamp: Long,
    ) {
        viewModelScope.launch {
            AppLogger.i("MainlineDailyViewModel: selectLink 开始保存数据")
            
            val gson = com.google.gson.Gson()
            val pathNodes = mutableListOf<Map<String, Any>>()

            // 现状
            pathNodes.add(mapOf(
                "id" to currentItem.id,
                "name" to currentItem.name,
            ))

            // 现状关联路标
            pathNodes.add(mapOf(
                "id" to currentBranch.id,
                "name" to currentBranch.name,
            ))

            // 理想路标（如果与现状路标不同）
            if (idealBranch != null && idealBranch.id != currentBranch.id) {
                pathNodes.add(mapOf(
                    "id" to idealBranch.id,
                    "name" to idealBranch.name,
                ))
            }

            // 人生主线
            pathNodes.add(mapOf(
                "id" to mainList.id,
                "name" to mainList.name,
            ))

            val pathJson = gson.toJson(pathNodes)
            AppLogger.i("MainlineDailyViewModel: selectLink pathJson=$pathJson")
            
            val record = com.fuke.daily.data.model.LinkRecord(
                path = pathJson,
                date = date,
                timestamp = timestamp,
            )
            AppLogger.i("MainlineDailyViewModel: selectLink 准备插入数据库 record=$record")
            
            mainlineRepo.insertRecord(record)
            AppLogger.i("MainlineDailyViewModel: selectLink 数据已保存到数据库")
            
            // 记录选择完成
            recordSelectionComplete()
        }
    }
}
