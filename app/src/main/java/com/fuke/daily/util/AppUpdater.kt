package com.fuke.daily.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fuke.daily.data.model.AppUpdateInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * App自动更新工具
 *
 * 降级链：服务器(主) → GitHub(查地址)
 * 跳过逻辑：用户点"稍后再说"后3天内不再提示
 */
object AppUpdater {

    // ── 更新源 ──
    private const val SERVER_UPDATE_URL = "http://101.33.200.139:15839/api/update"
    private const val GITHUB_UPDATE_URL = "https://raw.githubusercontent.com/chenk-1833597858/fuke-daily/main/update.json"

    // ── Release直链 ──
    private const val GITHUB_RELEASE_BASE = "https://github.com/chenk-1833597858/fuke-daily/releases/download"

    private const val APK_DIR = "updates"
    private const val APK_FILENAME = "app-release.apk"

    // ── 跳过提示相关 ──
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_LAST_SKIP_TIME = "last_skip_time"
    private const val KEY_SKIPPED_VERSION = "skipped_version_code"
    private const val SKIP_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L  // 3天

    /**
     * 是否应该提示更新（检查3天跳过间隔）
     */
    fun shouldShowUpdate(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skippedVersion = prefs.getInt(KEY_SKIPPED_VERSION, 0)
        val lastSkipTime = prefs.getLong(KEY_LAST_SKIP_TIME, 0L)

        // 如果是不同版本，重置跳过记录
        if (skippedVersion != versionCode) return true

        // 同一版本，检查3天间隔
        val elapsed = System.currentTimeMillis() - lastSkipTime
        return elapsed >= SKIP_INTERVAL_MS
    }

    /**
     * 记录用户跳过了本次更新
     */
    fun recordSkip(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_SKIP_TIME, System.currentTimeMillis())
            .putInt(KEY_SKIPPED_VERSION, versionCode)
            .apply()
    }

    /**
     * 检查更新，返回更新信息（null=无需更新或全部源失败）
     */
    suspend fun checkUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentCode = getCurrentVersionCode(context)

        // 降级链：依次尝试各源
        val sources = listOf(
            "服务器" to SERVER_UPDATE_URL,
            "GitHub" to GITHUB_UPDATE_URL,
        )

        for ((name, url) in sources) {
            try {
                AppLogger.d("检查更新 [$name]: $url")
                val info = fetchUpdateInfo(url)
                if (info != null && info.versionCode > currentCode) {
                    AppLogger.d("发现新版本: ${info.versionName} (来源: $name)")
                    // 记录来源，下载时按此URL
                    return@withContext info.copy(source = name)
                }
                if (info != null) {
                    AppLogger.d("[$name] 已是最新版本")
                    // 服务器能连上且无需更新，直接返回null
                    if (name == "服务器") return@withContext null
                }
            } catch (e: Exception) {
                AppLogger.d("[$name] 检查失败: ${e.message}")
            }
        }

        null
    }

    /**
     * 下载APK，按降级链尝试
     */
    suspend fun downloadApk(context: Context, info: AppUpdateInfo): File? = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), APK_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val apkFile = File(dir, "fuke-daily-${info.versionName}.apk")
        val tagName = "v${info.versionName}"

        // 构建降级下载URL列表
        val downloadUrls = mutableListOf<String>()

        // 1. 服务器/GitHub给的apkUrl
        if (!info.apkUrl.isNullOrEmpty()) {
            downloadUrls.add(info.apkUrl)
        }

        // 2. GitHub Release直链
        downloadUrls.add("$GITHUB_RELEASE_BASE/$tagName/$APK_FILENAME")

        // 依次尝试
        for ((index, url) in downloadUrls.withIndex()) {
            try {
                AppLogger.d("下载APK [源${index + 1}]: $url")
                val file = downloadFile(url, apkFile)
                if (file != null) {
                    AppLogger.d("下载成功 [源${index + 1}]")
                    return@withContext file
                }
            } catch (e: Exception) {
                AppLogger.d("下载失败 [源${index + 1}]: ${e.message}")
            }
        }

        AppLogger.e("所有下载源均失败")
        null
    }

    /**
     * 安装APK
     */
    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("安装APK异常: ${e.message}")
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                AppLogger.e("降级安装也失败: ${e2.message}")
            }
        }
    }

    // ── 内部方法 ──

    private fun fetchUpdateInfo(url: String): AppUpdateInfo? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        // GitHub raw需要UA
        conn.setRequestProperty("User-Agent", "FukeDaily/1.0")

        if (conn.responseCode != 200) {
            AppLogger.d("HTTP ${conn.responseCode}")
            return null
        }

        val json = conn.inputStream.bufferedReader().use { it.readText() }
        return Gson().fromJson(json, AppUpdateInfo::class.java)
    }

    private fun downloadFile(url: String, targetFile: File): File? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "FukeDaily/1.0")
        // 跟随重定向（GitHub Release会302跳转）
        conn.instanceFollowRedirects = true

        if (conn.responseCode != 200) {
            AppLogger.d("下载HTTP ${conn.responseCode}")
            return null
        }

        var downloadedSize = 0L
        conn.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead
                }
            }
        }

        if (downloadedSize > 0) {
            AppLogger.d("下载完成: ${downloadedSize} bytes")
            return targetFile
        }
        return null
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    }
}
