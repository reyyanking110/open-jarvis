package com.openjarvis.android.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.domain.model.MemoryEntry
import com.openjarvis.android.core.domain.model.RoutingStat
import com.openjarvis.android.data.repository.LearningRepository
import com.openjarvis.android.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryUiState(
    val query: String = "",
    val selectedCategory: MemoryCategory? = null,
    val memories: List<MemoryEntry> = emptyList(),
    val routingStats: List<RoutingStat> = emptyList(),
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    learningRepository: LearningRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<MemoryCategory?>(null)

    val uiState: StateFlow<MemoryUiState> = combine(
        searchQuery,
        categoryFilter,
        memoryRepository.observeMemories(),
        learningRepository.observeRoutingStats(),
    ) { query, cat, all, stats ->
        val filtered = all.filter { entry ->
            val matchesCat = cat == null || entry.category == cat
            val matchesQuery = query.isBlank() ||
                entry.title.contains(query, ignoreCase = true) ||
                entry.content.contains(query, ignoreCase = true) ||
                entry.tags.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }
        MemoryUiState(
            query = query,
            selectedCategory = cat,
            memories = filtered,
            routingStats = stats,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoryUiState(),
    )

    fun setQuery(q: String) {
        searchQuery.value = q
    }

    fun toggleCategory(cat: MemoryCategory) {
        categoryFilter.value = if (categoryFilter.value == cat) null else cat
    }

    fun addMemory(title: String, content: String, category: MemoryCategory, tags: String) {
        viewModelScope.launch {
            memoryRepository.saveApprovedMemory(title, content, category, tags)
        }
    }

    fun updateMemory(entry: MemoryEntry) {
        viewModelScope.launch {
            memoryRepository.updateMemory(entry)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }
}
