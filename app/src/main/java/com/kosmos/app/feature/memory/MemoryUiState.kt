package com.kosmos.app.feature.memory

enum class MemoryFilterType {
    ALL,
    TASK,
    KNOWLEDGE
}

data class MemoryUiState(
    val selectedFilter: MemoryFilterType = MemoryFilterType.ALL
)
