package com.openjarvis.android.ai.engine

import com.google.common.truth.Truth.assertThat
import com.openjarvis.android.core.config.ApiKeyProvider
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.telemetry.DeviceTelemetryProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GeminiCloudEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: GeminiCloudEngine
    private val keyProvider = mockk<ApiKeyProvider>()
    private val telemetryProvider = mockk<DeviceTelemetryProvider>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        every { keyProvider.getGeminiApiKey() } returns "test-api-key-123"
        every { keyProvider.hasGeminiApiKey() } returns true
        every { telemetryProvider.snapshot() } returns DeviceTelemetry(isOnline = true)
        every { telemetryProvider.telemetryFlow } returns MutableStateFlow(DeviceTelemetry(isOnline = true))

        engine = GeminiCloudEngine(
            okHttpClient = OkHttpClient(),
            apiKeyProvider = keyProvider,
            telemetryProvider = telemetryProvider,
            baseUrl = server.url("/v1beta").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `generateTurn parses functionCall and sends key in header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [{
                            "functionCall": {
                              "name": "set_flashlight",
                              "args": {"enabled": true}
                            }
                          }]
                        }
                      }]
                    }
                    """.trimIndent()
                )
        )

        val result = engine.generateTurn(
            InferenceRequest(
                modelId = "gemini-2.5-flash",
                systemInstruction = "Be helpful",
                messages = listOf(InferenceMessage(TurnRole.USER, "Turn on flashlight")),
            )
        )

        assertThat(result.isSuccess).isTrue()
        val turn = result.getOrThrow()
        assertThat(turn.toolCalls).hasSize(1)
        assertThat(turn.toolCalls.first().name).isEqualTo("set_flashlight")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("x-goog-api-key")).isEqualTo("test-api-key-123")
        assertThat(recorded.path).doesNotContain("test-api-key-123")
    }

    @Test
    fun `streamGenerate emits SSE text deltas and Completed event`() = runTest {
        val sseBody = """
            data: {"candidates":[{"content":{"parts":[{"text":"Hello "}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"JARVIS!"}]}}]}

        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val events = engine.streamGenerate(
            InferenceRequest(
                modelId = "gemini-2.5-flash",
                systemInstruction = "",
                messages = listOf(InferenceMessage(TurnRole.USER, "Hi")),
            )
        ).toList()

        val deltas = events.filterIsInstance<InferenceStreamEvent.Delta>().map { it.text }
        val completed = events.filterIsInstance<InferenceStreamEvent.Completed>().single()
        assertThat(deltas).containsExactly("Hello ", "JARVIS!").inOrder()
        assertThat(completed.result.text).isEqualTo("Hello JARVIS!")
    }

    @Test
    fun `streamGenerate emits Error on 401 unauthorized`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"API key invalid"}}""")
        )

        val events = engine.streamGenerate(
            InferenceRequest(
                modelId = "gemini-2.5-flash",
                systemInstruction = "",
                messages = listOf(InferenceMessage(TurnRole.USER, "Hi")),
            )
        ).toList()

        val err = events.filterIsInstance<InferenceStreamEvent.Error>().single()
        assertThat(err.recoverable).isFalse()
        assertThat(err.message).contains("401")
    }
}
