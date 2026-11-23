package com.openjarvis.android.ai.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.openjarvis.android.core.config.JarvisPreferencesRepository
import com.openjarvis.android.core.domain.model.EngineHealth
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.LocalAccelerator
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import com.openjarvis.android.data.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts structured JSON tool calls from offline LLM output when the local model
 * requests an Android tool via {"tool":"...","args":{...}}.
 */
object LocalToolCallParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val fenceRegex = Regex("```(?:json)?\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)

    fun extractToolCall(rawText: String): ToolCallRequest? {
        val trimmed = rawText.trim()
        val candidate = fenceRegex.find(trimmed)?.groupValues?.getOrNull(1)
            ?: extractFirstJsonObject(trimmed)
            ?: return null

        return runCatching {
            val obj = json.parseToJsonElement(candidate).jsonObject
            val toolName = obj["tool"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            val args = obj["args"]?.jsonObject
                ?: obj["arguments"]?.jsonObject
                ?: JsonObject(emptyMap())
            ToolCallRequest(name = toolName, arguments = args)
        }.getOrNull()
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

/**
 * Genuine on-device inference engine backed by Google AI Edge LiteRT-LM (`litertlm-android`).
 * Requires a real `.litertlm` / `.task` model file installed in app storage; never simulates replies.
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val preferencesRepository: JarvisPreferencesRepository,
    private val telemetryProvider: DeviceTelemetryProvider,
) : InferenceEngine {

    override val engineType: EngineType = EngineType.LOCAL_LITERT_LM

    private val engineMutex = Mutex()
    private var loadedEngine: Engine? = null
    private var loadedKey: String? = null

    @Volatile
    private var activeConversation: Conversation? = null

    override suspend fun health(): EngineHealth {
        val settings = preferencesRepository.settingsFlow.first()
        val installed = modelRepository.getInstalledModel(settings.selectedLocalModelId)
        val telemetry = telemetryProvider.snapshot()

        if (installed == null || !File(installed.localPath).exists()) {
            return EngineHealth(
                engineType = engineType,
                available = false,
                reason = "No local model file installed for '${settings.selectedLocalModelId}'. Import or download one in Models.",
            )
        }
        if (telemetry.availableRamMb < 1024L) {
            return EngineHealth(
                engineType = engineType,
                available = false,
                reason = "Available RAM (${telemetry.availableRamMb} MB) is too low to safely load local LLM.",
            )
        }
        return EngineHealth(
            engineType = engineType,
            available = true,
            reason = "Local model '${installed.spec.displayName}' ready on disk.",
        )
    }

    override suspend fun generateTurn(request: InferenceRequest): Result<EngineTurnResult> {
        val text = StringBuilder()
        var error: String? = null
        var completed: EngineTurnResult? = null

        streamGenerate(request).collect { event ->
            when (event) {
                is InferenceStreamEvent.Delta -> text.append(event.text)
                is InferenceStreamEvent.Completed -> completed = event.result
                is InferenceStreamEvent.Error -> error = event.message
            }
        }
        return if (error != null) {
            Result.failure(IllegalStateException(error))
        } else {
            val result = completed ?: EngineTurnResult(
                text = text.toString(),
                engineType = engineType,
                modelId = request.modelId,
            )
            Result.success(result)
        }
    }

    override fun streamGenerate(request: InferenceRequest): Flow<InferenceStreamEvent> = callbackFlow {
        val startMs = System.currentTimeMillis()
        val settings = preferencesRepository.settingsFlow.first()
        val targetModelId = request.modelId.ifBlank { settings.selectedLocalModelId }
        val installed = modelRepository.getInstalledModel(targetModelId)

        if (installed == null || !File(installed.localPath).exists()) {
            trySend(
                InferenceStreamEvent.Error(
                    message = "Local LiteRT-LM model '$targetModelId' is not installed. Import or download a .litertlm model in Models.",
                    recoverable = false,
                )
            )
            close()
            return@callbackFlow
        }

        val accelerator = settings.localAccelerator
        val engineResult = runCatching {
            ensureEngineLoaded(
                modelPath = installed.localPath,
                accelerator = accelerator,
                maxTokens = installed.spec.contextWindow.coerceAtMost(4096),
            )
        }

        val engine = engineResult.getOrElse { err ->
            trySend(
                InferenceStreamEvent.Error(
                    message = "Failed to initialize LiteRT-LM engine: ${err.message}",
                    recoverable = true,
                )
            )
            close()
            return@callbackFlow
        }

        val systemText = buildString {
            append(request.systemInstruction)
            if (request.enableTools && request.compactToolsPrompt.isNotBlank()) {
                append("\n\n")
                append(request.compactToolsPrompt)
            }
        }.trim()

        val promptText = buildConversationPrompt(request.messages)
        val samplerConfig = if (accelerator == LocalAccelerator.NPU) {
            null
        } else {
            SamplerConfig(
                topK = 40,
                topP = 0.95,
                temperature = request.temperature,
            )
        }

        val conversation = runCatching {
            withContext(Dispatchers.IO) {
                engine.createConversation(
                    ConversationConfig(
                        samplerConfig = samplerConfig,
                        systemInstruction = if (systemText.isNotBlank()) {
                            Contents.of(listOf(Content.Text(systemText)))
                        } else {
                            null
                        },
                    )
                )
            }
        }.getOrElse { err ->
            trySend(
                InferenceStreamEvent.Error(
                    message = "Failed to create local conversation: ${err.message}",
                    recoverable = true,
                )
            )
            close()
            return@callbackFlow
        }

        activeConversation = conversation
        val accumulated = StringBuilder()

        try {
            conversation.sendMessageAsync(
                Contents.of(listOf(Content.Text(promptText))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            trySend(InferenceStreamEvent.Delta(chunk))
                        }
                    }

                    override fun onDone() {
                        val rawOutput = accumulated.toString()
                        val parsedTool = if (request.enableTools) {
                            LocalToolCallParser.extractToolCall(rawOutput)
                        } else {
                            null
                        }
                        val toolCalls = listOfNotNull(parsedTool)
                        val cleanText = if (parsedTool != null) "" else rawOutput
                        trySend(
                            InferenceStreamEvent.Completed(
                                EngineTurnResult(
                                    text = cleanText,
                                    toolCalls = toolCalls,
                                    latencyMs = System.currentTimeMillis() - startMs,
                                    engineType = engineType,
                                    modelId = targetModelId,
                                )
                            )
                        )
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        trySend(
                            InferenceStreamEvent.Error(
                                message = throwable.message ?: "Local inference error.",
                                recoverable = true,
                            )
                        )
                        close()
                    }
                },
                emptyMap(),
                maxOutputToken = request.maxOutputTokens,
            )
        } catch (e: Exception) {
            trySend(
                InferenceStreamEvent.Error(
                    message = e.message ?: "Failed to dispatch local inference.",
                    recoverable = true,
                )
            )
            close()
        }

        awaitClose {
            activeConversation = null
            runCatching { conversation.cancelProcess() }
            runCatching { conversation.close() }
        }
    }

    override fun stop() {
        runCatching { activeConversation?.cancelProcess() }
    }

    suspend fun release() {
        engineMutex.withLock {
            runCatching { activeConversation?.close() }
            activeConversation = null
            runCatching { loadedEngine?.close() }
            loadedEngine = null
            loadedKey = null
        }
    }

    private suspend fun ensureEngineLoaded(
        modelPath: String,
        accelerator: LocalAccelerator,
        maxTokens: Int,
    ): Engine = engineMutex.withLock {
        val key = "$modelPath|${accelerator.name}|$maxTokens"
        val current = loadedEngine
        if (current != null && loadedKey == key) {
            return@withLock current
        }
        runCatching { current?.close() }
        loadedEngine = null
        loadedKey = null

        val backend = when (accelerator) {
            LocalAccelerator.CPU -> Backend.CPU()
            LocalAccelerator.GPU -> Backend.GPU()
            LocalAccelerator.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        }
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxTokens,
        )
        return@withLock withContext(Dispatchers.IO) {
            val engine = Engine(config)
            engine.initialize()
            loadedEngine = engine
            loadedKey = key
            engine
        }
    }

    private fun buildConversationPrompt(messages: List<InferenceMessage>): String {
        if (messages.size == 1 && messages.first().role == TurnRole.USER) {
            return messages.first().text
        }
        return buildString {
            messages.forEach { msg ->
                when (msg.role) {
                    TurnRole.USER -> appendLine("User: ${msg.text}")
                    TurnRole.ASSISTANT -> appendLine("Assistant: ${msg.text}")
                    TurnRole.TOOL_CALL -> appendLine("Assistant Tool Call [${msg.toolName}]: ${msg.toolArgs}")
                    TurnRole.TOOL_RESULT -> appendLine("Tool Observation [${msg.toolName}]: ${msg.text}")
                }
            }
            append("Assistant:")
        }
    }
}
