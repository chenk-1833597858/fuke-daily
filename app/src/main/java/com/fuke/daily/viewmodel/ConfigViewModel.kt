package com.fuke.daily.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuke.daily.data.model.*
import com.fuke.daily.data.repository.MainListRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  配置页 UI 状态
// ═══════════════════════════════════════════════════

data class ConfigUiState(
    val mainList: MainList? = null,
    val subLists: List<SubList> = emptyList(),
    val contentConfigs: Map<Long, ContentConfig> = emptyMap(),
    val optionButtons: Map<Long, List<OptionButton>> = emptyMap(),
    val richTexts: List<RichText> = emptyList(),
    val quizGroups: List<QuizGroup> = emptyList(),
    val quizCards: Map<Long, List<QuizCard>> = emptyMap(),
)

// ═══════════════════════════════════════════════════
//  配置页 ViewModel
// ═══════════════════════════════════════════════════

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repo: MainListRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private var currentListId: Long = 0
    private var hasAutoCreatedRichText = false
    private var hasAutoCreatedSubList = false

    fun setListId(listId: Long) {
        if (currentListId == listId) return
        currentListId = listId
        hasAutoCreatedRichText = false
        hasAutoCreatedSubList = false
        loadAll(listId)
    }

    private fun loadAll(listId: Long) {
        viewModelScope.launch {
            repo.getListById(listId)?.let { list ->
                _uiState.update { it.copy(mainList = list) }
            }
        }
        loadSubLists(listId)
        loadRichTexts(listId)
        loadQuizGroups(listId)
    }

    // ── 子列表 ──

    fun loadSubLists(listId: Long) {
        viewModelScope.launch {
            repo.getSubLists(listId).collect { subs ->
                _uiState.update { it.copy(subLists = subs) }
                subs.forEach { sub ->
                    loadContentConfig(sub.id, listId)
                    loadOptionButtons(sub.id, listId)
                }

                // Fix 3: 自动创建第一个子列表
                if (subs.isEmpty() && !hasAutoCreatedSubList && currentListId == listId) {
                    hasAutoCreatedSubList = true
                    addSubList()
                }
            }
        }
    }

    fun addSubList(name: String = "") {
        if (currentListId == 0L) return

        viewModelScope.launch {
            val maxSort = _uiState.value.subLists.maxOfOrNull { it.sortOrder } ?: 0
            // Fix 5: 第一个子列表如果是随机列表，默认设fixedSlot=1
            val isFirstSubList = _uiState.value.subLists.isEmpty()
            val isRandom = _uiState.value.mainList?.type == ListType.RANDOM
            val defaultFixedSlot = if (isFirstSubList && isRandom) 1 else 0
            val id = repo.insertSubList(
                SubList(
                    parentListId = currentListId,
                    name = name,
                    sortOrder = maxSort + 1,
                    fixedSlot = defaultFixedSlot,
                )
            )
            repo.saveContentConfig(
                ContentConfig(subListId = id, parentListId = currentListId)
            )
        }
    }

    fun updateSubList(subList: SubList) {
        viewModelScope.launch {
            repo.updateSubList(subList)
        }
    }

    fun deleteSubList(subList: SubList) {
        viewModelScope.launch {
            repo.deleteSubList(subList)
        }
    }

    // ── 内容配置 ──

    private fun loadContentConfig(subListId: Long, parentListId: Long) {
        viewModelScope.launch {
            val config = repo.getContentConfig(subListId, parentListId)
            if (config != null) {
                _uiState.update { state ->
                    state.copy(
                        contentConfigs = state.contentConfigs + (subListId to config)
                    )
                }
            }
        }
    }

    fun saveContentConfig(config: ContentConfig) {
        viewModelScope.launch {
            repo.saveContentConfig(config)
            _uiState.update { state ->
                state.copy(
                    contentConfigs = state.contentConfigs + (config.subListId to config)
                )
            }
        }
    }

    // ── 选项按钮 ──

    private fun loadOptionButtons(subListId: Long, parentListId: Long) {
        viewModelScope.launch {
            val buttons = repo.getOptionButtons(subListId, parentListId)
            _uiState.update { state ->
                state.copy(
                    optionButtons = state.optionButtons + (subListId to buttons)
                )
            }
        }
    }

    fun addOptionButton(subListId: Long, name: String = "") {
        if (currentListId == 0L) return
        viewModelScope.launch {
            val existing = _uiState.value.optionButtons[subListId] ?: emptyList()
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0
            repo.insertOptionButton(
                OptionButton(
                    subListId = subListId,
                    parentListId = currentListId,
                    name = name,
                    sortOrder = maxSort + 1,
                )
            )
            val buttons = repo.getOptionButtons(subListId, currentListId)
            _uiState.update { state ->
                state.copy(
                    optionButtons = state.optionButtons + (subListId to buttons)
                )
            }
        }
    }

    fun updateOptionButton(button: OptionButton) {
        viewModelScope.launch {
            repo.updateOptionButton(button)
            val buttons = repo.getOptionButtons(button.subListId, button.parentListId)
            _uiState.update { state ->
                state.copy(
                    optionButtons = state.optionButtons + (button.subListId to buttons)
                )
            }
        }
    }

    fun deleteOptionButton(button: OptionButton) {
        viewModelScope.launch {
            repo.deleteOptionButton(button)
            val buttons = repo.getOptionButtons(button.subListId, button.parentListId)
            _uiState.update { state ->
                state.copy(
                    optionButtons = state.optionButtons + (button.subListId to buttons)
                )
            }
        }
    }

    // ── 富文本 ──

    fun loadRichTexts(listId: Long) {
        viewModelScope.launch {
            repo.getRichTexts(listId).collect { texts ->
                _uiState.update { it.copy(richTexts = texts) }

                // Fix 3: 进入随机列表配置页时，如果没有富文本，自动创建第一个
                if (texts.isEmpty() && !hasAutoCreatedRichText && currentListId == listId) {
                    hasAutoCreatedRichText = true
                    addRichText()
                }
            }
        }
    }

    fun addRichText(name: String = "") {
        if (currentListId == 0L) return
        viewModelScope.launch {
            val maxSort = _uiState.value.richTexts.maxOfOrNull { it.sortOrder } ?: 0
            val cnNumbers = listOf("一", "二", "三", "四")
            val autoName = name.ifBlank { "【${cnNumbers.getOrElse(_uiState.value.richTexts.size) { "${_uiState.value.richTexts.size + 1}" }}】" }
            repo.insertRichText(
                RichText(
                    parentListId = currentListId,
                    name = autoName,
                    sortOrder = maxSort + 1,
                )
            )
        }
    }

    fun updateRichText(richText: RichText) {
        viewModelScope.launch {
            repo.updateRichText(richText)
        }
    }

    fun deleteRichText(richText: RichText) {
        viewModelScope.launch {
            repo.deleteRichText(richText)
        }
    }

    // ── 答题组 ──

    fun loadQuizGroups(listId: Long) {
        viewModelScope.launch {
            repo.getQuizGroups(listId).collect { groups ->
                _uiState.update { it.copy(quizGroups = groups) }
                groups.forEach { group ->
                    loadQuizCards(group.id)
                }
            }
        }
    }

    fun addQuizGroup(name: String = "") {
        if (currentListId == 0L) return
        viewModelScope.launch {
            val maxSort = _uiState.value.quizGroups.maxOfOrNull { it.sortOrder } ?: 0
            repo.insertQuizGroup(
                QuizGroup(
                    parentListId = currentListId,
                    name = name,
                    sortOrder = maxSort + 1,
                )
            )
        }
    }

    fun updateQuizGroup(group: QuizGroup) {
        viewModelScope.launch {
            repo.updateQuizGroup(group)
        }
    }

    fun deleteQuizGroup(group: QuizGroup) {
        viewModelScope.launch {
            repo.deleteQuizGroup(group)
        }
    }

    // ── 答题卡片 ──

    private fun loadQuizCards(groupId: Long) {
        viewModelScope.launch {
            val cards = repo.getQuizCards(groupId)
            _uiState.update { state ->
                state.copy(
                    quizCards = state.quizCards + (groupId to cards)
                )
            }
        }
    }

    fun addQuizCard(groupId: Long) {
        viewModelScope.launch {
            val existing = _uiState.value.quizCards[groupId] ?: emptyList()
            val maxSort = existing.maxOfOrNull { it.sortOrder } ?: 0
            repo.insertQuizCard(
                QuizCard(
                    groupId = groupId,
                    sortOrder = maxSort + 1,
                )
            )
            val cards = repo.getQuizCards(groupId)
            _uiState.update { state ->
                state.copy(
                    quizCards = state.quizCards + (groupId to cards)
                )
            }
        }
    }

    fun updateQuizCard(card: QuizCard) {
        viewModelScope.launch {
            repo.updateQuizCard(card)
        }
    }

    fun deleteQuizCard(card: QuizCard) {
        viewModelScope.launch {
            repo.deleteQuizCard(card)
            val cards = repo.getQuizCards(card.groupId)
            _uiState.update { state ->
                state.copy(
                    quizCards = state.quizCards + (card.groupId to cards)
                )
            }
        }
    }

    // ── 引用槽位分配 ──

    fun assignSlot(subListId: Long, lineIndex: Int, slot: Int) {
        val config = _uiState.value.contentConfigs[subListId] ?: return
        val updated = when (lineIndex) {
            0 -> config.copy(button1Storage = slot)
            1 -> config.copy(button2Storage = slot)
            2 -> config.copy(button3Storage = slot)
            else -> return
        }
        saveContentConfig(updated)
    }

    // ── 选项按钮引用槽位 ──

    fun assignOptionSlot(button: OptionButton, slot: Int) {
        updateOptionButton(button.copy(storageSlot = slot))
    }

    // ── 固定槽位 ──

    fun updateFixedSlot(subList: SubList, slot: Int) {
        updateSubList(subList.copy(fixedSlot = slot))
    }
}
