package com.fuke.daily.data.repository

import com.fuke.daily.data.db.TimerDao
import com.fuke.daily.data.model.TimerItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  定时任务仓库
// ═══════════════════════════════════════════════════

@Singleton
class TimerRepo @Inject constructor(
    private val dao: TimerDao,
) {
    fun getAllTimers(): Flow<List<TimerItem>> = dao.getAllTimers()

    fun getEnabledTimers(): Flow<List<TimerItem>> = dao.getEnabledTimers()

    suspend fun getTimerById(id: Long): TimerItem? = dao.getTimerById(id)

    suspend fun insertTimer(timer: TimerItem): Long = dao.insertTimer(timer)

    suspend fun updateTimer(timer: TimerItem) = dao.updateTimer(timer)

    suspend fun deleteTimerById(id: Long) = dao.deleteTimerById(id)

    // 同步获取所有已启用定时任务（Service里用）
    suspend fun getAllEnabledTimersSync(): List<TimerItem> =
        dao.getEnabledTimers().first()
}