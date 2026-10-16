package com.kosmos.app.navigation

sealed class AppDestination(val route: String) {
    object Splash : AppDestination("splash")
    object Chat : AppDestination("chat")
    object Calendar : AppDestination("calendar")
    object Memory : AppDestination("memory")
    /** 메모·할 일 화면을 할 일 탭으로 — 채팅 요약 타일의 입구(0.38.2). */
    object MemoryTasks : AppDestination("memory_tasks")
    object Settings : AppDestination("settings")
    object ModelManagement : AppDestination("model_management")
    object Audit : AppDestination("audit")
    object MemoryCleanup : AppDestination("memory_cleanup")
    object Documents : AppDestination("documents")
}
