package com.openjarvis.android.ai.engine

import com.google.common.truth.Truth.assertThat
import com.openjarvis.android.core.config.JarvisSettings
import com.openjarvis.android.core.domain.model.DeviceTelemetry
import com.openjarvis.android.core.domain.model.EngineHealth
import com.openjarvis.android.core.domain.model.EngineType
import com.openjarvis.android.core.domain.model.InferenceMode
import com.openjarvis.android.core.domain.model.IntentCategory
import com.openjarvis.android.core.domain.model.RoutingStat
import com.openjarvis.android.data.repository.LearningRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HybridInferenceRouterTest {

    private val geminiEngine = mockk<GeminiCloudEngine>()
    private val localEngine = mockk<LiteRtLmEngine>()
    private val learningRepo = mockk<LearningRepository>()
    private val router = HybridInferenceRouter(geminiEngine, localEngine, learningRepo)

    @Test
    fun `offline device automatically switches to local LiteRT-LM when installed`() = runTest {
        coEvery { learningRepo.getStatsForIntent(any()) } returns emptyList()
        val decision = router.decide(
            intent = IntentCategory.CHAT,
            settings = JarvisSettings(inferenceMode = InferenceMode.AUTO),
            telemetry = DeviceTelemetry(isOnline = false, availableRamMb = 3072L),
            geminiHealth = EngineHealth(EngineType.GEMINI_CLOUD, false, "Device is offline"),
            localHealth = EngineHealth(EngineType.LOCAL_LITERT_LM, true, "Ready"),
        )
        assertThat(decision.selectedEngine).isEqualTo(EngineType.LOCAL_LITERT_LM)
        assertThat(decision.fallbackAllowed).isFalse()
    }

    @Test
    fun `privacy local only blocks cloud even when online`() = runTest {
        val decision = router.decide(
            intent = IntentCategory.PLANNING,
            settings = JarvisSettings(inferenceMode = InferenceMode.ONLINE_GEMINI, privacyLocalOnly = true),
            telemetry = DeviceTelemetry(isOnline = true),
            geminiHealth = EngineHealth(EngineType.GEMINI_CLOUD, true, "OK"),
            localHealth = EngineHealth(EngineType.LOCAL_LITERT_LM, true, "Ready"),
        )
        assertThat(decision.selectedEngine).isEqualTo(EngineType.LOCAL_LITERT_LM)
        assertThat(decision.fallbackAllowed).isFalse()
    }

    @Test
    fun `thermal throttling offloads inference to Gemini cloud`() = runTest {
        val decision = router.decide(
            intent = IntentCategory.DEVICE_ACTION,
            settings = JarvisSettings(inferenceMode = InferenceMode.AUTO),
            telemetry = DeviceTelemetry(isOnline = true, isThermalThrottled = true),
            geminiHealth = EngineHealth(EngineType.GEMINI_CLOUD, true, "OK"),
            localHealth = EngineHealth(EngineType.LOCAL_LITERT_LM, true, "Ready"),
        )
        assertThat(decision.selectedEngine).isEqualTo(EngineType.GEMINI_CLOUD)
        assertThat(decision.fallbackAllowed).isTrue()
    }

    @Test
    fun `adaptive learning stats favor local engine when utility exceeds cloud`() = runTest {
        val localStat = RoutingStat(
            intentCategory = IntentCategory.CHAT,
            engineType = EngineType.LOCAL_LITERT_LM,
            totalAttempts = 5,
            successCount = 5,
            positiveFeedbackCount = 4,
            negativeFeedbackCount = 0,
            avgLatencyMs = 450L,
        )
        val cloudStat = RoutingStat(
            intentCategory = IntentCategory.CHAT,
            engineType = EngineType.GEMINI_CLOUD,
            totalAttempts = 5,
            successCount = 4,
            positiveFeedbackCount = 2,
            negativeFeedbackCount = 1,
            avgLatencyMs = 1500L,
        )
        coEvery { learningRepo.getStatsForIntent(IntentCategory.CHAT) } returns listOf(localStat, cloudStat)
        every { learningRepo.utilityScore(localStat) } returns 0.92
        every { learningRepo.utilityScore(cloudStat) } returns 0.71

        val decision = router.decide(
            intent = IntentCategory.CHAT,
            settings = JarvisSettings(inferenceMode = InferenceMode.AUTO),
            telemetry = DeviceTelemetry(isOnline = true, availableRamMb = 3500L),
            geminiHealth = EngineHealth(EngineType.GEMINI_CLOUD, true, "OK"),
            localHealth = EngineHealth(EngineType.LOCAL_LITERT_LM, true, "Ready"),
        )
        assertThat(decision.selectedEngine).isEqualTo(EngineType.LOCAL_LITERT_LM)
    }
}
