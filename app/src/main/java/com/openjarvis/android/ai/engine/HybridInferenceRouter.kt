package com.openjarvis.android.ai.engine

import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.domain.model.EngineHealth
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.InferenceMode
import com.openjarvis.android.core.domain.model.IntentCategory
import com.openjarvis.android.core.domain.model.RoutingDecision
import com.openjarvis.android.data.repository.LearningRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridInferenceRouter @Inject constructor(
    private val geminiEngine: GeminiCloudEngine,
    private val localEngine: LiteRtLmEngine,
    private val learningRepository: LearningRepository,
) {
    fun classifyIntent(prompt: String): IntentCategory {
        val lower = prompt.lowercase()
        return when {
            listOf("remember", "memory", "forget", "my preference", "note that").any { it in lower } ->
                IntentCategory.MEMORY
            listOf(" then ", " and then ", "step by step", "plan ", "prepare ", "schedule ").any { it in lower } ->
                IntentCategory.PLANNING
            listOf(
                "open ", "launch ", "flashlight", "torch", "volume", "alarm", "timer",
                "battery", "call ", "text ", "sms", "email", "notify", "settings", "wifi",
                "bluetooth", "file", "workspace",
            ).any { it in lower } ->
                IntentCategory.DEVICE_ACTION
            else -> IntentCategory.CHAT
        }
    }

    suspend fun selectEngine(
        prompt: String,
        settings: JarvisSettings,
        telemetry: DeviceTelemetry,
    ): RoutingDecision {
        val intent = classifyIntent(prompt)
        val geminiHealth = geminiEngine.health()
        val localHealth = localEngine.health()
        return decide(
            intent = intent,
            settings = settings,
            telemetry = telemetry,
            geminiHealth = geminiHealth,
            localHealth = localHealth,
        )
    }

    suspend fun decide(
        intent: IntentCategory,
        settings: JarvisSettings,
        telemetry: DeviceTelemetry,
        geminiHealth: EngineHealth,
        localHealth: EngineHealth,
    ): RoutingDecision {
        // Strict privacy mode keeps all inference on-device.
        if (settings.privacyLocalOnly || settings.inferenceMode == InferenceMode.OFFLINE_LOCAL) {
            return RoutingDecision(
                selectedEngine = EngineType.LOCAL_LITERT_LM,
                modelId = settings.selectedLocalModelId,
                reason = if (settings.privacyLocalOnly) {
                    "Privacy mode enabled (on-device only)."
                } else {
                    "Offline Local LLM mode selected."
                },
                fallbackAllowed = false,
                intentCategory = intent,
            )
        }

        if (settings.inferenceMode == InferenceMode.ONLINE_GEMINI) {
            return RoutingDecision(
                selectedEngine = EngineType.GEMINI_CLOUD,
                modelId = settings.selectedGeminiModelId,
                reason = "Online Gemini mode selected.",
                fallbackAllowed = localHealth.available,
                intentCategory = intent,
            )
        }

        // AUTO hybrid selection:
        if (!telemetry.isOnline || !geminiHealth.available) {
            if (localHealth.available) {
                return RoutingDecision(
                    selectedEngine = EngineType.LOCAL_LITERT_LM,
                    modelId = settings.selectedLocalModelId,
                    reason = if (!telemetry.isOnline) {
                        "Device offline — switched automatically to Local LiteRT-LM."
                    } else {
                        "${geminiHealth.reason} — switched to Local LiteRT-LM."
                    },
                    fallbackAllowed = false,
                    intentCategory = intent,
                )
            }
            return RoutingDecision(
                selectedEngine = EngineType.GEMINI_CLOUD,
                modelId = settings.selectedGeminiModelId,
                reason = "Neither offline model nor online Gemini is ready (${geminiHealth.reason}).",
                fallbackAllowed = false,
                intentCategory = intent,
            )
        }

        // Both online Gemini and local LLM are healthy: apply device health & adaptive stats.
        if (localHealth.available) {
            if (telemetry.isThermalThrottled || telemetry.isPowerSaveMode) {
                return RoutingDecision(
                    selectedEngine = EngineType.GEMINI_CLOUD,
                    modelId = settings.selectedGeminiModelId,
                    reason = "Device thermal/battery saver active — offloading inference to Gemini Cloud.",
                    fallbackAllowed = true,
                    intentCategory = intent,
                )
            }

            // Use observed feedback & success statistics when enough samples exist.
            val stats = learningRepository.getStatsForIntent(intent)
            val localStat = stats.firstOrNull { it.engineType == EngineType.LOCAL_LITERT_LM }
            val cloudStat = stats.firstOrNull { it.engineType == EngineType.GEMINI_CLOUD }
            if (localStat != null && localStat.totalAttempts >= 3) {
                val localScore = learningRepository.utilityScore(localStat)
                val cloudScore = cloudStat?.let { learningRepository.utilityScore(it) } ?: 0.72
                if (localScore >= cloudScore && intent != IntentCategory.PLANNING) {
                    return RoutingDecision(
                        selectedEngine = EngineType.LOCAL_LITERT_LM,
                        modelId = settings.selectedLocalModelId,
                        reason = "Adaptive router chose Local LiteRT-LM (learned utility ${"%.2f".format(localScore)}).",
                        fallbackAllowed = true,
                        intentCategory = intent,
                    )
                }
            }

            // Default fast local preference for simple device actions/chat when local model is loaded & device is healthy.
            if (intent == IntentCategory.DEVICE_ACTION && telemetry.availableRamMb >= 2048L) {
                return RoutingDecision(
                    selectedEngine = EngineType.LOCAL_LITERT_LM,
                    modelId = settings.selectedLocalModelId,
                    reason = "Hybrid auto-selected on-device LiteRT-LM for low-latency device command.",
                    fallbackAllowed = true,
                    intentCategory = intent,
                )
            }
        }

        return RoutingDecision(
            selectedEngine = EngineType.GEMINI_CLOUD,
            modelId = settings.selectedGeminiModelId,
            reason = "Hybrid auto-selected Gemini Cloud for high-accuracy reasoning & tool calling.",
            fallbackAllowed = localHealth.available,
            intentCategory = intent,
        )
    }

    fun engineFor(type: EngineType): InferenceEngine = when (type) {
        EngineType.GEMINI_CLOUD -> geminiEngine
        EngineType.LOCAL_LITERT_LM -> localEngine
    }
}
