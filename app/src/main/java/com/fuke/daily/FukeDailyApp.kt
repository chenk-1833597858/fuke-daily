package com.fuke.daily

import android.app.Application
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FukeDailyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早初始化日志系统，确保后台服务也能记录日志
        AppLogger.init(this)
        AppLogger.i("App: Application created")
    }
}
