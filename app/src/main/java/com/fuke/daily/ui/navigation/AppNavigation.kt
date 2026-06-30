package com.fuke.daily.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuke.daily.feature.floating.FloatingWindowService
import com.fuke.daily.ui.home.HomeScreen
import com.fuke.daily.ui.log.LogScreen
import com.fuke.daily.ui.mainline.MainlineDailyScreen
import com.fuke.daily.ui.mainline.MainlineDetailScreen
import com.fuke.daily.ui.mainline.MainlineEditScreen
import com.fuke.daily.ui.mainline.MainlineSelectScreen
import com.fuke.daily.ui.memory.MemoryCardScreen
import com.fuke.daily.ui.permission.PermissionGuideScreen
import com.fuke.daily.ui.quiz.QuizConfigScreen
import com.fuke.daily.ui.random.RandomConfigScreen
import com.fuke.daily.ui.random.RichTextPage
import com.fuke.daily.ui.settings.SettingsScreen
import com.fuke.daily.ui.selection.SelectionConfigScreen
import com.fuke.daily.feature.timer.TimerConfigScreen
import com.fuke.daily.feature.timer.TimerListScreen

// ═══════════════════════════════════════════════════
//  路由定义
// ═══════════════════════════════════════════════════

object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val SELECTION_CONFIG = "selection/{listId}"
    const val RANDOM_RICH_TEXT = "random/{listId}/richtext"
    const val RANDOM_CONFIG = "random/{listId}/config"
    const val QUIZ_CONFIG = "quiz/{listId}"
    const val MAINLINE_SELECT = "mainline/select/{listId}"
    const val MAINLINE_DETAIL = "mainline/detail/{listId}"
    const val MAINLINE_EDIT = "mainline/edit/{listId}"
    const val TIMER_LIST = "timer"
    const val TIMER_CONFIG = "timer/config/{timerId}"
    const val MEMORY_CARD = "memory/{listId}"
    const val MAINLINE_DAILY = "mainline/daily"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
}

// ═══════════════════════════════════════════════════
//  导航入口
// ═══════════════════════════════════════════════════

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.PERMISSION) {
            PermissionGuideScreen(
                onAllRequiredPermissionsGranted = {
                    // 启动悬浮窗服务
                    try {
                        FloatingWindowService.start(context)
                    } catch (e: Exception) {
                        com.fuke.daily.util.AppLogger.e(
                            "AppNavigation: failed to start FloatingWindowService",
                            e,
                        )
                    }
                    // 导航到主页，清除权限页回栈
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToConfig = { route -> navController.navigate(route) },
                onNavigateToTimer = { navController.navigate(Routes.TIMER_LIST) },
                onNavigateToMainline = { navController.navigate(Routes.MAINLINE_DAILY) },
                onNavigateToLogs = { navController.navigate(Routes.LOGS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToMainlineDetail = { listId ->
                    navController.navigate("mainline/detail/$listId")
                },
                onNavigateToRichText = { listId ->
                    navController.navigate("random/$listId/richtext")
                },
                onNavigateToQuizConfig = { listId ->
                    navController.navigate("quiz/$listId")
                },
            )
        }

        composable(Routes.SELECTION_CONFIG) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            SelectionConfigScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.RANDOM_RICH_TEXT) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            RichTextPage(
                listId = listId,
                onBack = { navController.popBackStack() },
                onNavigateToConfig = { navController.navigate("random/$listId/config") },
            )
        }

        composable(Routes.RANDOM_CONFIG) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            RandomConfigScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
                onNavigateToRichText = { navController.popBackStack() },
            )
        }

        composable(Routes.QUIZ_CONFIG) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            QuizConfigScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MAINLINE_SELECT) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            MainlineSelectScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { navController.navigate("mainline/detail/$listId") },
            )
        }

        composable(Routes.MAINLINE_DETAIL) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            MainlineDetailScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate("mainline/edit/$listId") },
            )
        }

        composable(Routes.MAINLINE_EDIT) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            MainlineEditScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TIMER_LIST) {
            TimerListScreen(
                onAddTimer = {
                    navController.navigate("timer/config/0")
                },
                onEditTimer = { timerId ->
                    navController.navigate("timer/config/$timerId")
                },
            )
        }

        composable(Routes.TIMER_CONFIG) { backStackEntry ->
            val timerId = backStackEntry.arguments?.getString("timerId")?.toLongOrNull() ?: 0L
            TimerConfigScreen(
                timerId = timerId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MAINLINE_DAILY) {
            MainlineDailyScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMainlineDetail = { listId ->
                    navController.navigate("mainline/detail/$listId")
                },
            )
        }

        composable(Routes.MEMORY_CARD) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            MemoryCardScreen(
                listId = listId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.LOGS) {
            LogScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogs = { navController.navigate(Routes.LOGS) },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title)
    }
}
