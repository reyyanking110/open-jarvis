package com.openjarvis.android.data.repository

import com.openjarvis.android.core.domain.model.MemoryCategory
import com.openjarvis.android.core.domain.model.MemoryEntry
import com.openjarvis.android.data.local.db.MemoryDao
import com.openjarvis.android.data.local.db.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Pure relevance scorer separated from Room for deterministic unit testing.
 */
object MemoryRelevanceScorer {
    private val stopWords = setOf(
        "the", "a", "an", "is", "are", "was", "were", "to", "of", "in", "on", "for",
        "and", "or", "with", "my", "me", "i", "you", "it", "this", "that", "please",
        "can", "could", "would", "jarvis", "open", "do", "make", "tell", "what",
    )

    fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()

    fun buildFtsQuery(query: String): String {
        val tokens = tokenize(query).take(6)
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "${it}*" }
    }

    fun score(queryTokens: Set<String>, memory: MemoryEntry): Double {
        if (queryTokens.isEmpty()) return memory.salienceBoost * 0.1
        val titleTokens = tokenize(memory.title)
        val bodyTokens = tokenize(memory.content)
        val tagTokens = tokenize(memory.tags)

        val titleHits = queryTokens.intersect(titleTokens).size * 3.0
        val tagHits = queryTokens.intersect(tagTokens).size * 2.5
        val bodyHits = queryTokens.intersect(bodyTokens).size * 1.2
        val overlapDenominator = queryTokens.size.coerceAtLeast(1).toDouble()
        val lexicalScore = (titleHits + tagHits + bodyHits) / overlapDenominator
        val salienceScore = memory.salienceBoost.coerceIn(-5, 10) * 0.15
        return lexicalScore + salienceScore
    }
}

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
) {
    fun observeMemories(): Flow<List<MemoryEntry>> =
        memoryDao.observeAllMemories().map { list -> list.map { it.toDomain() } }

    suspend fun saveApprovedMemory(
        title: String,
        content: String,
        category: MemoryCategory,
        tags: String = "",
    ): Long {
        val now = System.currentTimeMillis()
        return memoryDao.insertMemory(
            MemoryEntity(
                title = title.trim(),
                content = content.trim(),
                category = category.name,
                tags = tags.trim(),
                salienceBoost = 1,
                approvedByUser = true,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun updateMemory(entry: MemoryEntry) {
        memoryDao.updateMemory(
            MemoryEntity(
                id = entry.id,
                title = entry.title.trim(),
                content = entry.content.trim(),
                category = entry.category.name,
                tags = entry.tags.trim(),
                salienceBoost = entry.salienceBoost,
                approvedByUser = entry.approvedByUser,
                createdAtMillis = entry.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun adjustSalience(id: Long, delta: Int) {
        memoryDao.adjustSalience(id, delta, System.currentTimeMillis())
    }

    /**
     * Combines SQLite FTS4 candidate lookup with token/tag/salience re-ranking.
     */
    suspend fun retrieveRelevantMemories(query: String, limit: Int = 5): List<MemoryEntry> {
        val queryTokens = MemoryRelevanceScorer.tokenize(query)
        val ftsQuery = MemoryRelevanceScorer.buildFtsQuery(query)
        val candidates = if (ftsQuery.isNotBlank()) {
            runCatching { memoryDao.searchFts(ftsQuery, limit * 4) }
                .getOrElse { emptyList() }
                .ifEmpty { memoryDao.getAllMemories() }
        } else {
            memoryDao.getAllMemories()
        }.map { it.toDomain() }

        if (queryTokens.isEmpty()) {
            return candidates.take(limit)
        }

        return candidates
            .map { entry -> entry to MemoryRelevanceScorer.score(queryTokens, entry) }
            .filter { (_, score) -> score > 0.2 }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (entry, _) -> entry }
    }

    private fun MemoryEntity.toDomain(): MemoryEntry = MemoryEntry(
        id = id,
        title = title,
        content = content,
        category = runCatching { MemoryCategory.valueOf(category) }.getOrDefault(MemoryCategory.FACT),
        tags = tags,
        salienceBoost = salienceBoost,
        approvedByUser = approvedByUser,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
