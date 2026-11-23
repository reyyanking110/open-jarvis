package com.openjarvis.android.data.repository

import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.FeedbackRating
import com.openjarvis.android.core.domain.model.IntentCategory
import com.openjarvis.android.core.domain.model.RoutingStat
import com.openjarvis.android.data.local.db.ExecutionTraceEntity
import com.openjarvis.android.data.local.db.LearningDao
import com.openjarvis.android.data.local.db.RoutingStatEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LearningRepository @Inject constructor(
    private val learningDao: LearningDao,
) {
    fun observeRecentTraces(limit: Int = 50): Flow<List<ExecutionTraceEntity>> =
        learningDao.observeRecentTraces(limit)

    fun observeRoutingStats(): Flow<List<RoutingStat>> =
        learningDao.observeRoutingStats().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun recordExecution(
        taskId: Long?,
        messageId: Long?,
        intentCategory: IntentCategory,
        engineType: EngineType,
        modelId: String,
        routingReason: String,
        success: Boolean,
        latencyMs: Long,
        stepCount: Int,
        errorSummary: String? = null,
    ) {
        val now = System.currentTimeMillis()
        learningDao.insertTrace(
            ExecutionTraceEntity(
                taskId = taskId,
                messageId = messageId,
                intentCategory = intentCategory.name,
                engineType = engineType.name,
                modelId = modelId,
                routingReason = routingReason,
                success = success,
                latencyMs = latencyMs,
                stepCount = stepCount,
                errorSummary = errorSummary,
                createdAtMillis = now,
            )
        )

        val existing = learningDao.getRoutingStat(intentCategory.name, engineType.name)
        val updated = RoutingStatEntity(
            intentCategory = intentCategory.name,
            engineType = engineType.name,
            totalAttempts = (existing?.totalAttempts ?: 0) + 1,
            successCount = (existing?.successCount ?: 0) + if (success) 1 else 0,
            positiveFeedbackCount = existing?.positiveFeedbackCount ?: 0,
            negativeFeedbackCount = existing?.negativeFeedbackCount ?: 0,
            totalLatencyMs = (existing?.totalLatencyMs ?: 0L) + latencyMs.coerceAtLeast(0L),
            updatedAtMillis = now,
        )
        learningDao.upsertRoutingStat(updated)
    }

    suspend fun applyFeedback(messageId: Long, rating: FeedbackRating) {
        learningDao.updateTraceFeedbackByMessage(messageId, rating.score)
        val trace = learningDao.getTraceByMessage(messageId) ?: return
        val existing = learningDao.getRoutingStat(trace.intentCategory, trace.engineType) ?: return
        val deltaPos = if (rating == FeedbackRating.POSITIVE) 1 else 0
        val deltaNeg = if (rating == FeedbackRating.NEGATIVE) 1 else 0
        learningDao.upsertRoutingStat(
            existing.copy(
                positiveFeedbackCount = existing.positiveFeedbackCount + deltaPos,
                negativeFeedbackCount = existing.negativeFeedbackCount + deltaNeg,
                updatedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    suspend fun getStatsForIntent(intentCategory: IntentCategory): List<RoutingStat> =
        learningDao.getStatsForIntent(intentCategory.name).map { it.toDomain() }

    /**
     * Computes a deterministic utility score from observed success rate, feedback ratio, and latency.
     */
    fun utilityScore(stat: RoutingStat): Double {
        if (stat.totalAttempts == 0) return 0.5
        val successWeight = stat.successRate * 0.65
        val feedbackDenom = (stat.positiveFeedbackCount + stat.negativeFeedbackCount).coerceAtLeast(1)
        val feedbackBalance =
            (stat.positiveFeedbackCount - stat.negativeFeedbackCount).toDouble() / feedbackDenom
        val feedbackWeight = ((feedbackBalance + 1.0) / 2.0) * 0.25
        val latencyPenalty = (stat.avgLatencyMs.coerceAtMost(15_000L) / 15_000.0) * 0.10
        return (successWeight + feedbackWeight + (0.10 - latencyPenalty)).coerceIn(0.0, 1.0)
    }

    private fun RoutingStatEntity.toDomain(): RoutingStat = RoutingStat(
        intentCategory = runCatching { IntentCategory.valueOf(intentCategory) }
            .getOrDefault(IntentCategory.CHAT),
        engineType = runCatching { EngineType.valueOf(engineType) }
            .getOrDefault(EngineType.GEMINI_CLOUD),
        totalAttempts = totalAttempts,
        successCount = successCount,
        positiveFeedbackCount = positiveFeedbackCount,
        negativeFeedbackCount = negativeFeedbackCount,
        avgLatencyMs = if (totalAttempts > 0) totalLatencyMs / totalAttempts else 0L,
    )
}
