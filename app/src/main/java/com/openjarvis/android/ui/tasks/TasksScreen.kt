package com.openjarvis.android.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openjarvis.android.core.domain.model.AgentStepStatus
import com.openjarvis.android.core.domain.model.AgentTask
import com.openjarvis.android.core.domain.model.AgentTaskStatus
import com.openjarvis.android.ui.theme.JarvisAmber
import com.openjarvis.android.ui.theme.JarvisEmerald
import java.text.DateFormat
import java.util.Date

@Composable
fun TasksScreen(
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Agent Tasks & Traces", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Step-by-step plans, tool verifications, and execution traces.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.liveState.isBusy) {
                item(key = "active_task") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Active Execution", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { viewModel.cancelActiveTask() }) {
                                    Text("Cancel")
                                }
                            }
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Phase: ${state.liveState.phase.name}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            state.liveState.activeSteps.forEach { step ->
                                Text(
                                    text = "${step.stepIndex}. ${step.description} [${step.status.name}]",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            if (state.taskHistory.isEmpty()) {
                item {
                    Text(
                        text = "No agent tasks recorded yet. Send a command or question in Chat to see plans and verified steps here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.taskHistory, key = { it.id }) { task ->
                    TaskHistoryCard(
                        task = task,
                        onDelete = { viewModel.deleteTask(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskHistoryCard(
    task: AgentTask,
    onDelete: () -> Unit,
) {
    val statusColor = when (task.status) {
        AgentTaskStatus.COMPLETED -> JarvisEmerald
        AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED -> MaterialTheme.colorScheme.error
        AgentTaskStatus.AWAITING_CONFIRMATION -> JarvisAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.goal,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Task")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
                Text(
                    text = "${task.engineType.displayName} (${task.modelId})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(task.createdAtMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (task.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                task.steps.forEach { step ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = when (step.status) {
                                AgentStepStatus.COMPLETED -> Icons.Default.CheckCircle
                                AgentStepStatus.FAILED -> Icons.Default.ErrorOutline
                                else -> Icons.Default.Pending
                            },
                            contentDescription = null,
                            tint = when (step.status) {
                                AgentStepStatus.COMPLETED -> JarvisEmerald
                                AgentStepStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${step.stepIndex}. ${step.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (step.verified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = JarvisEmerald,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            step.resultSummary?.let {
                                Text(
                                    text = it,
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
