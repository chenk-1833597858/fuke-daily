package com.fuke.daily.data.repository

import com.fuke.daily.data.db.LinkHistoryDao
import com.fuke.daily.data.model.LinkRecord
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  主线仓库
// ═══════════════════════════════════════════════════

@Singleton
class MainlineRepo @Inject constructor(
    private val linkHistoryDao: LinkHistoryDao,
) {
    // ── 链路历史 ──

    fun getAllRecords(): Flow<List<LinkRecord>> = linkHistoryDao.getAllRecords()

    fun getRecordsByDate(date: String): Flow<List<LinkRecord>> =
        linkHistoryDao.getRecordsByDate(date)

    suspend fun getRecentRecords(limit: Int): List<LinkRecord> =
        linkHistoryDao.getRecentRecords(limit)

    suspend fun getRecentRecordsPerDay(limit: Int): List<LinkRecord> =
        linkHistoryDao.getRecentRecordsPerDay(limit)

    suspend fun insertRecord(record: LinkRecord): Long =
        linkHistoryDao.insertRecord(record)

    suspend fun deleteOldRecords(beforeTimestamp: Long) =
        linkHistoryDao.deleteOldRecords(beforeTimestamp)
}
