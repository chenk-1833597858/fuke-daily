package com.fuke.daily.floating

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.fuke.daily.data.model.ContentConfig
import com.fuke.daily.data.model.MainList
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.data.model.QuizCard
import com.fuke.daily.data.model.QuizGroup
import com.fuke.daily.data.model.RichText
import com.fuke.daily.data.model.StorageData
import com.fuke.daily.data.model.SubList
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  悬浮窗管理器（单例）
//  管理悬浮图标和内容窗口的添加/移除
//  洗牌池逻辑：从已启用主列表的子列表中随机选取
// ═══════════════════════════════════════════════════

@Singleton
class FloatingWindowManager @Inject constructor(
    private val mainListRepo: MainListRepo,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── 项目级别洗牌池（只存 MainList，子列表按顺序取） ──
    private var mainListShufflePool = listOf<MainList>()
    private var currentMainListIndex = -1
    private var currentSubListIndexInMain = -1

    // ── 当前展示数据 ──
    private var currentMainList: MainList? = null
    private var currentSubList: SubList? = null
    private var currentConfig: ContentConfig? = null
    private var currentButtons: List<OptionButton> = emptyList()
    private var currentStorageData = StorageData()
    private var currentFixedSlotData = ""
    private var currentRichTextData: Map<Long, List<RichText>> = emptyMap()

    // ── 固定槽洗牌池（富文本行内容洗牌） ──
    private var fixedSlotLines: List<String> = emptyList()
    private var fixedSlotLineIndex = 0

    // ── View引用 ──
    private var iconView: View? = null
    private var popupView: View? = null
    private var windowManager: WindowManager? = null
    private var context: Context? = null

    // ── 状态 ──
    private var isIconShowing = false
    private var isPopupShowing = false

    // ═══════════════════════════════════════════════════
    //  初始化
    // ═══════════════════════════════════════════════════

    fun init(ctx: Context) {
        context = ctx.applicationContext
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    // ═══════════════════════════════════════════════════
    //  悬浮图标
    // ═══════════════════════════════════════════════════

    fun showIcon(view: View, params: WindowManager.LayoutParams) {
        if (isIconShowing) return
        try {
            windowManager?.addView(view, params)
            iconView = view
            isIconShowing = true
            AppLogger.i("FloatingWindowManager: icon shown")
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowManager: showIcon failed", e)
        }
    }

    fun hideIcon() {
        if (!isIconShowing) return
        try {
            iconView?.let { windowManager?.removeView(it) }
            iconView = null
            isIconShowing = false
            AppLogger.i("FloatingWindowManager: icon hidden")
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowManager: hideIcon failed", e)
        }
    }

    fun updateIconParams(params: WindowManager.LayoutParams) {
        if (!isIconShowing) return
        try {
            iconView?.let { windowManager?.updateViewLayout(it, params) }
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowManager: updateIconParams failed", e)
        }
    }

    fun setIconVisibility(visible: Boolean) {
        iconView?.let {
            it.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun isIconVisible(): Boolean = isIconShowing

    // ═══════════════════════════════════════════════════
    //  悬浮弹窗
    // ═══════════════════════════════════════════════════

    fun showPopup(view: View, params: WindowManager.LayoutParams) {
        if (isPopupShowing) return
        try {
            windowManager?.addView(view, params)
            popupView = view
            isPopupShowing = true
            AppLogger.i("FloatingWindowManager: popup shown")
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowManager: showPopup failed", e)
        }
    }

    fun hidePopup() {
        if (!isPopupShowing) return
        try {
            popupView?.let { windowManager?.removeView(it) }
            popupView = null
            isPopupShowing = false
            AppLogger.i("FloatingWindowManager: popup hidden")
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowManager: hidePopup failed", e)
        }
    }

    fun isPopupVisible(): Boolean = isPopupShowing

    // ═══════════════════════════════════════════════════
    //  位置保存和恢复
    // ═══════════════════════════════════════════════════

    fun getCurrentMainListIndex(): Int = currentMainListIndex
    fun getCurrentSubListIndex(): Int = currentSubListIndexInMain

    fun restorePosition(mainListIndex: Int, subListIndex: Int) {
        currentMainListIndex = mainListIndex
        currentSubListIndexInMain = subListIndex
        AppLogger.d("FloatingWindowManager: position restored to mainList=$mainListIndex, subList=$subListIndex")
    }

    // ═══════════════════════════════════════════════════
    //  洗牌池逻辑 — 项目级别随机，子列表按顺序
    // ═══════════════════════════════════════════════════

    suspend fun refillShufflePool() {
        val enabledLists = mainListRepo.getAllLists().first()
            .filter { it.isEnabled }

        // 项目级别洗牌：只随机 MainList 的顺序
        mainListShufflePool = enabledLists.shuffled()
        currentMainListIndex = -1
        currentSubListIndexInMain = -1
        
        AppLogger.i("FloatingWindowManager: mainList shuffle pool refilled with ${mainListShufflePool.size} items")
    }

    suspend fun nextRandomItem(): Pair<MainList, SubList>? {
        // 如果当前项目的子列表已经遍历完，或者没有当前项目，取下一个项目
        if (currentMainListIndex < 0 || currentSubListIndexInMain < 0) {
            // 需要取下一个项目
            if (mainListShufflePool.isEmpty() || currentMainListIndex >= mainListShufflePool.size - 1) {
                refillShufflePool()
                if (mainListShufflePool.isEmpty()) return null
            }
            currentMainListIndex++
            currentSubListIndexInMain = 0
        }

        val mainList = mainListShufflePool[currentMainListIndex]
        val subLists = mainListRepo.getSubListsOnce(mainList.id)
        
        if (currentSubListIndexInMain >= subLists.size) {
            // 当前项目的子列表已遍历完，取下一个项目
            if (currentMainListIndex >= mainListShufflePool.size - 1) {
                refillShufflePool()
                if (mainListShufflePool.isEmpty()) return null
                currentMainListIndex = 0
            } else {
                currentMainListIndex++
            }
            currentSubListIndexInMain = 0
            return nextRandomItem() // 递归取下一个
        }

        val subList = subLists[currentSubListIndexInMain]
        currentSubListIndexInMain++
        return Pair(mainList, subList)
    }

    /**
     * 双击打开弹窗时调用：重置洗牌池，返回第一个启用项目的第一个子列表
     * 项目随机，但每个项目内从子列表1开始按顺序
     */
    suspend fun loadFirstEnabledItem(): Pair<MainList, SubList>? {
        refillShufflePool()
        if (mainListShufflePool.isEmpty()) return null

        // 取第一个项目（已洗牌），从该项目的第一个子列表开始
        currentMainListIndex = 0
        currentSubListIndexInMain = 0
        
        val mainList = mainListShufflePool[0]
        val subLists = mainListRepo.getSubListsOnce(mainList.id)
        if (subLists.isEmpty()) {
            // 如果第一个项目没有子列表，尝试下一个
            return nextRandomItem()
        }
        
        return Pair(mainList, subLists[0])
    }

    // ═══════════════════════════════════════════════════
    //  数据加载
    // ═══════════════════════════════════════════════════

    suspend fun loadStorageAndRichText(
        mainList: MainList,
        subList: SubList,
    ): Triple<ContentConfig?, List<OptionButton>, StorageData> {
        currentMainList = mainList
        currentSubList = subList

        currentConfig = mainListRepo.getContentConfig(subList.id, mainList.id)
        currentButtons = mainListRepo.getOptionButtons(subList.id, mainList.id)

        val richTexts = mainListRepo.getRichTextsOnce(mainList.id)
        currentRichTextData = mapOf(mainList.id to richTexts)

        if (subList.fixedSlot > 0) {
            loadFixedSlotData(richTexts)
        } else {
            currentFixedSlotData = ""
        }

        return Triple(currentConfig, currentButtons, currentStorageData)
    }

    private fun loadFixedSlotData(richTexts: List<RichText>) {
        if (fixedSlotLines.isEmpty() || fixedSlotLineIndex >= fixedSlotLines.size) {
            val allLines = mutableListOf<String>()
            for (rt in richTexts) {
                val lines = rt.content.split("\n").filter { it.isNotBlank() }
                allLines.addAll(lines)
            }
            fixedSlotLines = allLines.shuffled()
            fixedSlotLineIndex = 0
        }

        if (fixedSlotLines.isNotEmpty() && fixedSlotLineIndex < fixedSlotLines.size) {
            currentFixedSlotData = fixedSlotLines[fixedSlotLineIndex]
            fixedSlotLineIndex++
        } else {
            currentFixedSlotData = ""
        }
    }

    suspend fun jumpToSubList(jumpToIndex: Int): Triple<ContentConfig?, List<OptionButton>, StorageData>? {
        val mainList = currentMainList ?: return null
        val subLists = mainListRepo.getSubListsOnce(mainList.id)

        if (jumpToIndex < 1 || jumpToIndex > subLists.size) return null

        val targetSubList = subLists[jumpToIndex - 1]
        return loadStorageAndRichText(mainList, targetSubList)
    }

    // ═══════════════════════════════════════════════════
    //  存储槽操作
    // ═══════════════════════════════════════════════════

    fun writeStorageSlot(slot: Int, value: String) {
        if (slot in 1..5) {
            currentStorageData = currentStorageData.setSlot(slot, value)
        }
    }

    // ═══════════════════════════════════════════════════
    //  状态查询
    // ═══════════════════════════════════════════════════

    fun getCurrentFixedSlotData(): String = currentFixedSlotData
    fun getCurrentStorageData(): StorageData = currentStorageData
    fun getCurrentRichTextData(): Map<Long, List<RichText>> = currentRichTextData
    fun getCurrentConfig(): ContentConfig? = currentConfig
    fun getCurrentButtons(): List<OptionButton> = currentButtons
    fun getCurrentSubList(): SubList? = currentSubList
    fun getCurrentMainList(): MainList? = currentMainList

    // ═══════════════════════════════════════════════════
    //  答题卡片加载
    // ═══════════════════════════════════════════════════

    suspend fun loadQuizCards(parentListId: Long): List<QuizCard> {
        val groups = mainListRepo.getQuizGroupsOnce(parentListId)
        val allCards = mutableListOf<QuizCard>()
        for (group in groups) {
            val cards = mainListRepo.getQuizCards(group.id)
            allCards.addAll(cards)
        }
        return allCards
    }

    // ═══════════════════════════════════════════════════
    //  清理
    // ═══════════════════════════════════════════════════

    fun destroy() {
        hidePopup()
        hideIcon()
        context = null
        windowManager = null
        mainListShufflePool = emptyList()
        currentMainListIndex = -1
        currentSubListIndexInMain = -1
        fixedSlotLines = emptyList()
        fixedSlotLineIndex = 0
    }
}
