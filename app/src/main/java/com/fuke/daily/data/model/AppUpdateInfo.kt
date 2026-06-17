package com.fuke.daily.data.model

import com.google.gson.annotations.SerializedName

/**
 * 服务器返回的版本信息
 */
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val updateLog: String = "",
    val forceUpdate: Boolean = false,
)
