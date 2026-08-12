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
    startDestination: String = AppDestination.Splash.route
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
                }
            )
        }
        
        composable(route = AppDestination.Chat.route) {
            val webSearchViewModel: com.kosmos.app.feature.chat.WebSearchViewModel =
                androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val webSearchEnabled by webSearchViewModel.webSearchEnabled.collectAsStateWithLifecycle()
            ChatScreen(
                onSettingsClick = {
                    navController.navigate(AppDestination.Settings.route)
                },
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
