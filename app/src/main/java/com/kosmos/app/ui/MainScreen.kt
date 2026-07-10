package com.kosmos.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kosmos.app.navigation.AppDestination
import com.kosmos.app.navigation.AppNavHost

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                val SkyBlue = Color(0xFF5BC2E7)

                val items = listOf(
                    BottomNavItem("채팅", AppDestination.Chat.route),
                    BottomNavItem("일정", AppDestination.Calendar.route),
                    BottomNavItem("메모리", AppDestination.Memory.route),
                    BottomNavItem("설정", AppDestination.Settings.route)
                )

                items.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon = { }, // 임시: 아이콘 제거
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SkyBlue,
                            unselectedIconColor = Color.Gray,
                            selectedTextColor = SkyBlue,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = SkyBlue.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

data class BottomNavItem(
    val label: String,
    val route: String
)
