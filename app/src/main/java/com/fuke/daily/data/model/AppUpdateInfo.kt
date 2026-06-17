package com.fuke.daily.data.model

/**
 * 服务器返回的版本信息
 */
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val updateLog: String = "",
    val forceUpdate: Boolean = false,
    val source: String = "",  // 更新来源（非JSON字段，运行时标记）
)
