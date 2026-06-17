package com.fuke.daily.data.db

import androidx.room.*
import com.fuke.daily.data.model.LinkRecord
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════
//  链路历史 DAO
// ═══════════════════════════════════════════════════

@Dao
interface LinkHistoryDao {

    @Query("SELECT * FROM link_history ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<LinkRecord>>

    @Query("SELECT * FROM link_history WHERE date = :date ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): Flow<List<LinkRecord>>

    @Query("SELECT * FROM link_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<LinkRecord>

    // 每天只取最新一条记录
    @Query("SELECT * FROM link_history WHERE id IN (SELECT MAX(id) FROM link_history GROUP BY date) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRecordsPerDay(limit: Int): List<LinkRecord>

    @Insert
    suspend fun insertRecord(record: LinkRecord): Long

    @Delete
    suspend fun deleteRecord(record: LinkRecord)

    @Query("DELETE FROM link_history WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldRecords(beforeTimestamp: Long)
}
