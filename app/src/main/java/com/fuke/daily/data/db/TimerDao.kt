package com.fuke.daily.data.db

import androidx.room.*
import com.fuke.daily.data.model.TimerItem
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════
//  定时任务 DAO
// ═══════════════════════════════════════════════════

@Dao
interface TimerDao {

    @Query("SELECT * FROM timers ORDER BY hour ASC, minute ASC")
    fun getAllTimers(): Flow<List<TimerItem>>

    @Query("SELECT * FROM timers WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    fun getEnabledTimers(): Flow<List<TimerItem>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimerById(id: Long): TimerItem?

    @Insert
    suspend fun insertTimer(timer: TimerItem): Long

    @Update
    suspend fun updateTimer(timer: TimerItem)

    @Delete
    suspend fun deleteTimer(timer: TimerItem)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimerById(id: Long)
}
