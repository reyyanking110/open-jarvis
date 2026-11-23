package com.openjarvis.android.tools

import android.content.Context
import kotlinx.serialization.json.JsonObject

enum class ToolRiskLevel {
    SAFE_READ,
    DEVICE_STATE,
    CONFIRMATION_REQUIRED,
}

data class ToolParamSchema(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String> = emptyList(),
)

data class ToolSpec(
    val name: String,
    val description: String,
    val riskLevel: ToolRiskLevel,
    val requiredPermissions: List<String> = emptyList(),
    val parameters: List<ToolParamSchema> = emptyList(),
)

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val summary: String,
    val dataJson: String? = null,
    val verified: Boolean = false,
    val recoverable: Boolean = true,
    val missingPermission: String? = null,
)

interface AndroidTool {
    val spec: ToolSpec

    fun describeConfirmation(args: JsonObject): String =
        "Execute ${spec.name} with arguments $args"

    suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult

    suspend fun verify(
        context: Context,
        args: JsonObject,
        initialResult: ToolExecutionResult,
    ): Boolean = initialResult.success
}
