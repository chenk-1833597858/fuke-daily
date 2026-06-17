package com.fuke.daily.util

import android.content.Context
import android.content.Intent
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
 */
object AppUpdater {

    private const val UPDATE_URL = "http://101.33.200.139:15839/update.json"
    private const val APK_DIR = "updates"

    /**
     * 检查更新，返回更新信息（null=无需更新或检查失败）
     */
    suspend fun checkUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(UPDATE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"

            if (conn.responseCode != 200) {
                AppLogger.e("更新检查失败: HTTP ${conn.responseCode}")
                return@withContext null
            }

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val info = Gson().fromJson(json, AppUpdateInfo::class.java)

            // 比较版本号
            val currentCode = getCurrentVersionCode(context)
            if (info.versionCode > currentCode) {
                AppLogger.d("发现新版本: ${info.versionName} (当前: v${getCurrentVersionName(context)})")
                info
            } else {
                AppLogger.d("已是最新版本")
                null
            }
        } catch (e: Exception) {
            AppLogger.e("更新检查异常: ${e.message}")
            null
        }
    }

    /**
     * 下载APK，返回本地文件（null=下载失败）
     */
    suspend fun downloadApk(context: Context, info: AppUpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir(null), APK_DIR)
            if (!dir.exists()) dir.mkdirs()

            // 删旧APK
            dir.listFiles()?.forEach { it.delete() }

            val apkFile = File(dir, "fuke-daily-${info.versionName}.apk")
            AppLogger.d("开始下载: ${info.apkUrl}")

            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            if (conn.responseCode != 200) {
                AppLogger.e("下载失败: HTTP ${conn.responseCode}")
                return@withContext null
            }

            val totalSize = conn.contentLength.toLong()
            var downloadedSize = 0L

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                    }
                }
            }

            AppLogger.d("下载完成: ${apkFile.absolutePath} (${downloadedSize} bytes)")
            apkFile
        } catch (e: Exception) {
            AppLogger.e("下载APK异常: ${e.message}")
            null
        }
    }

    /**
     * 安装APK
     */
    fun installApk(context: Context, apkFile: File) {
        // Android 8+ 需要安装未知应用权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // 跳转到设置页请求权限
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
            // 降级方案：直接用file:// URI（Android 7以下）
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
