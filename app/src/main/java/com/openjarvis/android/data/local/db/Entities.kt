package com.openjarvis.android.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val engineUsed: String? = null,
    val modelId: String? = null,
    val toolSummary: String? = null,
    val feedbackScore: Int = 0,
    val latencyMs: Long = 0L,
    val createdAtMillis: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val category: String,
    val tags: String = "",
    val salienceBoost: Int = 0,
    val approvedByUser: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val title: String,
    val content: String,
    val tags: String,
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val goal: String,
    val status: String,
    val engineType: String,
    val modelId: String,
    val finalOutput: String? = null,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null,
)

@Entity(
    tableName = "agent_steps",
    foreignKeys = [
        ForeignKey(
            entity = AgentTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class AgentStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val stepIndex: Int,
    val description: String,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    val status: String,
    val resultSummary: String? = null,
    val verified: Boolean = false,
    val attempts: Int = 0,
    val errorMessage: String? = null,
)

@Entity(tableName = "execution_traces")
data class ExecutionTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long? = null,
    val messageId: Long? = null,
    val intentCategory: String,
    val engineType: String,
    val modelId: String,
    val routingReason: String,
    val success: Boolean,
    val latencyMs: Long,
    val stepCount: Int,
    val feedbackScore: Int = 0,
    val errorSummary: String? = null,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "routing_stats",
    primaryKeys = ["intentCategory", "engineType"],
)
data class RoutingStatEntity(
    val intentCategory: String,
    val engineType: String,
    val totalAttempts: Int = 0,
    val successCount: Int = 0,
    val positiveFeedbackCount: Int = 0,
    val negativeFeedbackCount: Int = 0,
    val totalLatencyMs: Long = 0L,
    val updatedAtMillis: Long,
)

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey val modelId: String,
    val localPath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val installedAtMillis: Long,
)
