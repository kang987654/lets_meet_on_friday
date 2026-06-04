package com.localfriday.app.ui.feature.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val knowledgeItems = viewModel.knowledgePagingData.collectAsLazyPagingItems()
    val taskItems = viewModel.taskPagingData.collectAsLazyPagingItems()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memory",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            // Export / Import Placeholder (TASK-055)
            TextButton(onClick = { /* TODO: Export/Import dialog */ }) {
                Text("Export/Import")
            }
        }

        // Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedFilter == MemoryFilterType.ALL,
                    onClick = { viewModel.onFilterSelected(MemoryFilterType.ALL) },
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedFilter == MemoryFilterType.TASK,
                    onClick = { viewModel.onFilterSelected(MemoryFilterType.TASK) },
                    label = { Text("Tasks") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedFilter == MemoryFilterType.KNOWLEDGE,
                    onClick = { viewModel.onFilterSelected(MemoryFilterType.KNOWLEDGE) },
                    label = { Text("Knowledge") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lists
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.selectedFilter == MemoryFilterType.ALL || uiState.selectedFilter == MemoryFilterType.TASK) {
                item {
                    Text(
                        text = "Pending Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(
                    count = taskItems.itemCount,
                    key = taskItems.itemKey { it.id },
                    contentType = taskItems.itemContentType { "Task" }
                ) { index ->
                    val task = taskItems[index]
                    if (task != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            viewModel.completeTask(task.id)
                                            taskItems.refresh()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            if (uiState.selectedFilter == MemoryFilterType.ALL || uiState.selectedFilter == MemoryFilterType.KNOWLEDGE) {
                item {
                    Text(
                        text = "Knowledge Notes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(
                    count = knowledgeItems.itemCount,
                    key = knowledgeItems.itemKey { it.id },
                    contentType = knowledgeItems.itemContentType { "Knowledge" }
                ) { index ->
                    val note = knowledgeItems[index]
                    if (note != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = note.content, style = MaterialTheme.typography.bodyLarge)
                                if (note.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = note.tags.joinToString(" • "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
