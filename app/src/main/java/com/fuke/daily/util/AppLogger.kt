package com.fuke.daily.util

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 日志工具 — 统一 TAG，内存缓存 + 文件备份
 */
object AppLogger {
    private const val TAG = "FukeDaily"
    private const val MAX_MEMORY_LOGS = 500
    private val memoryLogs = Collections.synchronizedList(ArrayList<LogEntry>())
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val maxFileSize = 1024 * 1024 // 1MB

    data class LogEntry(
        val time: String,
        val level: String,
        val tag: String,
        val message: String,
    )

    fun init(context: android.content.Context) {
        logFile = File(context.filesDir, "app_logs.txt")
        // 启动时从文件加载历史日志
        loadLogsFromFile()
    }

    private fun loadLogsFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return
        // 限制加载的日志行数，避免启动时加载过多历史日志
        val maxLinesToLoad = 100
        try {
            file.readLines().takeLast(maxLinesToLoad).forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    // 解析格式: "MM-dd HH:mm:ss.SSS D/FukeDaily: message"
                    val regex = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([DIWE])/([^:]+):\s+(.*)$""")
                    val match = regex.find(line)
                    if (match != null) {
                        val (time, level, tag, message) = match.destructured
                        memoryLogs.add(LogEntry(time, level, tag, message))
                    }
                } catch (_: Exception) {
                    // 单行解析失败，跳过
                }
            }
            // 限制内存日志数量
            while (memoryLogs.size > MAX_MEMORY_LOGS) {
                memoryLogs.removeAt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载历史日志失败", e)
        }
    }

    fun d(msg: String) = log("D", msg)
    fun i(msg: String) = log("I", msg)
    fun w(msg: String) = log("W", msg)
    fun e(msg: String, t: Throwable? = null) = log("E", msg, t)

    private fun log(level: String, msg: String, t: Throwable? = null) {
        val time = dateFormat.format(Date())
        val fullMsg = if (t != null) "$msg: ${t.message}" else msg
        
        // 写入 Android Logcat
        when (level) {
            "D" -> Log.d(TAG, fullMsg, t)
            "I" -> Log.i(TAG, fullMsg, t)
            "W" -> Log.w(TAG, fullMsg, t)
            "E" -> Log.e(TAG, fullMsg, t)
        }
        
        // 写入内存缓存（同步锁保护）
        synchronized(memoryLogs) {
            val entry = LogEntry(time, level, TAG, fullMsg)
            memoryLogs.add(entry)
            if (memoryLogs.size > MAX_MEMORY_LOGS) {
                memoryLogs.removeAt(0)
            }
        }
        
        // 写入文件（异步，避免阻塞）
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = logFile ?: return@launch
                // 检查文件大小，超过 1MB 清空
                if (file.exists() && file.length() > maxFileSize) {
                    file.writeText("")
                }
                file.appendText("$time $level/$TAG: $fullMsg\n")
            } catch (e: Exception) {
                // 忽略写入错误
            }
        }
    }

    fun getLogs(): List<LogEntry> = memoryLogs.toList()

    fun clearLogs() {
        memoryLogs.clear()
        logFile?.writeText("")
    }
}
