package com.kosmos.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kosmos.app.feature.calendar.CalendarScreen
import com.kosmos.app.feature.chat.ChatScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppDestination.Splash.route,
    /** 셸(MainScreen)의 드로어 열기 — 채팅 헤더 ☰ 이 부른다 (시안 A′). */
    onOpenDrawer: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = AppDestination.Splash.route) {
            com.kosmos.app.feature.splash.SplashScreen(
                onInitializationComplete = {
                    navController.navigate(AppDestination.Chat.route) {
                        popUpTo(AppDestination.Splash.route) { inclusive = true }
                    }
                },
                // [WHY] 스플래시를 백스택에 남긴다(popUpTo 없음) — 내려받기를 마치고 돌아오면
                // 스플래시가 Ready 를 보고 자동으로 채팅에 진입한다.
                onNavigateToModelManagement = {
                    navController.navigate(AppDestination.ModelManagement.route)
                }
            )
        }
        
        composable(route = AppDestination.Chat.route) {
            val webSearchViewModel: com.kosmos.app.feature.chat.WebSearchViewModel =
                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val webSearchEnabled by webSearchViewModel.webSearchEnabled.collectAsStateWithLifecycle()
            ChatScreen(
                // [WHY] 설정 진입은 드로어 타일로 이동(M2-2) — ☰ 이 셸의 드로어를 연다.
                onMenuClick = onOpenDrawer,
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = webSearchViewModel::setWebSearchEnabled
            )
        }
        
        composable(route = AppDestination.Calendar.route) {
            CalendarScreen()
        }
        
        composable(route = AppDestination.Memory.route) {
            com.kosmos.app.feature.memory.MemoryScreen()
        }
        
        composable(route = AppDestination.Settings.route) {
            com.kosmos.app.feature.settings.SettingsScreen(
                onNavigateToAudit = {
                    navController.navigate(AppDestination.Audit.route)
                },
                onNavigateToModelManagement = {
                    navController.navigate(AppDestination.ModelManagement.route)
                }
            )
        }

        composable(route = AppDestination.ModelManagement.route) {
            com.kosmos.app.feature.settings.ModelManagementScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = AppDestination.Audit.route) {
            com.kosmos.app.feature.settings.AuditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
