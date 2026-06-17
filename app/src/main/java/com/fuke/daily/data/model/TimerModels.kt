package com.fuke.daily.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

// ═══════════════════════════════════════════════════
//  定时任务
// ═══════════════════════════════════════════════════

@Entity(tableName = "timers")
data class TimerItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TimerType = TimerType.ALARM,
    // 闹钟模式
    val hour: Int = 0,
    val minute: Int = 0,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val selectedDays: String = "",         // 自定义周几，逗号分隔 "1,2,3" (1=周一,7=周日)
    // 提醒模式
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 0,
    val endMinute: Int = 0,
    val reminderSubType: ReminderSubType = ReminderSubType.LOOP,
    val intervalMinutes: Int = 0,          // 循环间隔（分钟）
    val count: Int = 0,                    // 次数
    // 提醒方式
    val alarmEnabled: Boolean = true,      // 闹铃
    val vibrationEnabled: Boolean = true,  // 震动
    val floatingWindowEnabled: Boolean = true, // 悬浮窗
    // 通用
    val isEnabled: Boolean = true,
    val linkedProjectId: Long = 0,         // 关联项目ID，0=无关联
    val message: String = "",              // 提醒消息
    val createdAt: Long = System.currentTimeMillis(),
    // 调度相关
    val nextTriggerTime: Long = 0,         // 下次触发时间戳
    val lastTriggerTime: Long = 0,         // 上次触发时间戳
    val reminderCurrentCount: Int = 0,     // 次数模式当前已触发次数
)

enum class TimerType(val displayName: String) {
    ALARM("闹钟"),
    REMINDER("提醒"),
}

enum class RepeatMode(val displayName: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    CUSTOM("自定义"),
}

enum class ReminderSubType(val displayName: String) {
    LOOP("循环"),
    COUNT("次数"),
}

// 提醒方式位掩码
data class ReminderMethods(
    val alarm: Boolean = true,
    val vibration: Boolean = true,
    val floatingWindow: Boolean = true,
) {
    fun toBitmask(): Int {
        var mask = 0
        if (alarm) mask = mask or FLAG_ALARM
        if (vibration) mask = mask or FLAG_VIBRATION
        if (floatingWindow) mask = mask or FLAG_FLOATING_WINDOW
        return mask
    }

    companion object {
        const val FLAG_ALARM = 1
        const val FLAG_VIBRATION = 2
        const val FLAG_FLOATING_WINDOW = 4

        fun fromBitmask(mask: Int): ReminderMethods = ReminderMethods(
            alarm = mask and FLAG_ALARM != 0,
            vibration = mask and FLAG_VIBRATION != 0,
            floatingWindow = mask and FLAG_FLOATING_WINDOW != 0,
        )
    }
}

// Room 类型转换器
class TimerTypeConverters {
    @TypeConverter
    fun fromTimerType(value: TimerType): String = value.name

    @TypeConverter
    fun toTimerType(value: String): TimerType = TimerType.valueOf(value)

    @TypeConverter
    fun fromRepeatMode(value: RepeatMode): String = value.name

    @TypeConverter
    fun toRepeatMode(value: String): RepeatMode = RepeatMode.valueOf(value)

    @TypeConverter
    fun fromReminderSubType(value: ReminderSubType): String = value.name

    @TypeConverter
    fun toReminderSubType(value: String): ReminderSubType = ReminderSubType.valueOf(value)
}

// 星期几的显示名称
val dayOfWeekLabels = mapOf(
    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
    5 to "周五", 6 to "周六", 7 to "周日",
)
