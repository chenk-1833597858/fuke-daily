package com.fuke.daily.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuke.daily.data.datastore.AppPrefs
import com.fuke.daily.data.model.MainlineBranch
import com.fuke.daily.data.model.MainlineItem
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.data.repository.MainlineRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

        // 判断当前是选路时段还是回顾时段
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        var eveningHour = 21  // 默认值
        // 先同步获取晚间时段配置
        viewModelScope.launch {
            appPrefs.mainlineConfig.collect { config ->
                eveningHour = config.eveningHour
                _uiState.update { it.copy(
                    isEveningSession = currentHour >= eveningHour,
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
    // path JSON格式：[{id, name, type}] 其中type为"current"/"signpost"/"ideal"/"goal"

    fun selectLink(
        currentItem: MainlineItem,
        currentBranch: MainlineBranch,
        idealBranch: MainlineBranch?,
        mainList: MainList,
        date: String,
        timestamp: Long,
    ) {
        viewModelScope.launch {
            val gson = com.google.gson.Gson()
            val pathNodes = mutableListOf<Map<String, Any>>()

            // 现状
            pathNodes.add(mapOf(
                "id" to currentItem.id,
                "name" to currentItem.name,
                "type" to "current",
            ))

            // 现状关联路标
            pathNodes.add(mapOf(
                "id" to currentBranch.id,
                "name" to currentBranch.name,
                "type" to "signpost",
            ))

            // 理想路标（如果与现状路标不同）
            if (idealBranch != null && idealBranch.id != currentBranch.id) {
                pathNodes.add(mapOf(
                    "id" to idealBranch.id,
                    "name" to idealBranch.name,
                    "type" to "ideal",
                ))
            }

            // 人生主线
            pathNodes.add(mapOf(
                "id" to mainList.id,
                "name" to mainList.name,
                "type" to "goal",
            ))

            val pathJson = gson.toJson(pathNodes)
            mainlineRepo.insertRecord(
                com.fuke.daily.data.model.LinkRecord(
                    path = pathJson,
                    date = date,
                    timestamp = timestamp,
                )
            )
        }
    }
}
