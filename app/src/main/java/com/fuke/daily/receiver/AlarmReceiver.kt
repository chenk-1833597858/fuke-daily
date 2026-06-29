package com.fuke.daily.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fuke.daily.data.model.ReminderMethods
import com.fuke.daily.data.model.ReminderSubType
import com.fuke.daily.feature.floating.FloatingWindowService
import com.fuke.daily.feature.timer.TimerReminderService
import com.fuke.daily.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟触发接收器
 * AlarmManager触发时调用，启动前台服务并触发提醒效果
 */
class AlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)
        val reminderMethods = intent.getIntExtra(EXTRA_REMINDER_METHODS, 0)

        AppLogger.d("AlarmReceiver: 闹钟触发: taskId=$taskId, methods=$reminderMethods")

        if (taskId == 0L) {
            AppLogger.w("AlarmReceiver: taskId为0，跳过")
            return
        }

        // 记录闹钟触发
        TimerReminderService.recordAlarmTriggered(context, taskId)

        // 启动前台服务并保持运行
        TimerReminderService.startKeepRunning(context)

        // 发送触发提醒请求
        val serviceIntent = Intent(context, TimerReminderService::class.java).apply {
            action = TimerReminderService.ACTION_TRIGGER_REMINDER
            putExtra(TimerReminderService.EXTRA_TASK_ID, taskId)
            putExtra(TimerReminderService.EXTRA_REMINDER_METHODS, reminderMethods)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            AppLogger.e("AlarmReceiver: 启动TimerReminderService失败: ${e.message}")
        }

        // 启动悬浮窗服务
        val floatingIntent = Intent(context, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_SHOW_ICON
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(floatingIntent)
            } else {
                context.startService(floatingIntent)
            }
        } catch (e: Exception) {
            AppLogger.e("AlarmReceiver: 启动FloatingWindowService失败: ${e.message}")
        }

        // 处理后续调度（循环/次数提醒）
        scope.launch {
            try {
                handlePostReminder(context, taskId)
            } catch (e: Exception) {
                AppLogger.e("AlarmReceiver: 处理后续调度失败: ${e.message}")
            }
        }
    }

    private suspend fun handlePostReminder(context: Context, taskId: Long) {
        delay(1000)

        // 通过TimerReminderService获取任务并调度
        TimerReminderService.scheduleNextIfNeeded(context, taskId)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_METHODS = "reminder_methods"
    }
}
