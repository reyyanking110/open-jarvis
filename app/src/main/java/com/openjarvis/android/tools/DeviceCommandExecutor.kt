package com.openjarvis.android.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

@Singleton
class DeviceCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolRegistry: ToolRegistry,
) {
    fun getTool(name: String): AndroidTool? = toolRegistry.get(name)

    fun missingPermissions(tool: AndroidTool): List<String> =
        tool.spec.requiredPermissions.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }

    suspend fun executeAndVerify(
        toolName: String,
        args: JsonObject,
        userConfirmed: Boolean,
    ): ToolExecutionResult {
        val tool = toolRegistry.get(toolName)
            ?: return ToolExecutionResult(
                toolName = toolName,
                success = false,
                summary = "Unknown tool: '$toolName'.",
                verified = false,
                recoverable = true,
            )

        if (tool.spec.riskLevel == ToolRiskLevel.CONFIRMATION_REQUIRED && !userConfirmed) {
            return ToolExecutionResult(
                toolName = toolName,
                success = false,
                summary = "Action '${tool.spec.name}' requires explicit user confirmation and was not executed.",
                verified = false,
                recoverable = false,
            )
        }

        val missing = missingPermissions(tool)
        if (missing.isNotEmpty()) {
            return ToolExecutionResult(
                toolName = toolName,
                success = false,
                summary = "Missing required permission(s): ${missing.joinToString(", ")}. Grant them in Permissions screen.",
                verified = false,
                recoverable = false,
                missingPermission = missing.first(),
            )
        }

        return runCatching {
            val result = tool.execute(context, args)
            val verified = if (result.success) {
                runCatching { tool.verify(context, args, result) }.getOrDefault(result.verified)
            } else {
                false
            }
            result.copy(verified = verified)
        }.getOrElse { err ->
            ToolExecutionResult(
                toolName = toolName,
                success = false,
                summary = "Tool '${tool.spec.name}' threw error: ${err.message ?: err.javaClass.simpleName}",
                verified = false,
                recoverable = true,
            )
        }
    }
}
