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
import com.fuke.daily.feature.floating.FloatingWindowService
import com.fuke.daily.feature.timer.TimerReminderService
import com.fuke.daily.ui.navigation.AppNavigation
import com.fuke.daily.ui.theme.FukeDailyTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.i("MainActivity: onCreate, action=${intent.action}")
        
        // 处理从闹钟打开人生主线的请求
        val openMainline = intent.action == "com.fuke.daily.OPEN_MAINLINE"
        
        // Fix: 键盘弹起时调整布局，避免遮挡输入框
        // 不使用 enableEdgeToEdge()，避免与 adjustResize 冲突
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val hasOverlay = Settings.canDrawOverlays(this)
        
        // 检查是否需要自动触发人生主线（同步获取配置）
        var autoTriggerMainline = false
        
        if (hasOverlay && !openMainline) {
            try {
                val config = runBlocking { appPrefs.mainlineConfig.first() }
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val isEvening = currentHour >= config.eveningHour
                val isMorning = currentHour < config.eveningHour
                
                // 判断今天是否已经手动触发过
                val hasTriggeredToday = when {
                    isEvening -> config.lastEveningDate == today
                    isMorning -> config.lastMorningDate == today
                    else -> false
                }
                
                // 判断今天是否已经自动触发过
                val hasAutoTriggeredToday = config.autoTriggerDate == today
                
                // 如果需要自动触发（今天还没自动触发过，也没手动触发过）
                autoTriggerMainline = !hasAutoTriggeredToday && !hasTriggeredToday
                
                AppLogger.i("MainActivity: 自动触发检查: today=$today, hasTriggeredToday=$hasTriggeredToday, hasAutoTriggeredToday=$hasAutoTriggeredToday, autoTriggerMainline=$autoTriggerMainline")
                
                if (autoTriggerMainline) {
                    // 记录自动触发
                    val newConfig = config.copy(autoTriggerDate = today)
                    runBlocking { appPrefs.setMainlineConfig(newConfig) }
                    AppLogger.i("MainActivity: 记录自动触发日期: $today")
                }
            } catch (e: Exception) {
                AppLogger.e("MainActivity: 自动触发检查失败: ${e.message}")
            }
        }
        
        val startRoute = when {
            !hasOverlay -> "permission"
            openMainline -> "mainline/daily"
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

    // Fix 3: onResume时检查overlay权限并启动悬浮窗服务
    override fun onResume() {
        super.onResume()
        try {
            // 同时检查悬浮窗权限和通知权限（Android 14+ 需要通知权限才能启动前台服务）
            val hasOverlay = Settings.canDrawOverlays(this)
            val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true

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
    }
}
