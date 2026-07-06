package com.fuke.daily.feature.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fuke.daily.feature.timer.TimerReminderService
import com.fuke.daily.MainActivity
import com.fuke.daily.data.datastore.AppPrefs
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.fuke.daily.data.model.ContentConfig
import com.fuke.daily.data.model.ListType
import com.fuke.daily.data.model.OptionButton
import com.fuke.daily.data.model.QuizCard
import com.fuke.daily.floating.FloatingPopup
import com.fuke.daily.floating.FloatingWindowManager
import com.fuke.daily.ui.theme.FukeDailyTheme
import com.fuke.daily.ui.theme.ThemeMode
import com.fuke.daily.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════════════════
//  悬浮窗 Foreground Service
//  实现 LifecycleOwner + SavedStateRegistryOwner
//  使用 ComposeView 而非原生 View
// ═══════════════════════════════════════════════════

@AndroidEntryPoint
class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var floatingWindowManager: FloatingWindowManager

    init {
        AppLogger.i("FloatingWindowService: init block")
    }
    @Inject lateinit var appPrefs: AppPrefs

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    // ── 生命周期 ──
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ── View引用 ──
    private var iconComposeView: View? = null
    private var iconParams: WindowManager.LayoutParams? = null

    private var popupComposeView: View? = null
    private var popupParams: WindowManager.LayoutParams? = null

    private lateinit var windowManager: WindowManager

    // ── 状态 ──
    private val _isPopupVisible = MutableStateFlow(false)
    private val _currentContent = MutableStateFlow(AnnotatedString(""))
    private val _currentButtons = MutableStateFlow<List<OptionButton>>(emptyList())
    private val _activeButtons = MutableStateFlow(0)
    private val _currentImageUri = MutableStateFlow<String?>(null)
    private val _currentImageEnabled = MutableStateFlow(true)
    private val _currentImageUris = MutableStateFlow<List<String>>(emptyList())
    private val _currentImageIndex = MutableStateFlow(0)
    private val _currentCarouselInterval = MutableStateFlow(3000L)  // 轮播间隔（毫秒），默认3000ms
    private val _globalCarouselInterval = MutableStateFlow(3000L)     // 全局轮播间隔（毫秒），默认3000ms
    private val _themeMode = MutableStateFlow(ThemeMode.WARM)
    private val _currentListType = MutableStateFlow(ListType.SELECTION)
    private val _currentQuizCards = MutableStateFlow<List<QuizCard>>(emptyList())
    private val _isIconFlashing = MutableStateFlow(false)

    // ── 轮播 ──
    private var carouselJob: kotlinx.coroutines.Job? = null

    // ── 全屏轮播 ──
    private var carouselComposeView: View? = null
    private var carouselParams: WindowManager.LayoutParams? = null
    private val _isCarouselVisible = MutableStateFlow(false)

    // ── 图片悬浮窗 ──
    private var imageFloatingWindowManager: ImageFloatingWindowManager? = null

    // ── 拖动 + 双击 ──
    private var isDragging = false
    private var dragStartX = 0
    private var dragStartY = 0
    private var iconStartX = 0
    private var iconStartY = 0
    private var totalDragDistance = 0
    private var lastClickTime = 0L

    // ═══════════════════════════════════════════════════
    //  Service 生命周期
    // ═══════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingWindowManager.init(this)
            createNotificationChannel()
            startForegroundNotification()
            loadThemeMode()
            loadGlobalCarouselInterval()
            AppLogger.i("FloatingWindowService: created")
        } catch (e: Throwable) {
            AppLogger.e("FloatingWindowService: onCreate failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                AppLogger.w("FloatingWindowService: no overlay permission, stopping")
                stopSelf()
                return START_NOT_STICKY
            }

            when (intent?.action) {
                ACTION_SHOW_ICON -> {
                    AppLogger.i("FloatingWindowService: ACTION_SHOW_ICON")
                    showIcon()
                }
                ACTION_HIDE_ICON -> {
                    AppLogger.i("FloatingWindowService: ACTION_HIDE_ICON")
                    hideIcon()
                }
                ACTION_TOGGLE_POPUP -> {
                    AppLogger.i("FloatingWindowService: ACTION_TOGGLE_POPUP")
                    togglePopup()
                }
                ACTION_NEXT_ITEM -> {
                    AppLogger.i("FloatingWindowService: ACTION_NEXT_ITEM")
                    loadNextItem()
                }
                ACTION_START_FLASHING -> {
                    AppLogger.i("FloatingWindowService: ACTION_START_FLASHING, current=${_isIconFlashing.value}")
                    _isIconFlashing.value = true
                }
                ACTION_STOP_FLASHING -> {
                    AppLogger.i("FloatingWindowService: ACTION_STOP_FLASHING")
                    _isIconFlashing.value = false
                }
                ACTION_STOP_ALARM -> {
                    AppLogger.i("FloatingWindowService: ACTION_STOP_ALARM")
                    stopAlarmSound()
                }
                ACTION_HIDE_ALL -> {
                    AppLogger.i("FloatingWindowService: ACTION_HIDE_ALL")
                    hidePopup()
                    hideIcon()
                }
                ACTION_OPEN_IMAGE_CAROUSEL -> {
                    AppLogger.i("FloatingWindowService: ACTION_OPEN_IMAGE_CAROUSEL")
                    val imageUris = intent.getStringArrayExtra("imageUris")?.toList() ?: emptyList()
                    val imageIndex = intent.getIntExtra("imageIndex", 0)
                    AppLogger.d("FloatingWindowService: ACTION_OPEN_IMAGE_CAROUSEL imageUris.size=${imageUris.size}, imageIndex=$imageIndex")
                    if (imageUris.isNotEmpty()) {
                        _currentImageUris.value = imageUris
                        _currentImageIndex.value = imageIndex
                        showImageCarousel(imageIndex)
                    } else {
                        AppLogger.w("FloatingWindowService: ACTION_OPEN_IMAGE_CAROUSEL imageUris is empty, ignoring")
                    }
                }
                else -> {
                    AppLogger.i("FloatingWindowService: unknown action=${intent?.action}, showing icon")
                    showIcon()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: onStartCommand failed", e)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            hidePopup()
            hideIcon()
            floatingWindowManager.destroy()
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: onDestroy error", e)
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        AppLogger.i("FloatingWindowService: destroyed")
    }

    // ═══════════════════════════════════════════════════
    //  主题加载
    // ═══════════════════════════════════════════════════

    private fun loadThemeMode() {
        serviceScope.launch {
            try {
                appPrefs.themeMode.collect { mode ->
                    _themeMode.value = mode
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadGlobalCarouselInterval() {
        serviceScope.launch {
            try {
                appPrefs.carouselInterval.collect { interval ->
                    _globalCarouselInterval.value = interval
                    AppLogger.d("FloatingWindowService: 全局轮播间隔更新为 ${interval}ms")
                }
            } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════
    //  前台通知
    // ═══════════════════════════════════════════════════

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "悬浮窗服务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持悬浮窗运行"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        // Android 14+ (API 34) 需要通知权限才能调用 startForeground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasNotificationPermission = checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                AppLogger.w("FloatingWindowService: no notification permission on Android 14+, stopping")
                stopSelf()
                return
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("浮刻日常")
            .setContentText("悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            AppLogger.e("FloatingWindowService: startForeground failed", e)
            stopSelf()
        }
    }

    // ═══════════════════════════════════════════════════
    //  悬浮图标（ComposeView）
    // ═══════════════════════════════════════════════════

    private fun showIcon() {
        if (floatingWindowManager.isIconVisible()) return

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        serviceScope.launch {
            try {
                val savedPos = appPrefs.iconPosition.first()
                createAndShowIcon(savedPos.first, savedPos.second)
            } catch (e: Exception) {
                AppLogger.e("FloatingWindowService: showIcon failed", e)
                createAndShowIcon(0, 0)
            }
        }
    }

    private fun createAndShowIcon(posX: Int, posY: Int) {
        try {
            val params = createIconLayoutParams().apply {
                gravity = Gravity.TOP or Gravity.START
                val density = resources.displayMetrics.density
                x = if (posX != 0) posX else (resources.displayMetrics.widthPixels - (48 * density).toInt())
                y = if (posY != 0) posY else (resources.displayMetrics.heightPixels / 3).toInt()
            }
            iconParams = params

            iconComposeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

                setContent {
                    val themeMode by _themeMode.collectAsState()
                    val isFlashing by _isIconFlashing.collectAsState()
                    FukeDailyTheme(themeMode = themeMode) {
                        com.fuke.daily.floating.FloatingIcon(
                            isFlashing = isFlashing,
                            onDoubleTap = { onIconDoubleClick() },
                            onLongPress = {
                                // 长按刷新
                                loadNextItem()
                            },
                            onDrag = { offset ->
                                val p = iconParams ?: return@FloatingIcon
                                p.x += offset.x.toInt()
                                p.y += offset.y.toInt()
                                try {
                                    windowManager.updateViewLayout(iconComposeView, p)
                                } catch (_: Exception) {}
                            },
                        )
                    }
                }
            }

            // 设置触摸监听（拖动+双击）
            iconComposeView?.setOnTouchListener(IconTouchListener())

            floatingWindowManager.showIcon(iconComposeView!!, params)
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: createAndShowIcon failed", e)
        }
    }

    private fun createIconLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun hideIcon() {
        try {
            iconParams?.let { params ->
                serviceScope.launch {
                    try {
                        appPrefs.setIconPosition(params.x, params.y)
                    } catch (_: Exception) {}
                }
            }
            floatingWindowManager.hideIcon()
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: hideIcon failed", e)
        }
        iconComposeView = null
        iconParams = null
    }

    // ═══════════════════════════════════════════════════
    //  悬浮弹窗（ComposeView，全屏底部滑入）
    // ═══════════════════════════════════════════════════

    private fun showPopup() {
        if (floatingWindowManager.isPopupVisible()) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        try {
            // 隐藏悬浮图标
            floatingWindowManager.setIconVisibility(false)

            popupParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            popupComposeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

                setContent {
                    val themeMode by _themeMode.collectAsState()
                    val content by _currentContent.collectAsState()
                    val buttons by _currentButtons.collectAsState()
                    val isVisible by _isPopupVisible.collectAsState()
                    val activeButtons by _activeButtons.collectAsState()
                    val imageUri by _currentImageUri.collectAsState()
                    val imageEnabled by _currentImageEnabled.collectAsState()
                    val imageUris by _currentImageUris.collectAsState()
                    val imageIndex by _currentImageIndex.collectAsState()
                    val listType by _currentListType.collectAsState()
                    val quizCards by _currentQuizCards.collectAsState()

                    FukeDailyTheme(themeMode = themeMode) {
                        FloatingPopup(
                            content = content,
                            buttons = buttons,
                            isVisible = isVisible,
                            activeButtons = activeButtons,
                            imageUri = imageUri,
                            imageEnabled = imageEnabled,
                            imageUris = imageUris,
                            imageIndex = imageIndex,
                            listType = listType,
                            quizCards = quizCards,
                            onQuizNext = {
                                loadNextItem()
                            },
                            onContentButtonClick = { buttonIndex ->
                                handleContentButtonClick(buttonIndex)
                            },
                            onButtonClick = { button ->
                                handleButtonClick(button)
                            },
                            onDismiss = {
                                hidePopup()
                            },
                        )
                    }
                }
            }

            floatingWindowManager.showPopup(popupComposeView!!, popupParams!!)
            _isPopupVisible.value = true
            // 双击打开：加载第一个启用项目的第一个子列表
            // 加载完成后再启动轮播
            serviceScope.launch {
                loadFirstItemSuspend()
                startCarousel()
            }
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: showPopup failed", e)
        }
    }

    private fun startCarousel() {
        carouselJob?.cancel()
        AppLogger.d("Carousel: starting carousel, job cancelled")
        carouselJob = serviceScope.launch {
            // 先显示第一张图片（立即执行，不等待）
            val imageUris = _currentImageUris.value
            AppLogger.d("Carousel: imageUris.size=${imageUris.size}, isActive=$isActive")
            if (imageUris.isNotEmpty()) {
                _currentImageIndex.value = 0
                _currentImageUri.value = imageUris[0]
                AppLogger.d("Carousel: 显示第一张图片 0/${imageUris.size}")
            } else {
                AppLogger.w("Carousel: imageUris is empty, stopping")
                return@launch
            }
            // 如果只有一张图片，不需要轮播
            if (imageUris.size <= 1) {
                AppLogger.d("Carousel: only 1 image, no carousel needed")
                return@launch
            }
            // 循环轮播：从第二张开始
            AppLogger.d("Carousel: starting loop with interval=${_currentCarouselInterval.value}ms")
            while (isActive) {
                val interval = _currentCarouselInterval.value
                AppLogger.d("Carousel: delaying ${interval}ms")
                delay(interval)
                val currentUris = _currentImageUris.value
                AppLogger.d("Carousel: after delay, currentUris.size=${currentUris.size}, isActive=$isActive")
                if (currentUris.size > 1) {
                    val nextIndex = (_currentImageIndex.value + 1) % currentUris.size
                    _currentImageIndex.value = nextIndex
                    _currentImageUri.value = currentUris[nextIndex]
                    AppLogger.d("Carousel: switched to image $nextIndex/${currentUris.size}, interval=${interval}ms")
                } else {
                    AppLogger.w("Carousel: currentUris.size <= 1, breaking loop")
                    break
                }
            }
            AppLogger.d("Carousel: loop ended, isActive=$isActive")
        }
        AppLogger.d("Carousel: job started")
    }

    private fun stopAlarmSound() {
        // 停止闹钟特效和铃声（不停止任务调度）
        // 使用 TimerReminderService.stopEffectOnly() 静态方法，与原悬浮窗工具一致
        TimerReminderService.stopEffectOnly(this)
        
        _isIconFlashing.value = false
        AppLogger.i("FloatingWindowService: alarm stopped via TimerReminderService.stopEffectOnly()")
        
        // 双击停止闹钟后，展开悬浮弹窗显示内容（与原项目一致）
        AppLogger.i("FloatingWindowService: 双击停止闹钟，展开悬浮弹窗")
        showPopup()
    }

    private fun hidePopup() {
        try {
            floatingWindowManager.hidePopup()
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: hidePopup failed", e)
        }
        _isPopupVisible.value = false
        popupComposeView = null
        popupParams = null

        // 停止轮播
        carouselJob?.cancel()
        carouselJob = null

        // 恢复悬浮图标
        floatingWindowManager.setIconVisibility(true)
    }

    private fun togglePopup() {
        if (_isPopupVisible.value) {
            hidePopup()
        } else {
            showPopup()
        }
    }

    // ═══════════════════════════════════════════════════
    //  全屏图片轮播
    // ═══════════════════════════════════════════════════

    private fun showImageCarousel(initialIndex: Int = 0) {
        if (imageFloatingWindowManager?.isShowing() == true) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        AppLogger.d("FloatingWindowService: showImageCarousel called, initialIndex=$initialIndex")

        try {
            // 隐藏悬浮弹窗（不恢复图标）
            try {
                floatingWindowManager.hidePopup()
            } catch (e: Exception) {
                AppLogger.e("FloatingWindowService: hidePopup failed", e)
            }
            _isPopupVisible.value = false
            popupComposeView = null
            popupParams = null

            // 停止轮播
            carouselJob?.cancel()
            carouselJob = null

            // 隐藏悬浮图标
            hideIcon()

            AppLogger.d("FloatingWindowService: popup and icon hidden")

            // 创建图片悬浮窗
            imageFloatingWindowManager = ImageFloatingWindowManager(this, windowManager).apply {
                show(
                    imageUris = _currentImageUris.value,
                    initialIndex = initialIndex,
                    carouselInterval = _currentCarouselInterval.value,
                    onClose = {
                        // 显示原悬浮窗弹窗
                        showPopup()
                    },
                )
            }

            AppLogger.d("FloatingWindowService: ImageFloatingWindow shown successfully")
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: showImageCarousel failed", e)
        }
    }

    private fun hideImageCarousel() {
        imageFloatingWindowManager?.hide()
        imageFloatingWindowManager = null
    }

    // ═══════════════════════════════════════════════════
    //  数据加载
    // ═══════════════════════════════════════════════════

    private fun loadNextItem() {
        serviceScope.launch {
            try {
                val item = floatingWindowManager.nextRandomItem()
                if (item == null) {
                    AppLogger.w("FloatingWindowService: no items in shuffle pool")
                    return@launch
                }

                val (mainList, subList) = item

                if (mainList.type == ListType.QUIZ) {
                    // QUIZ类型：加载答题卡片
                    val cards = floatingWindowManager.loadQuizCards(mainList.id)
                    _currentListType.value = ListType.QUIZ
                    _currentQuizCards.value = cards
                    _currentContent.value = AnnotatedString("")
                    _currentButtons.value = emptyList()
                    _currentImageUri.value = null
                    _currentImageEnabled.value = true
                } else {
                    // 非QUIZ类型：走原有逻辑
                    _currentListType.value = mainList.type
                    _currentQuizCards.value = emptyList()

                    val (config, buttons, storageData) = floatingWindowManager.loadStorageAndRichText(mainList, subList)

                    // 构建内容文本
                    val content = buildContentText(
                        subList = subList,
                        config = config ?: ContentConfig(subListId = subList.id, parentListId = mainList.id),
                        storageData = storageData,
                        fixedSlotData = floatingWindowManager.getCurrentFixedSlotData(),
                    )

                    _currentContent.value = content
                    _currentButtons.value = buttons
                    // 从 imageUris 解析图片列表
                    val imageUris = try {
                        val array = org.json.JSONArray(subList.imageUris)
                        val list = mutableListOf<String>()
                        for (i in 0 until array.length()) {
                            list.add(array.getString(i))
                        }
                        list
                    } catch (_: Exception) {
                        emptyList<String>()
                    }
                    _currentImageUris.value = imageUris
                    _currentImageIndex.value = 0
                    _currentImageUri.value = imageUris.firstOrNull() ?: subList.imageUri
                    _currentImageEnabled.value = subList.imageEnabled
                    // 设置轮播速度：优先使用 SubList 局部设置，为0时使用全局设置
                    _currentCarouselInterval.value = if (subList.carouselInterval > 0) subList.carouselInterval else _globalCarouselInterval.value
                }
                // 加载完成后重新启动轮播
                startCarousel()
            } catch (e: Exception) {
                AppLogger.e("FloatingWindowService: loadNextItem failed", e)
            }
        }
    }

    /**
     * 双击打开时：加载第一个启用项目的第一个子列表
     * 每次双击都重新查库，实时反映开关/删除变化
     */
    private suspend fun loadFirstItemSuspend() {
        try {
            // 重置洗牌池并加载第一个启用项目的第一个子列表
            val result = floatingWindowManager.loadFirstEnabledItem()
            if (result == null) {
                AppLogger.w("FloatingWindowService: no enabled items")
                _currentContent.value = AnnotatedString("")
                _currentButtons.value = emptyList()
                _currentQuizCards.value = emptyList()
                return
            }

            val (mainList, subList) = result

            if (mainList.type == ListType.QUIZ) {
                // QUIZ类型：加载答题卡片
                val cards = floatingWindowManager.loadQuizCards(mainList.id)
                _currentListType.value = ListType.QUIZ
                _currentQuizCards.value = cards
                _currentContent.value = AnnotatedString("")
                _currentButtons.value = emptyList()
                _currentImageUri.value = null
                _currentImageEnabled.value = true
            } else {
                // 非QUIZ类型：走原有逻辑
                _currentListType.value = mainList.type
                _currentQuizCards.value = emptyList()

                val (config, buttons, storageData) = floatingWindowManager.loadStorageAndRichText(mainList, subList)

                val content = buildContentText(
                    subList = subList,
                    config = config ?: ContentConfig(subListId = subList.id, parentListId = mainList.id),
                    storageData = storageData,
                    fixedSlotData = floatingWindowManager.getCurrentFixedSlotData(),
                )

                _currentContent.value = content
                _currentButtons.value = buttons
                // 从 imageUris 解析图片列表
                val imageUris = try {
                    val array = org.json.JSONArray(subList.imageUris)
                    val list = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        list.add(array.getString(i))
                    }
                    list
                } catch (_: Exception) {
                    emptyList<String>()
                }
                _currentImageUris.value = imageUris
                _currentImageIndex.value = 0
                _currentImageUri.value = imageUris.firstOrNull() ?: subList.imageUri
                _currentImageEnabled.value = subList.imageEnabled
                // 设置轮播速度：优先使用 SubList 局部设置，为0时使用全局设置
                _currentCarouselInterval.value = if (subList.carouselInterval > 0) subList.carouselInterval else _globalCarouselInterval.value
            }
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: loadFirstItem failed", e)
        }
    }

    private fun buildContentText(
        subList: com.fuke.daily.data.model.SubList,
        config: ContentConfig,
        storageData: com.fuke.daily.data.model.StorageData,
        fixedSlotData: String,
    ): AnnotatedString {
        return buildAnnotatedString {
            // 行1: input1 + storageData[button1Storage]
            val line1Suffix = if (config.button1Storage > 0) storageData.getSlot(config.button1Storage) else ""
            appendLineWithColor(config.input1Text, line1Suffix, config.input1TextColor, config.input1RefColor)

            // 行2: input2 + (fixedSlotData or storageData[button2Storage])
            val line2Suffix = if (subList.fixedSlot > 0 && fixedSlotData.isNotBlank()) {
                fixedSlotData
            } else if (config.button2Storage > 0) {
                storageData.getSlot(config.button2Storage)
            } else ""
            appendLineWithColor(config.input2Text, line2Suffix, config.input2TextColor, config.input2RefColor)

            // 行3: input3 + storageData[button3Storage]
            val line3Suffix = if (config.button3Storage > 0) storageData.getSlot(config.button3Storage) else ""
            appendLineWithColor(config.input3Text, line3Suffix, config.input3TextColor, config.input3RefColor)
        }
    }

    private fun AnnotatedString.Builder.appendLineWithColor(
        prefix: String,
        suffix: String,
        textColor: String,
        refColor: String,
    ) {
        val textColorParsed = parseColor(textColor)
        val refColorParsed = parseColor(refColor)

        when {
            prefix.isBlank() && suffix.isBlank() -> {}
            prefix.isBlank() -> {
                withStyle(style = SpanStyle(color = refColorParsed)) {
                    append(suffix)
                }
            }
            suffix.isBlank() -> {
                withStyle(style = SpanStyle(color = textColorParsed)) {
                    append(prefix)
                }
            }
            else -> {
                withStyle(style = SpanStyle(color = textColorParsed)) {
                    append(prefix)
                }
                withStyle(style = SpanStyle(color = refColorParsed)) {
                    append(suffix)
                }
            }
        }
    }

    private fun parseColor(colorString: String): androidx.compose.ui.graphics.Color {
        return try {
            if (colorString.isBlank()) return androidx.compose.ui.graphics.Color.Unspecified
            androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorString))
        } catch (e: Exception) {
            androidx.compose.ui.graphics.Color.Unspecified
        }
    }

    // ═══════════════════════════════════════════════════
    //  按钮点击处理
    // ═══════════════════════════════════════════════════

    private fun handleContentButtonClick(buttonIndex: Int) {
        val mask = when (buttonIndex) {
            0 -> 1
            1 -> 2
            2 -> 4
            else -> 0
        }
        _activeButtons.value = _activeButtons.value xor mask
    }

    private fun handleButtonClick(button: OptionButton) {
        try {
            if (button.storageSlot > 0) {
                floatingWindowManager.writeStorageSlot(button.storageSlot, button.name)
            }

            if (button.jumpTo > 0) {
                serviceScope.launch {
                    try {
                        val result = floatingWindowManager.jumpToSubList(button.jumpTo)
                        if (result != null) {
                            val (config, buttons, storageData) = result
                            val subList = floatingWindowManager.getCurrentSubList() ?: return@launch

                            val content = buildContentText(
                                subList = subList,
                                config = config ?: ContentConfig(
                                    subListId = subList.id,
                                    parentListId = floatingWindowManager.getCurrentMainList()?.id ?: 0,
                                ),
                                storageData = storageData,
                                fixedSlotData = floatingWindowManager.getCurrentFixedSlotData(),
                            )

                            _currentContent.value = content
                            _currentButtons.value = buttons
                            // 从 imageUris 解析图片列表
                            val imageUris = try {
                                val array = org.json.JSONArray(subList.imageUris)
                                val list = mutableListOf<String>()
                                for (i in 0 until array.length()) {
                                    list.add(array.getString(i))
                                }
                                list
                            } catch (_: Exception) {
                                emptyList<String>()
                            }
                            _currentImageUris.value = imageUris
                            _currentImageIndex.value = 0
                            _currentImageUri.value = imageUris.firstOrNull() ?: subList.imageUri
                            _currentImageEnabled.value = subList.imageEnabled
                            // 设置轮播速度：优先使用 SubList 局部设置，为0时使用全局设置
                            _currentCarouselInterval.value = if (subList.carouselInterval > 0) subList.carouselInterval else _globalCarouselInterval.value
                            // 跳转后重新启动轮播
                            startCarousel()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("FloatingWindowService: jumpToSubList failed", e)
                    }
                }
            } else {
                hidePopup()
            }
        } catch (e: Exception) {
            AppLogger.e("FloatingWindowService: handleButtonClick failed", e)
        }
    }

    // ═══════════════════════════════════════════════════
    //  拖动 + 双击检测
    // ═══════════════════════════════════════════════════

    private inner class IconTouchListener : View.OnTouchListener {
        override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
            val params = iconParams ?: return false

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    totalDragDistance = 0
                    dragStartX = event.rawX.toInt()
                    dragStartY = event.rawY.toInt()
                    iconStartX = params.x
                    iconStartY = params.y
                    return true
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - dragStartX
                    val dy = event.rawY.toInt() - dragStartY
                    totalDragDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy)

                    if (totalDragDistance > 10) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = iconStartX + dx
                        params.y = iconStartY + dy
                        floatingWindowManager.updateIconParams(params)
                    }
                    return true
                }

                android.view.MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < DOUBLE_CLICK_THRESHOLD) {
                            lastClickTime = 0L
                            onIconDoubleClick()
                        } else {
                            lastClickTime = now
                        }
                    }
                    isDragging = false
                    return true
                }
            }
            return false
        }
    }

    private fun onIconDoubleClick() {
        if (_isIconFlashing.value) {
            // 如果图标在闪烁（闹钟响起），先检查是否有关联的人生主线任务
            AppLogger.i("FloatingWindowService: icon double-clicked while flashing, checking for pending mainline task")
            serviceScope.launch {
                try {
                    // 检查TimerReminderService是否有待处理的人生主线任务
                    val pendingTaskId = TimerReminderService.getPendingMainlineTaskId()
                    if (pendingTaskId > 0) {
                        AppLogger.i("FloatingWindowService: 有待处理的人生主线任务: taskId=$pendingTaskId")
                        // 停止闹钟
                        TimerReminderService.stopEffectOnly(this@FloatingWindowService)
                        _isIconFlashing.value = false
                        // 清除待处理任务
                        TimerReminderService.clearPendingMainlineTask()
                        // 打开人生主线页面
                        val intent = Intent(this@FloatingWindowService, com.fuke.daily.MainActivity::class.java).apply {
                            action = "com.fuke.daily.OPEN_MAINLINE"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        return@launch
                    }
                } catch (e: Exception) {
                    AppLogger.e("FloatingWindowService: 检查待处理任务失败: ${e.message}")
                }
                // 没有待处理任务，停止闹钟并显示弹窗
                stopAlarmSound()
            }
        } else {
            // 正常双击，检查是否有待处理的人生主线任务
            AppLogger.i("FloatingWindowService: icon double-clicked, checking for pending mainline task")
            serviceScope.launch {
                try {
                    // 检查TimerReminderService是否有待处理的人生主线任务
                    val pendingTaskId = TimerReminderService.getPendingMainlineTaskId()
                    if (pendingTaskId > 0) {
                        AppLogger.i("FloatingWindowService: 有待处理的人生主线任务: taskId=$pendingTaskId")
                        // 清除待处理任务
                        TimerReminderService.clearPendingMainlineTask()
                        // 打开人生主线页面
                        val intent = Intent(this@FloatingWindowService, com.fuke.daily.MainActivity::class.java).apply {
                            action = "com.fuke.daily.OPEN_MAINLINE"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        return@launch
                    }
                } catch (e: Exception) {
                    AppLogger.e("FloatingWindowService: 检查待处理任务失败: ${e.message}")
                }
                // 没有待处理任务，正常显示弹窗
                AppLogger.i("FloatingWindowService: 没有待处理任务，显示弹窗")
                showPopup()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1
        private const val DOUBLE_CLICK_THRESHOLD = 300L

        const val ACTION_SHOW_ICON = "com.fuke.daily.SHOW_ICON"
        const val ACTION_HIDE_ICON = "com.fuke.daily.HIDE_ICON"
        const val ACTION_TOGGLE_POPUP = "com.fuke.daily.TOGGLE_POPUP"
        const val ACTION_NEXT_ITEM = "com.fuke.daily.NEXT_ITEM"
        const val ACTION_START_FLASHING = "com.fuke.daily.START_FLASHING"
        const val ACTION_STOP_FLASHING = "com.fuke.daily.STOP_FLASHING"
        const val ACTION_STOP_ALARM = "com.fuke.daily.STOP_ALARM"
        const val ACTION_OPEN_IMAGE_CAROUSEL = "com.fuke.daily.OPEN_IMAGE_CAROUSEL"
        const val ACTION_HIDE_ALL = "com.fuke.daily.HIDE_ALL"

        fun start(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW_ICON
            }
            context.startForegroundService(intent)
        }

        fun startFlashing(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_START_FLASHING
            }
            context.startForegroundService(intent)
        }

        fun stopFlashing(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP_FLASHING
            }
            context.startForegroundService(intent)
        }

        fun stopAlarm(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }
    }
}
