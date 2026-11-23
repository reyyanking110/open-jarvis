package com.openjarvis.android.ai.engine

import com.openjarvis.android.core.domain.model.EngineHealth
import com.openjarvis.android.core.domain.model.EngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class ToolCallRequest(
    val name: String,
    val arguments: JsonObject,
)

enum class TurnRole {
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
}

data class InferenceMessage(
    val role: TurnRole,
    val text: String = "",
    val toolName: String? = null,
    val toolArgs: JsonObject? = null,
)

data class InferenceRequest(
    val modelId: String,
    val systemInstruction: String,
    val messages: List<InferenceMessage>,
    val geminiToolDeclarations: JsonArray? = null,
    val compactToolsPrompt: String = "",
    val enableTools: Boolean = true,
    val temperature: Double = 0.4,
    val maxOutputTokens: Int = 1024,
)

data class EngineTurnResult(
    val text: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val latencyMs: Long = 0L,
    val engineType: EngineType,
    val modelId: String,
)

sealed interface InferenceStreamEvent {
    data class Delta(val text: String) : InferenceStreamEvent
    data class Completed(val result: EngineTurnResult) : InferenceStreamEvent
    data class Error(val message: String, val recoverable: Boolean = true) : InferenceStreamEvent
}

interface InferenceEngine {
    val engineType: EngineType
    suspend fun health(): EngineHealth
    suspend fun generateTurn(request: InferenceRequest): Result<EngineTurnResult>
    fun streamGenerate(request: InferenceRequest): Flow<InferenceStreamEvent>
    fun stop()
}
