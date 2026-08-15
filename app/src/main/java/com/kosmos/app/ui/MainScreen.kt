package com.kosmos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kosmos.app.feature.drawer.AppDrawerContent
import com.kosmos.app.navigation.AppDestination
import com.kosmos.app.navigation.AppNavHost
import com.kosmos.app.ui.theme.KosmosTheme
import kotlinx.coroutines.launch

/**
 * 앱 셸 — 드로어 + 네비게이션 호스트 (시안 A′, M2-2).
 *
 * [WHY] 하단 탭을 제거했다. 비서의 본체는 대화이고(ADR-022) 채팅이 루트다 — 캘린더·기억·
 * 설정은 좌측 드로어의 보조 공간으로 이동해 채팅 몰입을 방해하지 않는다.
 *
 * [WHY] 드로어가 셸 층에 있는 이유: ① 드로어 타일이 navController 를 직접 써야 하는데
 * ChatScreen 은 navController 를 모른다(기존 구조 유지) ② E2E 가 ChatScreen 을 단독
 * compose 하므로 드로어를 화면 안에 넣으면 테스트마다 딸려온다.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // [WHY] 드로어가 열려 있을 때 시스템 뒤로가기는 드로어를 닫는다 — 앱 종료가 아니라.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // [WHY] 엣지 스와이프는 채팅(루트)에서만 — 푸시된 화면(캘린더 등)에서 스와이프로
        // 드로어가 열리면 뒤로가기 제스처와 충돌한다.
        gesturesEnabled = currentRoute == AppDestination.Chat.route,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = KosmosTheme.colors.surface,
                drawerContentColor = KosmosTheme.colors.textPrimary
            ) {
                AppDrawerContent(
                    onNavigateToCalendar = { navigateFromDrawer(AppDestination.Calendar.route) },
                    onNavigateToAudit = { navigateFromDrawer(AppDestination.Audit.route) },
                    onNavigateToSettings = { navigateFromDrawer(AppDestination.Settings.route) },
                    onNavigateToMemory = { navigateFromDrawer(AppDestination.Memory.route) }
                )
            }
        }
    ) {
        AppNavHost(
            navController = navController,
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}
