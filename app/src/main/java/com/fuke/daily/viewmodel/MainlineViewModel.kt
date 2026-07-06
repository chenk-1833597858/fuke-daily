package com.fuke.daily.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuke.daily.data.datastore.AppPrefs
import com.fuke.daily.data.model.MainlineBranch
import com.fuke.daily.data.model.MainlineConfig
import com.fuke.daily.data.model.MainlineItem
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.data.repository.MainlineRepo
import com.fuke.daily.util.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  主线 UI 状态
// ═══════════════════════════════════════════════════

data class MainlineUiState(
    val mainList: MainList? = null,
    val branches: List<MainlineBranch> = emptyList(),
    val items: Map<Long, List<MainlineItem>> = emptyMap(),   // branchId -> items
    val linkHistory: List<LinkHistoryEntry> = emptyList(),
    val todayLinks: List<LinkHistoryEntry> = emptyList(),
    val config: MainlineConfig = MainlineConfig(),
    val mainlineEnabled: Boolean = true,
)

data class LinkHistoryEntry(
    val id: Long = 0,
    val path: List<Pair<Long, String>>,
    val date: String,
    val timestamp: Long,
)

// ═══════════════════════════════════════════════════
//  主线 ViewModel — Phase 6
// ═══════════════════════════════════════════════════

@HiltViewModel
class MainlineViewModel @Inject constructor(
    private val mainListRepo: MainListRepo,
    private val mainlineRepo: MainlineRepo,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainlineUiState())
    val uiState: StateFlow<MainlineUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private var currentListId: Long = 0

    // ── 加载主线数据 ──

    fun loadMainline(listId: Long) {
        if (currentListId == listId) {
            // 即使 listId 相同，也重新加载数据（用于刷新）
            reloadAll(listId)
            return
        }
        currentListId = listId
        reloadAll(listId)
    }

    private fun reloadAll(listId: Long) {
        viewModelScope.launch {
            mainListRepo.getListById(listId)?.let { list ->
                _uiState.update { it.copy(mainList = list) }
            }
        }
        loadBranches(listId)
        loadLinkHistory()
        loadConfig()
        loadEnabled()
    }

    private fun loadBranches(listId: Long) {
        viewModelScope.launch {
            val branches = mainListRepo.getBranches(listId)
            _uiState.update { it.copy(branches = branches) }
            // 加载每条支线的子项
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

    // ── 链路历史 ──

    fun loadLinkHistory() {
        viewModelScope.launch {
            val records = mainlineRepo.getRecentRecordsPerDay(7)
            AppLogger.i("MainlineViewModel: loadLinkHistory loaded ${records.size} records")
            val entries = records.map { record ->
                LinkHistoryEntry(
                    id = record.id,
                    path = parsePath(record.path),
                    date = record.date,
                    timestamp = record.timestamp,
                )
            }
            _uiState.update { it.copy(linkHistory = entries) }
        }
    }

    fun loadTodayLinks(date: String) {
        viewModelScope.launch {
            val records = mainlineRepo.getRecordsByDate(date).first()
            AppLogger.i("MainlineViewModel: loadTodayLinks for date=$date, records=${records.size}")
            records.forEach { record ->
                AppLogger.i("MainlineViewModel: record path=${record.path}")
            }
            val entries = records.map { record ->
                LinkHistoryEntry(
                    id = record.id,
                    path = parsePath(record.path),
                    date = record.date,
                    timestamp = record.timestamp,
                )
            }
            _uiState.update { it.copy(todayLinks = entries) }
        }
    }

    // ── 链路选择记录 ──

    fun selectLink(path: List<Pair<Long, String>>, date: String, timestamp: Long) {
        viewModelScope.launch {
            val pathJson = serializePath(path)
            mainlineRepo.insertRecord(
                com.fuke.daily.data.model.LinkRecord(
                    path = pathJson,
                    date = date,
                    timestamp = timestamp,
                )
            )
            loadLinkHistory()
        }
    }

    // ── 主线配置 ──

    private fun loadConfig() {
        viewModelScope.launch {
            appPrefs.mainlineConfig.collect { config ->
                _uiState.update { it.copy(config = config) }
            }
        }
    }

    fun setMorningHour(hour: Int) {
        viewModelScope.launch {
            val current = _uiState.value.config
            appPrefs.setMainlineConfig(current.copy(morningHour = hour))
        }
    }

    fun setEveningHour(hour: Int) {
        viewModelScope.launch {
            val current = _uiState.value.config
            appPrefs.setMainlineConfig(current.copy(eveningHour = hour))
        }
    }

    // ── 主线开关 ──

    private fun loadEnabled() {
        viewModelScope.launch {
            appPrefs.mainlineEnabled.collect { enabled ->
                _uiState.update { it.copy(mainlineEnabled = enabled) }
            }
        }
    }

    fun setMainlineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPrefs.setMainlineEnabled(enabled)
        }
    }

    // ── 被动弹出判断 ──

    fun shouldShowMainlineSelect(currentHour: Int, currentDate: String): Boolean {
        val config = _uiState.value.config
        val enabled = _uiState.value.mainlineEnabled
        if (!enabled) return false

        // 上午时段判断
        if (config.morningHour > 0 &&
            currentHour >= config.morningHour &&
            config.lastMorningDate != currentDate
        ) {
            return true
        }

        // 晚间时段判断
        if (config.eveningHour > 0 &&
            currentHour >= config.eveningHour &&
            config.lastEveningDate != currentDate
        ) {
            return true
        }

        return false
    }

    // ── 记录已弹出 ──

    fun markShownMorning(date: String) {
        viewModelScope.launch {
            val current = _uiState.value.config
            appPrefs.setMainlineConfig(current.copy(lastMorningDate = date))
        }
    }

    fun markShownEvening(date: String) {
        viewModelScope.launch {
            val current = _uiState.value.config
            appPrefs.setMainlineConfig(current.copy(lastEveningDate = date))
        }
    }

    // ── 支线 CRUD ──

    fun addBranch(name: String = "") {
        if (currentListId == 0L) return
        viewModelScope.launch {
            val maxSort = _uiState.value.branches.maxOfOrNull { it.sortOrder } ?: 0
            mainListRepo.insertBranch(
                MainlineBranch(
                    parentListId = currentListId,
                    name = name,
                    sortOrder = maxSort + 1,
                )
            )
            loadBranches(currentListId)
        }
    }

    fun updateBranch(branch: MainlineBranch) {
        viewModelScope.launch {
            mainListRepo.updateBranch(branch)
            loadBranches(currentListId)
        }
    }

    fun deleteBranch(branch: MainlineBranch) {
        viewModelScope.launch {
            mainListRepo.deleteBranch(branch)
            // 移除该支线的子项缓存
            _uiState.update { state ->
                state.copy(items = state.items - branch.id)
            }
            loadBranches(currentListId)
        }
    }

    // ── 子项 CRUD ──

    fun addBranchItem(branchId: Long, name: String = "", isCurrent: Boolean = false) {
        viewModelScope.launch {
            val existing = _uiState.value.items[branchId] ?: emptyList()
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0
            mainListRepo.insertBranchItem(
                MainlineItem(
                    branchId = branchId,
                    name = name,
                    isCurrent = isCurrent,
                    sortOrder = maxSort + 1,
                )
            )
            loadBranchItems(branchId)
        }
    }

    fun addCurrentItem(branchId: Long, name: String = "") {
        addBranchItem(branchId, name, isCurrent = true)
    }

    fun updateBranchItem(item: MainlineItem) {
        viewModelScope.launch {
            mainListRepo.updateBranchItem(item)
            loadBranchItems(item.branchId)
        }
    }

    fun deleteBranchItem(item: MainlineItem) {
        viewModelScope.launch {
            mainListRepo.deleteBranchItem(item)
            loadBranchItems(item.branchId)
        }
    }

    // ── 主线名称更新 ──

    fun updateMainListName(name: String) {
        viewModelScope.launch {
            val list = _uiState.value.mainList ?: return@launch
            mainListRepo.updateList(list.copy(name = name))
            _uiState.update { it.copy(mainList = list.copy(name = name)) }
        }
    }

    // ── 删除主线项目 ──
    
    fun deleteMainline() {
        viewModelScope.launch {
            val listId = currentListId
            if (listId > 0) {
                mainListRepo.deleteListCascade(listId)
                AppLogger.i("MainlineViewModel: 删除主线项目 id=$listId")
            }
        }
    }

    // ── JSON 序列化/反序列化 ──

    private fun serializePath(path: List<Pair<Long, String>>): String {
        val list = path.map { mapOf("id" to it.first, "name" to it.second) }
        return gson.toJson(list)
    }

    private fun parsePath(json: String): List<Pair<Long, String>> {
        return try {
            AppLogger.i("MainlineViewModel: parsePath input=$json")
            // 使用 JSONArray 手动解析，避免 TypeToken 问题
            val list = org.json.JSONArray(json)
            val result = mutableListOf<Pair<Long, String>>()
            for (i in 0 until list.length()) {
                val obj = list.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name")
                result.add(id to name)
            }
            AppLogger.i("MainlineViewModel: parsePath result=$result")
            result
        } catch (e: Exception) {
            AppLogger.e("MainlineViewModel: parsePath failed", e)
            emptyList()
        }
    }
}
