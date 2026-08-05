package com.kosmos.app.ui

import com.kosmos.app.ui.theme.KosmosTheme
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
import androidx.compose.ui.unit.dp
import com.kosmos.app.navigation.AppDestination
import com.kosmos.app.navigation.AppNavHost
import com.kosmos.app.ui.component.glassEffect

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.glassEffect(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ),
                containerColor = Color.Transparent
            ) {
                val SkyBlue = KosmosTheme.colors.accent

                val items = listOf(
                    BottomNavItem("채팅", AppDestination.Chat.route),
                    BottomNavItem("일정", AppDestination.Calendar.route),
                    BottomNavItem("메모리", AppDestination.Memory.route)
                )

                items.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon = { }, // 임시: 아이콘 제거
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(AppDestination.Chat.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SkyBlue,
                            unselectedIconColor = KosmosTheme.colors.textMuted,
                            selectedTextColor = SkyBlue,
                            unselectedTextColor = KosmosTheme.colors.textMuted,
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
