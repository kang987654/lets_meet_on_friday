package com.kosmos.app.ui.feature.memory

enum class MemoryFilterType {
    ALL,
    TASK,
    KNOWLEDGE
}

data class MemoryUiState(
    val selectedFilter: MemoryFilterType = MemoryFilterType.ALL
)
