package com.kosmos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kosmos.app.feature.calendar.CalendarScreen
import com.kosmos.app.feature.chat.ChatScreen
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.feature.chat.ChatViewModel
import com.kosmos.app.feature.chat.WebSearchViewModel
import com.kosmos.app.feature.chat.result.DocSourceViewModel
import com.kosmos.app.feature.chat.result.ChatExtrasViewModel
import com.kosmos.app.feature.chat.result.ReminderEditSheetHost
import com.kosmos.app.feature.cleanup.MemoryCleanupScreen
import com.kosmos.app.feature.document.DocumentHomeScreen
import com.kosmos.app.feature.memory.MemoryFilterType
import com.kosmos.app.feature.memory.MemoryScreen
import com.kosmos.app.feature.settings.AuditScreen
import com.kosmos.app.feature.settings.ModelManagementScreen
import com.kosmos.app.feature.settings.SettingsScreen
import com.kosmos.app.feature.splash.SplashScreen
import com.kosmos.app.ui.component.rememberReducedMotion
import com.kosmos.app.ui.component.rememberToday
import com.kosmos.app.ui.theme.Motion
import com.kosmos.app.ui.theme.SplashStyle
import com.kosmos.app.feature.settings.EmbeddingModelSectionRoute

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppDestination.Splash.route,
    /** 설정의 시작 화면 장면 — 저장값을 읽기 전에는 null. */
    splashStyle: SplashStyle? = SplashStyle.PLANET,
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
    // [WHY] 화면의 뒤로·이동 버튼은 `dropUnlessResumed { }` 로 감싼다 — 전환(250ms) 중 나가는 화면의 ←를 한 번 더 누르면
    // 루트 가드로는 못 막는 두 단계 pop(모델 관리 → 설정을 건너뛰고 채팅)이, 이동 버튼을 두 번 누르면 같은 화면이 두 겹 쌓였다(0.41.9 외부 분석 C2).
    // 내비게이션 항목은 전환이 끝나야 RESUMED 라 그 사이 누름을 버린다.
    fun navigateBack() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    // [WHY] 전환을 정하지 않으면 내비게이션 기본(페이드 700ms)이라 두 화면이 겹쳐 비쳤다 — 공유 축 250ms(0.38.4 M3, 사용자 선택 B).
    // pop 쪽을 따로 주면 시스템 예측 뒤로(끌기)가 같은 움직임을 따라간다.
    val nav = Motion.NavTransitions(
        androidx.compose.ui.platform.LocalDensity.current,
        rememberReducedMotion()
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
            SplashScreen(
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
                onNavigateToModelManagement = dropUnlessResumed {
                    navController.navigate(AppDestination.ModelManagement.route) { launchSingleTop = true }
                }
            )
        }
        
        composable(route = AppDestination.Chat.route) {
            val webSearchViewModel: WebSearchViewModel =
                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val webSearchEnabled by webSearchViewModel.webSearchEnabled.collectAsStateWithLifecycle()
            val docSearchEnabled by webSearchViewModel.docSearchEnabled.collectAsStateWithLifecycle()
            val docSearchAvailable by webSearchViewModel.docSearchAvailable.collectAsStateWithLifecycle()
            // [WHY] 결과 카드·요약 타일(0.38.2 M3) — ChatViewModel 과 나눠 E2E 의 단독 compose 계약을 지킨다.
            val extras: ChatExtrasViewModel =
                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val resultStates by extras.results.collectAsStateWithLifecycle()
            val summary by extras.summary.collectAsStateWithLifecycle()
            // [WHY] ChatScreen 의 기본 hiltViewModel() 과 같은 인스턴스(같은 내비게이션 항목) — 수정 결과 한 줄을 대화 끝에 붙이려고 꺼낸다.
            val chatViewModel: ChatViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            // [WHY] 출처 카드(0.41.2) — 누르면 문서를 뷰어의 그 위치로 연다.
            val docSources: DocSourceViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val context = androidx.compose.ui.platform.LocalContext.current
            val today = rememberToday()
            androidx.compose.runtime.LaunchedEffect(today) { extras.setToday(today) }
            ChatScreen(
                viewModel = chatViewModel,
                // [WHY] 설정 진입은 드로어 타일로 이동(M2-2) — ☰ 이 셸의 드로어를 연다.
                onMenuClick = onOpenDrawer,
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = webSearchViewModel::setWebSearchEnabled,
                docSearchEnabled = docSearchEnabled,
                onToggleDocSearch = webSearchViewModel::setDocSearchEnabled,
                docSearchAvailable = docSearchAvailable,
                jumpToTimestamp = jumpToTimestamp,
                onJumpConsumed = onJumpConsumed,
                resultStates = resultStates,
                onEnsureResult = { extras.ensure(context, it) },
                summary = summary,
                onRefreshSummary = { extras.refreshSummary() },
                onOpenTasks = { navController.navigate(AppDestination.MemoryTasks.route) { launchSingleTop = true } },
                onOpenCalendar = { navController.navigate(AppDestination.Calendar.route) { launchSingleTop = true } },
                // [WHY] 알림만 수정 시트, 일정·기억은 해당 화면으로 바로(사용자 결정 2026-10-19).
                onResultClick = { message ->
                    val result = message.result ?: return@ChatScreen
                    when (result.kind) {
                        MessageResult.Kind.REMINDER -> extras.openReminder(context, result.ref)
                        MessageResult.Kind.SCHEDULE ->
                            navController.navigate(AppDestination.Calendar.route) { launchSingleTop = true }
                        MessageResult.Kind.MEMORY_NOTE,
                        MessageResult.Kind.MEMORY_RECALL ->
                            navController.navigate(AppDestination.Memory.route) { launchSingleTop = true }
                        MessageResult.Kind.DOC_SOURCE -> docSources.open(context, result)
                    }
                },
                overlay = { ReminderEditSheetHost(extras, onSaved = chatViewModel::appendLocalMessage) }
            )
        }
        
        // [WHY] 아래 세 화면은 Scaffold 가 없다 — 상태바는 화면의 KosmosTopBar(M3 TopAppBar)가 지고, 내비게이션 바만
        // 여기서 감아 준다(채팅은 자기 헤더·입력바가 인셋을 직접 진다, AGENTS §2-③).
        composable(route = AppDestination.Calendar.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                CalendarScreen(onBack = dropUnlessResumed { navigateBack() })
            }
        }

        composable(route = AppDestination.Memory.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                MemoryScreen(onBack = dropUnlessResumed { navigateBack() })
            }
        }

        composable(route = AppDestination.MemoryTasks.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                MemoryScreen(
                    onBack = dropUnlessResumed { navigateBack() },
                    initialFilter = MemoryFilterType.TASK
                )
            }
        }

        composable(route = AppDestination.Settings.route) {
            Box(modifier = Modifier.navigationBarsPadding()) {
                SettingsScreen(
                    onNavigateToModelManagement = dropUnlessResumed {
                        navController.navigate(AppDestination.ModelManagement.route) { launchSingleTop = true }
                    },
                    onNavigateToMemoryCleanup = dropUnlessResumed {
                        navController.navigate(AppDestination.MemoryCleanup.route) { launchSingleTop = true }
                    },
                    onBack = dropUnlessResumed { navigateBack() },
                    embeddingSection = { EmbeddingModelSectionRoute() }
                )
            }
        }

        composable(route = AppDestination.Documents.route) {
            DocumentHomeScreen(
                onBack = dropUnlessResumed { navigateBack() }
            )
        }

        composable(route = AppDestination.MemoryCleanup.route) {
            MemoryCleanupScreen(
                onBack = dropUnlessResumed { navigateBack() }
            )
        }

        composable(route = AppDestination.ModelManagement.route) {
            ModelManagementScreen(
                onBack = dropUnlessResumed { navigateBack() }
            )
        }

        composable(route = AppDestination.Audit.route) {
            AuditScreen(
                onNavigateBack = dropUnlessResumed { navigateBack() }
            )
        }
    }
}
