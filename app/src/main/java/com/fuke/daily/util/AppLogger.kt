package com.fuke.daily.util

import android.util.Log
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
    private val memoryLogs = CopyOnWriteArrayList<LogEntry>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val time: String,
        val level: String,
        val tag: String,
        val message: String,
    )

    fun init(context: android.content.Context) {
        logFile = File(context.filesDir, "app_logs.txt")
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
        
        // 写入内存缓存
        val entry = LogEntry(time, level, TAG, fullMsg)
        memoryLogs.add(entry)
        if (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.removeAt(0)
        }
        
        // 写入文件
        logFile?.appendText("$time $level/$TAG: $fullMsg\n")
    }

    fun getLogs(): List<LogEntry> = memoryLogs.toList()

    fun clearLogs() {
        memoryLogs.clear()
        logFile?.writeText("")
    }
}
