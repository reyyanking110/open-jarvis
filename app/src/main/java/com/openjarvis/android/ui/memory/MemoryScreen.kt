package com.openjarvis.android.ui.memory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.domain.model.MemoryEntry

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingEntry by remember { mutableStateOf<MemoryEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Persistent Memory", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "User-approved facts & preferences retrieved on-device via Room FTS4.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.setQuery(it) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search memories...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = state.selectedCategory == cat,
                                onClick = { viewModel.toggleCategory(cat) },
                                label = { Text(cat.label) },
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.routingStats.isNotEmpty()) {
                    item(key = "learning_stats") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Adaptive Feedback & Routing Metrics",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                state.routingStats.forEach { stat ->
                                    Text(
                                        text = "${stat.intentCategory.id} · ${stat.engineType.displayName}: " +
                                            "${stat.successCount}/${stat.totalAttempts} ok, " +
                                            "+${stat.positiveFeedbackCount}/-${stat.negativeFeedbackCount} feedback, " +
                                            "${stat.avgLatencyMs}ms avg",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.memories.isEmpty()) {
                    item {
                        Text(
                            text = "No approved memories yet. Tap + to add one or save from Chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.memories, key = { it.id }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = entry.category.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingEntry = entry }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { viewModel.deleteMemory(entry.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                                if (entry.tags.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Tags: ${entry.tags}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, content, cat, tags ->
                viewModel.addMemory(title, content, cat, tags)
                showAddDialog = false
            },
        )
    }

    editingEntry?.let { entry ->
        MemoryEditorDialog(
            existing = entry,
            onDismiss = { editingEntry = null },
            onSave = { title, content, cat, tags ->
                viewModel.updateMemory(
                    entry.copy(title = title, content = content, category = cat, tags = tags)
                )
                editingEntry = null
            },
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    existing: MemoryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory, String) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: MemoryCategory.FACT) }
    var tags by remember { mutableStateOf(existing?.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Memory" else "Edit Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content, category, tags) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
