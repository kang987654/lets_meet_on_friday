package com.localfriday.app.navigation

sealed class AppDestination(val route: String) {
    object Chat : AppDestination("chat")
    object Calendar : AppDestination("calendar")
    object Memory : AppDestination("memory")
    object Settings : AppDestination("settings")
}
