package com.openjarvis.android.ai.engine

import com.openjarvis.android.core.config.ApiKeyProvider
import com.openjarvis.android.core.domain.model.EngineHealth
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class GeminiCloudEngine(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: ApiKeyProvider,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val baseUrl: String,
) : InferenceEngine {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        apiKeyProvider: ApiKeyProvider,
        telemetryProvider: DeviceTelemetryProvider,
    ) : this(
        okHttpClient = okHttpClient,
        apiKeyProvider = apiKeyProvider,
        telemetryProvider = telemetryProvider,
        baseUrl = DEFAULT_BASE_URL,
    )

    override val engineType: EngineType = EngineType.GEMINI_CLOUD

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activeCall: Call? = null

    override suspend fun health(): EngineHealth {
        val online = telemetryProvider.snapshot().isOnline
        val hasKey = apiKeyProvider.hasGeminiApiKey()
        return when {
            !hasKey -> EngineHealth(
                engineType = engineType,
                available = false,
                reason = "Gemini API key not configured (set in Settings or local.properties).",
            )
            !online -> EngineHealth(
                engineType = engineType,
                available = false,
                reason = "Device is offline.",
            )
            else -> EngineHealth(
                engineType = engineType,
                available = true,
                reason = "Online and Gemini API key configured.",
            )
        }
    }

    override suspend fun generateTurn(request: InferenceRequest): Result<EngineTurnResult> =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider.getGeminiApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing. Configure it in Settings."),
                )
            }
            val startMs = System.currentTimeMillis()
            val payload = buildRequestPayload(request).toString()
            val url = "${baseUrl.trimEnd('/')}/models/${request.modelId}:generateContent"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val call = okHttpClient.newCall(httpRequest)
            activeCall = call
            try {
                call.execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(formatHttpError(response.code, rawBody)),
                        )
                    }
                    val parsed = parseResponseBody(
                        rawJson = rawBody,
                        modelId = request.modelId,
                        latencyMs = System.currentTimeMillis() - startMs,
                    )
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (activeCall === call) activeCall = null
            }
        }

    override fun streamGenerate(request: InferenceRequest): Flow<InferenceStreamEvent> = callbackFlow {
        val apiKey = apiKeyProvider.getGeminiApiKey()
        if (apiKey.isBlank()) {
            trySend(
                InferenceStreamEvent.Error(
                    message = "Gemini API key is missing. Add it in Settings.",
                    recoverable = false,
                )
            )
            close()
            return@callbackFlow
        }

        val startMs = System.currentTimeMillis()
        val payload = buildRequestPayload(request).toString()
        val url = "${baseUrl.trimEnd('/')}/models/${request.modelId}:streamGenerateContent?alt=sse"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = okHttpClient.newCall(httpRequest)
        activeCall = call

        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty()
                        trySend(
                            InferenceStreamEvent.Error(
                                message = formatHttpError(response.code, errBody),
                                recoverable = response.code in listOf(429, 500, 502, 503, 504),
                            )
                        )
                        return@withContext
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        trySend(InferenceStreamEvent.Error("Empty response stream from Gemini."))
                        return@withContext
                    }

                    val fullText = StringBuilder()
                    val toolCalls = mutableListOf<ToolCallRequest>()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val jsonPayload = line.removePrefix("data:").trim()
                        if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                        val chunk = runCatching {
                            parseResponseBody(
                                rawJson = jsonPayload,
                                modelId = request.modelId,
                                latencyMs = 0L,
                            )
                        }.getOrNull() ?: continue

                        if (chunk.text.isNotEmpty()) {
                            fullText.append(chunk.text)
                            trySend(InferenceStreamEvent.Delta(chunk.text))
                        }
                        if (chunk.toolCalls.isNotEmpty()) {
                            toolCalls.addAll(chunk.toolCalls)
                        }
                    }

                    trySend(
                        InferenceStreamEvent.Completed(
                            EngineTurnResult(
                                text = fullText.toString(),
                                toolCalls = toolCalls,
                                latencyMs = System.currentTimeMillis() - startMs,
                                engineType = engineType,
                                modelId = request.modelId,
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            trySend(
                InferenceStreamEvent.Error(
                    message = e.message ?: "Network failure contacting Gemini API.",
                    recoverable = true,
                )
            )
        } finally {
            if (activeCall === call) activeCall = null
            close()
        }

        awaitClose {
            call.cancel()
        }
    }

    override fun stop() {
        activeCall?.cancel()
        activeCall = null
    }

    internal fun buildRequestPayload(request: InferenceRequest): JsonObject = buildJsonObject {
        if (request.systemInstruction.isNotBlank()) {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", request.systemInstruction) })
                        },
                    )
                },
            )
        }

        put(
            "contents",
            buildJsonArray {
                request.messages.forEach { msg ->
                    when (msg.role) {
                        TurnRole.USER -> add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(buildJsonObject { put("text", msg.text) })
                                    },
                                )
                            }
                        )
                        TurnRole.ASSISTANT -> add(
                            buildJsonObject {
                                put("role", "model")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(buildJsonObject { put("text", msg.text) })
                                    },
                                )
                            }
                        )
                        TurnRole.TOOL_CALL -> add(
                            buildJsonObject {
                                put("role", "model")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "functionCall",
                                                    buildJsonObject {
                                                        put("name", msg.toolName.orEmpty())
                                                        put("args", msg.toolArgs ?: JsonObject(emptyMap()))
                                                    },
                                                )
                                            }
                                        )
                                    },
                                )
                            }
                        )
                        TurnRole.TOOL_RESULT -> add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "functionResponse",
                                                    buildJsonObject {
                                                        put("name", msg.toolName.orEmpty())
                                                        put(
                                                            "response",
                                                            buildJsonObject {
                                                                put("result", msg.text)
                                                            },
                                                        )
                                                    },
                                                )
                                            }
                                        )
                                    },
                                )
                            }
                        )
                    }
                }
            },
        )

        if (request.enableTools && request.geminiToolDeclarations != null && request.geminiToolDeclarations.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("functionDeclarations", request.geminiToolDeclarations)
                        }
                    )
                },
            )
        }

        put(
            "generationConfig",
            buildJsonObject {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxOutputTokens)
            },
        )
    }

    internal fun parseResponseBody(
        rawJson: String,
        modelId: String,
        latencyMs: Long,
    ): EngineTurnResult {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: JsonArray(emptyList())
        val first = candidates.firstOrNull()?.jsonObject
        val content = first?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray ?: JsonArray(emptyList())

        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCallRequest>()

        for (partElement in parts) {
            val part = partElement.jsonObject
            part["text"]?.jsonPrimitive?.contentOrNull?.let { textBuilder.append(it) }
            part["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.contentOrNull
                val args = fc["args"]?.jsonObject ?: JsonObject(emptyMap())
                if (!name.isNullOrBlank()) {
                    toolCalls.add(ToolCallRequest(name = name, arguments = args))
                }
            }
        }

        return EngineTurnResult(
            text = textBuilder.toString(),
            toolCalls = toolCalls,
            latencyMs = latencyMs,
            engineType = engineType,
            modelId = modelId,
        )
    }

    private fun formatHttpError(code: Int, rawBody: String): String {
        val extracted = runCatching {
            json.parseToJsonElement(rawBody).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            400 -> "Gemini request error (400): ${extracted ?: rawBody.take(180)}"
            401, 403 -> "Gemini API key rejected ($code). Verify your key in Settings."
            429 -> "Gemini rate limit / quota exceeded (429). Retry shortly or switch to Offline Local LLM."
            else -> "Gemini HTTP $code: ${extracted ?: rawBody.take(180)}"
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
