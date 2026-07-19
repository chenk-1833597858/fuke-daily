package com.fuke.daily.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * .fuke 备份导出工具
 *
 * 负责将数据库 + 图片打包成 .fuke 压缩包（zip格式）
 * 压缩包结构：
 *   data.db       — Room数据库文件
 *   images/       — 图片目录
 *     {subListId}_{序号}.{ext}
 */
object FukeBackupExporter {

    private const val TAG = "FukeBackupExporter"
    private const val DB_ENTRY_NAME = "data.db"
    private const val IMAGES_DIR = "images"

    /**
     * 整体导出：将全部数据（数据库+图片）打包成 .fuke 文件
     *
     * 前置条件：调用方已关闭Room数据库（closeDatabaseForCheckpoint）并等待WAL刷入
     *
     * @param context Context
     * @param excludeMainline 是否排除人生主线数据
     * @return .fuke 临时文件（调用方负责写入SAF后删除）
     */
    suspend fun exportFullBackup(context: Context, excludeMainline: Boolean): File {
        AppLogger.i("$TAG: 开始整体导出, excludeMainline=$excludeMainline")

        val dbPath = context.getDatabasePath("fuke-daily-db").absolutePath
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            throw IllegalStateException("数据库文件不存在: $dbPath")
        }

        // 创建临时目录
        val tempDir = File(context.cacheDir, "fuke_export_full_${System.currentTimeMillis()}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            // 1. 复制主db文件到临时目录（只复制主文件，不带wal/shm）
            val tempDbFile = File(tempDir, DB_ENTRY_NAME)
            dbFile.copyTo(tempDbFile, overwrite = true)
            // 注意：不要复制wal/shm文件，关闭数据库后数据已在主文件中

            // 2. 如果排除人生主线，在副本中删除相关数据
            if (excludeMainline) {
                removeMainlineData(tempDbFile)
                // 删除removeMainlineData产生的wal/shm文件
                File(tempDir, "$DB_ENTRY_NAME-wal").delete()
                File(tempDir, "$DB_ENTRY_NAME-shm").delete()
            }

            // 3. 收集数据库中所有 imageUris，复制图片
            val imagesDir = File(tempDir, IMAGES_DIR)
            imagesDir.mkdirs()
            val imageCount = collectAndCopyImages(context, tempDbFile, imagesDir, null)
            AppLogger.i("$TAG: 整体导出收集 $imageCount 张图片")

            // 4. 打包成 .fuke
            val fukeFile = File(context.cacheDir, "fuke_full_backup_${System.currentTimeMillis()}.fuke")
            zipDirectory(tempDir, fukeFile)

            AppLogger.i("$TAG: 整体导出完成, 文件大小=${fukeFile.length()}字节")
            return fukeFile
        } finally {
            // 清理临时目录
            tempDir.deleteRecursively()
        }
    }

    /**
     * 单项目导出：将指定项目数据（数据库+图片）打包成 .fuke 文件
     *
     * 前置条件：调用方已关闭Room数据库（closeDatabaseForCheckpoint）并等待WAL刷入
     *
     * @param context Context
     * @param mainListId 要导出的项目ID
     * @return .fuke 临时文件（调用方负责写入SAF后删除）
     */
    suspend fun exportProjectBackup(context: Context, mainListId: Long): File {
        AppLogger.i("$TAG: 开始项目导出, mainListId=$mainListId")

        // 创建临时目录
        val tempDir = File(context.cacheDir, "fuke_export_project_${System.currentTimeMillis()}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            // 1. 用 ProjectExporter 创建临时db
            val projectDbFile = ProjectExporter.exportProject(context, mainListId)

            // 2. 将临时db移到导出目录
            val tempDbFile = File(tempDir, DB_ENTRY_NAME)
            projectDbFile.copyTo(tempDbFile, overwrite = true)
            // 删除 ProjectExporter 创建的临时文件
            projectDbFile.delete()
            // 清理可能的wal/shm
            File(tempDir, "$DB_ENTRY_NAME-wal").delete()
            File(tempDir, "$DB_ENTRY_NAME-shm").delete()

            // 3. 收集该项目相关的 imageUris，复制图片
            val imagesDir = File(tempDir, IMAGES_DIR)
            imagesDir.mkdirs()
            val imageCount = collectAndCopyImages(context, tempDbFile, imagesDir, mainListId)
            AppLogger.i("$TAG: 项目导出收集 $imageCount 张图片")

            // 4. 打包成 .fuke
            val fukeFile = File(context.cacheDir, "fuke_project_backup_${System.currentTimeMillis()}.fuke")
            zipDirectory(tempDir, fukeFile)

            AppLogger.i("$TAG: 项目导出完成, 文件大小=${fukeFile.length()}字节")
            return fukeFile
        } finally {
            // 清理临时目录
            tempDir.deleteRecursively()
        }
    }

    /**
     * 删除临时db文件中的人生主线及关联数据
     */
    private fun removeMainlineData(dbFile: File) {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
        )
        try {
            val cursor = db.query("main_lists", arrayOf("id"), "type='MAINLINE'", null, null, null, null)
            val mainlineIds = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                mainlineIds.add(cursor.getLong(0))
            }
            cursor.close()

            if (mainlineIds.isNotEmpty()) {
                val idsStr = mainlineIds.joinToString(",")
                AppLogger.i("$TAG: 删除人生主线, ids=$idsStr")
                db.delete("mainline_items", "branchId IN (SELECT id FROM mainline_branches WHERE parentListId IN ($idsStr))", null)
                db.delete("mainline_branches", "parentListId IN ($idsStr)", null)
                db.delete("sub_lists", "parentListId IN ($idsStr)", null)
                db.delete("content_configs", "parentListId IN ($idsStr)", null)
                db.delete("option_buttons", "parentListId IN ($idsStr)", null)
                db.delete("rich_texts", "parentListId IN ($idsStr)", null)
                db.delete("quiz_cards", "groupId IN (SELECT id FROM quiz_groups WHERE parentListId IN ($idsStr))", null)
                db.delete("quiz_groups", "parentListId IN ($idsStr)", null)
                db.delete("main_lists", "type='MAINLINE'", null)
            }
        } finally {
            db.close()
        }
    }

    /**
     * 从数据库中收集所有 imageUris，并将图片复制到 imagesDir
     *
     * @param context Context
     * @param dbFile 数据库文件
     * @param imagesDir 图片目标目录
     * @param filterParentListId 如果非null，只收集该项目的图片
     * @return 复制的图片数量
     */
    private fun collectAndCopyImages(
        context: Context,
        dbFile: File,
        imagesDir: File,
        filterParentListId: Long?
    ): Int {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        var copiedCount = 0

        try {
            // 检查 sub_lists 表是否有 imageUris 列
            val columns = getTableColumns(db, "sub_lists")
            val hasImageUris = "imageUris" in columns
            val hasImageUri = "imageUri" in columns

            if (!hasImageUris && !hasImageUri) {
                AppLogger.w("$TAG: sub_lists 表没有图片字段，跳过图片收集")
                return 0
            }

            // 查询 sub_lists
            val selection = if (filterParentListId != null) {
                "parentListId = $filterParentListId"
            } else {
                null
            }

            val cursor = db.query(
                "sub_lists",
                null,
                selection,
                null, null, null, null
            )

            val uriMap = mutableMapOf<String, String>() // 原始URI → 目标文件名

            try {
                val idIndex = cursor.getColumnIndex("id")
                val imageUrisIndex = if (hasImageUris) cursor.getColumnIndex("imageUris") else -1
                val imageUriIndex = if (hasImageUri) cursor.getColumnIndex("imageUri") else -1

                while (cursor.moveToNext()) {
                    val subListId = if (idIndex >= 0) cursor.getLong(idIndex) else continue

                    // 处理 imageUris（JSON数组）
                    if (imageUrisIndex >= 0) {
                        val imageUrisStr = cursor.getString(imageUrisIndex)
                        if (!imageUrisStr.isNullOrBlank() && imageUrisStr != "[]") {
                            try {
                                val array = JSONArray(imageUrisStr)
                                for (i in 0 until array.length()) {
                                    val uri = array.getString(i)
                                    if (uri.isNotBlank()) {
                                        val fileName = generateImageFileName(uri, i)
                                        uriMap[uri] = fileName
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.e("$TAG: 解析imageUris失败, subListId=$subListId: ${e.message}")
                            }
                        }
                    }

                    // 处理 imageUri（单张，已废弃但兼容）
                    if (imageUriIndex >= 0) {
                        val singleUri = cursor.getString(imageUriIndex)
                        if (!singleUri.isNullOrBlank()) {
                            // 避免和 imageUris 里的重复
                            if (!uriMap.containsKey(singleUri)) {
                                val fileName = generateImageFileName(singleUri, 0)
                                uriMap[singleUri] = fileName
                            }
                        }
                    }
                }
            } finally {
                cursor.close()
            }

            // 复制图片文件
            for ((uri, fileName) in uriMap) {
                try {
                    val targetFile = File(imagesDir, fileName)
                    copyUriToFile(context, uri, targetFile)
                    copiedCount++
                    AppLogger.d("$TAG: 复制图片 $uri → $fileName")
                } catch (e: Exception) {
                    // 图片无法读取（已删除等），跳过不阻断
                    AppLogger.w("$TAG: 无法读取图片 $uri，跳过: ${e.message}")
                }
            }
        } finally {
            db.close()
        }

        return copiedCount
    }

    /**
     * 将 URI 内容复制到文件
     */
    private fun copyUriToFile(context: Context, uriString: String, targetFile: File) {
        val uri = android.net.Uri.parse(uriString)
        when {
            // content:// 或 file:// URI，用ContentResolver
            uri.scheme == "content" || uri.scheme == "file" -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法打开URI: $uriString")
            }
            // 绝对路径（如 /data/user/0/...），直接读文件
            uriString.startsWith("/") -> {
                val sourceFile = File(uriString)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(targetFile, overwrite = true)
                } else {
                    throw IllegalStateException("图片文件不存在: $uriString")
                }
            }
            else -> throw IllegalStateException("不支持的URI格式: $uriString")
        }
    }

    /**
     * 生成图片文件名
     * 如果原文件名已是新规范（Fuker_开头），直接保留原文件名
     * 否则用新规范生成：Fuker_yyyyMMddHHmmssSSS_4位随机数.ext
     */
    private fun generateImageFileName(uri: String, index: Int): String {
        val path = android.net.Uri.parse(uri).path ?: ""
        val originalName = path.substringAfterLast('/')
        // 如果已经是新规范命名，直接用
        if (originalName.startsWith("Fuker_")) {
            return originalName
        }
        // 旧命名，生成新规范
        val ext = guessExtension(uri)
        val timestamp = java.text.SimpleDateFormat("yyyyMMddHHmmssSSS", java.util.Locale.US)
            .format(System.currentTimeMillis() + index) // 加index避免同一批时间戳重复
        val random = (1000..9999).random()
        return "Fuker_${timestamp}_${random}${ext}"
    }

    /**
     * 从URI推断文件扩展名
     */
    private fun guessExtension(uri: String): String {
        // 尝试从URI路径获取扩展名
        val path = android.net.Uri.parse(uri).path ?: return ".jpg"
        val lastDot = path.lastIndexOf('.')
        if (lastDot > 0 && lastDot > path.lastIndexOf('/')) {
            val ext = path.substring(lastDot).lowercase()
            if (ext.length <= 5 && ext.matches(Regex("\\.[a-z]+"))) {
                return ext
            }
        }
        return ".jpg"
    }

    /**
     * 获取表的列名列表
     */
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
     * 将目录打包成zip文件
     */
    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                val entryName = file.relativeTo(sourceDir).path
                // 跳过SQLite临时文件
                if (entryName.endsWith("-wal") || entryName.endsWith("-shm")) return@forEach
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
