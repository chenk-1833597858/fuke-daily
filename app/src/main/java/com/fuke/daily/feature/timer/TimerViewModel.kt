package com.fuke.daily.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.fuke.daily.data.model.*
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.data.repository.TimerRepo
import com.fuke.daily.feature.timer.TimerReminderService
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  定时任务 UI 状态
// ═══════════════════════════════════════════════════

data class TimerUiState(
    val timers: List<TimerItem> = emptyList(),
    val mainLists: List<MainList> = emptyList(),
)

// ═══════════════════════════════════════════════════
//  定时任务 ViewModel
// ═══════════════════════════════════════════════════

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerRepo: TimerRepo,
    private val mainListRepo: MainListRepo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        loadTimers()
        loadMainLists()
    }

    fun loadTimers() {
        viewModelScope.launch {
            timerRepo.getAllTimers().collect { timers ->
                _uiState.update { it.copy(timers = timers) }
            }
        }
    }

    fun loadMainLists() {
        viewModelScope.launch {
            mainListRepo.getAllLists().collect { lists ->
                _uiState.update { it.copy(mainLists = lists) }
            }
        }
    }

    fun insertTimer(timer: TimerItem, context: Context? = null) {
        viewModelScope.launch {
            val id = timerRepo.insertTimer(timer)
            // 如果启用，立即调度
            if (timer.isEnabled && context != null) {
                val saved = timer.copy(id = id)
                TimerReminderService.scheduleTask(context, saved)
            }
        }
    }

    fun updateTimer(timer: TimerItem, context: Context? = null) {
        viewModelScope.launch {
            timerRepo.updateTimer(timer)
            // 重新调度或取消
            if (context != null) {
                if (timer.isEnabled) {
                    TimerReminderService.scheduleTask(context, timer)
                } else {
                    TimerReminderService.cancelTask(context, timer.id)
                }
            }
        }
    }

    fun deleteTimer(id: Long, context: Context? = null) {
        viewModelScope.launch {
            if (context != null) {
                TimerReminderService.cancelTask(context, id)
            }
            timerRepo.deleteTimerById(id)
        }
    }

    fun toggleTimer(id: Long, context: Context? = null) {
        val timer = _uiState.value.timers.find { it.id == id } ?: return
        val updated = timer.copy(isEnabled = !timer.isEnabled)
        viewModelScope.launch {
            timerRepo.updateTimer(updated)
            if (context != null) {
                if (updated.isEnabled) {
                    TimerReminderService.scheduleTask(context, updated)
                } else {
                    TimerReminderService.cancelTask(context, id)
                }
            }
        }
    }

    /**
     * 暂停/恢复定时任务
     * - 前台暂停：isPaused = true，下次到点继续（保留isEnabled）
     * - 恢复：isPaused = false，重新调度
     */
    fun pauseTimer(id: Long, context: Context? = null) {
        val timer = _uiState.value.timers.find { it.id == id } ?: return
        val updated = timer.copy(isPaused = true)
        viewModelScope.launch {
            timerRepo.updateTimer(updated)
            // 前台暂停：取消当前调度但保留isEnabled
            if (context != null) {
                TimerReminderService.cancelTask(context, id)
                AppLogger.i("Timer: 任务已前台暂停: taskId=$id, isPaused=true")
            }
        }
    }

    fun resumeTimer(id: Long, context: Context? = null) {
        val timer = _uiState.value.timers.find { it.id == id } ?: return
        val updated = timer.copy(isPaused = false)
        viewModelScope.launch {
            timerRepo.updateTimer(updated)
            // 恢复调度
            if (context != null && updated.isEnabled) {
                TimerReminderService.scheduleTask(context, updated)
                AppLogger.i("Timer: 任务已恢复: taskId=$id, isPaused=false")
            }
        }
    }

    /**
     * 永久关闭定时任务（设置界面关闭）
     * - isEnabled = false, isPaused = false
     */
    fun disableTimer(id: Long, context: Context? = null) {
        val timer = _uiState.value.timers.find { it.id == id } ?: return
        val updated = timer.copy(isEnabled = false, isPaused = false)
        viewModelScope.launch {
            timerRepo.updateTimer(updated)
            if (context != null) {
                TimerReminderService.cancelTask(context, id)
                AppLogger.i("Timer: 任务已永久关闭: taskId=$id, isEnabled=false, isPaused=false")
            }
        }
    }
}
