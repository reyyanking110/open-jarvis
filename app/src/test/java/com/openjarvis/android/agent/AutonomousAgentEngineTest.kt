package com.openjarvis.android.agent

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.openjarvis.android.ai.engine.EngineTurnResult
import com.openjarvis.android.ai.engine.GeminiCloudEngine
import com.openjarvis.android.ai.engine.HybridInferenceRouter
import com.openjarvis.android.ai.engine.InferenceStreamEvent
import com.openjarvis.android.ai.engine.LiteRtLmEngine
import com.openjarvis.android.ai.engine.ToolCallRequest
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.IntentCategory
import com.openjarvis.android.core.domain.model.MessageRole
import com.openjarvis.android.core.domain.model.RoutingDecision
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.data.local.db.AgentTaskDao
import com.openjarvis.android.data.repository.ConversationRepository
import com.openjarvis.android.data.repository.LearningRepository
import com.openjarvis.android.data.repository.MemoryRepository
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.DeviceCommandExecutor
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolRegistry
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class AutonomousAgentEngineTest {

    @Test
    fun `multi-step tool execution pauses for confirmation and completes`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val confirmTool = object : AndroidTool {
            override val spec = ToolSpec(
                name = "save_memory",
                description = "Save memory",
                riskLevel = ToolRiskLevel.CONFIRMATION_REQUIRED,
            )
            override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult =
                ToolExecutionResult("save_memory", true, "Saved fact", verified = true)
        }
        val registry = ToolRegistry(setOf(confirmTool))
        val executor = DeviceCommandExecutor(context, registry)

        val router = mockk<HybridInferenceRouter>()
        val gemini = mockk<GeminiCloudEngine>(relaxed = true)
        val local = mockk<LiteRtLmEngine>(relaxed = true)
        val convRepo = mockk<ConversationRepository>(relaxed = true)
        val memRepo = mockk<MemoryRepository>(relaxed = true)
        val learningRepo = mockk<LearningRepository>(relaxed = true)
        val taskDao = mockk<AgentTaskDao>(relaxed = true)
        val prefsRepo = mockk<JarvisPreferencesRepository>()
        val telemetryProvider = mockk<DeviceTelemetryProvider>()

        every { prefsRepo.settingsFlow } returns flowOf(JarvisSettings())
        every { telemetryProvider.snapshot() } returns DeviceTelemetry(isOnline = true)
        coEvery { memRepo.retrieveRelevantMemories(any(), any()) } returns emptyList()
        coEvery { convRepo.getRecentMessages(any(), any()) } returns emptyList()
        coEvery { convRepo.addMessage(any()) } returns 10L
        coEvery { taskDao.insertTask(any()) } returns 1L
        coEvery { taskDao.insertStep(any()) } returns 2L

        val decision = RoutingDecision(
            selectedEngine = EngineType.GEMINI_CLOUD,
            modelId = "gemini-2.5-flash",
            reason = "Test",
            fallbackAllowed = false,
            intentCategory = IntentCategory.MEMORY,
        )
        coEvery { router.selectEngine(any(), any(), any()) } returns decision
        every { router.engineFor(EngineType.GEMINI_CLOUD) } returns gemini
        every { router.engineFor(EngineType.LOCAL_LITERT_LM) } returns local

        var turnCount = 0
        every { gemini.streamGenerate(any()) } answers {
            turnCount++
            if (turnCount == 1) {
                flowOf(
                    InferenceStreamEvent.Completed(
                        EngineTurnResult(
                            text = "",
                            toolCalls = listOf(ToolCallRequest("save_memory", JsonObject(emptyMap()))),
                            engineType = EngineType.GEMINI_CLOUD,
                            modelId = "gemini-2.5-flash",
                        )
                    )
                )
            } else {
                flowOf(
                    InferenceStreamEvent.Delta("Memory saved."),
                    InferenceStreamEvent.Completed(
                        EngineTurnResult(
                            text = "Memory saved.",
                            toolCalls = emptyList(),
                            engineType = EngineType.GEMINI_CLOUD,
                            modelId = "gemini-2.5-flash",
                        )
                    )
                )
            }
        }

        val agent = AutonomousAgentEngine(
            router = router,
            toolRegistry = registry,
            commandExecutor = executor,
            conversationRepository = convRepo,
            memoryRepository = memRepo,
            learningRepository = learningRepo,
            agentTaskDao = taskDao,
            preferencesRepository = prefsRepo,
            telemetryProvider = telemetryProvider,
        )

        val approverJob = launch {
            agent.liveState.collect { st ->
                if (st.pendingConfirmation != null) {
                    agent.resolveConfirmation(true)
                }
            }
        }

        val reply = agent.handleUserPrompt(1L, "Remember I prefer dark mode")
        approverJob.cancel()

        assertThat(reply.role).isEqualTo(MessageRole.ASSISTANT)
        assertThat(reply.content).isEqualTo("Memory saved.")
        assertThat(reply.toolSummary).contains("✓ save_memory")
    }
}
