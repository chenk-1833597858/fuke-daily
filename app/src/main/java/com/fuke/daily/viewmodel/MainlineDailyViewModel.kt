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
import kotlinx.coroutines.flow.first
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
            appPrefs.mainlineConfig.collect { config ->
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val morningStartHour = config.morningHour  // 早间开始时间（默认6点）
                val eveningStartHour = config.eveningHour    // 晚间开始时间（默认21点）
                
                // 时段判断逻辑：
                // 早间时段：从早间开始时间 到 晚间开始时间（如 6:00 ~ 21:00）
                // 晚间时段：从晚间开始时间 到 次日早间开始时间（如 21:00 ~ 次日6:00）
                // 如果没有设置早间开始时间（morningStartHour=0），只设置了晚间开始时间：默认属于早间时段
                val (isEvening, isMorning) = if (morningStartHour == 0) {
                    // 未设置早间开始时间，默认属于早间时段
                    false to true
                } else {
                    // 已设置早间开始时间
                    val evening = currentHour >= eveningStartHour || currentHour < morningStartHour
                    val morning = currentHour >= morningStartHour && currentHour < eveningStartHour
                    evening to morning
                }
                
                // 获取"今天"的日期（以早间开始时间作为一天的分界点）
                val calendar = Calendar.getInstance()
                val today = if (currentHour < morningStartHour) {
                    // 凌晨未到早间时间，算前一天
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                }

                // 判断今天是否已经手动触发过
                // 晚间：lastEveningDate 是今天或更晚（跨天情况）才算已触发；否则算过期，需要重新触发
                val hasTriggeredToday = when {
                    isEvening -> {
                        // 晚间时段：检查 lastEveningDate 是否是今天或之后的日期
                        // 如果 lastEveningDate < today，说明是以前的标记，已过期
                        config.lastEveningDate >= today
                    }
                    isMorning -> config.lastMorningDate == today
                    else -> false
                }

                // 判断今天是否已经自动触发过（按时段分别检查）
                val hasAutoTriggeredToday = when {
                    isEvening -> config.autoTriggerEveningDate == today
                    isMorning -> config.autoTriggerMorningDate == today
                    else -> false
                }

                // 判断是否应该自动触发（今天该时段还没有自动触发过，也没手动触发过）
                val shouldAutoTrigger = !hasAutoTriggeredToday && !hasTriggeredToday
                
                // 时段标签
                val sessionLabel = when {
                    isEvening -> "晚间回顾"
                    isMorning -> "早间选路"
                    else -> ""
                }
                
                AppLogger.d("MainlineDaily: 当前时段=$sessionLabel, 今天=$today, " +
                    "lastMorningDate=${config.lastMorningDate}, lastEveningDate=${config.lastEveningDate}, " +
                    "autoTriggerMorningDate=${config.autoTriggerMorningDate}, autoTriggerEveningDate=${config.autoTriggerEveningDate}, " +
                    "hasTriggeredToday=$hasTriggeredToday, shouldAutoTrigger=$shouldAutoTrigger")
                
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
            val isEvening = _uiState.value.isEveningSession
            
            // 获取当前配置（只取一次）
            val config = appPrefs.mainlineConfig.first()
            val newConfig = if (isEvening) {
                config.copy(autoTriggerEveningDate = today)
            } else {
                config.copy(autoTriggerMorningDate = today)
            }
            appPrefs.setMainlineConfig(newConfig)
            
            AppLogger.d("MainlineDaily: 记录自动触发，日期=$today, 时段=${if (isEvening) "晚间" else "早间"}")
            
            // 更新状态为已自动触发
            _uiState.update { it.copy(shouldAutoTrigger = false) }
        }
    }

    // ── 记录选择完成（手动触发时调用）──
    
    fun recordSelectionComplete() {
        viewModelScope.launch {
            // 获取配置以计算正确的日期
            val config = appPrefs.mainlineConfig.first()
            val morningStartHour = config.morningHour
            
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            
            // 计算"今天"的日期（以早间开始时间作为一天的分界点）
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = if (currentHour < morningStartHour) {
                // 凌晨未到早间时间，算前一天
                now.add(Calendar.DAY_OF_MONTH, -1)
                sdf.format(now.time)
            } else {
                sdf.format(now.time)
            }
            
            val isEvening = _uiState.value.isEveningSession
            
            // 获取当前配置
            appPrefs.mainlineConfig.collect { currentConfig ->
                val newConfig = if (isEvening) {
                    currentConfig.copy(lastEveningDate = today)
                } else {
                    // 早间触发时，清除前一天的晚间标记
                    currentConfig.copy(
                        lastMorningDate = today,
                        lastEveningDate = ""  // 清除前一天的晚间标记
                    )
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
