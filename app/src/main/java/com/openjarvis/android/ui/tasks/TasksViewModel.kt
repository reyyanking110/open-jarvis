package com.openjarvis.android.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openjarvis.android.agent.AgentLiveState
import com.openjarvis.android.agent.AutonomousAgentEngine
import com.openjarvis.android.core.domain.model.AgentTask
import com.openjarvis.android.data.local.db.AgentTaskDao
import com.openjarvis.android.data.local.db.ExecutionTraceEntity
import com.openjarvis.android.data.repository.LearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val liveState: AgentLiveState = AgentLiveState(),
    val taskHistory: List<AgentTask> = emptyList(),
    val recentTraces: List<ExecutionTraceEntity> = emptyList(),
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val agentEngine: AutonomousAgentEngine,
    private val agentTaskDao: AgentTaskDao,
    learningRepository: LearningRepository,
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = combine(
        agentEngine.liveState,
        agentEngine.observeTaskHistory(),
        learningRepository.observeRecentTraces(30),
    ) { live, tasks, traces ->
        TasksUiState(
            liveState = live,
            taskHistory = tasks,
            recentTraces = traces,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(),
    )

    fun cancelActiveTask() {
        agentEngine.cancelActiveTask()
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            agentTaskDao.deleteTask(taskId)
        }
    }
}
