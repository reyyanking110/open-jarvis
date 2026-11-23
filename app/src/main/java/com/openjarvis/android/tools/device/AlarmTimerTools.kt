package com.openjarvis.android.tools.device

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.openjarvis.android.tools.AndroidTool
import com.openjarvis.android.tools.ToolExecutionResult
import com.openjarvis.android.tools.ToolParamSchema
import com.openjarvis.android.tools.ToolRiskLevel
import com.openjarvis.android.tools.ToolSpec
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject

class SetAlarmTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "set_alarm",
        description = "Create an alarm in the system Clock app at a given 24-hour hour and minute.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema("hour", "integer", "Hour of day (0-23).", required = true),
            ToolParamSchema("minute", "integer", "Minute of hour (0-59).", required = true),
            ToolParamSchema("label", "string", "Optional alarm label.", required = false),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val hour = args.int("hour")?.coerceIn(0, 23)
            ?: return ToolExecutionResult(spec.name, false, "Missing 'hour' (0-23).")
        val minute = args.int("minute")?.coerceIn(0, 59)
            ?: return ToolExecutionResult(spec.name, false, "Missing 'minute' (0-59).")
        val label = args.str("label") ?: "Open JARVIS Alarm"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "No Clock app found to handle alarm intents.",
                recoverable = false,
            )
        }
        context.startActivity(intent)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Scheduled alarm '%s' for %02d:%02d.".format(label, hour, minute),
            verified = true,
        )
    }
}

class SetTimerTool @Inject constructor() : AndroidTool {
    override val spec = ToolSpec(
        name = "set_timer",
        description = "Start a countdown timer in the system Clock app for a duration in seconds.",
        riskLevel = ToolRiskLevel.DEVICE_STATE,
        parameters = listOf(
            ToolParamSchema("seconds", "integer", "Duration in seconds (>0).", required = true),
            ToolParamSchema("label", "string", "Optional timer label.", required = false),
        ),
    )

    override suspend fun execute(context: Context, args: JsonObject): ToolExecutionResult {
        val seconds = args.int("seconds")?.takeIf { it > 0 }
            ?: return ToolExecutionResult(spec.name, false, "Parameter 'seconds' must be > 0.")
        val label = args.str("label") ?: "JARVIS Timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                summary = "No Clock app found to handle timer intents.",
                recoverable = false,
            )
        }
        context.startActivity(intent)
        return ToolExecutionResult(
            toolName = spec.name,
            success = true,
            summary = "Started ${seconds}s timer '$label'.",
            verified = true,
        )
    }
}
