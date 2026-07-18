package com.fuke.daily.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 单项目导出工具类
 *
 * 根据mainListId，从Room数据库查出该项目相关的所有数据，
 * 写入一个新的SQLite数据库文件。
 *
 * 导出的表：main_lists(1条), sub_lists, content_configs, option_buttons,
 *           rich_texts, mainline_branches, mainline_items, quiz_groups,
 *           quiz_cards, link_history
 * 不导出：timers, android_metadata, room_master_table
 *
 * 注意：调用前需先关闭Room数据库连接（closeDatabaseForCheckpoint），
 * 本类用SQLiteDatabase直接打开主文件读数据，不走Room DAO。
 */
object ProjectExporter {

    private const val TAG = "ProjectExporter"

    // 需要导出的表（按依赖顺序）
    private val EXPORT_TABLES = listOf(
        "main_lists",
        "sub_lists",
        "content_configs",
        "option_buttons",
        "rich_texts",
        "mainline_branches",
        "mainline_items",
        "quiz_groups",
        "quiz_cards",
        "link_history",
    )

    /**
     * 导出单个项目数据为独立.db文件
     *
     * @param context Context
     * @param mainListId 要导出的项目ID
     * @return 临时文件（调用方负责用SAF写入用户选择的位置后删除）
     */
    suspend fun exportProject(context: Context, mainListId: Long): File {
        AppLogger.i("$TAG: 开始导出项目, mainListId=$mainListId")

        // 获取主数据库文件路径
        val dbPath = context.getDatabasePath("fuke-daily-db").absolutePath
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("数据库文件不存在: $dbPath")
        }

        // 创建临时导出文件
        val tempFile = File(context.cacheDir, "export_project_${mainListId}.db")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        // 用SQLiteDatabase直接打开主数据库（只读）
        AppLogger.i("$TAG: 打开源数据库, path=$dbPath, exists=${dbFile.exists()}, size=${dbFile.length()}")
        val sourceDb = SQLiteDatabase.openDatabase(
            dbPath, null, SQLiteDatabase.OPEN_READONLY
        )
        AppLogger.i("$TAG: 源数据库打开成功, version=${sourceDb.version}")

        try {
            // 创建新数据库
            val destDb = SQLiteDatabase.openOrCreateDatabase(tempFile, null)

            try {
                // 设置journal_mode为DELETE，避免导出文件带wal
                destDb.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToNext() }

                // 从源数据库复制表结构并导入数据
                copyProjectData(sourceDb, destDb, mainListId)

                // 写入room_master_table（让Room能识别这个数据库）
                insertRoomMasterTable(destDb)

                AppLogger.i("$TAG: 导出完成, 文件大小=${tempFile.length()}字节")
            } finally {
                destDb.close()
            }
        } finally {
            sourceDb.close()
        }

        return tempFile
    }

    /**
     * 从源数据库复制项目相关数据到目标数据库
     */
    private fun copyProjectData(sourceDb: SQLiteDatabase, destDb: SQLiteDatabase, mainListId: Long) {
        // 1. 复制表结构（从源数据库获取CREATE TABLE语句）
        for (table in EXPORT_TABLES) {
            val createSql = getCreateTableSql(sourceDb, table)
            if (createSql != null) {
                destDb.execSQL(createSql)
                AppLogger.d("$TAG: 创建表 $table")
            } else {
                AppLogger.w("$TAG: 表 $table 在源数据库中不存在，跳过")
            }
        }

        // 检查源数据库main_lists里有没有该项目
        val checkCursor = sourceDb.query("main_lists", arrayOf("id", "name"), "id=$mainListId", null, null, null, null)
        if (checkCursor.count == 0) {
            AppLogger.e("$TAG: 源数据库中找不到mainListId=$mainListId")
        } else {
            checkCursor.moveToFirst()
            AppLogger.i("$TAG: 找到项目 id=$mainListId, name=${checkCursor.getString(1)}")
        }
        checkCursor.close()

        // 2. 按表导入数据
        var totalRows = 0

        // main_lists: 只导出id=mainListId的1条
        totalRows += copyFilteredRows(
            sourceDb, destDb, "main_lists",
            "id = $mainListId"
        )

        // sub_lists: WHERE parentListId=mainListId
        totalRows += copyFilteredRows(
            sourceDb, destDb, "sub_lists",
            "parentListId = $mainListId"
        )

        // content_configs: WHERE parentListId=mainListId
        totalRows += copyFilteredRows(
            sourceDb, destDb, "content_configs",
            "parentListId = $mainListId"
        )

        // option_buttons: WHERE parentListId=mainListId
        totalRows += copyFilteredRows(
            sourceDb, destDb, "option_buttons",
            "parentListId = $mainListId"
        )

        // rich_texts: WHERE parentListId=mainListId
        totalRows += copyFilteredRows(
            sourceDb, destDb, "rich_texts",
            "parentListId = $mainListId"
        )

        // mainline_branches: WHERE parentListId=mainListId
        val branchIds = mutableListOf<Long>()
        totalRows += copyFilteredRowsAndCollectIds(
            sourceDb, destDb, "mainline_branches",
            "parentListId = $mainListId",
            "id", branchIds
        )

        // mainline_items: WHERE branchId IN (branchIds)
        if (branchIds.isNotEmpty()) {
            val branchIdsStr = branchIds.joinToString(",")
            totalRows += copyFilteredRows(
                sourceDb, destDb, "mainline_items",
                "branchId IN ($branchIdsStr)"
            )
        }

        // quiz_groups: WHERE parentListId=mainListId
        val groupIds = mutableListOf<Long>()
        totalRows += copyFilteredRowsAndCollectIds(
            sourceDb, destDb, "quiz_groups",
            "parentListId = $mainListId",
            "id", groupIds
        )

        // quiz_cards: WHERE groupId IN (groupIds)
        if (groupIds.isNotEmpty()) {
            val groupIdsStr = groupIds.joinToString(",")
            totalRows += copyFilteredRows(
                sourceDb, destDb, "quiz_cards",
                "groupId IN ($groupIdsStr)"
            )
        }

        // link_history: 通过path字段中的JSON匹配mainListId
        // path格式: [{"id":1,"name":"释放法"},{"id":2,"name":"路标1"}]
        // 精确匹配 '"id":mainListId,' 或 '"id":mainListId}'
        totalRows += copyFilteredRows(
            sourceDb, destDb, "link_history",
            "(path LIKE '%\"id\":$mainListId,%' OR path LIKE '%\"id\":$mainListId}%')"
        )

        AppLogger.i("$TAG: 共导出 $totalRows 行数据")
    }

    /**
     * 从源数据库获取表的CREATE TABLE语句
     */
    private fun getCreateTableSql(db: SQLiteDatabase, tableName: String): String? {
        val cursor = db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * 从源数据库复制满足条件的行到目标数据库
     * 数据的ID保持原样不变
     */
    private fun copyFilteredRows(
        sourceDb: SQLiteDatabase,
        destDb: SQLiteDatabase,
        tableName: String,
        whereClause: String,
    ): Int {
        // 检查源表是否存在
        if (!tableExists(sourceDb, tableName)) return 0

        val cursor = sourceDb.query(tableName, null, whereClause, null, null, null, null)
        var count = 0

        return try {
            if (cursor.count == 0) {
                AppLogger.d("$TAG: 表 $tableName 无匹配数据 ($whereClause)")
                return 0
            }

            val columnNames = cursor.columnNames
            destDb.beginTransaction()
            try {
                while (cursor.moveToNext()) {
                    val values = ContentValues()
                    for (col in columnNames) {
                        val colIndex = cursor.getColumnIndex(col)
                        if (colIndex < 0) continue

                        when (cursor.getType(colIndex)) {
                            Cursor.FIELD_TYPE_NULL -> values.putNull(col)
                            Cursor.FIELD_TYPE_INTEGER -> values.put(col, cursor.getLong(colIndex))
                            Cursor.FIELD_TYPE_FLOAT -> values.put(col, cursor.getDouble(colIndex))
                            Cursor.FIELD_TYPE_BLOB -> values.put(col, cursor.getBlob(colIndex))
                            Cursor.FIELD_TYPE_STRING -> values.put(col, cursor.getString(colIndex))
                        }
                    }
                    destDb.insert(tableName, null, values)
                    count++
                }
                destDb.setTransactionSuccessful()
            } finally {
                destDb.endTransaction()
            }

            AppLogger.d("$TAG: 表 $tableName 导出 $count 行")
            count
        } finally {
            cursor.close()
        }
    }

    /**
     * 复制满足条件的行，同时收集指定id列的值
     */
    private fun copyFilteredRowsAndCollectIds(
        sourceDb: SQLiteDatabase,
        destDb: SQLiteDatabase,
        tableName: String,
        whereClause: String,
        idColumnName: String,
        idCollection: MutableList<Long>,
    ): Int {
        if (!tableExists(sourceDb, tableName)) return 0

        val cursor = sourceDb.query(tableName, null, whereClause, null, null, null, null)
        var count = 0

        return try {
            if (cursor.count == 0) {
                AppLogger.d("$TAG: 表 $tableName 无匹配数据 ($whereClause)")
                return 0
            }

            val columnNames = cursor.columnNames
            val idColIndex = cursor.getColumnIndex(idColumnName)

            destDb.beginTransaction()
            try {
                while (cursor.moveToNext()) {
                    // 收集ID
                    if (idColIndex >= 0) {
                        idCollection.add(cursor.getLong(idColIndex))
                    }

                    val values = ContentValues()
                    for (col in columnNames) {
                        val colIndex = cursor.getColumnIndex(col)
                        if (colIndex < 0) continue

                        when (cursor.getType(colIndex)) {
                            Cursor.FIELD_TYPE_NULL -> values.putNull(col)
                            Cursor.FIELD_TYPE_INTEGER -> values.put(col, cursor.getLong(colIndex))
                            Cursor.FIELD_TYPE_FLOAT -> values.put(col, cursor.getDouble(colIndex))
                            Cursor.FIELD_TYPE_BLOB -> values.put(col, cursor.getBlob(colIndex))
                            Cursor.FIELD_TYPE_STRING -> values.put(col, cursor.getString(colIndex))
                        }
                    }
                    destDb.insert(tableName, null, values)
                    count++
                }
                destDb.setTransactionSuccessful()
            } finally {
                destDb.endTransaction()
            }

            AppLogger.d("$TAG: 表 $tableName 导出 $count 行, 收集${idCollection.size}个ID")
            count
        } finally {
            cursor.close()
        }
    }

    /**
     * 检查表是否存在
     */
    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        return try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    /**
     * 写入room_master_table，让Room能识别导出的数据库
     */
    private fun insertRoomMasterTable(destDb: SQLiteDatabase) {
        destDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `room_master_table` (`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)"
        )
        destDb.execSQL(
            "INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) VALUES (42, 'placeholder')"
        )
    }
}
