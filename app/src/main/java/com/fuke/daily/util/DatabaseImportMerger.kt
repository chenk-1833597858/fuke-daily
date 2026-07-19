package com.fuke.daily.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File

/**
 * 数据库导入合并工具类
 *
 * 职责：
 * 1. 校验导入数据库的表结构是否与当前数据库匹配（表名+字段名相似度）
 * 2. 校验通过后以追加方式合并数据（ID重新分配，外键关系维护）
 * 3. 忽略导入数据库有但当前数据库没有的字段
 *
 * 合并策略：复制当前数据库到临时文件，在临时文件上合并，最后替换原数据库
 */
object DatabaseImportMerger {

    private const val TAG = "DatabaseImportMerger"

    // 按依赖顺序排列，先导入被依赖的表
    private val TABLE_IMPORT_ORDER = listOf(
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
        "timers",
    )

    // 外键映射：字段名 → 引用的表名
    private val TABLE_FOREIGN_KEYS = mapOf(
        "sub_lists" to mapOf("parentListId" to "main_lists"),
        "content_configs" to mapOf("subListId" to "sub_lists", "parentListId" to "main_lists"),
        "option_buttons" to mapOf("subListId" to "sub_lists", "parentListId" to "main_lists"),
        "rich_texts" to mapOf("parentListId" to "main_lists"),
        "mainline_branches" to mapOf("parentListId" to "main_lists"),
        "mainline_items" to mapOf("branchId" to "mainline_branches"),
        "quiz_groups" to mapOf("parentListId" to "main_lists"),
        "quiz_cards" to mapOf("groupId" to "quiz_groups"),
        "timers" to mapOf("linkedProjectId" to "main_lists"),
    )

    private val REQUIRED_CORE_TABLES = listOf("main_lists", "sub_lists")

    private const val TEMP_IMPORT_DB = "import_temp_db"
    private const val TEMP_MERGE_DB = "merge_temp_db"

    data class ValidationResult(
        val similarity: Float,
        val matchedTables: List<String>,
        val missingTables: List<String>,
        val fieldMatchRatio: Float,
        val hasMainline: Boolean = false,  // 导入文件是否包含人生主线
    )

    data class MergeResult(
        val success: Boolean,
        val importedCounts: Map<String, Int>,
        val error: String? = null,
        val idMappings: Map<String, Map<Long, Long>> = emptyMap(),  // 表名 → 旧ID→新ID
    )

    /**
     * 校验导入数据库的结构相似度
     */
    fun validateSchema(context: Context, importUri: Uri): ValidationResult {
        val currentSchema = getSchemaFromDb(context, "fuke-daily-db")
        val importSchema = getSchemaFromUri(context, importUri)

        if (importSchema.isEmpty()) {
            AppLogger.e("导入：导入数据库结构为空")
            return ValidationResult(0f, emptyList(), TABLE_IMPORT_ORDER, 0f)
        }

        val importTableNames = importSchema.keys
        val matchedTables = TABLE_IMPORT_ORDER.filter { it in importTableNames }
        val missingTables = TABLE_IMPORT_ORDER.filter { it !in importTableNames }

        // 核心表缺失 → 归零
        if (REQUIRED_CORE_TABLES.any { it !in importTableNames }) {
            return ValidationResult(0f, matchedTables, missingTables, 0f)
        }

        // 字段匹配比例
        var totalImportFields = 0
        var totalMatchedFields = 0
        // 检查导入文件的所有字段是否都是当前数据库的子集
        var allFieldsSubset = true
        for (tableName in matchedTables) {
            val currentFields = currentSchema[tableName] ?: emptyList()
            val importFields = importSchema[tableName] ?: emptyList()
            totalImportFields += importFields.size
            totalMatchedFields += importFields.count { it in currentFields.toSet() }
            // 如果导入文件有当前数据库没有的字段，就不是子集
            if (importFields.any { it !in currentFields.toSet() }) {
                allFieldsSubset = false
            }
        }
        val fieldMatchRatio = if (totalImportFields > 0) totalMatchedFields.toFloat() / totalImportFields else 0f

        val tableMatchRatio = matchedTables.size.toFloat() / TABLE_IMPORT_ORDER.size
        var similarity = tableMatchRatio * 0.4f + fieldMatchRatio * 0.6f

        // 子集兼容：导入文件的所有字段都在当前数据库中存在
        // 即使表不全（单项目导出），只要字段是子集就认为兼容
        if (allFieldsSubset && fieldMatchRatio == 1.0f) {
            similarity = 1.0f  // 完全兼容
            AppLogger.i("导入：字段子集兼容，导入文件是当前数据库的子集")
        }

        // 检查导入文件是否包含人生主线
        var hasMainline = false
        try {
            val tempDbFile = context.getDatabasePath(TEMP_IMPORT_DB)
            if (tempDbFile.exists()) {
                val db = SQLiteDatabase.openDatabase(tempDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.query("main_lists", arrayOf("id"), "type='MAINLINE'", null, null, null, null)
                hasMainline = cursor.count > 0
                cursor.close()
                db.close()
            }
        } catch (e: Exception) {
            AppLogger.w("导入：检查人生主线失败: ${e.message}")
        }

        AppLogger.d("导入：校验结果 相似度=${(similarity * 100).toInt()}% 表匹配=${(tableMatchRatio * 100).toInt()}% 字段匹配=${(fieldMatchRatio * 100).toInt()}% 人生主线=$hasMainline")
        // 清理临时文件
        deleteDbFiles(context, TEMP_IMPORT_DB)
        return ValidationResult(similarity, matchedTables, missingTables, fieldMatchRatio, hasMainline)
    }

    /**
     * 合并导入数据
     *
     * 流程：复制当前数据库 → 在副本上合并 → 替换原数据库
     */
    fun mergeData(context: Context, importUri: Uri, overwriteMainline: Boolean = true): MergeResult {
        val importedCounts = mutableMapOf<String, Int>()
        val idMappings = mutableMapOf<String, MutableMap<Long, Long>>()
        var importDb: SQLiteDatabase? = null
        var mergeDb: SQLiteDatabase? = null

        try {
            AppLogger.d("导入：开始合并数据")

            // 1. 复制导入文件到临时路径
            copyUriToDbFile(context, importUri, TEMP_IMPORT_DB)
            AppLogger.d("导入：导入文件复制完成")

            // 2. 复制当前数据库到合并临时文件
            val currentDbFile = context.getDatabasePath("fuke-daily-db")
            val mergeDbFile = context.getDatabasePath(TEMP_MERGE_DB)
            deleteDbFiles(context, TEMP_MERGE_DB)
            currentDbFile.copyTo(mergeDbFile, overwrite = true)
            // wal和shm也复制（如果存在）
            File(currentDbFile.absolutePath + "-wal").let { if (it.exists()) it.copyTo(File(mergeDbFile.absolutePath + "-wal"), overwrite = true) }
            File(currentDbFile.absolutePath + "-shm").let { if (it.exists()) it.copyTo(File(mergeDbFile.absolutePath + "-shm"), overwrite = true) }
            AppLogger.d("导入：当前数据库副本创建完成")

            // 3. 打开导入数据库（只读）
            importDb = SQLiteDatabase.openDatabase(
                context.getDatabasePath(TEMP_IMPORT_DB).absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            AppLogger.d("导入：导入数据库打开成功")

            // 4. 打开合并副本数据库（读写）
            mergeDb = SQLiteDatabase.openDatabase(
                mergeDbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            )
            AppLogger.d("导入：合并数据库打开成功")

            // 5. checkpoint合并副本
            mergeDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToNext() }

            // 6. 获取合并副本的字段信息
            val currentSchema = mutableMapOf<String, List<String>>()
            for (tableName in TABLE_IMPORT_ORDER) {
                currentSchema[tableName] = getTableColumns(mergeDb, tableName)
            }

            // 6.5 处理人生主线冲突
            if (overwriteMainline) {
                // 覆盖模式：先删掉合并副本（当前数据库）里的人生主线
                val cursor = mergeDb.query("main_lists", arrayOf("id"), "type='MAINLINE'", null, null, null, null)
                val mainlineIds = mutableListOf<Long>()
                while (cursor.moveToNext()) { mainlineIds.add(cursor.getLong(0)) }
                cursor.close()
                if (mainlineIds.isNotEmpty()) {
                    val idsStr = mainlineIds.joinToString(",")
                    AppLogger.i("导入：覆盖人生主线, 删除当前ids=$idsStr")
                    mergeDb.delete("mainline_items", "branchId IN (SELECT id FROM mainline_branches WHERE parentListId IN ($idsStr))", null)
                    mergeDb.delete("mainline_branches", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("sub_lists", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("content_configs", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("option_buttons", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("rich_texts", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("quiz_cards", "groupId IN (SELECT id FROM quiz_groups WHERE parentListId IN ($idsStr))", null)
                    mergeDb.delete("quiz_groups", "parentListId IN ($idsStr)", null)
                    mergeDb.delete("main_lists", "type='MAINLINE'", null)
                }
            }

            // 7. 逐表合并
            // 如果不覆盖人生主线，需要收集导入文件中人生主线的ID，用于过滤关联表
            var skipMainlineIds: Set<Long>? = null
            if (!overwriteMainline) {
                val cursor = importDb.query("main_lists", arrayOf("id"), "type='MAINLINE'", null, null, null, null)
                val ids = mutableSetOf<Long>()
                while (cursor.moveToNext()) { ids.add(cursor.getLong(0)) }
                cursor.close()
                skipMainlineIds = ids
                if (ids.isNotEmpty()) {
                    AppLogger.i("导入：跳过人生主线, importMainlineIds=$ids")
                }
            }

            for (tableName in TABLE_IMPORT_ORDER) {
                val currentFields = currentSchema[tableName] ?: continue

                if (!tableExists(importDb, tableName)) {
                    AppLogger.d("导入：表$tableName 在导入库中不存在，跳过")
                    continue
                }

                val importFields = getTableColumns(importDb, tableName)
                val commonFields = importFields.filter { it in currentFields.toSet() }

                if (commonFields.isEmpty()) {
                    AppLogger.w("导入：表$tableName 无公共字段，跳过")
                    continue
                }

                AppLogger.d("导入：表$tableName 公共字段=${commonFields.size}/${importFields.size}")

                // 构建WHERE条件：跳过人生主线关联数据
                val whereClause = when {
                    !overwriteMainline && skipMainlineIds != null && skipMainlineIds.isNotEmpty() -> {
                        when (tableName) {
                            "main_lists" -> "type != 'MAINLINE'"
                            "sub_lists", "content_configs", "option_buttons", "rich_texts", "mainline_branches", "quiz_groups" ->
                                "parentListId NOT IN (${skipMainlineIds.joinToString(",")})"
                            "mainline_items" -> "branchId NOT IN (SELECT id FROM mainline_branches WHERE parentListId IN (${skipMainlineIds.joinToString(",")}))"
                            "quiz_cards" -> "groupId NOT IN (SELECT id FROM quiz_groups WHERE parentListId IN (${skipMainlineIds.joinToString(",")}))"
                            else -> null
                        }
                    }
                    else -> null
                }

                val cursor = if (whereClause != null) {
                    importDb.query(tableName, null, whereClause, null, null, null, null)
                } else {
                    importDb.query(tableName, null, null, null, null, null, null)
                }
                if (cursor.count == 0) {
                    cursor.close()
                    importedCounts[tableName] = 0
                    continue
                }

                val columnIndexMap = mutableMapOf<String, Int>()
                for (field in commonFields) {
                    columnIndexMap[field] = cursor.getColumnIndex(field)
                }

                val foreignKeys = TABLE_FOREIGN_KEYS[tableName] ?: emptyMap()
                var insertedCount = 0
                var errorCount = 0

                cursor.moveToFirst()
                do {
                    try {
                        val values = ContentValues()
                        for (field in commonFields) {
                            val colIdx = columnIndexMap[field] ?: continue
                            if (colIdx < 0) continue

                            if (field in foreignKeys) {
                                val refTable = foreignKeys[field]!!
                                val oldRefId = cursor.getLong(colIdx)
                                if (oldRefId == 0L) {
                                    values.put(field, 0L)
                                } else {
                                    val newRefId = idMappings[refTable]?.get(oldRefId) ?: 0L
                                    values.put(field, newRefId)
                                }
                            } else if (field == "id") {
                                continue
                            } else {
                                putCursorValue(cursor, colIdx, field, values)
                            }
                        }

                        val newId = mergeDb.insert(tableName, null, values)

                        val idIdx = columnIndexMap["id"]
                        if (idIdx != null && idIdx >= 0) {
                            val oldId = cursor.getLong(idIdx)
                            if (oldId > 0 && newId > 0) {
                                idMappings.getOrPut(tableName) { mutableMapOf() }[oldId] = newId
                            }
                        }

                        insertedCount++
                    } catch (e: Exception) {
                        errorCount++
                        if (errorCount <= 3) {
                            AppLogger.e("导入：表$tableName 插入行失败: ${e.message}")
                        }
                    }
                } while (cursor.moveToNext())

                cursor.close()
                importedCounts[tableName] = insertedCount
                AppLogger.d("导入：表$tableName 导入 $insertedCount 条，失败 $errorCount 条")
            }

            // 8. checkpoint合并副本
            mergeDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToNext() }

            // 9. 关闭所有数据库
            importDb.close()
            importDb = null
            mergeDb.close()
            mergeDb = null

            // 10. 用合并副本替换当前数据库
            // 先删除原数据库的wal/shm
            File(currentDbFile.absolutePath + "-wal").delete()
            File(currentDbFile.absolutePath + "-shm").delete()
            // 用合并副本替换
            mergeDbFile.copyTo(currentDbFile, overwrite = true)
            // 删除合并副本
            deleteDbFiles(context, TEMP_MERGE_DB)
            // 删除导入临时文件
            deleteDbFiles(context, TEMP_IMPORT_DB)

            AppLogger.d("导入：合并完成 ${importedCounts.entries.filter { it.value > 0 }.joinToString { "${it.key}=${it.value}" }}")
            AppLogger.d("导入：ID映射 sub_lists=${idMappings["sub_lists"]}")
            return MergeResult(true, importedCounts, idMappings = idMappings)

        } catch (e: Exception) {
            AppLogger.e("导入：合并失败 ${e.message}")
            return MergeResult(false, importedCounts, e.message)
        } finally {
            try { importDb?.close() } catch (_: Exception) {}
            try { mergeDb?.close() } catch (_: Exception) {}
            try { deleteDbFiles(context, TEMP_IMPORT_DB) } catch (_: Exception) {}
            try { deleteDbFiles(context, TEMP_MERGE_DB) } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════

    /**
     * 从指定数据库名获取表结构（直接用SQLiteDatabase打开，不走Room）
     */
    private fun getSchemaFromDb(context: Context, dbName: String): Map<String, List<String>> {
        val schema = mutableMapOf<String, List<String>>()
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return schema

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        for (tableName in TABLE_IMPORT_ORDER) {
            if (tableExists(db, tableName)) {
                schema[tableName] = getTableColumns(db, tableName)
            }
        }
        db.close()
        return schema
    }

    /**
     * 从URI获取导入数据库的表结构
     */
    private fun getSchemaFromUri(context: Context, importUri: Uri): Map<String, List<String>> {
        val schema = mutableMapOf<String, List<String>>()
        var db: SQLiteDatabase? = null

        try {
            copyUriToDbFile(context, importUri, TEMP_IMPORT_DB)
            val dbFile = context.getDatabasePath(TEMP_IMPORT_DB)
            AppLogger.d("导入：临时数据库路径=${dbFile.absolutePath} 存在=${dbFile.exists()} 大小=${dbFile.length()}")

            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            AppLogger.d("导入：临时数据库打开成功")

            for (tableName in TABLE_IMPORT_ORDER) {
                if (tableExists(db, tableName)) {
                    schema[tableName] = getTableColumns(db, tableName)
                }
            }
            AppLogger.d("导入：导入数据库表=${schema.keys.toList()}")
        } catch (e: Exception) {
            AppLogger.e("导入：读取导入数据库结构失败: ${e.message}")
        } finally {
            try { db?.close() } catch (_: Exception) {}
            // 不删临时文件，让validateSchema检查人生主线后再删
        }

        return schema
    }

    private fun copyUriToDbFile(context: Context, uri: Uri, dbName: String) {
        deleteDbFiles(context, dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        context.contentResolver.openInputStream(uri)?.use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("无法读取导入文件")

        AppLogger.d("导入：复制文件大小=${dbFile.length()} bytes")
    }

    private fun deleteDbFiles(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.query("sqlite_master", arrayOf("name"), "type='table' AND name=?", arrayOf(tableName), null, null, null)
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun getTableColumns(db: SQLiteDatabase, tableName: String): List<String> {
        val columns = mutableListOf<String>()
        val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        cursor.close()
        return columns
    }

    private fun putCursorValue(cursor: Cursor, colIdx: Int, fieldName: String, values: ContentValues) {
        when (cursor.getType(colIdx)) {
            Cursor.FIELD_TYPE_NULL -> values.putNull(fieldName)
            Cursor.FIELD_TYPE_INTEGER -> values.put(fieldName, cursor.getLong(colIdx))
            Cursor.FIELD_TYPE_FLOAT -> values.put(fieldName, cursor.getDouble(colIdx))
            Cursor.FIELD_TYPE_STRING -> values.put(fieldName, cursor.getString(colIdx))
            Cursor.FIELD_TYPE_BLOB -> values.put(fieldName, cursor.getBlob(colIdx))
        }
    }
}
