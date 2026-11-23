package com.openjarvis.android.data.repository

import com.openjarvis.android.core.domain.model.ChatMessage
import com.openjarvis.android.core.domain.model.ConversationSummary
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.FeedbackRating
import com.openjarvis.android.core.domain.model.MessageRole
import com.openjarvis.android.data.local.db.ConversationDao
import com.openjarvis.android.data.local.db.ConversationEntity
import com.openjarvis.android.data.local.db.MessageEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
) {
    fun observeConversations(): Flow<List<ConversationSummary>> =
        conversationDao.observeConversations().map { list ->
            list.map {
                ConversationSummary(
                    id = it.id,
                    title = it.title,
                    updatedAtMillis = it.updatedAtMillis,
                    messageCount = conversationDao.countMessages(it.id),
                )
            }
        }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        conversationDao.observeMessages(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun ensureConversationExists(conversationId: Long): Long {
        val existing = conversationDao.getConversation(conversationId)
        if (existing != null) return existing.id
        val now = System.currentTimeMillis()
        return conversationDao.insertConversation(
            ConversationEntity(
                title = "JARVIS Session",
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun createNewConversation(title: String = "New Session"): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insertConversation(
            ConversationEntity(
                title = title,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun getRecentMessages(conversationId: Long, limit: Int = 14): List<ChatMessage> =
        conversationDao.getRecentMessages(conversationId, limit)
            .reversed()
            .map { it.toDomain() }

    suspend fun addMessage(message: ChatMessage): Long {
        val convId = ensureConversationExists(message.conversationId)
        val now = message.createdAtMillis
        val id = conversationDao.insertMessage(
            MessageEntity(
                conversationId = convId,
                role = message.role.name,
                content = message.content,
                engineUsed = message.engineUsed?.name,
                modelId = message.modelId,
                toolSummary = message.toolSummary,
                feedbackScore = message.feedback.score,
                latencyMs = message.latencyMs,
                createdAtMillis = now,
            )
        )
        if (message.role == MessageRole.USER) {
            val count = conversationDao.countMessages(convId)
            if (count <= 2) {
                val previewTitle = message.content.trim().take(42).ifBlank { "JARVIS Session" }
                conversationDao.updateConversationHeader(convId, previewTitle, now)
            } else {
                val current = conversationDao.getConversation(convId)
                conversationDao.updateConversationHeader(
                    convId,
                    current?.title ?: "JARVIS Session",
                    now,
                )
            }
        }
        return id
    }

    suspend fun setFeedback(messageId: Long, rating: FeedbackRating) {
        conversationDao.updateMessageFeedback(messageId, rating.score)
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteConversation(conversationId)
    }

    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.ASSISTANT),
        content = content,
        engineUsed = engineUsed?.let { name -> runCatching { EngineType.valueOf(name) }.getOrNull() },
        modelId = modelId,
        toolSummary = toolSummary,
        feedback = FeedbackRating.entries.firstOrNull { it.score == feedbackScore } ?: FeedbackRating.NONE,
        latencyMs = latencyMs,
        createdAtMillis = createdAtMillis,
    )
}
