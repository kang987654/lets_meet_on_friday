package com.kosmos.app.feature.memory

enum class MemoryFilterType {
    TASK,
    KNOWLEDGE
}

data class MemoryUiState(
    // [WHY] 기존 기본값 ALL은 두 탭(Memory/Tasks) 중 어느 것도 선택되지 않은 상태로 렌더링돼
    // 첫 진입 시 탭이 모두 비활성처럼 보였다. 기본 탭을 KNOWLEDGE로 고정한다.
    val selectedFilter: MemoryFilterType = MemoryFilterType.KNOWLEDGE
)
