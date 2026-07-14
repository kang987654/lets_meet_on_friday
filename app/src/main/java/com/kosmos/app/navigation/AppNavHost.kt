package com.kosmos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kosmos.app.feature.calendar.CalendarScreen
import com.kosmos.app.feature.chat.ChatScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppDestination.Chat.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = AppDestination.Chat.route) {
            ChatScreen()
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

@Composable
fun PlaceholderScreen(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.Gray)
    }
}
