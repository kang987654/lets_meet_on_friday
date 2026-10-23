package com.kosmos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
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
    /** 설정의 시작 화면 장면 — 저장값을 읽기 전에는 null. */
    splashStyle: com.kosmos.app.ui.theme.SplashStyle? = com.kosmos.app.ui.theme.SplashStyle.PLANET,
    /** 셸(MainScreen)의 드로어 열기 — 채팅 헤더 ☰ 이 부른다 (시안 A′). */
    onOpenDrawer: () -> Unit = {},
    /** 드로어 에피소드 시트의 "채팅에서 보기" 요청 시각 — ChatScreen 이 소비한다 (M2-5). */
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

    // [WHY] 전환을 정하지 않으면 내비게이션 기본(페이드 700ms)이라 두 화면이 겹쳐 비쳤다 — 공유 축 250ms(0.38.4 M3, 사용자 선택 B).
    // pop 쪽을 따로 주면 시스템 예측 뒤로(끌기)가 같은 움직임을 따라간다.
    val nav = com.kosmos.app.ui.theme.Motion.NavTransitions(
        androidx.compose.ui.platform.LocalDensity.current,
        com.kosmos.app.ui.component.rememberReducedMotion()
    )
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // [WHY] 스플래시 → 채팅은 내비게이션 전환 없이 바로 바꾼다 — 셸의 덮개(SplashOverlay)가 장면을 이어 그리며 채팅을 드러낸다.
        enterTransition = { if (initialState.destination.route == AppDestination.Splash.route) androidx.compose.animation.EnterTransition.None else nav.enter() },
        exitTransition = { if (initialState.destination.route == AppDestination.Splash.route) androidx.compose.animation.ExitTransition.None else nav.exit() },
        popEnterTransition = { nav.popEnter() },
        popExitTransition = { nav.popExit() }
    ) {
        composable(route = AppDestination.Splash.route) {
            com.kosmos.app.feature.splash.SplashScreen(
                splashStyle = splashStyle,
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
            // [WHY] 결과 카드·요약 타일(0.38.2 M3) — ChatViewModel 과 나눠 E2E 의 단독 compose 계약을 지킨다.
            val extras: com.kosmos.app.feature.chat.result.ChatExtrasViewModel =
                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val resultStates by extras.results.collectAsStateWithLifecycle()
            val summary by extras.summary.collectAsStateWithLifecycle()
            // [WHY] ChatScreen 의 기본 hiltViewModel() 과 같은 인스턴스(같은 내비게이션 항목) — 수정 결과 한 줄을 대화 끝에 붙이려고 꺼낸다.
            val chatViewModel: com.kosmos.app.feature.chat.ChatViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            ChatScreen(
                viewModel = chatViewModel,
                // [WHY] 설정 진입은 드로어 타일로 이동(M2-2) — ☰ 이 셸의 드로어를 연다.
                onMenuClick = onOpenDrawer,
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = webSearchViewModel::setWebSearchEnabled,
                jumpToTimestamp = jumpToTimestamp,
                onJumpConsumed = onJumpConsumed,
                resultStates = resultStates,
                onEnsureResult = { extras.ensure(it) },
                summary = summary,
                onRefreshSummary = { extras.refreshSummary() },
                onOpenTasks = { navController.navigate(AppDestination.MemoryTasks.route) { launchSingleTop = true } },
                onOpenCalendar = { navController.navigate(AppDestination.Calendar.route) { launchSingleTop = true } },
                // [WHY] 알림만 수정 시트, 일정·기억은 해당 화면으로 바로(사용자 결정 2026-10-19).
                onResultClick = { message ->
                    val result = message.result ?: return@ChatScreen
                    when (result.kind) {
                        com.kosmos.app.domain.model.MessageResult.Kind.REMINDER -> extras.openReminder(result.ref)
                        com.kosmos.app.domain.model.MessageResult.Kind.SCHEDULE ->
                            navController.navigate(AppDestination.Calendar.route) { launchSingleTop = true }
                        com.kosmos.app.domain.model.MessageResult.Kind.MEMORY_NOTE,
                        com.kosmos.app.domain.model.MessageResult.Kind.MEMORY_RECALL ->
                            navController.navigate(AppDestination.Memory.route) { launchSingleTop = true }
                    }
                },
                overlay = { com.kosmos.app.feature.chat.result.ReminderEditSheetHost(extras, onSaved = chatViewModel::appendLocalMessage) }
            )
        }
        
        // [WHY] 아래 세 화면은 Scaffold 가 없다 — 상태바는 화면의 KosmosTopBar(M3 TopAppBar)가 지고, 내비게이션 바만
        // 여기서 감아 준다(채팅은 자기 헤더·입력바가 인셋을 직접 진다, AGENTS §2-③).
        composable(route = AppDestination.Calendar.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                CalendarScreen(onBack = { navigateBack() })
            }
        }

        composable(route = AppDestination.Memory.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                com.kosmos.app.feature.memory.MemoryScreen(onBack = { navigateBack() })
            }
        }

        composable(route = AppDestination.MemoryTasks.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                com.kosmos.app.feature.memory.MemoryScreen(
                    onBack = { navigateBack() },
                    initialFilter = com.kosmos.app.feature.memory.MemoryFilterType.TASK
                )
            }
        }

        composable(route = AppDestination.Settings.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                com.kosmos.app.feature.settings.SettingsScreen(
                    onNavigateToModelManagement = {
                        navController.navigate(AppDestination.ModelManagement.route)
                    },
                    onNavigateToMemoryCleanup = {
                        navController.navigate(AppDestination.MemoryCleanup.route)
                    },
                    onBack = { navigateBack() }
                )
            }
        }

        composable(route = AppDestination.Documents.route) {
            com.kosmos.app.feature.document.DocumentHomeScreen(
                onBack = { navigateBack() }
            )
        }

        composable(route = AppDestination.MemoryCleanup.route) {
            com.kosmos.app.feature.cleanup.MemoryCleanupScreen(
                onBack = { navigateBack() }
            )
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
