package com.fuke.daily.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fuke.daily.data.model.MainlineConfig
import com.fuke.daily.ui.theme.ThemeMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  DataStore 偏好设置
// ═══════════════════════════════════════════════════

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fuke_daily_prefs")

@Singleton
class AppPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    // ── Keys ──

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val LAST_MORNING_DATE = stringPreferencesKey("last_morning_date")
        val LAST_EVENING_DATE = stringPreferencesKey("last_evening_date")
        val MAINLINE_ENABLED = booleanPreferencesKey("mainline_enabled")
        val ICON_POS_X = intPreferencesKey("icon_pos_x")
        val ICON_POS_Y = intPreferencesKey("icon_pos_y")
    }

    // ── 主题模式 ──

    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            when (prefs[Keys.THEME_MODE]) {
                ThemeMode.PURPLE.name -> ThemeMode.PURPLE
                else -> ThemeMode.WARM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    // ── 主线配置 ──

    val mainlineConfig: Flow<MainlineConfig> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            MainlineConfig(
                morningHour = prefs[Keys.MORNING_HOUR] ?: 0,
                eveningHour = prefs[Keys.EVENING_HOUR] ?: 21,
                lastMorningDate = prefs[Keys.LAST_MORNING_DATE] ?: "",
                lastEveningDate = prefs[Keys.LAST_EVENING_DATE] ?: "",
            )
        }

    suspend fun setMainlineConfig(config: MainlineConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.MORNING_HOUR] = config.morningHour
            prefs[Keys.EVENING_HOUR] = config.eveningHour
            prefs[Keys.LAST_MORNING_DATE] = config.lastMorningDate
            prefs[Keys.LAST_EVENING_DATE] = config.lastEveningDate
        }
    }

    // ── 主线开关 ──

    val mainlineEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.MAINLINE_ENABLED] ?: true }

    suspend fun setMainlineEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MAINLINE_ENABLED] = enabled }
    }

    // ── 悬浮图标位置 ──

    val iconPosition: Flow<Pair<Int, Int>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            Pair(
                prefs[Keys.ICON_POS_X] ?: 0,
                prefs[Keys.ICON_POS_Y] ?: 0,
            )
        }

    suspend fun setIconPosition(x: Int, y: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.ICON_POS_X] = x
            prefs[Keys.ICON_POS_Y] = y
        }
    }
}
