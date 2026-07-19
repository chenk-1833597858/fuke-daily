package com.fuke.daily.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipInputStream

/**
 * .fuke 备份导入工具
 *
 * 负责解压 .fuke 文件，校验数据库，合并数据，还原图片
 */
object FukeBackupImporter {

    private const val TAG = "FukeBackupImporter"
    private const val DB_ENTRY_NAME = "data.db"
    private const val IMAGES_DIR = "images"
    private const val TEMP_EXTRACT_DIR = "fuke_import_extract"
    private const val TEMP_IMPORT_DB = "fuke_import_db"

    /**
     * 解压后的备份数据
     */
    data class ExtractedBackup(
        val dataDb: File,
        val imagesDir: File?,
        val hasMainline: Boolean,
    )

    /**
     * 校验结果（复用 DatabaseImportMerger.ValidationResult）
     */
    data class ValidationResult(
        val similarity: Float,
        val matchedTables: List<String>,
        val missingTables: List<String>,
        val fieldMatchRatio: Float,
        val hasMainline: Boolean,
    )

    /**
     * 解压 .fuke 文件
     *
     * @param context Context
     * @param fukeFile .fuke 文件
     * @return ExtractedBackup
     */
    fun extractBackup(context: Context, fukeFile: File): ExtractedBackup {
        AppLogger.i("$TAG: 开始解压 .fuke 文件, size=${fukeFile.length()}")

        // 清理旧临时目录
        val extractDir = File(context.cacheDir, TEMP_EXTRACT_DIR)
        if (extractDir.exists()) extractDir.deleteRecursively()
        extractDir.mkdirs()

        try {
            // 解压zip
            ZipInputStream(fukeFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(extractDir, entry.name)
                    // 安全检查：防止zip路径遍历攻击
                    if (!outFile.canonicalPath.startsWith(extractDir.canonicalPath)) {
                        AppLogger.w("$TAG: 跳过可疑路径: ${entry.name}")
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 校验 data.db 存在
            val dataDb = File(extractDir, DB_ENTRY_NAME)
            if (!dataDb.exists()) {
                throw IllegalStateException("备份文件中缺少 $DB_ENTRY_NAME")
            }

            // 检查是否包含人生主线
            var hasMainline = false
            try {
                val db = SQLiteDatabase.openDatabase(
                    dataDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                val cursor = db.query("main_lists", arrayOf("id"), "type='MAINLINE'", null, null, null, null)
                hasMainline = cursor.count > 0
                cursor.close()
                db.close()
            } catch (e: Exception) {
                AppLogger.w("$TAG: 检查人生主线失败: ${e.message}")
            }

            // 检查 images 目录
            val imagesDir = File(extractDir, IMAGES_DIR)
            val validImagesDir = if (imagesDir.exists() && imagesDir.isDirectory) imagesDir else null

            AppLogger.i("$TAG: 解压完成, hasMainline=$hasMainline, hasImages=${validImagesDir != null}")
            return ExtractedBackup(dataDb, validImagesDir, hasMainline)
        } catch (e: Exception) {
            // 解压失败，清理
            extractDir.deleteRecursively()
            throw e
        }
    }

    /**
     * 校验 .fuke 备份文件
     *
     * @param context Context
     * @param fukeFile .fuke 文件
     * @return ValidationResult
     */
    fun validateBackup(context: Context, fukeFile: File): ValidationResult {
        AppLogger.i("$TAG: 开始校验 .fuke 备份")

        val extracted = extractBackup(context, fukeFile)

        try {
            // 使用 DatabaseImportMerger 的校验逻辑
            // 先把 data.db 复制到临时导入路径
            val tempImportDb = context.getDatabasePath(TEMP_IMPORT_DB)
            deleteDbFiles(context, TEMP_IMPORT_DB)
            extracted.dataDb.copyTo(tempImportDb, overwrite = true)

            // 获取当前数据库schema
            val currentSchema = getSchemaFromDb(context, "fuke-daily-db")
            val importSchema = getSchemaFromFile(extracted.dataDb)

            if (importSchema.isEmpty()) {
                AppLogger.e("$TAG: 导入数据库结构为空")
                return ValidationResult(0f, emptyList(), emptyList(), 0f, extracted.hasMainline)
            }

            val importTableNames = importSchema.keys
            val tableImportOrder = listOf(
                "main_lists", "sub_lists", "content_configs", "option_buttons",
                "rich_texts", "mainline_branches", "mainline_items", "quiz_groups",
                "quiz_cards", "link_history", "timers",
            )
            val requiredCoreTables = listOf("main_lists", "sub_lists")

            val matchedTables = tableImportOrder.filter { it in importTableNames }
            val missingTables = tableImportOrder.filter { it !in importTableNames }

            // 核心表缺失 → 归零
            if (requiredCoreTables.any { it !in importTableNames }) {
                return ValidationResult(0f, matchedTables, missingTables, 0f, extracted.hasMainline)
            }

            // 字段匹配比例
            var totalImportFields = 0
            var totalMatchedFields = 0
            var allFieldsSubset = true
            for (tableName in matchedTables) {
                val currentFields = currentSchema[tableName] ?: emptyList()
                val importFields = importSchema[tableName] ?: emptyList()
                totalImportFields += importFields.size
                totalMatchedFields += importFields.count { it in currentFields.toSet() }
                if (importFields.any { it !in currentFields.toSet() }) {
                    allFieldsSubset = false
                }
            }
            val fieldMatchRatio = if (totalImportFields > 0) totalMatchedFields.toFloat() / totalImportFields else 0f

            val tableMatchRatio = matchedTables.size.toFloat() / tableImportOrder.size
            var similarity = tableMatchRatio * 0.4f + fieldMatchRatio * 0.6f

            if (allFieldsSubset && fieldMatchRatio == 1.0f) {
                similarity = 1.0f
                AppLogger.i("$TAG: 字段子集兼容")
            }

            AppLogger.d("$TAG: 校验结果 相似度=${(similarity * 100).toInt()}% 人生主线=${extracted.hasMainline}")
            return ValidationResult(similarity, matchedTables, missingTables, fieldMatchRatio, extracted.hasMainline)
        } finally {
            // 清理临时导入db
            deleteDbFiles(context, TEMP_IMPORT_DB)
        }
    }

    /**
     * 导入 .fuke 备份
     *
     * @param context Context
     * @param extracted 已解压的备份数据
     * @param overwriteMainline 是否覆盖人生主线
     * @return DatabaseImportMerger.MergeResult
     */
    fun importBackup(
        context: Context,
        extracted: ExtractedBackup,
        overwriteMainline: Boolean
    ): DatabaseImportMerger.MergeResult {
        AppLogger.i("$TAG: 开始导入 .fuke 备份, overwriteMainline=$overwriteMainline")

        // 1. 把 data.db 复制到临时导入路径，用 Uri 传给 DatabaseImportMerger
        val tempImportDb = context.getDatabasePath(TEMP_IMPORT_DB)
        deleteDbFiles(context, TEMP_IMPORT_DB)
        extracted.dataDb.copyTo(tempImportDb, overwrite = true)

        // 构造一个 file:// URI 传给 mergeData
        val importUri = Uri.fromFile(tempImportDb)

        // 2. 调用 DatabaseImportMerger 合并数据
        val mergeResult = DatabaseImportMerger.mergeData(context, importUri, overwriteMainline)

        if (!mergeResult.success) {
            AppLogger.e("$TAG: 数据合并失败: ${mergeResult.error}")
            cleanupExtract(context)
            return mergeResult
        }

        // 3. 合并成功后，还原图片（用ID映射找到正确的subListId）
        if (extracted.imagesDir != null && extracted.imagesDir.exists()) {
            try {
                restoreImages(context, extracted.imagesDir, mergeResult.idMappings)
            } catch (e: Exception) {
                AppLogger.e("$TAG: 图片还原失败（不影响数据）: ${e.message}")
            }
        }

        // 4. 清理
        cleanupExtract(context)
        deleteDbFiles(context, TEMP_IMPORT_DB)

        AppLogger.i("$TAG: 导入完成")
        return mergeResult
    }

    /**
     * 还原图片：将 images/ 里的图片复制到 App 内部存储，并更新数据库中的 URI
     *
     * 新规范图片文件名（Fuker_开头）自带唯一性，直接复制不重命名
     * 旧命名图片也直接复制，冲突则覆盖（用户量极少，可忽略）
     */
    private fun restoreImages(context: Context, imagesDir: File, idMappings: Map<String, Map<Long, Long>>) {
        val imageFiles = imagesDir.listFiles()?.filter { it.isFile } ?: return
        if (imageFiles.isEmpty()) {
            AppLogger.d("$TAG: 无图片需要还原")
            return
        }

        // 确保目标目录存在
        val appImagesDir = File(context.filesDir, "images")
        if (!appImagesDir.exists()) appImagesDir.mkdirs()

        // 1. 复制所有图片到 App 内部存储（文件名不变）
        val fileNameToNewPath = mutableMapOf<String, String>()
        for (imageFile in imageFiles) {
            val fileName = imageFile.name
            val newFile = File(appImagesDir, fileName)
            // 如果目标已存在且内容相同则跳过，否则覆盖
            if (!newFile.exists() || newFile.length() != imageFile.length()) {
                imageFile.copyTo(newFile, overwrite = true)
            }
            fileNameToNewPath[fileName] = newFile.absolutePath
            AppLogger.d("$TAG: 还原图片 $fileName → ${newFile.absolutePath}")
        }

        // 2. 更新数据库中的 imageUris
        // 导入的db里imageUris存的是旧路径（如 /data/user/0/.../images/旧名.jpg）
        // 需要把旧路径替换为新路径
        val dbFile = context.getDatabasePath("fuke-daily-db")
        if (!dbFile.exists()) {
            AppLogger.w("$TAG: 数据库不存在，无法更新图片URI")
            return
        }

        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
        )

        try {
            // 构建旧文件名→新路径的映射，用于替换URI中的旧路径
            // 同时也构建旧绝对路径→新绝对路径的映射
            val oldPathToNewPath = mutableMapOf<String, String>()
            for ((fileName, newPath) in fileNameToNewPath) {
                // 匹配各种可能的旧路径格式
                oldPathToNewPath[fileName] = newPath
                oldPathToNewPath["/data/user/0/com.fuke.daily/files/images/$fileName"] = newPath
                oldPathToNewPath["file:///data/user/0/com.fuke.daily/files/images/$fileName"] = newPath
            }

            // 查询所有 sub_lists 的 imageUris
            val cursor = db.query("sub_lists", arrayOf("id", "imageUris", "imageUri"), null, null, null, null, null)
            var updatedCount = 0

            db.beginTransaction()
            try {
                val idIndex = cursor.getColumnIndex("id")
                val imageUrisIndex = cursor.getColumnIndex("imageUris")
                val imageUriIndex = cursor.getColumnIndex("imageUri")

                while (cursor.moveToNext()) {
                    val subListId = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                    var changed = false

                    // 处理 imageUris（JSON数组）
                    if (imageUrisIndex >= 0) {
                        val imageUrisStr = cursor.getString(imageUrisIndex)
                        if (!imageUrisStr.isNullOrBlank() && imageUrisStr != "[]") {
                            try {
                                val array = JSONArray(imageUrisStr)
                                val newUris = mutableListOf<String>()
                                for (i in 0 until array.length()) {
                                    val uri = array.getString(i)
                                    // 尝试替换旧路径为新路径
                                    val newUri = replaceImagePath(uri, oldPathToNewPath)
                                    newUris.add(newUri)
                                    if (newUri != uri) changed = true
                                }
                                if (changed) {
                                    val jsonArray = JSONArray(newUris)
                                    db.execSQL(
                                        "UPDATE sub_lists SET imageUris = ? WHERE id = ?",
                                        arrayOf(jsonArray.toString(), subListId.toString())
                                    )
                                }
                            } catch (e: Exception) {
                                AppLogger.e("$TAG: 更新imageUris失败, subListId=$subListId: ${e.message}")
                            }
                        }
                    }

                    // 处理 imageUri（单张，已废弃但兼容）
                    if (imageUriIndex >= 0) {
                        val singleUri = cursor.getString(imageUriIndex)
                        if (!singleUri.isNullOrBlank()) {
                            val newUri = replaceImagePath(singleUri, oldPathToNewPath)
                            if (newUri != singleUri) {
                                db.execSQL(
                                    "UPDATE sub_lists SET imageUri = ? WHERE id = ?",
                                    arrayOf(newUri, subListId.toString())
                                )
                            }
                        }
                    }

                    if (changed) updatedCount++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                cursor.close()
            }

            AppLogger.i("$TAG: 图片还原完成, 复制${fileNameToNewPath.size}张, 更新${updatedCount}个subList的URI")
        } finally {
            db.close()
        }
    }

    /**
     * 替换URI中的旧图片路径为新路径
     */
    private fun replaceImagePath(uri: String, oldPathToNewPath: Map<String, String>): String {
        // 直接匹配完整路径
        oldPathToNewPath[uri]?.let { return it }

        // 从URI中提取文件名，匹配文件名
        val fileName = uri.substringAfterLast('/')
        oldPathToNewPath[fileName]?.let { newPath ->
            // 保持URI格式一致：如果原URI是绝对路径，返回绝对路径
            return if (uri.startsWith("/")) newPath else newPath
        }

        // 没有匹配，原样返回
        return uri
    }

    /**
     * 清理解压临时目录
     */
    fun cleanupExtract(context: Context) {
        val extractDir = File(context.cacheDir, TEMP_EXTRACT_DIR)
        if (extractDir.exists()) extractDir.deleteRecursively()
    }

    /**
     * 从数据库文件获取schema
     */
    private fun getSchemaFromDb(context: Context, dbName: String): Map<String, List<String>> {
        val schema = mutableMapOf<String, List<String>>()
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return schema

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val tableImportOrder = listOf(
            "main_lists", "sub_lists", "content_configs", "option_buttons",
            "rich_texts", "mainline_branches", "mainline_items", "quiz_groups",
            "quiz_cards", "link_history", "timers",
        )
        for (tableName in tableImportOrder) {
            if (tableExists(db, tableName)) {
                schema[tableName] = getTableColumns(db, tableName)
            }
        }
        db.close()
        return schema
    }

    /**
     * 从数据库文件获取schema
     */
    private fun getSchemaFromFile(dbFile: File): Map<String, List<String>> {
        val schema = mutableMapOf<String, List<String>>()
        if (!dbFile.exists()) return schema

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val tableImportOrder = listOf(
            "main_lists", "sub_lists", "content_configs", "option_buttons",
            "rich_texts", "mainline_branches", "mainline_items", "quiz_groups",
            "quiz_cards", "link_history", "timers",
        )
        for (tableName in tableImportOrder) {
            if (tableExists(db, tableName)) {
                schema[tableName] = getTableColumns(db, tableName)
            }
        }
        db.close()
        return schema
    }

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

    /**
     * 删除数据库相关文件（db, wal, shm）
     */
    private fun deleteDbFiles(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }
}
