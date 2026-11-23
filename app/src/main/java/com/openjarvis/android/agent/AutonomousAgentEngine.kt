package com.openjarvis.android.agent

import com.openjarvis.android.ai.engine.EngineTurnResult
import com.openjarvis.android.ai.engine.HybridInferenceRouter
import com.openjarvis.android.ai.engine.InferenceMessage
import com.openjarvis.android.ai.engine.InferenceRequest
import com.openjarvis.android.ai.engine.InferenceStreamEvent
import com.openjarvis.android.ai.engine.ToolCallRequest
import com.openjarvis.android.ai.engine.TurnRole
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.domain.model.AgentStep
import com.openjarvis.android.core.domain.model.AgentStepStatus
import com.openjarvis.android.core.domain.model.AgentTask
import com.openjarvis.android.core.domain.model.AgentTaskStatus
import com.openjarvis.android.core.domain.model.ChatMessage
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.MemoryEntry
import com.openjarvis.android.core.domain.model.MessageRole
import com.openjarvis.android.core.domain.model.PendingConfirmation
import com.openjarvis.android.core.domain.model.RoutingDecision
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.data.local.db.AgentStepEntity
import com.openjarvis.android.data.local.db.AgentTaskDao
import com.openjarvis.android.data.local.db.AgentTaskEntity
import com.openjarvis.android.data.repository.ConversationRepository
import com.openjarvis.android.data.repository.LearningRepository
import com.openjarvis.android.data.repository.MemoryRepository
import com.openjarvis.android.tools.DeviceCommandExecutor
import com.openjarvis.android.tools.ToolRegistry
import com.openjarvis.android.tools.ToolRiskLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AgentExecutionPhase {
    IDLE,
    ROUTING,
    THINKING,
    EXECUTING_TOOL,
    AWAITING_CONFIRMATION,
    STREAMING_ANSWER,
}

data class AgentLiveState(
    val phase: AgentExecutionPhase = AgentExecutionPhase.IDLE,
    val streamingText: String = "",
    val activeRouting: RoutingDecision? = null,
    val activeSteps: List<AgentStep> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null,
    val lastError: String? = null,
) {
    val isBusy: Boolean get() = phase != AgentExecutionPhase.IDLE
}

@Singleton
class AutonomousAgentEngine @Inject constructor(
    private val router: HybridInferenceRouter,
    private val toolRegistry: ToolRegistry,
    private val commandExecutor: DeviceCommandExecutor,
    private val conversationRepository: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val learningRepository: LearningRepository,
    private val agentTaskDao: AgentTaskDao,
    private val preferencesRepository: JarvisPreferencesRepository,
    private val telemetryProvider: DeviceTelemetryProvider,
) {
    private val _liveState = MutableStateFlow(AgentLiveState())
    val liveState: StateFlow<AgentLiveState> = _liveState.asStateFlow()

    @Volatile
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var cancelledByUser: Boolean = false

    fun observeTaskHistory(): Flow<List<AgentTask>> =
        agentTaskDao.observeTasks().map { tasks ->
            tasks.map { entity ->
                val steps = agentTaskDao.getStepsForTask(entity.id).map { s ->
                    AgentStep(
                        id = s.id,
                        taskId = s.taskId,
                        stepIndex = s.stepIndex,
                        description = s.description,
                        toolName = s.toolName,
                        toolArgumentsJson = s.toolArgumentsJson,
                        status = runCatching { AgentStepStatus.valueOf(s.status) }
                            .getOrDefault(AgentStepStatus.PENDING),
                        resultSummary = s.resultSummary,
                        verified = s.verified,
                        attempts = s.attempts,
                        errorMessage = s.errorMessage,
                    )
                }
                AgentTask(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    goal = entity.goal,
                    status = runCatching { AgentTaskStatus.valueOf(entity.status) }
                        .getOrDefault(AgentTaskStatus.COMPLETED),
                    engineType = runCatching { EngineType.valueOf(entity.engineType) }
                        .getOrDefault(EngineType.GEMINI_CLOUD),
                    modelId = entity.modelId,
                    steps = steps,
                    finalOutput = entity.finalOutput,
                    createdAtMillis = entity.createdAtMillis,
                    completedAtMillis = entity.completedAtMillis,
                )
            }
        }

    fun resolveConfirmation(approved: Boolean) {
        confirmationDeferred?.complete(approved)
    }

    fun cancelActiveTask() {
        cancelledByUser = true
        confirmationDeferred?.complete(false)
        val activeEngine = _liveState.value.activeRouting?.selectedEngine
        if (activeEngine != null) {
            router.engineFor(activeEngine).stop()
        }
    }

    suspend fun handleUserPrompt(
        conversationId: Long,
        userPrompt: String,
    ): ChatMessage {
        cancelledByUser = false
        val startMs = System.currentTimeMillis()
        val settings = preferencesRepository.settingsFlow.first()
        val telemetry = telemetryProvider.snapshot()

        // 1. Persist user message immediately.
        conversationRepository.addMessage(
            ChatMessage(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = userPrompt,
            )
        )

        _liveState.value = AgentLiveState(phase = AgentExecutionPhase.ROUTING)

        // 2. Hybrid route selection & relevant memory retrieval.
        val routing = router.selectEngine(userPrompt, settings, telemetry)
        val relevantMemories = memoryRepository.retrieveRelevantMemories(userPrompt, limit = 5)
        val history = conversationRepository.getRecentMessages(conversationId, limit = 12)

        val now = System.currentTimeMillis()
        val taskId = agentTaskDao.insertTask(
            AgentTaskEntity(
                conversationId = conversationId,
                goal = userPrompt,
                status = AgentTaskStatus.RUNNING.name,
                engineType = routing.selectedEngine.name,
                modelId = routing.modelId,
                createdAtMillis = now,
            )
        )

        _liveState.value = _liveState.value.copy(
            phase = AgentExecutionPhase.THINKING,
            activeRouting = routing,
        )

        val systemInstruction = buildSystemPrompt(relevantMemories)
        val inferenceMessages = history.mapNotNull { msg ->
            when (msg.role) {
                MessageRole.USER -> InferenceMessage(TurnRole.USER, msg.content)
                MessageRole.ASSISTANT -> InferenceMessage(TurnRole.ASSISTANT, msg.content)
                else -> null
            }
        }.toMutableList()

        val steps = mutableListOf<AgentStep>()
        val toolCallSignatures = mutableSetOf<String>()
        var activeEngineType = routing.selectedEngine
        var activeModelId = routing.modelId
        var finalAssistantText = ""
        var taskSucceeded = false
        var lastError: String? = null

        try {
            val maxSteps = settings.maxAgentSteps.coerceIn(2, 16)
            for (stepIdx in 0 until maxSteps) {
                if (cancelledByUser) throw CancellationException("Cancelled by user.")

                val request = InferenceRequest(
                    modelId = activeModelId,
                    systemInstruction = systemInstruction,
                    messages = inferenceMessages.toList(),
                    geminiToolDeclarations = toolRegistry.toGeminiFunctionDeclarations(),
                    compactToolsPrompt = toolRegistry.toCompactPromptSpec(),
                    enableTools = true,
                    temperature = 0.35,
                    maxOutputTokens = 1024,
                )

                val turnResult = executeModelTurnWithFallback(
                    primaryEngine = activeEngineType,
                    primaryModelId = activeModelId,
                    fallbackAllowed = routing.fallbackAllowed,
                    fallbackModelId = if (activeEngineType == EngineType.GEMINI_CLOUD) {
                        settings.selectedLocalModelId
                    } else {
                        settings.selectedGeminiModelId
                    },
                    request = request,
                )

                val result = turnResult.getOrElse { err ->
                    lastError = err.message ?: "Inference failed."
                    break
                }

                activeEngineType = result.engineType
                activeModelId = result.modelId

                // If no tool calls, we have our final answer!
                if (result.toolCalls.isEmpty()) {
                    finalAssistantText = result.text.trim()
                    if (finalAssistantText.isEmpty()) {
                        finalAssistantText = "Done."
                    }
                    taskSucceeded = true
                    break
                }

                // Process tool calls sequentially within this step.
                for (toolCall in result.toolCalls) {
                    if (cancelledByUser) throw CancellationException("Cancelled by user.")

                    val sig = "${toolCall.name}:${toolCall.arguments}"
                    if (!toolCallSignatures.add(sig)) {
                        inferenceMessages.add(
                            InferenceMessage(
                                role = TurnRole.TOOL_RESULT,
                                toolName = toolCall.name,
                                text = "Duplicate tool call blocked to avoid a loop. Summarize results for the user.",
                            )
                        )
                        continue
                    }

                    val stepNumber = steps.size + 1
                    val tool = commandExecutor.getTool(toolCall.name)
                    val stepDescription = tool?.describeConfirmation(toolCall.arguments)
                        ?: "Run ${toolCall.name}"

                    val stepEntityId = agentTaskDao.insertStep(
                        AgentStepEntity(
                            taskId = taskId,
                            stepIndex = stepNumber,
                            description = stepDescription,
                            toolName = toolCall.name,
                            toolArgumentsJson = toolCall.arguments.toString(),
                            status = AgentStepStatus.RUNNING.name,
                            attempts = 1,
                        )
                    )

                    var currentStep = AgentStep(
                        id = stepEntityId,
                        taskId = taskId,
                        stepIndex = stepNumber,
                        description = stepDescription,
                        toolName = toolCall.name,
                        toolArgumentsJson = toolCall.arguments.toString(),
                        status = AgentStepStatus.RUNNING,
                        attempts = 1,
                    )
                    steps.add(currentStep)
                    _liveState.value = _liveState.value.copy(
                        phase = AgentExecutionPhase.EXECUTING_TOOL,
                        activeSteps = steps.toList(),
                    )

                    // Confirmation gate for sensitive/irreversible tools.
                    var userConfirmed = true
                    if (tool?.spec?.riskLevel == ToolRiskLevel.CONFIRMATION_REQUIRED) {
                        val deferred = CompletableDeferred<Boolean>()
                        confirmationDeferred = deferred
                        val pending = PendingConfirmation(
                            taskId = taskId,
                            stepIndex = stepNumber,
                            toolName = tool.spec.name,
                            humanReadableAction = tool.describeConfirmation(toolCall.arguments),
                            argumentsPreview = toolCall.arguments.toString(),
                            riskReason = "Sensitive or state-modifying Android action requires explicit approval.",
                        )
                        currentStep = currentStep.copy(status = AgentStepStatus.AWAITING_CONFIRMATION)
                        steps[steps.lastIndex] = currentStep
                        updateStepInDb(currentStep)
                        agentTaskDao.updateTaskStatus(
                            taskId,
                            AgentTaskStatus.AWAITING_CONFIRMATION.name,
                            null,
                            null,
                        )
                        _liveState.value = _liveState.value.copy(
                            phase = AgentExecutionPhase.AWAITING_CONFIRMATION,
                            activeSteps = steps.toList(),
                            pendingConfirmation = pending,
                        )

                        userConfirmed = deferred.await()
                        confirmationDeferred = null
                        _liveState.value = _liveState.value.copy(pendingConfirmation = null)
                        agentTaskDao.updateTaskStatus(
                            taskId,
                            AgentTaskStatus.RUNNING.name,
                            null,
                            null,
                        )
                    }

                    val execResult = commandExecutor.executeAndVerify(
                        toolName = toolCall.name,
                        args = toolCall.arguments,
                        userConfirmed = userConfirmed,
                    )

                    currentStep = currentStep.copy(
                        status = if (execResult.success) AgentStepStatus.COMPLETED else AgentStepStatus.FAILED,
                        resultSummary = execResult.summary,
                        verified = execResult.verified,
                        errorMessage = if (execResult.success) null else execResult.summary,
                    )
                    steps[steps.lastIndex] = currentStep
                    updateStepInDb(currentStep)
                    _liveState.value = _liveState.value.copy(
                        phase = AgentExecutionPhase.THINKING,
                        activeSteps = steps.toList(),
                    )

                    inferenceMessages.add(
                        InferenceMessage(
                            role = TurnRole.TOOL_CALL,
                            toolName = toolCall.name,
                            toolArgs = toolCall.arguments,
                        )
                    )
                    inferenceMessages.add(
                        InferenceMessage(
                            role = TurnRole.TOOL_RESULT,
                            toolName = toolCall.name,
                            text = buildString {
                                append(if (execResult.success) "SUCCESS" else "FAILED")
                                if (execResult.verified) append(" (VERIFIED)")
                                append(": ")
                                append(execResult.summary)
                            },
                        )
                    )
                }
            }

            if (!taskSucceeded && finalAssistantText.isBlank()) {
                finalAssistantText = if (lastError != null) {
                    "I could not complete your request: $lastError"
                } else if (steps.isNotEmpty()) {
                    "Completed ${steps.count { it.status == AgentStepStatus.COMPLETED }}/${steps.size} steps:\n" +
                        steps.joinToString("\n") { "• ${it.description}: ${it.resultSummary ?: it.errorMessage ?: it.status}" }
                } else {
                    "Unable to generate a response right now. Check your Gemini API key or local model in Settings/Models."
                }
            }

            val totalLatency = System.currentTimeMillis() - startMs
            val finalStatus = if (taskSucceeded) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED
            agentTaskDao.updateTaskStatus(
                taskId = taskId,
                status = finalStatus.name,
                finalOutput = finalAssistantText,
                completedAt = System.currentTimeMillis(),
            )

            val toolSummary = if (steps.isEmpty()) {
                null
            } else {
                steps.joinToString(" | ") {
                    val mark = if (it.status == AgentStepStatus.COMPLETED) "✓" else "✗"
                    "$mark ${it.toolName ?: "step"}"
                }
            }

            val assistantMsg = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = finalAssistantText,
                engineUsed = activeEngineType,
                modelId = activeModelId,
                toolSummary = toolSummary,
                latencyMs = totalLatency,
            )
            val savedId = conversationRepository.addMessage(assistantMsg)

            learningRepository.recordExecution(
                taskId = taskId,
                messageId = savedId,
                intentCategory = routing.intentCategory,
                engineType = activeEngineType,
                modelId = activeModelId,
                routingReason = routing.reason,
                success = taskSucceeded,
                latencyMs = totalLatency,
                stepCount = steps.size,
                errorSummary = lastError,
            )

            return assistantMsg.copy(id = savedId)
        } catch (ce: CancellationException) {
            agentTaskDao.updateTaskStatus(
                taskId = taskId,
                status = AgentTaskStatus.CANCELLED.name,
                finalOutput = "Cancelled by user.",
                completedAt = System.currentTimeMillis(),
            )
            val msg = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "Task cancelled.",
                engineUsed = activeEngineType,
                modelId = activeModelId,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            val id = conversationRepository.addMessage(msg)
            return msg.copy(id = id)
        } finally {
            confirmationDeferred = null
            _liveState.value = AgentLiveState(
                phase = AgentExecutionPhase.IDLE,
                activeRouting = routing,
                lastError = lastError,
            )
        }
    }

    private suspend fun executeModelTurnWithFallback(
        primaryEngine: EngineType,
        primaryModelId: String,
        fallbackAllowed: Boolean,
        fallbackModelId: String,
        request: InferenceRequest,
    ): Result<EngineTurnResult> {
        val primaryResult = streamAndCollect(primaryEngine, request.copy(modelId = primaryModelId))
        if (primaryResult.isSuccess || !fallbackAllowed) {
            return primaryResult
        }

        val backupEngine = if (primaryEngine == EngineType.GEMINI_CLOUD) {
            EngineType.LOCAL_LITERT_LM
        } else {
            EngineType.GEMINI_CLOUD
        }
        val backupHealth = router.engineFor(backupEngine).health()
        if (!backupHealth.available) {
            return primaryResult
        }
        _liveState.value = _liveState.value.copy(streamingText = "")
        return streamAndCollect(backupEngine, request.copy(modelId = fallbackModelId))
    }

    private suspend fun streamAndCollect(
        engineType: EngineType,
        request: InferenceRequest,
    ): Result<EngineTurnResult> {
        val engine = router.engineFor(engineType)
        val deltaBuffer = StringBuilder()
        var completed: EngineTurnResult? = null
        var errorMessage: String? = null

        engine.streamGenerate(request).collect { event ->
            when (event) {
                is InferenceStreamEvent.Delta -> {
                    deltaBuffer.append(event.text)
                    _liveState.value = _liveState.value.copy(
                        phase = AgentExecutionPhase.STREAMING_ANSWER,
                        streamingText = deltaBuffer.toString(),
                    )
                }
                is InferenceStreamEvent.Completed -> {
                    completed = event.result
                }
                is InferenceStreamEvent.Error -> {
                    errorMessage = event.message
                }
            }
        }

        if (errorMessage != null) {
            return Result.failure(IllegalStateException(errorMessage))
        }
        val finalResult = completed ?: EngineTurnResult(
            text = deltaBuffer.toString(),
            engineType = engineType,
            modelId = request.modelId,
        )
        return Result.success(finalResult)
    }

    private suspend fun updateStepInDb(step: AgentStep) {
        agentTaskDao.updateStep(
            AgentStepEntity(
                id = step.id,
                taskId = step.taskId,
                stepIndex = step.stepIndex,
                description = step.description,
                toolName = step.toolName,
                toolArgumentsJson = step.toolArgumentsJson,
                status = step.status.name,
                resultSummary = step.resultSummary,
                verified = step.verified,
                attempts = step.attempts,
                errorMessage = step.errorMessage,
            )
        )
    }

    private fun buildSystemPrompt(memories: List<MemoryEntry>): String = buildString {
        appendLine("You are Open JARVIS, a capable, trustworthy autonomous AI assistant running natively on Android.")
        appendLine("Rules:")
        appendLine("1. Be concise, accurate, and transparent. Never fabricate tool results or device actions.")
        appendLine("2. If a task requires device interaction or multiple steps, invoke the appropriate Android tools one or more steps at a time and verify outcomes.")
        appendLine("3. If a tool fails, analyze the error and either retry with corrected parameters or explain clearly to the user.")
        appendLine("4. Respect privacy and confirmation gates for calls, messaging, file writes, and saving memories.")
        if (memories.isNotEmpty()) {
            appendLine("\nRelevant user-approved persistent memories:")
            memories.forEach { m ->
                appendLine("- [${m.category.label}] ${m.title}: ${m.content}")
            }
        }
    }
}
