package com.fuke.daily.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuke.daily.data.datastore.AppPrefs
import com.fuke.daily.data.model.ListType
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  主界面 ViewModel
// ═══════════════════════════════════════════════════

data class HomeUiState(
    val lists: List<MainList> = emptyList(),
    val currentFilter: String = "ALL",     // ALL / SELECTION / RANDOM / QUIZ / MAINLINE
    val currentTheme: ThemeMode = ThemeMode.WARM,
    val hasMainline: Boolean = false,
    val showCreateDialog: Boolean = false,
    val currentBottomTab: Int = 0,         // 0=项目, 1=定时, 2=记忆
    val showItemActionDialog: Boolean = false,
    val actionListItem: MainList? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val mainListRepo: MainListRepo,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 监听主列表
        viewModelScope.launch {
            mainListRepo.getAllLists().collect { lists ->
                val sorted = lists.sortedWith(
                    compareBy<MainList> { if (it.type == ListType.MAINLINE) 0 else 1 }
                        .thenBy { if (it.type != ListType.MAINLINE && !it.pinned) 1 else 0 }
                        .thenByDescending { if (it.pinned) it.updatedAt else 0L }
                        .thenByDescending { if (it.type != ListType.MAINLINE && !it.pinned) it.createdAt else 0L }
                )
                _uiState.update { state ->
                    state.copy(
                        lists = sorted,
                        hasMainline = lists.any { it.type == ListType.MAINLINE },
                    )
                }
            }
        }

        // 监听主题
        viewModelScope.launch {
            appPrefs.themeMode.collect { mode ->
                _uiState.update { it.copy(currentTheme = mode) }
            }
        }
    }

    // ── 筛选 ──

    fun setFilter(filter: String) {
        _uiState.update { it.copy(currentFilter = filter) }
    }

    fun getFilteredLists(): List<MainList> {
        val state = _uiState.value
        return if (state.currentFilter == "ALL") {
            state.lists
        } else {
            state.lists.filter { it.type.name == state.currentFilter }
        }
    }

    // ── 开关 ──

    fun toggleList(id: Long) {
        viewModelScope.launch {
            val list = mainListRepo.getListById(id) ?: return@launch
            mainListRepo.updateList(list.copy(isEnabled = !list.isEnabled))
        }
    }

    // ── 置顶 ──

    fun pinList(id: Long) {
        viewModelScope.launch {
            val list = mainListRepo.getListById(id) ?: return@launch
            if (!list.pinned) {
                // 检查置顶数量上限
                val pinnedCount = _uiState.value.lists.count { it.pinned }
                if (pinnedCount >= 10) return@launch
            }
            mainListRepo.updateList(list.copy(pinned = !list.pinned, updatedAt = System.currentTimeMillis()))
        }
    }

    // ── 长按弹窗 ──

    fun showItemActionDialog(item: MainList) {
        // 主线不弹窗
        if (item.type == ListType.MAINLINE) return
        _uiState.update { it.copy(showItemActionDialog = true, actionListItem = item) }
    }

    fun dismissItemActionDialog() {
        _uiState.update { it.copy(showItemActionDialog = false, actionListItem = null) }
    }

    // ── 创建 ──

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createList(name: String, type: ListType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val maxSort = _uiState.value.lists.maxOfOrNull { it.sortOrder } ?: 0
            mainListRepo.insertList(
                MainList(
                    name = name,
                    type = type,
                    sortOrder = maxSort + 1,
                )
            )
            _uiState.update { it.copy(showCreateDialog = false) }
        }
    }

    // ── 删除 ──

    fun deleteList(id: Long) {
        viewModelScope.launch {
            mainListRepo.deleteListCascade(id)
        }
    }

    // ── 主题切换 ──

    fun switchTheme() {
        viewModelScope.launch {
            val newMode = if (_uiState.value.currentTheme == ThemeMode.WARM) {
                ThemeMode.PURPLE
            } else {
                ThemeMode.WARM
            }
            appPrefs.setThemeMode(newMode)
        }
    }

    // ── 底部导航 ──

    fun setBottomTab(index: Int) {
        _uiState.update { it.copy(currentBottomTab = index) }
    }
}
