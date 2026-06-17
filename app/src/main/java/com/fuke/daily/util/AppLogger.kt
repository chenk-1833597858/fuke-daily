package com.fuke.daily.util

import android.util.Log

/**
 * 日志工具 — 统一 TAG
 */
object AppLogger {
    private const val TAG = "FukeDaily"

    fun d(msg: String) = Log.d(TAG, msg)
    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
}
