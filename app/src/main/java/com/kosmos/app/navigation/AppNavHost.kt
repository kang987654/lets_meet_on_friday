package com.kosmos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
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
    onOpenDrawer: () -> Unit = {},
    /** 드로어 에피소드 시트의 "원문 대화 보기" 요청 시각 — ChatScreen 이 소비한다 (M2-5). */
    jumpToTimestamp: Long? = null,
    onJumpConsumed: () -> Unit = {}
) {
    // [WHY] popBackStack() 을 그대로 노출하면 화면 내 "뒤로" 버튼과 시스템 예측 뒤로가기가
    // 같은 전환 구간에 겹칠 때 **두 번 pop** 될 수 있다 — 루트(chat)까지 뽑혀 NavHost 가
    // 비면 오로라 배경만 남는 빈 화면이 되고, 복구 수단이 없다(2026-08-15 실기기: 화면 이동
    // 중 빈 화면, 시맨틱 트리 노드 0개로 확인). 루트가 마지막 항목이면 pop 하지 않는다.
    fun navigateBack() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

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
                        // [WHY] Ready 가 재진입 요동(Ready→FileFound→Ready, 0.16.2 수명주기)으로
                        // 두 번 올 수 있다 — 없으면 chat 이 중복 푸시되어 ViewModel 이 두 벌 뜬다.
                        launchSingleTop = true
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
                onToggleWebSearch = webSearchViewModel::setWebSearchEnabled,
                jumpToTimestamp = jumpToTimestamp,
                onJumpConsumed = onJumpConsumed
            )
        }
        
        // [WHY] 아래 세 화면은 자체 Scaffold/TopAppBar 가 없어 M2-2 전에는 셸 Scaffold 의
        // innerPadding 이 시스템 바 여백을 대신 만들어 줬다. 셸 Scaffold 가 사라졌으므로
        // 여기서 시스템 바 패딩을 감아 준다 (채팅은 자기 헤더·입력바가 인셋을 직접 진다).
        composable(route = AppDestination.Calendar.route) {
            Box(modifier = Modifier.systemBarsPadding()) {
                CalendarScreen()
            }
        }

        composable(route = AppDestination.Memory.route) {
            Box(modifier = Modifier.systemBarsPadding()) {
                com.kosmos.app.feature.memory.MemoryScreen()
            }
        }

        composable(route = AppDestination.Settings.route) {
            Box(modifier = Modifier.systemBarsPadding()) {
                com.kosmos.app.feature.settings.SettingsScreen(
                    onNavigateToModelManagement = {
                        navController.navigate(AppDestination.ModelManagement.route)
                    }
                )
            }
        }

        composable(route = AppDestination.ModelManagement.route) {
            com.kosmos.app.feature.settings.ModelManagementScreen(
                onBack = { navigateBack() }
            )
        }

        composable(route = AppDestination.Audit.route) {
            com.kosmos.app.feature.settings.AuditScreen(
                onNavigateBack = { navigateBack() }
            )
        }
    }
}
