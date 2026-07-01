package com.fuke.daily.feature.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.fuke.daily.MainActivity
import com.fuke.daily.feature.floating.FloatingWindowService
import com.fuke.daily.R
import com.fuke.daily.data.model.ReminderMethods
import com.fuke.daily.data.model.ReminderSubType
import com.fuke.daily.data.model.RepeatMode
import com.fuke.daily.data.model.TimerItem
import com.fuke.daily.data.model.TimerType
import com.fuke.daily.data.repository.TimerRepo
import com.fuke.daily.receiver.AlarmReceiver
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 定时提醒服务（前台服务）
 * - 管理闹钟和提醒任务
 * - 触发提醒（闹铃、震动、悬浮窗）
 * - 定期检查并重新注册闹钟
 */
class TimerReminderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var alarmManager: AlarmManager
    private lateinit var vibrator: Vibrator
    private var ringtone: Ringtone? = null
    private var periodicCheckJob: kotlinx.coroutines.Job? = null
    private var stopAlarmReceiver: android.content.BroadcastReceiver? = null

    // 通过Hilt EntryPoint获取TimerRepo
    private val timerRepo: TimerRepo by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            TimerRepoEntryPoint::class.java
        ).timerRepo()
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("Timer: TimerReminderService onCreate")
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        startForeground(NOTIFICATION_ID, createNotification())
        AppLogger.i("Timer: 前台服务已启动")

        // 恢复所有已启用任务
        serviceScope.launch {
            delay(300)
            restoreEnabledTasks()
        }
        
        // 注册停止闹钟广播接收器
        // 先注销旧的接收器，防止重复注册
        try {
            stopAlarmReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        
        stopAlarmReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.fuke.daily.STOP_ALARM_SOUND") {
                    AppLogger.i("Timer: 收到停止闹钟广播")
                    stopEffectOnly()
                }
            }
        }
        val filter = android.content.IntentFilter("com.fuke.daily.STOP_ALARM_SOUND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopAlarmReceiver, filter)
        }
        AppLogger.i("Timer: 停止闹钟广播接收器已注册")

        // 定期检查（每10分钟）
        startPeriodicCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_KEEP_RUNNING -> {
                AppLogger.d("Timer: 收到启动并保持运行请求")
            }
            ACTION_TRIGGER_REMINDER -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)
                val methods = intent.getIntExtra(EXTRA_REMINDER_METHODS, 0)
                AppLogger.d("Timer: 触发提醒: taskId=$taskId, methods=$methods")
                triggerReminder(taskId, methods)
            }
            ACTION_STOP_REMINDER -> {
                stopReminder()
            }
            ACTION_STOP_EFFECT_ONLY -> {
                stopEffectOnly()
            }
            ACTION_SCHEDULE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)
                if (taskId > 0) {
                    serviceScope.launch {
                        val task = timerRepo.getTimerById(taskId)
                        if (task != null) scheduleTask(task)
                    }
                }
            }
            ACTION_CANCEL_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)
                if (taskId > 0) cancelTask(taskId)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopReminder()
        periodicCheckJob?.cancel()
        serviceScope.cancel()
        
        // 注销广播接收器
        try {
            stopAlarmReceiver?.let { unregisterReceiver(it) }
            AppLogger.i("Timer: 停止闹钟广播接收器已注销")
        } catch (e: Exception) {
            AppLogger.w("Timer: 注销广播接收器失败: ${e.message}")
        }
        
        AppLogger.d("Timer: 前台服务已停止")
    }

    // ═══════════════════════════════════════════════════
    //  定期检查
    // ═══════════════════════════════════════════════════

    private fun startPeriodicCheck() {
        periodicCheckJob?.cancel()
        periodicCheckJob = serviceScope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                AppLogger.d("Timer: 定期检查闹钟状态")
                checkAndReregisterAlarms()
            }
        }
    }

    private suspend fun checkAndReregisterAlarms() {
        try {
            val enabledTasks = timerRepo.getAllEnabledTimersSync()
            val now = System.currentTimeMillis()

            enabledTasks.forEach { task ->
                if (task.nextTriggerTime > now) {
                    // 检查闹钟是否存在
                    val pendingIntent = PendingIntent.getBroadcast(
                        this, task.id.toInt(),
                        Intent(this, AlarmReceiver::class.java),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pendingIntent == null) {
                        AppLogger.d("Timer: 闹钟丢失，重新注册: taskId=${task.id}")
                        scheduleTask(task)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Timer: 检查闹钟失败: ${e.message}")
        }
    }

    private suspend fun restoreEnabledTasks() {
        try {
            val enabledTasks = timerRepo.getAllEnabledTimersSync()
            AppLogger.d("Timer: 恢复定时任务: 已启用=${enabledTasks.size}")
            enabledTasks.forEach { task ->
                scheduleTask(task)
            }
        } catch (e: Exception) {
            AppLogger.e("Timer: 恢复任务失败: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════
    //  调度逻辑
    // ═══════════════════════════════════════════════════

    private fun scheduleTask(task: TimerItem) {
        if (!task.isEnabled) return

        when (task.type) {
            TimerType.ALARM -> scheduleAlarm(task)
            TimerType.REMINDER -> scheduleReminder(task)
        }
    }

    private fun scheduleAlarm(task: TimerItem) {
        val triggerTime = calculateNextTriggerTime(task)
        if (triggerTime <= System.currentTimeMillis()) return

        val methods = ReminderMethods(
            alarm = task.alarmEnabled,
            vibration = task.vibrationEnabled,
            floatingWindow = task.floatingWindowEnabled,
        )

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_REMINDER_METHODS, methods.toBitmask())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(triggerTime, pendingIntent)
        recordAlarmRegistered(this, task.id, triggerTime)
        updateTaskNextTriggerTime(task.id, triggerTime)
    }

    private fun scheduleReminder(task: TimerItem) {
        when (task.reminderSubType) {
            ReminderSubType.LOOP -> scheduleLoopReminder(task)
            ReminderSubType.COUNT -> scheduleCountReminder(task)
        }
    }

    private fun scheduleLoopReminder(task: TimerItem) {
        if (!isCurrentTimeInRange(task.startHour, task.startMinute, task.endHour, task.endMinute)) return

        val intervalMs = task.intervalMinutes.toLong() * 60 * 1000
        val triggerTime = System.currentTimeMillis() + intervalMs

        // 不超过结束时间
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.endHour)
            set(Calendar.MINUTE, task.endMinute)
            set(Calendar.SECOND, 0)
        }
        if (triggerTime > endCal.timeInMillis) return

        scheduleAlarmAtTime(task, triggerTime)
    }

    private fun scheduleCountReminder(task: TimerItem) {
        if (task.reminderCurrentCount >= task.count) return
        if (!isCurrentTimeInRange(task.startHour, task.startMinute, task.endHour, task.endMinute)) return

        val intervalMs = task.intervalMinutes.toLong().coerceAtLeast(5) * 60 * 1000
        val triggerTime = System.currentTimeMillis() + intervalMs

        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.endHour)
            set(Calendar.MINUTE, task.endMinute)
            set(Calendar.SECOND, 0)
        }
        if (triggerTime > endCal.timeInMillis) return

        scheduleAlarmAtTime(task, triggerTime)
    }

    private fun scheduleAlarmAtTime(task: TimerItem, triggerTime: Long) {
        val methods = ReminderMethods(
            alarm = task.alarmEnabled,
            vibration = task.vibrationEnabled,
            floatingWindow = task.floatingWindowEnabled,
        )

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_REMINDER_METHODS, methods.toBitmask())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, task.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(triggerTime, pendingIntent)
        recordAlarmRegistered(this, task.id, triggerTime)
        updateTaskNextTriggerTime(task.id, triggerTime)
    }

    private fun setAlarm(triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // 使用 AlarmClock 最可靠，会在状态栏显示闹钟图标
                    val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                    alarmManager.setAlarmClock(alarmInfo, pendingIntent)
                    AppLogger.i("Timer: 使用 AlarmClock 设置闹钟: ${formatTime(triggerTime)}")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                    AppLogger.i("Timer: 使用 setAndAllowWhileIdle 设置闹钟: ${formatTime(triggerTime)}")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
                AppLogger.i("Timer: 使用 setExactAndAllowWhileIdle 设置闹钟: ${formatTime(triggerTime)}")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
                AppLogger.i("Timer: 使用 setExact 设置闹钟: ${formatTime(triggerTime)}")
            }
        } catch (e: Exception) {
            AppLogger.e("Timer: 设置闹钟失败: ${e.message}")
        }
    }

    private fun formatTime(timeMs: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeMs))
    }

    private fun cancelTask(taskId: Long) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        AppLogger.d("Timer: 任务已取消: taskId=$taskId")
    }

    // ═══════════════════════════════════════════════════
    //  提醒触发
    // ═══════════════════════════════════════════════════

    private fun triggerReminder(taskId: Long, methodsMask: Int) {
        val methods = ReminderMethods.fromBitmask(methodsMask)

        AppLogger.i("Timer: triggerReminder taskId=$taskId, methodsMask=$methodsMask")
        AppLogger.i("Timer: alarm=${methods.alarm}, vib=${methods.vibration}, float=${methods.floatingWindow}")

        if (methods.alarm) playAlarm()
        if (methods.vibration) vibrate()

        // 异步更新计数 + 启动悬浮窗
        serviceScope.launch {
            delay(500)

            val task = timerRepo.getTimerById(taskId)
            if (task == null) {
                AppLogger.w("Timer: 任务不存在: taskId=$taskId")
                return@launch
            }

            // 次数模式：更新计数
            if (task.reminderSubType == ReminderSubType.COUNT) {
                val newCount = task.reminderCurrentCount + 1
                timerRepo.updateTimer(task.copy(reminderCurrentCount = newCount))
                AppLogger.d("Timer: 次数更新: $newCount/${task.count}")
            }

            // 启动悬浮窗闪烁
            if (methods.floatingWindow) {
                AppLogger.i("Timer: 准备启动悬浮窗闪烁")
                try {
                    FloatingWindowService.startFlashing(this@TimerReminderService)
                    AppLogger.i("Timer: 悬浮窗闪烁启动成功")
                } catch (e: Exception) {
                    AppLogger.e("Timer: 悬浮窗闪烁启动失败: ${e.message}")
                }
            } else {
                AppLogger.i("Timer: 未勾选悬浮窗提醒，跳过")
            }
        }

        showReminderNotification(taskId)
    }

    private fun playAlarm() {
        try {
            val uri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                ringtone = RingtoneManager.getRingtone(this, uri).apply { play() }
            }
        } catch (e: Exception) {
            AppLogger.e("Timer: 播放闹铃失败: ${e.message}")
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10000)
            }
        } catch (e: Exception) {
            AppLogger.e("Timer: 震动失败: ${e.message}")
        }
    }

    private fun stopReminder() {
        ringtone?.stop()
        ringtone = null
        if (vibrator.hasVibrator()) vibrator.cancel()
        FloatingWindowService.stop(this)
    }

    private fun stopEffectOnly() {
        ringtone?.stop()
        ringtone = null
        if (vibrator.hasVibrator()) vibrator.cancel()
        // 只停止铃声和震动，不停止 FloatingWindowService
        // 悬浮窗的闪烁由 FloatingWindowService 自己控制
        AppLogger.d("Timer: 提醒特效已停止（任务继续调度）")
    }

    // ═══════════════════════════════════════════════════
    //  通知
    // ═══════════════════════════════════════════════════

    private fun showReminderNotification(taskId: Long) {
        serviceScope.launch {
            val task = timerRepo.getTimerById(taskId) ?: return@launch

            val notificationManager = getSystemService(NotificationManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    REMINDER_CHANNEL_ID, "提醒通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val stopIntent = Intent(this@TimerReminderService, TimerReminderService::class.java).apply {
                action = ACTION_STOP_REMINDER
            }
            val stopPendingIntent = PendingIntent.getService(
                this@TimerReminderService, taskId.toInt(), stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@TimerReminderService, REMINDER_CHANNEL_ID)
                .setContentTitle(task.name)
                .setContentText("定时提醒已触发")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
                .build()

            notificationManager.notify(taskId.toInt(), notification)
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("定时任务服务运行中")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "定时提醒服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "定时提醒运行状态通知" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // ═══════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════

    private fun calculateNextTriggerTime(task: TimerItem): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, task.hour)
        cal.set(Calendar.MINUTE, task.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // 处理重复模式
        when (task.repeatMode) {
            RepeatMode.NONE -> {
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            RepeatMode.DAILY -> {
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            RepeatMode.WEEKLY -> {
                // 下周同一天
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_MONTH, 7)
                }
            }
            RepeatMode.CUSTOM -> {
                // 自定义周几，找到下一个匹配的
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                // 如果指定了周几，找到最近的
                val days = task.selectedDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (days.isNotEmpty()) {
                    // 简单实现：逐天找直到匹配
                    var attempts = 0
                    while (cal.get(Calendar.DAY_OF_WEEK) !in days.map { toCalendarDay(it) } && attempts < 7) {
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        attempts++
                    }
                }
            }
        }

        return cal.timeInMillis
    }

    // 周一=1 → Calendar.MONDAY=2
    private fun toCalendarDay(day: Int): Int = when (day) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> Calendar.MONDAY
    }

    private fun isCurrentTimeInRange(startH: Int, startM: Int, endH: Int, endM: Int): Boolean {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM
        return currentMinutes in startMinutes..endMinutes
    }

    private fun updateTaskNextTriggerTime(taskId: Long, triggerTime: Long) {
        serviceScope.launch {
            val task = timerRepo.getTimerById(taskId)
            if (task != null) {
                timerRepo.updateTimer(
                    task.copy(
                        nextTriggerTime = triggerTime,
                        lastTriggerTime = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "timer_reminder_service"
        const val REMINDER_CHANNEL_ID = "timer_reminder_notification"
        const val NOTIFICATION_ID = 1002

        const val ACTION_TRIGGER_REMINDER = "com.fuke.daily.TRIGGER_REMINDER"
        const val ACTION_STOP_REMINDER = "com.fuke.daily.STOP_REMINDER"
        const val ACTION_STOP_EFFECT_ONLY = "com.fuke.daily.STOP_EFFECT_ONLY"
        const val ACTION_SCHEDULE_TASK = "com.fuke.daily.SCHEDULE_TASK"
        const val ACTION_CANCEL_TASK = "com.fuke.daily.CANCEL_TASK"
        const val ACTION_START_KEEP_RUNNING = "com.fuke.daily.START_KEEP_RUNNING"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_METHODS = "reminder_methods"

        fun startKeepRunning(context: Context) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_START_KEEP_RUNNING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, TimerReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerReminderService::class.java))
        }

        fun scheduleTask(context: Context, task: TimerItem) {
            start(context)
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_SCHEDULE_TASK
                putExtra(EXTRA_TASK_ID, task.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelTask(context: Context, taskId: Long) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_CANCEL_TASK
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopReminder(context: Context) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_STOP_REMINDER
            }
            context.startService(intent)
        }

        fun stopEffectOnly(context: Context) {
            val intent = Intent(context, TimerReminderService::class.java).apply {
                action = ACTION_STOP_EFFECT_ONLY
            }
            context.startService(intent)
        }

        fun recordAlarmRegistered(context: Context, taskId: Long, expectedTriggerTime: Long) {
            val prefs = context.getSharedPreferences("alarm_registry", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("task_${taskId}_lastRegistered", System.currentTimeMillis())
                .putLong("task_${taskId}_expectedTrigger", expectedTriggerTime)
                .putBoolean("task_${taskId}_triggered", false)
                .apply()
        }

        fun recordAlarmTriggered(context: Context, taskId: Long) {
            val prefs = context.getSharedPreferences("alarm_registry", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("task_${taskId}_triggered", true)
                .apply()
        }

        /** AlarmReceiver里调用：触发后调度下一次（循环/次数） */
        suspend fun scheduleNextIfNeeded(context: Context, taskId: Long) {
            // 延迟获取，等数据库初始化
            delay(1000)

            val entryPoint = EntryPointAccessors.fromApplication(
                context, TimerRepoEntryPoint::class.java
            )
            val repo = entryPoint.timerRepo()
            val task = repo.getTimerById(taskId) ?: return

            when (task.reminderSubType) {
                ReminderSubType.LOOP -> {
                    scheduleTask(context, task)
                }
                ReminderSubType.COUNT -> {
                    val newCount = task.reminderCurrentCount + 1
                    repo.updateTimer(task.copy(reminderCurrentCount = newCount))
                    if (newCount < task.count) {
                        scheduleTask(context, task.copy(reminderCurrentCount = newCount))
                    }
                }
            }
        }
    }
}

// Hilt EntryPoint — 让Service/Receiver获取TimerRepo
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
@dagger.hilt.EntryPoint
interface TimerRepoEntryPoint {
    fun timerRepo(): TimerRepo
}
