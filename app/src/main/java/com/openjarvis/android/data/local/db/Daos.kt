package com.openjarvis.android.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: Long): ConversationEntity?

    @Insert
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateConversationHeader(id: Long, title: String, updatedAt: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun countMessages(conversationId: Long): Int

    @Insert
    suspend fun insertMessage(entity: MessageEntity): Long

    @Query("UPDATE messages SET feedbackScore = :score WHERE id = :messageId")
    suspend fun updateMessageFeedback(messageId: Long, score: Int)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAtMillis DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY salienceBoost DESC, updatedAtMillis DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query(
        """
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.id = memories_fts.rowid
        WHERE memories_fts MATCH :ftsQuery
        ORDER BY memories.salienceBoost DESC, memories.updatedAtMillis DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(entity: MemoryEntity): Long

    @Update
    suspend fun updateMemory(entity: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("UPDATE memories SET salienceBoost = salienceBoost + :delta, updatedAtMillis = :now WHERE id = :id")
    suspend fun adjustSalience(id: Long, delta: Int, now: Long)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY createdAtMillis DESC")
    fun observeTasks(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun observeStepsForTask(taskId: Long): Flow<List<AgentStepEntity>>

    @Query("SELECT * FROM agent_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    suspend fun getStepsForTask(taskId: Long): List<AgentStepEntity>

    @Insert
    suspend fun insertTask(entity: AgentTaskEntity): Long

    @Query("UPDATE agent_tasks SET status = :status, finalOutput = :finalOutput, completedAtMillis = :completedAt WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: String, finalOutput: String?, completedAt: Long?)

    @Insert
    suspend fun insertStep(entity: AgentStepEntity): Long

    @Update
    suspend fun updateStep(entity: AgentStepEntity)

    @Query("DELETE FROM agent_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM execution_traces ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecentTraces(limit: Int = 50): Flow<List<ExecutionTraceEntity>>

    @Insert
    suspend fun insertTrace(entity: ExecutionTraceEntity): Long

    @Query("UPDATE execution_traces SET feedbackScore = :score WHERE messageId = :messageId")
    suspend fun updateTraceFeedbackByMessage(messageId: Long, score: Int)

    @Query("SELECT * FROM execution_traces WHERE messageId = :messageId LIMIT 1")
    suspend fun getTraceByMessage(messageId: Long): ExecutionTraceEntity?

    @Query("SELECT * FROM routing_stats")
    fun observeRoutingStats(): Flow<List<RoutingStatEntity>>

    @Query("SELECT * FROM routing_stats WHERE intentCategory = :intentCategory AND engineType = :engineType LIMIT 1")
    suspend fun getRoutingStat(intentCategory: String, engineType: String): RoutingStatEntity?

    @Query("SELECT * FROM routing_stats WHERE intentCategory = :intentCategory")
    suspend fun getStatsForIntent(intentCategory: String): List<RoutingStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutingStat(stat: RoutingStatEntity)
}

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY installedAtMillis DESC")
    fun observeInstalledModels(): Flow<List<LocalModelEntity>>

    @Query("SELECT * FROM local_models WHERE modelId = :modelId LIMIT 1")
    suspend fun getInstalledModel(modelId: String): LocalModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstalledModel(entity: LocalModelEntity)

    @Query("DELETE FROM local_models WHERE modelId = :modelId")
    suspend fun deleteInstalledModel(modelId: String)
}
