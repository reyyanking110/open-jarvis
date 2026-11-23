package com.openjarvis.android.tools.native

import android.content.Context
import android.media.AudioManager
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

class VolumeControlTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "control_volume",
        description = "Read or set device audio stream volume percentage (0-100) for media, ring, alarm, or notification.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema(
                name = "stream",
                type = "string",
                description = "Audio stream name: media, ring, alarm, or notification.",
                required = true,
                enumValues = listOf("media", "ring", "alarm", "notification"),
            ),
            ToolParamSchema(
                name = "level_percent",
                type = "integer",
                description = "Target volume percentage from 0 to 100. Omit to just read current volume.",
                required = false,
            ),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val streamName = args.str("stream")?.lowercase() ?: "media"
        val streamType = resolveStream(streamName)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)

        val targetPercent = args.int("level_percent")
        if (targetPercent == null) {
            val cur = audioManager.getStreamVolume(streamType)
            val pct = ((cur.toFloat() / maxVol) * 100f).roundToInt()
            return ToolExecutionResult(
                toolName = spec.name,
                success = true,
                summary = "$streamName volume is currently $pct% ($cur/$maxVol).",
                verified = true,
            )
        }

        val clampedPct = targetPercent.coerceIn(0, 100)
        val targetIndex = ((clampedPct / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)

        val actualIndex = audioManager.getStreamVolume(streamType)
        val actualPct = ((actualIndex.toFloat() / maxVol) * 100f).roundToInt()
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Set $streamName volume to $actualPct% ($actualIndex/$maxVol).",
            verified = actualIndex == targetIndex,
        )
    }

    private fun resolveStream(stream: String): Int = when (stream) {
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        else -> AudioManager.STREAM_MUSIC
    }
}
