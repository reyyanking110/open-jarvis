package com.openjarvis.android.tools

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class DeviceCommandExecutorTest {

    private val context = mockk<Context>(relaxed = true)

    private val sensitiveTool = object : AndroidTool {
        override val spec = ToolSpec(
            name = "mock_sensitive",
            description = "Sensitive action",
            riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
        )

        override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult =
            ToolExecutionResult(spec.name, true, "Executed sensitive action", verified = true)
    }

    private val safeTool = object : AndroidTool {
        override val spec = ToolSpec(
            name = "mock_safe",
            description = "Safe action",
            riskLevel = ToolRiskLevel.SAFE_READ,
        )

        override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult =
            ToolExecutionResult(spec.name, true, "Read OK", verified = false)

        override suspend fun verify(
            context: Context,
            args: JsonObject,
            initialResult: ToolExecutionResult,
        ): Boolean = true
    }

    private val executor = DeviceCommandExecutor(
        context = context,
        toolRegistry = ToolRegistry(setOf(sensitiveTool, safeTool)),
    )

    @Test
    fun `blocks confirmation-required tool when userConfirmed is false`() = runTest {
        val res = executor.executeAndVerify(
            toolName = "mock_sensitive",
            args = JsonObject(mapOf("x" to JsonPrimitive("1"))),
            userConfirmed = false,
        )
        assertThat(res.success).isFalse()
        assertThat(res.summary).contains("requires explicit user confirmation")
    }

    @Test
    fun `executes and verifies safe tool`() = runTest {
        val res = executor.executeAndVerify(
            toolName = "mock_safe",
            args = JsonObject(emptyMap()),
            userConfirmed = false,
        )
        assertThat(res.success).isTrue()
        assertThat(res.verified).isTrue()
    }
}
