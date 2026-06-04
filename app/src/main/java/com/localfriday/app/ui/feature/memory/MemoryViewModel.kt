package com.localfriday.app.ui.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.localfriday.app.domain.memory.KnowledgeRepository
import com.localfriday.app.domain.memory.TaskRepository
import com.localfriday.app.domain.model.KnowledgeNote
import com.localfriday.app.domain.model.TaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    // Paging Data Flows
    val knowledgePagingData: Flow<PagingData<KnowledgeNote>> =
        knowledgeRepository.getPagedData().cachedIn(viewModelScope)

    val taskPagingData: Flow<PagingData<TaskItem>> =
        taskRepository.getPendingTasksData().cachedIn(viewModelScope)

    fun onFilterSelected(filterType: MemoryFilterType) {
        _uiState.update { it.copy(selectedFilter = filterType) }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.updateCompletion(taskId, true)
            // Note: PagingData will not automatically refresh unless you invalidate the PagingSource
            // For MVP, we can assume completing a task just updates the DB. In production, Paging 3 needs manual invalidation.
        }
    }
}
