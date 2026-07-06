package com.fuke.daily

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuke.daily.data.datastore.AppPrefs
import com.fuke.daily.data.repository.MainListRepo
import com.fuke.daily.feature.floating.FloatingWindowService
import com.fuke.daily.feature.timer.TimerReminderService
import com.fuke.daily.ui.navigation.AppNavigation
import com.fuke.daily.ui.theme.FukeDailyTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPrefs: AppPrefs
    @Inject lateinit var mainListRepo: MainListRepo
    
    // 缓存权限检查结果，避免重复查询
    private var cachedOverlayPermission: Boolean? = null
    private var cachedNotificationPermission: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.i("MainActivity: onCreate, action=${intent.action}")
        
        // 处理从闹钟打开人生主线的请求
        val openMainline = intent.action == "com.fuke.daily.OPEN_MAINLINE"
        
        // Fix: 键盘弹起时调整布局，避免遮挡输入框
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val hasOverlay = checkOverlayPermission()
        
        // 异步检查是否需要自动触发主线选择
        // 如果当前在触发时段内且今天未触发，则导航到主线页面
        var autoTriggerMainline = false
        try {
            // 先检查是否有启用的 MAINLINE 项目
            val mainlineList = runBlocking { mainListRepo.getMainlineList() }
            if (mainlineList == null) {
                AppLogger.i("MainActivity: 没有启用的 MAINLINE 项目，跳过自动触发")
            } else {
                val config = runBlocking { appPrefs.mainlineConfig.first() }
                val morningHour = config.morningHour
                val eveningStartHour = config.eveningHour
                
                val now = java.util.Calendar.getInstance()
                val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
                
                // 判断当前时段
                val isMorning = hour in morningHour until eveningStartHour
                val isEvening = !isMorning
                
                // 获取今天的日期字符串
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val today = sdf.format(now.time)
                
                // 检查是否需要触发
                val shouldTrigger = when {
                    isMorning -> config.lastMorningDate != today
                    isEvening -> {
                        // 晚间时段跨越两天，需要特殊处理
                        // 如果当前是凌晨（0:00~morningHour），检查昨晚是否已触发
                        // 如果当前是晚上（eveningStartHour~23:59），检查今天是否已触发
                        val isEarlyMorning = hour < morningHour
                        if (isEarlyMorning) {
                            // 凌晨时段：检查昨晚（昨天）是否已触发
                            val yesterdayCalendar = now.clone() as java.util.Calendar
                            yesterdayCalendar.add(java.util.Calendar.DATE, -1)
                            val yesterday = sdf.format(yesterdayCalendar.time)
                            config.lastEveningDate != yesterday && config.lastEveningDate != today
                        } else {
                            // 晚上时段：检查今天是否已触发
                            config.lastEveningDate != today
                        }
                    }
                    else -> false
                }
                
                autoTriggerMainline = shouldTrigger
                AppLogger.i("MainActivity: autoTrigger check: isMorning=$isMorning, isEvening=$isEvening, shouldTrigger=$shouldTrigger, mainlineList=${mainlineList.name}")
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity: Failed to check auto trigger", e)
        }
        
        val startRoute = when {
            !hasOverlay -> "permission"
            openMainline -> "mainline/daily"
            autoTriggerMainline -> "mainline/daily"
            else -> "home"
        }

        setContent {
            val themeMode by appPrefs.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.WARM,
            )

            FukeDailyTheme(themeMode = themeMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding),
                        startDestination = startRoute,
                        autoTriggerMainline = autoTriggerMainline,
                    )
                }
            }
        }
    }

    // 检查悬浮窗权限（带缓存）
    private fun checkOverlayPermission(): Boolean {
        if (cachedOverlayPermission == null) {
            cachedOverlayPermission = Settings.canDrawOverlays(this)
        }
        return cachedOverlayPermission ?: false
    }
    
    // 检查通知权限（带缓存）
    private fun checkNotificationPermission(): Boolean {
        if (cachedNotificationPermission == null) {
            cachedNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        }
        return cachedNotificationPermission ?: true
    }

    // Fix 3: onResume时检查overlay权限并启动悬浮窗服务
    override fun onResume() {
        super.onResume()
        
        // 清除权限缓存，下次重新检查
        cachedOverlayPermission = null
        cachedNotificationPermission = null
        
        // 异步启动服务，避免阻塞主线程
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val hasOverlay = checkOverlayPermission()
                val hasNotification = checkNotificationPermission()

                if (hasOverlay && hasNotification) {
                    AppLogger.i("MainActivity: starting FloatingWindowService")
                    val intent = Intent(this, FloatingWindowService::class.java)
                    startForegroundService(intent)
                } else {
                    AppLogger.w("MainActivity: missing permission, overlay=$hasOverlay, notification=$hasNotification")
                }
            } catch (e: Exception) {
                AppLogger.e("MainActivity: Failed to start FloatingWindowService", e)
            }
            
            // 启动定时服务（如果已创建定时任务）
            try {
                AppLogger.i("MainActivity: starting TimerReminderService")
                TimerReminderService.start(this)
            } catch (e: Exception) {
                AppLogger.e("MainActivity: Failed to start TimerReminderService", e)
            }
        }, 500) // 延迟 500ms 启动，让页面先显示
    }
}
