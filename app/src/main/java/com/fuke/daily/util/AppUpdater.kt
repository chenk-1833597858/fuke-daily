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
    private const val SERVER_BASE_URL = "http://101.33.200.139:15839"
    private const val SERVER_UPDATE_URL = "${SERVER_BASE_URL}/api/update"
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
     * @param context 上下文
     * @param serverVersionCode 服务器返回的版本号
     */
    fun shouldShowUpdate(context: Context, serverVersionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skippedVersion = prefs.getInt(KEY_SKIPPED_VERSION, 0)
        val lastSkipTime = prefs.getLong(KEY_LAST_SKIP_TIME, 0L)
        val currentCode = getCurrentVersionCode(context)

        // 如果当前App版本已经>=服务器版本，不需要更新
        if (currentCode >= serverVersionCode) return false

        // 如果跳过的是不同版本（说明服务器出了新版），重新提示
        if (skippedVersion != serverVersionCode) return true

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
                val info = fetchUpdateInfo(url)
                if (info != null && info.versionCode > currentCode) {
                    AppLogger.d("发现新版本: v${info.versionName} (来源: $name)")
                    return@withContext info.copy(source = name)
                }
                if (info != null) {
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
     * @param onSourceChange 当切换下载源时回调，参数为源编号和描述（含URL）
     */
    suspend fun downloadApk(context: Context, info: AppUpdateInfo, onSourceChange: ((Int, String) -> Unit)? = null): File? = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), APK_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val apkFile = File(dir, "fuke-daily-${info.versionName}.apk")
        val tagName = "v${info.versionName}"

        // 构建降级下载URL列表
        data class DownloadSource(val url: String, val label: String)

        val downloadSources = mutableListOf<DownloadSource>()

        // 1. 服务器update.json里的apkUrl（动态地址，服务器可随时变更）
        if (!info.apkUrl.isNullOrEmpty()) {
            val fullUrl = if (info.apkUrl.startsWith("http")) {
                info.apkUrl
            } else {
                "${SERVER_BASE_URL}${info.apkUrl}"
            }
            downloadSources.add(DownloadSource(fullUrl, "服务器①"))
        }

        // 2. 服务器固定下载地址（兜底）
        downloadSources.add(DownloadSource("${SERVER_BASE_URL}/api/download", "服务器②"))

        // 3. 码云（占位，暂未部署）
        // downloadSources.add(DownloadSource("https://gitee.com/xxx/fuke-daily/releases/download/$tagName/$APK_FILENAME", "码云③"))

        // 4. GitHub Release直链
        downloadSources.add(DownloadSource("$GITHUB_RELEASE_BASE/$tagName/$APK_FILENAME", "GitHub④"))

        // 下载前：检查所有下载源状态
        onSourceChange?.invoke(0, "检查下载源…")
        val sourceStatuses = mutableListOf<String>()
        for (source in downloadSources) {
            val status = checkSourceStatus(source.url)
            val statusText = if (status == 200) "✓" else "✗($status)"
            val statusLine = "${source.label} $statusText ${source.url}"
            sourceStatuses.add(statusLine)
            AppLogger.d("下载源检查 [${source.label}]: $status ${source.url}")
        }
        // 状态信息写日志，UI只显示检查中
        AppLogger.d("下载源状态:\\n${sourceStatuses.joinToString("\\n")}")
        kotlinx.coroutines.delay(800)

        // 依次尝试下载
        for ((index, source) in downloadSources.withIndex()) {
            try {
                AppLogger.d("下载APK [${source.label}]: ${source.url}")
                onSourceChange?.invoke(index + 1, "")
                val file = downloadFile(source.url, apkFile)
                if (file != null) {
                    AppLogger.d("下载成功 [${source.label}]")
                    onSourceChange?.invoke(index + 1, "")
                    return@withContext file
                }
            } catch (e: Exception) {
                AppLogger.d("下载失败 [${source.label}]: ${e.message}")
            }
        }

        AppLogger.e("所有下载源均失败")
        null
    }

    /**
     * 检查下载源状态（HTTP HEAD请求）
     * @return HTTP状态码，0表示连接失败
     */
    private fun checkSourceStatus(url: String): Int {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code
        } catch (e: Exception) {
            0
        }
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
