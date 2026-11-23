package com.openjarvis.android.core.domain.model

import kotlinx.serialization.Serializable

enum class InferenceMode(val label: String) {
    AUTO("Auto (Hybrid)"),
    ONLINE_GEMINI("Online (Gemini)"),
    OFFLINE_LOCAL("Offline (Local LLM)"),
}

enum class EngineType(val displayName: String) {
    GEMINI_CLOUD("Gemini Cloud"),
    LOCAL_LITERT_LM("Local LiteRT-LM"),
}

enum class LocalAccelerator(val label: String) {
    GPU("GPU"),
    CPU("CPU"),
    NPU("NPU"),
}

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM,
}

enum class FeedbackRating(val score: Int) {
    NONE(0),
    POSITIVE(1),
    NEGATIVE(-1),
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    FACT("Fact"),
    ROUTINE("Routine"),
    DEVICE_NOTE("Device Note"),
}

enum class AgentStepStatus {
    PENDING,
    RUNNING,
    AWAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    SKIPPED,
}

enum class AgentTaskStatus {
    PLANNING,
    RUNNING,
    AWAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class IntentCategory(val id: String) {
    CHAT("chat"),
    DEVICE_ACTION("device_action"),
    PLANNING("planning"),
    MEMORY("memory"),
}

@Serializable
data class ModelSpec(
    val id: String,
    val displayName: String,
    val engineType: EngineType,
    val description: String,
    val minRamMb: Int,
    val approxSizeMb: Int,
    val contextWindow: Int,
    val supportsToolCalling: Boolean,
    val fileName: String = "",
    val downloadUrl: String = "",
)

data class InstalledLocalModel(
    val spec: ModelSpec,
    val localPath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val installedAtMillis: Long,
)

data class DeviceTelemetry(
    val isOnline: Boolean = false,
    val isUnmetered: Boolean = false,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isThermalThrottled: Boolean = false,
    val availableRamMb: Long = 4096L,
    val totalRamMb: Long = 8192L,
)

data class EngineHealth(
    val engineType: EngineType,
    val available: Boolean,
    val reason: String,
)

data class RoutingDecision(
    val selectedEngine: EngineType,
    val modelId: String,
    val reason: String,
    val fallbackAllowed: Boolean,
    val intentCategory: IntentCategory,
)

data class ChatMessage(
    val id: Long = 0L,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val engineUsed: EngineType? = null,
    val modelId: String? = null,
    val toolSummary: String? = null,
    val feedback: FeedbackRating = FeedbackRating.NONE,
    val latencyMs: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class ConversationSummary(
    val id: Long,
    val title: String,
    val updatedAtMillis: Long,
    val messageCount: Int,
)

data class MemoryEntry(
    val id: Long = 0L,
    val title: String,
    val content: String,
    val category: MemoryCategory,
    val tags: String = "",
    val salienceBoost: Int = 0,
    val approvedByUser: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class AgentStep(
    val id: Long = 0L,
    val taskId: Long = 0L,
    val stepIndex: Int,
    val description: String,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    val status: AgentStepStatus = AgentStepStatus.PENDING,
    val resultSummary: String? = null,
    val verified: Boolean = false,
    val attempts: Int = 0,
    val errorMessage: String? = null,
)

data class AgentTask(
    val id: Long = 0L,
    val conversationId: Long,
    val goal: String,
    val status: AgentTaskStatus,
    val engineType: EngineType,
    val modelId: String,
    val steps: List<AgentStep> = emptyList(),
    val finalOutput: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
)

data class PendingConfirmation(
    val taskId: Long,
    val stepIndex: Int,
    val toolName: String,
    val humanReadableAction: String,
    val argumentsPreview: String,
    val riskReason: String,
)

data class RoutingStat(
    val intentCategory: IntentCategory,
    val engineType: EngineType,
    val totalAttempts: Int,
    val successCount: Int,
    val positiveFeedbackCount: Int,
    val negativeFeedbackCount: Int,
    val avgLatencyMs: Long,
) {
    val successRate: Float
        get() = if (totalAttempts == 0) 0.5f else successCount.toFloat() / totalAttempts
}
