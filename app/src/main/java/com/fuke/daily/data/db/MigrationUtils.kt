package com.fuke.daily.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移工具类
 * 提供通用的字段添加、表创建等方法
 */
object MigrationUtils {

    /**
     * 安全地添加字段（如果字段不存在）
     */
    fun addColumnIfNotExists(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String,
        defaultValue: String = ""
    ) {
        val defaultClause = if (defaultValue.isNotEmpty()) " DEFAULT $defaultValue" else ""
        try {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType$defaultClause")
        } catch (e: Exception) {
            // 字段已存在或其他错误，忽略
            android.util.Log.w("Migration", "添加字段 $tableName.$columnName 失败: ${e.message}")
        }
    }

    /**
     * 检查表是否存在
     */
    fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    /**
     * 检查字段是否存在
     */
    fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = db.query("PRAGMA table_info($tableName)")
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                cursor.close()
                return true
            }
        }
        cursor.close()
        return false
    }
}
