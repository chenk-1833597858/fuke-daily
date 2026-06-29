package com.fuke.daily

import android.content.Intent
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
import com.fuke.daily.ui.navigation.AppNavigation
import com.fuke.daily.ui.theme.FukeDailyTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fix 1: 键盘弹起时调整布局，避免遮挡输入框
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val hasOverlay = Settings.canDrawOverlays(this)
        val startRoute = if (hasOverlay) "home" else "permission"

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
                    )
                }
            }
        }
    }

    // Fix 3: onResume时检查overlay权限并启动悬浮窗服务
    override fun onResume() {
        super.onResume()
        try {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingWindowService::class.java)
                startForegroundService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity: Failed to start FloatingWindowService", e)
        }
    }
}
